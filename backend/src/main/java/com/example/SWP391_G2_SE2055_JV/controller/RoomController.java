package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.ChangeRoomStatusRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RoomResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusHistoryResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusSummaryResponse;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Phòng — BR-ROOM-*.
 *
 * <p>Path bị ràng buộc bởi rule URL có sẵn trong {@code SecurityConfig}: {@code GET /rooms/**}
 * cho cả 4 role, {@code PATCH /rooms/{id}/status} cho Manager và Lễ tân. Phạm vi dữ liệu
 * (Tenant, Location) và "ai được bấm bước nào" kiểm tra ở tầng service.
 */
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    /** Mọi người trong Tenant đều xem được phòng — Lễ tân, Dọn dẹp cần sơ đồ phòng để làm việc. */
    private static final String ANY_TENANT_ROLE =
        "hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')";

    /**
     * Khớp rule URL của {@code PATCH /rooms/*}{@code /status}: Manager (khóa/mở khóa — BR-ROOM-03)
     * và Position loại Lễ tân (BR-PERM-04). Giám đốc, Dọn dẹp, Position Khác bị chặn từ đây.
     */
    private static final String CAN_CHANGE_STATUS =
        "hasAnyRole('PLATFORM_ADMIN','MANAGER') or hasAuthority('POSITION_RECEPTION')";

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

    /** S-05 Lịch sử trạng thái — BR-ROOM-09. Mới nhất trước, mặc định 20 dòng/trang. */
    @GetMapping("/{id}/history")
    @PreAuthorize(ANY_TENANT_ROLE)
    public ResponseEntity<Page<RoomStatusHistoryResponse>> getHistory(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(roomService.getHistory(id, pageable));
    }

    /**
     * Đổi trạng thái phòng — BR-ROOM-02. ĐÚNG MỘT endpoint cho mọi bước người bấm được (khóa,
     * mở khóa, đặt trước, check-in, check-out…), vì rule URL đã viết riêng cho path này.
     *
     * <p>Lỗi: 400 (ngoài ma trận, bước của task dọn, thiếu lý do khóa, enum sai), 403 (không được
     * bấm bước này), 404 (phòng ngoài phạm vi), 422 (thiếu {@code targetStatus}, lý do quá dài).
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize(CAN_CHANGE_STATUS)
    public ResponseEntity<RoomResponse> changeStatus(@PathVariable UUID id,
                                                     @Valid @RequestBody ChangeRoomStatusRequest request) {
        return ResponseEntity.ok(roomService.changeStatus(id, request));
    }
}
