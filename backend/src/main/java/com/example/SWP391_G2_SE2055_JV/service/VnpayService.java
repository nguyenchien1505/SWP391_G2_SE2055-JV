package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.config.VnpayProperties;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Tích hợp VNPay (phiên bản 2.1.0): tạo link thanh toán có chữ ký, kiểm tra chữ ký kết quả, và
 * tra cứu giao dịch (querydr) cho các lần thanh toán bị bỏ dở.
 *
 * <p>Chữ ký = HMAC-SHA512(khóa bí mật, dữ liệu). VNPay ký CẢ số tiền, nên sửa bất kỳ tham số nào
 * trên URL (số tiền, mã giao dịch, mã kết quả) đều làm chữ ký sai.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VnpayService {

    /** Mã VNPay: giao dịch / truy vấn thành công. */
    public static final String CODE_SUCCESS = "00";
    /** Mã kết quả VNPay: khách hàng tự hủy giao dịch. */
    public static final String CODE_CANCELLED = "24";

    public static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** Link thanh toán hết hạn sau khoảng này nếu Giám đốc không thanh toán. */
    public static final int EXPIRE_MINUTES = 15;

    private final VnpayProperties props;
    private final ObjectMapper    objectMapper;
    private final HttpClient      httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /**
     * Kết quả tra cứu một giao dịch.
     *
     * @param answered        VNPay trả lời hợp lệ (đúng chữ ký). {@code false} = lỗi mạng / phản hồi lạ → chưa kết luận được
     * @param responseCode    mã của lần TRA CỨU: 00 có giao dịch, 91 không tìm thấy giao dịch
     * @param transactionStatus trạng thái GIAO DỊCH: 00 thành công, 01 chưa hoàn tất, 02 lỗi, 07 nghi ngờ gian lận…
     * @param amountVnd       số tiền giao dịch (đã chia 100), -1 nếu không có
     */
    public record QueryResult(boolean answered, String responseCode, String transactionStatus,
                              long amountVnd, String transactionNo) {

        static QueryResult unknown() {
            return new QueryResult(false, null, null, -1, null);
        }
    }

    /**
     * @param txnRef     mã giao dịch phía hệ thống — mỗi hóa đơn một mã
     * @param amountVnd  số tiền VND (VNPay yêu cầu nhân 100)
     * @param orderInfo  mô tả đơn hàng, nên không dấu
     * @param createDate thời điểm tạo giao dịch (giờ Việt Nam) — cũng là ngày dùng để tra cứu sau này
     */
    public String buildPaymentUrl(String txnRef, long amountVnd, String orderInfo, String clientIp,
                                  LocalDateTime createDate) {
        props.requireConfigured();

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", props.getTmnCode());
        params.put("vnp_Amount", String.valueOf(amountVnd * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", props.getReturnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", createDate.format(VNP_TIME));
        params.put("vnp_ExpireDate", createDate.plusMinutes(EXPIRE_MINUTES).format(VNP_TIME));

        String query = encode(params);
        return props.getPayUrl() + "?" + query + "&vnp_SecureHash=" + sign(query);
    }

    /** Chữ ký trong kết quả VNPay trả về có khớp với khóa bí mật của hệ thống không. */
    public boolean verify(Map<String, String> returned) {
        String received = returned.get("vnp_SecureHash");
        if (received == null || received.isBlank() || props.getHashSecret().isBlank()) {
            return false;
        }
        Map<String, String> params = new TreeMap<>();
        returned.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")
                    && value != null && !value.isEmpty()) {
                params.put(key, value);
            }
        });
        return sameHash(sign(encode(params)), received);
    }

    /**
     * Hỏi VNPay một giao dịch đã thanh toán chưa (API querydr, server gọi server — chạy được cả khi
     * hệ thống ở localhost). Mọi lỗi mạng / phản hồi sai chữ ký đều trả {@link QueryResult#unknown()}
     * để nơi gọi KHÔNG kết luận gì và thử lại sau.
     *
     * @param transactionDate {@code vnp_CreateDate} của giao dịch gốc
     */
    public QueryResult queryTransaction(String txnRef, LocalDateTime transactionDate) {
        if (props.getTmnCode().isBlank() || props.getHashSecret().isBlank()) {
            return QueryResult.unknown();
        }
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String createDate = LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE).format(VNP_TIME);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", "2.1.0");
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", props.getTmnCode());
        body.put("vnp_TxnRef", txnRef);
        body.put("vnp_OrderInfo", "Tra cuu giao dich " + txnRef);
        body.put("vnp_TransactionDate", transactionDate.format(VNP_TIME));
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", "127.0.0.1");
        // Thứ tự ghép theo tài liệu querydr của VNPay.
        body.put("vnp_SecureHash", sign(String.join("|", body.get("vnp_RequestId"), body.get("vnp_Version"),
            body.get("vnp_Command"), body.get("vnp_TmnCode"), body.get("vnp_TxnRef"),
            body.get("vnp_TransactionDate"), body.get("vnp_CreateDate"), body.get("vnp_IpAddr"),
            body.get("vnp_OrderInfo"))));

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(props.getApiUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
            JsonNode res = objectMapper.readTree(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());

            // Chữ ký phản hồi: các trường ghép bằng "|", trường không có thì để chuỗi rỗng.
            String responseData = String.join("|", text(res, "vnp_ResponseId"), text(res, "vnp_Command"),
                text(res, "vnp_ResponseCode"), text(res, "vnp_Message"), text(res, "vnp_TmnCode"),
                text(res, "vnp_TxnRef"), text(res, "vnp_Amount"), text(res, "vnp_BankCode"),
                text(res, "vnp_PayDate"), text(res, "vnp_TransactionNo"), text(res, "vnp_TransactionType"),
                text(res, "vnp_TransactionStatus"), text(res, "vnp_OrderInfo"), text(res, "vnp_PromotionCode"),
                text(res, "vnp_PromotionAmount"));
            if (!sameHash(sign(responseData), text(res, "vnp_SecureHash"))) {
                log.warn("Phản hồi tra cứu VNPay sai chữ ký cho giao dịch {}", txnRef);
                return QueryResult.unknown();
            }

            long amount = text(res, "vnp_Amount").isEmpty() ? -1 : Long.parseLong(text(res, "vnp_Amount")) / 100;
            return new QueryResult(true, text(res, "vnp_ResponseCode"), text(res, "vnp_TransactionStatus"),
                amount, text(res, "vnp_TransactionNo"));
        } catch (IOException | NumberFormatException e) {
            log.warn("Không tra cứu được giao dịch VNPay {}: {}", txnRef, e.getMessage());
            return QueryResult.unknown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return QueryResult.unknown();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    /** So sánh thời gian cố định để không lộ thông tin về chữ ký đúng. */
    private static boolean sameHash(String expected, String received) {
        if (received == null || received.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
            received.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    /** Cùng cách mã hóa với code mẫu của VNPay: tên=giá trị (US-ASCII), nối bằng &. */
    private static String encode(Map<String, String> sortedParams) {
        StringJoiner joiner = new StringJoiner("&");
        sortedParams.forEach((key, value) -> joiner.add(
            URLEncoder.encode(key, StandardCharsets.US_ASCII) + "=" + URLEncoder.encode(value, StandardCharsets.US_ASCII)));
        return joiner.toString();
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(props.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không ký được dữ liệu VNPay", e);
        }
    }
}
