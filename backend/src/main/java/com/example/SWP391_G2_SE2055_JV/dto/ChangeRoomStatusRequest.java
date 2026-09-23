package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code PATCH /rooms/{id}/status} — một endpoint cho mọi bước chuyển do người bấm
 * (BR-ROOM-02). Giá trị enum sai (ví dụ {@code "FOO"}) bị chặn ngay lúc đọc JSON → 400.
 */
@Data
public class ChangeRoomStatusRequest {

    @NotNull(message = "Chưa chọn trạng thái đích")
    private RoomStatus targetStatus;

    /**
     * Bắt buộc khi {@code targetStatus = UNAVAILABLE} (BR-ROOM-07) — ràng buộc có điều kiện nên
     * kiểm tra ở service. Các bước khác: tùy chọn, ghi vào lịch sử (ví dụ phân biệt hủy đặt với
     * no-show). 500 ký tự khớp cột {@code unavailable_reason} và {@code room_status_history.reason}.
     */
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
