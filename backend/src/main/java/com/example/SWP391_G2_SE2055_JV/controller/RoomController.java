package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.RoomResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusSummaryResponse;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Phòng — BR-ROOM-*.
 *
 * <p>Path bị ràng buộc bởi rule URL có sẵn trong {@code SecurityConfig}: {@code GET /rooms/**}
 * cho cả 4 role, còn đổi trạng thái sẽ là {@code PATCH /rooms/{id}/status} (F2). Phạm vi
 * dữ liệu (Tenant, Location) kiểm tra ở {@link RoomService}.
 */
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    /** Mọi người trong Tenant đều xem được phòng — Lễ tân, Dọn dẹp cần sơ đồ phòng để làm việc. */
    private static final String ANY_TENANT_ROLE =
        "hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')";

    private final RoomService roomService;

    /**
     * S-02 / S-06. Sắp xếp mặc định theo tầng rồi số phòng; client đổi bằng {@code sort}.
     * Tham số enum sai (ví dụ {@code status=FOO}) trả 400 — xem GlobalExceptionHandler.
     */
    @GetMapping
    @PreAuthorize(ANY_TENANT_ROLE)
    public ResponseEntity<Page<RoomResponse>> getRooms(
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) RoomStatus status,
            @RequestParam(required = false) String floor,
            @RequestParam(required = false) UUID roomTypeId,
            @PageableDefault(size = 20, sort = {"floor", "roomNumber"}, direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(roomService.getRooms(locationId, status, floor, roomTypeId, pageable));
    }

    /**
     * BR-DASH-02, BR-DASH-03: đếm theo đủ 7 trạng thái. Khai báo TRƯỚC {@code /{id}} cho dễ
     * đọc — Spring vẫn ưu tiên path cố định hơn path có biến nên hai endpoint không lẫn nhau.
     */
    @GetMapping("/status-summary")
    @PreAuthorize(ANY_TENANT_ROLE)
    public ResponseEntity<RoomStatusSummaryResponse> getStatusSummary(
            @RequestParam(required = false) UUID locationId) {
        return ResponseEntity.ok(roomService.getStatusSummary(locationId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(ANY_TENANT_ROLE)
    public ResponseEntity<RoomResponse> getRoomById(@PathVariable UUID id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }
}
