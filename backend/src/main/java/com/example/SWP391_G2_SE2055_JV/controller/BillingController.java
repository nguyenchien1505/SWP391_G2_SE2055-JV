package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.BillingOverviewResponse;
import com.example.SWP391_G2_SE2055_JV.dto.InvoiceResponse;
import com.example.SWP391_G2_SE2055_JV.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
