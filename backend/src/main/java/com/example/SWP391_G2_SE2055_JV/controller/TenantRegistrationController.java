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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

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
            .body(asAnonymous(() -> tenantRegistrationService.registerTenant(request)));
    }

    /**
     * Xác thực email của Giám đốc vừa đăng ký — công khai vì người dùng chưa đăng nhập được.
     * {@code POST /api/auth/verify-email}: 204 khi thành công; 400 khi mã sai hoặc đã dùng.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        asAnonymous(() -> {
            emailVerificationService.verify(request.getToken());
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    /**
     * Gửi lại email xác thực — công khai. Luôn trả 204 dù email có tồn tại hay không, để không
     * lộ email nào đã đăng ký.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        asAnonymous(() -> {
            emailVerificationService.resend(request.getEmail());
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    /**
     * Chạy như người CHƯA đăng nhập, kể cả khi trình duyệt còn phiên của tài khoản khác (tab
     * khác, cookie cũ). Nếu không, JPA Auditing ghi tài khoản đó vào created_by / updated_by của
     * Tenant và Giám đốc mới — sai nghĩa, và khóa ngoại tới tài khoản đó làm không xóa được dữ
     * liệu sau này.
     *
     * <p>Bọc NGOÀI transaction của service: updated_by được điền lúc flush khi commit, nên phải
     * giữ context rỗng cho tới khi service trả về.
     */
    private static <T> T asAnonymous(Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContextHolder.clearContext();
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
