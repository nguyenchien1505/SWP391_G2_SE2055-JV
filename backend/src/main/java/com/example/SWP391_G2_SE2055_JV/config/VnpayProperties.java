package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cấu hình VNPay sandbox, đọc từ {@code backend/.env} (đã nằm trong .gitignore):
 * <pre>
 *   VNPAY_TMN_CODE=...      mã website do VNPay cấp
 *   VNPAY_HASH_SECRET=...   khóa ký HMAC-SHA512 — KHÔNG ghi vào code, KHÔNG commit
 *   VNPAY_PAY_URL=...       (tùy chọn) mặc định là trang thanh toán sandbox
 *   VNPAY_RETURN_URL=...    (tùy chọn) trang FE nhận kết quả, mặc định /thanh-toan/ket-qua
 *   VNPAY_API_URL=...       (tùy chọn) API tra cứu giao dịch, mặc định là sandbox
 * </pre>
 * Thiếu mã hoặc khóa thì backend vẫn khởi động; chỉ báo lỗi khi có người bấm thanh toán.
 */
@Getter
@Component
public class VnpayProperties {

    @Value("${VNPAY_TMN_CODE:}")
    private String tmnCode;

    @Value("${VNPAY_HASH_SECRET:}")
    private String hashSecret;

    @Value("${VNPAY_PAY_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    @Value("${VNPAY_RETURN_URL:http://localhost:3000/thanh-toan/ket-qua}")
    private String returnUrl;

    /** API server-gọi-server của VNPay (tra cứu giao dịch — querydr). */
    @Value("${VNPAY_API_URL:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}")
    private String apiUrl;

    public void requireConfigured() {
        if (tmnCode.isBlank() || hashSecret.isBlank()) {
            throw new BusinessException(
                "Chưa cấu hình VNPay: thêm VNPAY_TMN_CODE và VNPAY_HASH_SECRET vào backend/.env.");
        }
    }
}
