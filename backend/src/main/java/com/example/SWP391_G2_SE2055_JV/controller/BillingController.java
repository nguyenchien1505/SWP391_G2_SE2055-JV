package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.BillingOverviewResponse;
import com.example.SWP391_G2_SE2055_JV.dto.InvoiceResponse;
import com.example.SWP391_G2_SE2055_JV.dto.PaymentResultResponse;
import com.example.SWP391_G2_SE2055_JV.dto.PaymentUrlResponse;
import com.example.SWP391_G2_SE2055_JV.dto.PurchaseRequest;
import com.example.SWP391_G2_SE2055_JV.service.BillingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Gói dịch vụ của Tenant — màn của Giám đốc.
 *
 * <p>{@code SecurityConfig} đã mở sẵn {@code /billing/**} cho Giám đốc, kể cả khi Tenant đang
 * chỉ đọc vì hết hạn dùng thử (để còn vào thanh toán). {@code @PreAuthorize} ở đây là lớp thứ
 * hai, và loại Admin Platform vì Admin không thuộc Tenant nào.
 */
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<BillingOverviewResponse> getOverview() {
        return ResponseEntity.ok(billingService.getOverview());
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<List<InvoiceResponse>> listInvoices() {
        return ResponseEntity.ok(billingService.listInvoices());
    }

    /**
     * Tenant mới chọn Dùng thử: bắt đầu dùng thử từ hôm nay. Trả {@code message} + {@code activated}
     * (cùng dạng kết quả với các thao tác thanh toán để FE xử lý chung).
     */
    @PostMapping("/start-trial")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<PaymentUrlResponse> startTrial() {
        return ResponseEntity.ok(billingService.startTrial());
    }

    /** Mua gói lần đầu: trả link sang trang thanh toán VNPay. */
    @PostMapping("/purchase")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<PaymentUrlResponse> purchase(@Valid @RequestBody PurchaseRequest request,
                                                       HttpServletRequest http) {
        return ResponseEntity.ok(billingService.purchase(request, clientIp(http)));
    }

    /** Mua thêm quota giữa kỳ: trả link VNPay cho phần chênh lệch (số lượng mới gửi trong body). */
    @PostMapping("/upgrade")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<PaymentUrlResponse> upgrade(@Valid @RequestBody PurchaseRequest request,
                                                      HttpServletRequest http) {
        return ResponseEntity.ok(billingService.upgrade(request, clientIp(http)));
    }

    /** Gia hạn 1 kỳ 30 ngày theo quota hiện tại: trả link sang trang thanh toán VNPay. */
    @PostMapping("/renew")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<PaymentUrlResponse> renew(HttpServletRequest http) {
        return ResponseEntity.ok(billingService.renew(clientIp(http)));
    }

    /**
     * FE chuyển nguyên các tham số {@code vnp_*} VNPay gắn vào trang kết quả lên đây (kèm phiên
     * đăng nhập của Giám đốc) để kiểm tra chữ ký và cập nhật hóa đơn / gói.
     */
    @GetMapping("/vnpay-return")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<PaymentResultResponse> vnpayReturn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(billingService.handleVnpayReturn(params));
    }

    /** VNPay nhận IPv4; chạy local trình duyệt hay gửi IPv6 ::1 nên đổi về 127.0.0.1. */
    private static String clientIp(HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        return ip == null || ip.contains(":") ? "127.0.0.1" : ip;
    }
}
