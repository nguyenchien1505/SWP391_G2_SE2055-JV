package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.AreaResponse;
import com.example.SWP391_G2_SE2055_JV.dto.CreateAreaRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateAreaRequest;
import com.example.SWP391_G2_SE2055_JV.service.AreaService;
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
 * Khu vực — cấp Location, do Manager CRUD (BR-ORG-12).
 *
 * <p>Đây là ngoại lệ duy nhất trong nhóm {@code /organization}: ba danh mục còn lại thuộc
 * cấp Tenant và chỉ Giám đốc được sửa, còn khu vực là đặc thù vật lý từng khách sạn nên
 * Manager tự quản. Rule phân quyền tương ứng nằm ở SecurityConfig.
 */
@RestController
@RequestMapping("/organization/areas")
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    /**
     * @param locationId Manager bỏ trống để lấy khu vực của Location mình đang vận hành;
     *                   Giám đốc bắt buộc chỉ rõ đang xem khách sạn nào.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<Page<AreaResponse>> getAreas(
            @RequestParam(required = false) UUID locationId,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(areaService.getAreas(locationId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<AreaResponse> getAreaById(@PathVariable UUID id) {
        return ResponseEntity.ok(areaService.getAreaById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<AreaResponse> createArea(@Valid @RequestBody CreateAreaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(areaService.createArea(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<AreaResponse> updateArea(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAreaRequest request) {
        return ResponseEntity.ok(areaService.updateArea(id, request));
    }

    /** BR-ORG-15: chặn cứng khi còn tài sản cố định gắn vào. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<Void> deleteArea(@PathVariable UUID id) {
        areaService.deleteArea(id);
        return ResponseEntity.noContent().build();
    }
}
