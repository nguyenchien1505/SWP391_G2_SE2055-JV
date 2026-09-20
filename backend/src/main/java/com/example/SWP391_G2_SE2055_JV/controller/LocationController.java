package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.CreateLocationRequest;
import com.example.SWP391_G2_SE2055_JV.dto.LocationResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateLocationContactRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateLocationRequest;
import com.example.SWP391_G2_SE2055_JV.service.LocationService;
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
 * Location (khách sạn) — BR-ORG-02..05.
 *
 * <p>BR-ORG-03 chia đôi quyền: Giám đốc CRUD mọi Location trong Tenant, Manager chỉ sửa
 * thông tin vận hành qua {@code PATCH} và chỉ với Location của mình.
 *
 * <p>Không có endpoint đổi trạng thái vận hành: trạng thái đi theo việc Location có
 * Manager hay không, do luồng tạo/cho nghỉ việc tài khoản Manager lật (BR-ORG-02, DM-13).
 */
@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /**
     * Manager cần đọc để xem thông tin khách sạn mình đang vận hành.
     *
     * <p>Sắp xếp mặc định theo tên để danh sách khách sạn đọc được ngay; client đổi bằng
     * tham số {@code sort} (ví dụ {@code ?sort=createdAt,desc}).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<Page<LocationResponse>> getLocations(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(locationService.getLocations(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<LocationResponse> getLocationById(@PathVariable UUID id) {
        return ResponseEntity.ok(locationService.getLocationById(id));
    }

    /** BR-ORG-03: chỉ Giám đốc tạo Location. Location mới ở trạng thái "Chưa vận hành". */
    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<LocationResponse> createLocation(
            @Valid @RequestBody CreateLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(locationService.createLocation(request));
    }

    /** Sửa toàn bộ thông tin — chỉ Giám đốc (BR-ORG-03, BR-ORG-04). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<LocationResponse> updateLocation(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationRequest request) {
        return ResponseEntity.ok(locationService.updateLocation(id, request));
    }

    /** Thông tin vận hành (địa chỉ, SĐT) — phần Manager được sửa (BR-ORG-03). */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<LocationResponse> updateContactInfo(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationContactRequest request) {
        return ResponseEntity.ok(locationService.updateContactInfo(id, request));
    }

    /** BR-ORG-05: chặn cứng khi còn nhân sự hoặc phòng trực thuộc. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<Void> deleteLocation(@PathVariable UUID id) {
        locationService.deleteLocation(id);
        return ResponseEntity.noContent().build();
    }
}
