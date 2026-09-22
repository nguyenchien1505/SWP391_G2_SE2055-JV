package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.CreatePositionRequest;
import com.example.SWP391_G2_SE2055_JV.dto.PositionResponse;
import com.example.SWP391_G2_SE2055_JV.dto.SetActiveRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdatePositionRequest;
import com.example.SWP391_G2_SE2055_JV.service.PositionService;
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
 * Chức danh — danh mục cấp Tenant, chỉ Giám đốc tạo (BR-ORG-06).
 *
 * <p>Manager chỉ đọc để chọn Position khi tạo Staff; Department suy ra từ Position nên
 * không có API chọn Department riêng (BR-ORG-07).
 */
@RestController
@RequestMapping("/organization/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    /**
     * @param includeInactive true để quản trị danh mục (thấy cả mục đã ẩn); mặc định false
     *                        cho danh sách chọn — BR-ORG-14.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<Page<PositionResponse>> getPositions(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(positionService.getPositions(includeInactive, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<PositionResponse> getPositionById(@PathVariable UUID id) {
        return ResponseEntity.ok(positionService.getPositionById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<PositionResponse> createPosition(
            @Valid @RequestBody CreatePositionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(positionService.createPosition(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<PositionResponse> updatePosition(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePositionRequest request) {
        return ResponseEntity.ok(positionService.updatePosition(id, request));
    }

    /** BR-ORG-14: ẩn/hiện thay cho xóa. */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<PositionResponse> setActive(
            @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest request) {
        return ResponseEntity.ok(positionService.setActive(id, request.getActive()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<Void> deletePosition(@PathVariable UUID id) {
        positionService.deletePosition(id);
        return ResponseEntity.noContent().build();
    }
}
