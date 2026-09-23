package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.asset.AssetCategoryResponse;
import com.example.SWP391_G2_SE2055_JV.dto.asset.CreateAssetCategoryRequest;
import com.example.SWP391_G2_SE2055_JV.dto.asset.UpdateAssetCategoryRequest;
import com.example.SWP391_G2_SE2055_JV.enums.AssetKind;
import com.example.SWP391_G2_SE2055_JV.service.AssetCategoryService;
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
 * Danh mục tài sản — BR-ASSET-08, BR-ASSET-09.
 *
 * <p>Đặt dưới {@code /organization} vì đây là danh mục cấp TENANT, cùng nhóm với
 * Department / Position / RoomType. {@code SecurityConfig} đã có sẵn rule cho tiền tố
 * này và trích dẫn thẳng BR-ASSET-09: GET mở cho cả Manager (để chọn khi tạo tài sản),
 * còn mọi method ghi chỉ Giám đốc. Đổi tiền tố là rơi xuống {@code anyRequest()
 * .authenticated()} và mất toàn bộ lớp chặn theo URL.
 */
@RestController
@RequestMapping("/organization/asset-categories")
@RequiredArgsConstructor
public class AssetCategoryController {

    private final AssetCategoryService categoryService;

    /**
     * @param assetKind lọc FIXED / CONSUMABLE, bỏ trống để lấy cả hai
     * @param active    bỏ trống để lấy CẢ danh mục đang ẩn — Giám đốc phải thấy chúng
     *                  thì mới bật lại được (BR-ORG-14)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('DIRECTOR','MANAGER')")
    public ResponseEntity<Page<AssetCategoryResponse>> getCategories(
            @RequestParam(required = false) AssetKind assetKind,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(categoryService.getCategories(assetKind, active, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DIRECTOR','MANAGER')")
    public ResponseEntity<AssetCategoryResponse> getCategoryById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    /** Chỉ Giám đốc — BR-ASSET-09, BR-PERM-02. Manager KHÔNG tạo danh mục cấp Tenant. */
    @PostMapping
    @PreAuthorize("hasAnyRole('DIRECTOR')")
    public ResponseEntity<AssetCategoryResponse> createCategory(
            @Valid @RequestBody CreateAssetCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DIRECTOR')")
    public ResponseEntity<AssetCategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetCategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    /** Ẩn / hiện lại danh mục — BR-ORG-14. Đây mới là thao tác thay cho việc xóa. */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('DIRECTOR')")
    public ResponseEntity<AssetCategoryResponse> setActive(
            @PathVariable UUID id,
            @RequestParam boolean value) {
        return ResponseEntity.ok(categoryService.setActive(id, value));
    }

    /** Xóa cứng, chỉ khi không còn tài sản / tồn kho nào tham chiếu tới. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DIRECTOR')")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
