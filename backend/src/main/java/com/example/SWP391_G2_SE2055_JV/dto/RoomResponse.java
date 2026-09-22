package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phòng trả về cho màn hình — BR-ROOM-05 (các trường thông tin phòng).
 *
 * <p>Kèm sẵn {@code roomTypeName} để S-02/S-06 hiển thị ngay, không phải gọi thêm API loại
 * phòng cho từng dòng (BR-ROOM-06). Nhãn tiếng Việt của {@code status} KHÔNG trả ở đây mà
 * nằm ở frontend ({@code pages/rooms/roomLabels.js}), theo cách codebase đang làm với các
 * trạng thái khác.
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

    /** @param roomTypeName tên loại phòng đã tra sẵn; {@code null} nếu không tìm thấy. */
    public static RoomResponse fromEntity(Room room, String roomTypeName) {
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
            .build();
    }
}
