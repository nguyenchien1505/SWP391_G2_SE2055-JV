package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.CreatePricingConfigRequest;
import com.example.SWP391_G2_SE2055_JV.dto.PricingConfigResponse;
import com.example.SWP391_G2_SE2055_JV.dto.SystemConfigResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateSystemConfigRequest;
import com.example.SWP391_G2_SE2055_JV.service.PlatformConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cấu hình nền tảng — chỉ Admin Platform (BR-PERM-01).
 *
 * <p>Đường dẫn thực tế có thêm context-path: {@code /api/platform/...}.
 *
 * <p>Hai lớp chặn quyền cùng tồn tại có chủ ý: rule URL {@code /platform/**} trong
 * {@code SecurityConfig} là lớp thô, {@code @PreAuthorize} ở đây (cần
 * {@code @EnableMethodSecurity}, đã bật) là lớp phòng thủ thứ hai nếu ai đó sửa nhầm rule URL.
 *
 * <pre>
 *   GET  /platform/system-config    xem số ngày dùng thử / ân hạn
 *   PUT  /platform/system-config    sửa (áp cho Tenant đăng ký từ đây về sau)
 *   GET  /platform/pricing          lịch sử bảng giá, mới nhất trước
 *   GET  /platform/pricing/current  bảng giá đang hiệu lực hôm nay
 *   POST /platform/pricing          thêm bảng giá mới (chỉ thêm, không sửa/xóa)
 * </pre>
 *
 * <p>Lỗi trả về: 422 khi DTO sai định dạng (kèm {@code fieldErrors}), 400 khi vi phạm
 * quy tắc nghiệp vụ, 403 khi không phải Admin, 409 khi đụng UNIQUE ở DB.
 */
@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
public class PlatformConfigController {

    private final PlatformConfigService platformConfigService;

    @GetMapping("/system-config")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<SystemConfigResponse> getSystemConfig() {
        return ResponseEntity.ok(platformConfigService.getSystemConfig());
    }

    @PutMapping("/system-config")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<SystemConfigResponse> updateSystemConfig(
            @Valid @RequestBody UpdateSystemConfigRequest request) {
        return ResponseEntity.ok(platformConfigService.updateSystemConfig(request));
    }

    @GetMapping("/pricing")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<PricingConfigResponse>> listPricing() {
        return ResponseEntity.ok(platformConfigService.listPricing());
    }

    @GetMapping("/pricing/current")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<PricingConfigResponse> getCurrentPricing() {
        return ResponseEntity.ok(platformConfigService.getCurrentPricing());
    }

    @PostMapping("/pricing")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<PricingConfigResponse> createPricing(
            @Valid @RequestBody CreatePricingConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformConfigService.createPricing(request));
    }
}
