package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Phòng trả về cho màn hình — BR-ROOM-05 (các trường thông tin phòng).
 *
 * <p>Kèm sẵn {@code roomTypeName} để S-02/S-06 hiển thị ngay, không phải gọi thêm API loại
 * phòng cho từng dòng (BR-ROOM-06). Nhãn tiếng Việt của {@code status} KHÔNG trả ở đây mà
 * nằm ở frontend ({@code pages/rooms/roomLabels.js}), theo cách codebase đang làm với các
 * trạng thái khác.
 *
 * <p>{@code allowedTargets} (F2) cho frontend biết vẽ nút nào, để FE không phải chép lại ma
 * trận BR-ROOM-02 và bảng phân quyền.
 */
@Data
@Builder
public class RoomResponse {

    private UUID          id;
    private UUID          locationId;
    private String        roomNumber;
    private String        floor;
    private UUID          roomTypeId;

    /** Có thể là loại phòng đã ẩn (BR-ORG-14) — phòng cũ vẫn giữ loại lịch sử. */
    private String        roomTypeName;

    private int           capacity;
    private String        note;
    private RoomStatus    status;

    /** Chỉ có giá trị khi {@code status = UNAVAILABLE} — BR-ROOM-07. */
    private String        unavailableReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Các trạng thái đích mà NGƯỜI ĐANG ĐĂNG NHẬP được bấm từ trạng thái hiện tại — BR-ROOM-02,
     * BR-ROOM-03. Rỗng nghĩa là không có nút đổi trạng thái nào (ví dụ Giám đốc, Dọn dẹp).
     * Chỉ để hiển thị: backend vẫn kiểm tra lại khi nhận {@code PATCH /rooms/{id}/status}.
     */
    private Set<RoomStatus> allowedTargets;

    /**
     * @param roomTypeName   tên loại phòng đã tra sẵn; {@code null} nếu không tìm thấy
     * @param allowedTargets đích người đang đăng nhập được bấm, do {@code RoomTransitionPolicy} tính
     */
    public static RoomResponse fromEntity(Room room, String roomTypeName, Set<RoomStatus> allowedTargets) {
        return RoomResponse.builder()
            .id(room.getId())
            .locationId(room.getLocationId())
            .roomNumber(room.getRoomNumber())
            .floor(room.getFloor())
            .roomTypeId(room.getRoomTypeId())
            .roomTypeName(roomTypeName)
            .capacity(room.getCapacity())
            .note(room.getNote())
            .status(room.getStatus())
            .unavailableReason(room.getUnavailableReason())
            .createdAt(room.getCreatedAt())
            .updatedAt(room.getUpdatedAt())
            .allowedTargets(allowedTargets)
            .build();
    }
}
