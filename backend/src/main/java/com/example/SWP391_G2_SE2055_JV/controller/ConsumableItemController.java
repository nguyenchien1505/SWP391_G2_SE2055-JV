package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.asset.AddConsumableItemRequest;
import com.example.SWP391_G2_SE2055_JV.dto.asset.ConsumableItemResponse;
import com.example.SWP391_G2_SE2055_JV.dto.asset.StockCountRequest;
import com.example.SWP391_G2_SE2055_JV.service.ConsumableItemService;
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

import java.util.List;
import java.util.UUID;

/**
 * Tồn kho tài sản tiêu hao — BR-ASSET-04, BR-ASSET-07, BR-ASSET-10, DM-11.
 *
 * <p>{@code SecurityConfig} có rule riêng cho {@code /assets/consumables/**} đứng
 * TRƯỚC "GET /assets/**": Staff KHÔNG được xem kho (khác Tài sản cố định, nơi Staff
 * xem được). Quyền GHI chỉ dành cho Manager — BR-ASSET-09 giao Manager quản lý từng
 * tài sản/tồn kho trong Location của mình; Giám đốc chỉ đọc.
 */
@RestController
@RequestMapping("/assets/consumables")
@RequiredArgsConstructor
public class ConsumableItemController {

    private final ConsumableItemService consumableItemService;

    /**
     * Giám đốc nhận toàn Tenant, Manager chỉ nhận Location của mình — lọc ở service.
     *
     * @param outOfStock true để chỉ xem mặt hàng hết hàng (BR-ASSET-10: không có
     *                   ngưỡng cảnh báo, chỉ phân biệt còn hàng / hết hàng)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('DIRECTOR','MANAGER')")
    public ResponseEntity<Page<ConsumableItemResponse>> getConsumables(
            @RequestParam(required = false) Boolean outOfStock,
            @PageableDefault(size = 20, sort = "categoryId", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(consumableItemService.getConsumables(outOfStock, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DIRECTOR','MANAGER')")
    public ResponseEntity<ConsumableItemResponse> getConsumableById(@PathVariable UUID id) {
        return ResponseEntity.ok(consumableItemService.getConsumableById(id));
    }

    /** Location lấy từ session, không nhận từ body — BR-ASSET-13 (cùng lối với FixedAsset). */
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ConsumableItemResponse> addConsumableItem(
            @Valid @RequestBody AddConsumableItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consumableItemService.addConsumableItem(request));
    }

    /** Kiểm kê theo đợt — nhiều dòng trong 1 request, 1 transaction (BR-ASSET-07). */
    @PutMapping("/stock-count")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<ConsumableItemResponse>> stockCount(
            @Valid @RequestBody StockCountRequest request) {
        return ResponseEntity.ok(consumableItemService.stockCount(request));
    }

    /** Chỉ xóa được khi tồn kho hiện tại bằng 0. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteConsumableItem(@PathVariable UUID id) {
        consumableItemService.deleteConsumableItem(id);
        return ResponseEntity.noContent().build();
    }
}
