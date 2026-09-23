package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.ChangeRoomStatusRequest;
import com.example.SWP391_G2_SE2055_JV.dto.CreateRoomRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RoomResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusHistoryResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusSummaryResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateRoomOperationalRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateRoomRequest;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Phòng — BR-ROOM-*.
 *
 * <p>Path bị ràng buộc bởi rule URL có sẵn trong {@code SecurityConfig}: {@code GET /rooms/**}
 * cho cả 4 role, {@code PATCH /rooms/{id}/status} cho Manager và Lễ tân, {@code POST} và
 * {@code DELETE} chỉ cho Giám đốc. Phạm vi dữ liệu (Tenant, Location) và "ai được bấm bước
 * nào" kiểm tra ở tầng service.
 *
 * <p>Riêng {@code PUT /rooms/{id}} rơi vào rule chung {@code /rooms/**} nên tầng URL cho
 * Manager đi qua; {@code @PreAuthorize} bên dưới mới là chốt chặn BR-ROOM-04. Hai tầng này
 * cố ý không giống nhau: rule URL phải đủ rộng cho {@code PATCH /rooms/{id}} của Manager.
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

    /** BR-ROOM-04: chỉ Giám đốc tạo / sửa cấu trúc / xóa phòng, trên MỌI khách sạn của chuỗi. */
    private static final String CAN_MANAGE_ROOM = "hasAnyRole('PLATFORM_ADMIN','DIRECTOR')";

    /** BR-ROOM-04: Manager chỉ sửa được thông tin vận hành; Giám đốc cũng dùng được endpoint này. */
    private static final String CAN_EDIT_OPERATIONAL =
        "hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')";

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

    // ── F3: CRUD phòng ──────────────────────────────────────────────────────

    /**
     * RM-02 Giám đốc tạo phòng — BR-ROOM-04. Phòng mới luôn vào «Chờ dọn» và tự sinh một việc
     * dọn chưa phân công (BR-ROOM-10, BR-HK-01); client không gửi {@code status}.
     *
     * <p>Lỗi: 400 (loại phòng đã ẩn, số phòng trùng, hết hạn mức gói dịch vụ), 403 (Manager, Lễ
     * tân — chặn ngay ở tầng URL), 404 (khách sạn hoặc loại phòng không thuộc chuỗi của mình),
     * 422 (thiếu trường bắt buộc, sức chứa ≤ 0).
     */
    @PostMapping
    @PreAuthorize(CAN_MANAGE_ROOM)
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(request));
    }

    /**
     * RM-03 Giám đốc sửa thông tin cấu trúc — BR-ROOM-04, BR-ROOM-05. Không đổi được trạng thái
     * và khách sạn của phòng.
     */
    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE_ROOM)
    public ResponseEntity<RoomResponse> updateRoom(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateRoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }

    /**
     * RM-04 Manager sửa thông tin vận hành (ghi chú) — BR-ROOM-04. Giám đốc cũng dùng được.
     * Phòng ngoài Location của Manager trả 404.
     */
    @PatchMapping("/{id}")
    @PreAuthorize(CAN_EDIT_OPERATIONAL)
    public ResponseEntity<RoomResponse> updateOperational(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoomOperationalRequest request) {
        return ResponseEntity.ok(roomService.updateOperational(id, request));
    }

    /**
     * RM-05 Giám đốc xóa phòng — BR-ROOM-08. Xóa mềm, và chỉ khi phòng đang «Trống / Sẵn sàng»
     * hoặc «Không khả dụng», không còn việc dọn đang mở và không còn tài sản cố định gắn vào;
     * vi phạm bất kỳ điều nào trả 400 kèm câu nêu rõ điều kiện chưa đạt.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_MANAGE_ROOM)
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }
}
