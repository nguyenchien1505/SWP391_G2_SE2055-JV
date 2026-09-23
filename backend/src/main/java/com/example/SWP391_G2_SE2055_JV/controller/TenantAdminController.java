package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.LocationResponse;
import com.example.SWP391_G2_SE2055_JV.dto.TenantDetailResponse;
import com.example.SWP391_G2_SE2055_JV.dto.TenantResponse;
import com.example.SWP391_G2_SE2055_JV.dto.TenantUsageResponse;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.service.LocationService;
import com.example.SWP391_G2_SE2055_JV.service.TenantAdminService;
import com.example.SWP391_G2_SE2055_JV.service.TenantUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Quản lý Tenant — chỉ Admin Platform (BR-PERM-01). Đường dẫn thực tế: {@code /api/platform/tenants}.
 *
 * <p>Hai lớp chặn quyền cùng tồn tại có chủ ý: rule URL {@code /platform/**} trong
 * {@code SecurityConfig} là lớp thô, {@code @PreAuthorize} ở đây là lớp phòng thủ thứ hai.
 *
 * <pre>
 *   GET  /platform/tenants?status=&amp;keyword=&amp;page=&amp;size=   danh sách phân trang (page bắt đầu từ 0)
 *   GET  /platform/tenants/{id}                            chi tiết + tóm tắt gói dịch vụ
 *   POST /platform/tenants/{id}/suspend                    khóa Tenant (lý do ADMIN_LOCKED)
 *   POST /platform/tenants/{id}/reactivate                 mở lại Tenant — chỉ khi đang SUSPENDED do ADMIN_LOCKED;
 *                                                          về TRIAL nếu còn dùng thử, về ACTIVE nếu đã trả phí
 *   GET  /platform/tenants/{id}/usage                      mức sử dụng (Location, Staff, Phòng) so với quota
 * </pre>
 *
 * <p>Không có API sửa tên/SĐT: Admin không có quyền này. Khóa/mở lại trả 200 kèm chi tiết
 * Tenant; 400 khi trạng thái hiện tại không cho phép; 404 khi id không tồn tại.
 */
@RestController
@RequestMapping("/platform/tenants")
@RequiredArgsConstructor
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;
    private final TenantUsageService tenantUsageService;
    private final LocationService    locationService;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Page<TenantResponse>> search(
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(tenantAdminService.search(status, keyword, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TenantDetailResponse> getTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantAdminService.getDetail(id));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TenantDetailResponse> suspend(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantAdminService.suspendTenant(id));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TenantDetailResponse> reactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantAdminService.reactivateTenant(id));
    }

    /** Chỉ xem được theo từng Tenant; không có danh sách tổng hợp. */
    @GetMapping("/{id}/usage")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TenantUsageResponse> getUsage(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantUsageService.getUsage(id));
    }

    /** Danh sách Location của Tenant — dùng cho màn hình chi tiết Tenant ở Admin Platform. */
    @GetMapping("/{id}/locations")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Page<LocationResponse>> getLocations(
            @PathVariable UUID id,
            @PageableDefault(size = 100, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(locationService.getLocationsForTenant(id, pageable));
    }
}
