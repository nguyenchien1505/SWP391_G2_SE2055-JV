package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.RegisterTenantRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RegisterTenantResponse;
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

    @PostMapping("/register-tenant")
    public ResponseEntity<RegisterTenantResponse> registerTenant(
            @Valid @RequestBody RegisterTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(tenantRegistrationService.registerTenant(request));
    }
}
