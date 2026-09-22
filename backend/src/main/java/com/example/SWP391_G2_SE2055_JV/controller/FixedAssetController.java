package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.asset.FixedAssetRequest;
import com.example.SWP391_G2_SE2055_JV.dto.asset.FixedAssetResponse;
import com.example.SWP391_G2_SE2055_JV.dto.asset.UpdateFixedAssetStatusRequest;
import com.example.SWP391_G2_SE2055_JV.service.FixedAssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Tài sản cố định — BR-ASSET-01..03, BR-ASSET-12..14.
 *
 * <p>Đặt dưới {@code /assets} để khớp rule đã có trong {@code SecurityConfig}: GET mở
 * tới STAFF, mọi method ghi giới hạn ở Manager trở lên.
 *
 * <p>Quyền GHI chỉ dành cho Manager — BR-ASSET-09 phân vai rõ: Giám đốc quản lý DANH
 * MỤC cấp Tenant, Manager quản lý từng TÀI SẢN cá thể trong Location của mình. Giám
 * đốc vẫn xem được (phạm vi toàn Tenant) nhưng không tạo/sửa.
 *
 * <p>PLATFORM_ADMIN không có mặt ở đây: vai trò này đứng ngoài mọi Tenant (BR-PERM-01)
 * nên {@code SecurityUtils.getCurrentTenantId()} sẽ ném lỗi ngay — mời vào rồi chặn ở
 * tầng service chỉ tạo ra lỗi 403 khó hiểu.
 */
@RestController
@RequestMapping("/assets/fixed-assets")
@RequiredArgsConstructor
public class FixedAssetController {

    private final FixedAssetService fixedAssetService;

    /**
     * Giám đốc nhận toàn Tenant, Manager và Staff chỉ nhận Location của mình — lọc ở
     * service.
     *
     * @param includeDisposed mặc định false: tài sản đã thanh lý ẩn khỏi danh sách vận
     *                        hành nhưng vẫn tra được khi cần (BR-ASSET-14)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<Page<FixedAssetResponse>> getFixedAssets(
            @RequestParam(defaultValue = "false") boolean includeDisposed,
            @PageableDefault(size = 20, sort = "assetCode", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(fixedAssetService.getFixedAssets(includeDisposed, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<FixedAssetResponse> getFixedAssetById(@PathVariable UUID id) {
        return ResponseEntity.ok(fixedAssetService.getFixedAssetById(id));
    }

    /** Location lấy từ session, không nhận từ body — BR-ASSET-13. */
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<FixedAssetResponse> createFixedAsset(
            @Valid @RequestBody FixedAssetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fixedAssetService.createFixedAsset(request));
    }

    /** Bao gồm cả đổi vị trí giữa Phòng/Khu vực trong cùng Location — BR-ASSET-13. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<FixedAssetResponse> updateFixedAsset(
            @PathVariable UUID id,
            @Valid @RequestBody FixedAssetRequest request) {
        return ResponseEntity.ok(fixedAssetService.updateFixedAsset(id, request));
    }

    /**
     * Đổi trạng thái — BR-ASSET-02 (Manager là người DUY NHẤT cập nhật trạng thái),
     * BR-ASSET-14. Thanh lý cũng đi qua đây với {@code status = DISPOSED}, vì thanh lý
     * là một trạng thái chứ không phải xóa bản ghi.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<FixedAssetResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFixedAssetStatusRequest request) {
        return ResponseEntity.ok(fixedAssetService.updateStatus(id, request.getStatus()));
    }
}
