package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code PATCH /rooms/{id}} — phần thông tin VẬN HÀNH mà Manager được sửa
 * (BR-ROOM-04).
 *
 * <p>Chỉ có ghi chú. Số phòng, tầng, loại phòng và sức chứa là thông tin cấu trúc, thuộc quyền
 * Giám đốc (Q1 chốt 22/09/2026); trạng thái đi theo {@code PATCH /rooms/{id}/status}.
 *
 * <p>Bỏ trống hoặc gửi toàn khoảng trắng nghĩa là XÓA ghi chú — khác với các DTO "bỏ trống là
 * giữ nguyên", vì đây là trường duy nhất của endpoint nên không có gì để giữ.
 */
@Data
public class UpdateRoomOperationalRequest {

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
