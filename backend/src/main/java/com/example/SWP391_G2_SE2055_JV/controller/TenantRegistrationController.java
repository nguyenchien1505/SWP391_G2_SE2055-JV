package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.RegisterTenantRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RegisterTenantResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ResendVerificationRequest;
import com.example.SWP391_G2_SE2055_JV.dto.VerifyEmailRequest;
import com.example.SWP391_G2_SE2055_JV.service.EmailVerificationService;
import com.example.SWP391_G2_SE2055_JV.service.TenantRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant tự đăng ký — BR-SAAS-13.
 *
 * <p>Đường dẫn thực tế: {@code POST /api/auth/register-tenant}.
 *
 * <p>Endpoint này CÔNG KHAI có chủ ý: người đăng ký chưa có tài khoản nên không thể đăng
 * nhập trước. Vì vậy KHÔNG có {@code @PreAuthorize} ở đây; quyền truy cập do
 * {@code PUBLIC_ENDPOINTS} trong {@code SecurityConfig} quyết định (đã khai báo sẵn
 * {@code /auth/register-tenant}). Mọi endpoint quản lý Tenant khác đều chỉ dành cho
 * Admin Platform.
 *
 * <p>Trả 201 khi thành công; 422 khi DTO sai (kèm {@code fieldErrors}); 400 khi email đã
 * tồn tại hoặc thiếu cấu hình; 409 khi hai request trùng email chạy đồng thời.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class TenantRegistrationController {

    private final TenantRegistrationService tenantRegistrationService;
    private final EmailVerificationService  emailVerificationService;

    @PostMapping("/register-tenant")
    public ResponseEntity<RegisterTenantResponse> registerTenant(
            @Valid @RequestBody RegisterTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(tenantRegistrationService.registerTenant(request));
    }

    /**
     * Xác thực email của Giám đốc vừa đăng ký — công khai vì người dùng chưa đăng nhập được.
     * {@code POST /api/auth/verify-email}: 204 khi thành công; 400 khi mã sai hoặc đã dùng.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.getToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Gửi lại email xác thực — công khai. Luôn trả 204 dù email có tồn tại hay không, để không
     * lộ email nào đã đăng ký.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.getEmail());
        return ResponseEntity.noContent().build();
    }
}
