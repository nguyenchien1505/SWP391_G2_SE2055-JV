package com.example.SWP391_G2_SE2055_JV.controller;

<<<<<<< HEAD
import com.example.SWP391_G2_SE2055_JV.dto.AreaResponse;
import com.example.SWP391_G2_SE2055_JV.dto.CreateAreaRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateAreaRequest;
=======
import com.example.SWP391_G2_SE2055_JV.dto.organization.AreaRequest;
import com.example.SWP391_G2_SE2055_JV.dto.organization.AreaResponse;
>>>>>>> Nguyen
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
<<<<<<< HEAD
 * Khu vực — cấp Location, do Manager CRUD (BR-ORG-12).
 *
 * <p>Đây là ngoại lệ duy nhất trong nhóm {@code /organization}: ba danh mục còn lại thuộc
 * cấp Tenant và chỉ Giám đốc được sửa, còn khu vực là đặc thù vật lý từng khách sạn nên
 * Manager tự quản. Rule phân quyền tương ứng nằm ở SecurityConfig.
=======
 * Khu vực — BR-ORG-12, BR-ORG-13, BR-ORG-15.
 *
 * <p>Đặt dưới {@code /organization} vì đây vẫn là một danh mục tổ chức, nhưng
 * {@code SecurityConfig} có rule RIÊNG cho {@code /organization/areas/**} đứng TRƯỚC
 * rule chung của {@code /organization/**} — vì Khu vực cấp LOCATION do Manager CRUD,
 * ngược với 3 danh mục cấp Tenant còn lại (Department/Position/RoomType, chỉ Giám đốc
 * ghi). Đổi đường dẫn ở đây thì phải đổi cả rule tương ứng trong SecurityConfig.
>>>>>>> Nguyen
 */
@RestController
@RequestMapping("/organization/areas")
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

<<<<<<< HEAD
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
=======
    /** Giám đốc nhận toàn Tenant, Manager chỉ nhận Location của mình — lọc ở service. */
    @GetMapping
    @PreAuthorize("hasAnyRole('DIRECTOR','MANAGER')")
    public ResponseEntity<Page<AreaResponse>> getAreas(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(areaService.getAreas(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DIRECTOR','MANAGER')")
>>>>>>> Nguyen
    public ResponseEntity<AreaResponse> getAreaById(@PathVariable UUID id) {
        return ResponseEntity.ok(areaService.getAreaById(id));
    }

<<<<<<< HEAD
    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<AreaResponse> createArea(@Valid @RequestBody CreateAreaRequest request) {
=======
    /** Location lấy từ session, không nhận từ body — BR-ORG-12 chỉ giao Manager CRUD. */
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<AreaResponse> createArea(@Valid @RequestBody AreaRequest request) {
>>>>>>> Nguyen
        return ResponseEntity.status(HttpStatus.CREATED).body(areaService.createArea(request));
    }

    @PutMapping("/{id}")
<<<<<<< HEAD
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<AreaResponse> updateArea(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAreaRequest request) {
        return ResponseEntity.ok(areaService.updateArea(id, request));
    }

    /** BR-ORG-15: chặn cứng khi còn tài sản cố định gắn vào. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
=======
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<AreaResponse> updateArea(
            @PathVariable UUID id,
            @Valid @RequestBody AreaRequest request) {
        return ResponseEntity.ok(areaService.updateArea(id, request));
    }

    /** BR-ORG-15: service chặn nếu còn tài sản cố định gắn vào. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
>>>>>>> Nguyen
    public ResponseEntity<Void> deleteArea(@PathVariable UUID id) {
        areaService.deleteArea(id);
        return ResponseEntity.noContent().build();
    }
}
