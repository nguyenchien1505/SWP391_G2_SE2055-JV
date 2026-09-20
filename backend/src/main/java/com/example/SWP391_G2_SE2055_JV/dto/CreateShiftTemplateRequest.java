package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

/**
 * Tạo mẫu ca — BR-SCH-22: tên, giờ bắt đầu, giờ kết thúc, mô tả (tùy chọn), cờ is_active.
 *
 * <p>Mẫu mới luôn ở trạng thái đang dùng; bật/tắt đi qua endpoint riêng.
 *
 * <p>Giờ kết thúc KHÔNG cần lớn hơn giờ bắt đầu: mẫu ca đêm (22:00–06:00) là hợp lệ và
 * được tính trọn giờ vào ngày bắt đầu ca (BR-SCH-03).
 */
@Data
public class CreateShiftTemplateRequest {

    @NotBlank(message = "Tên mẫu ca là bắt buộc")
    @Size(max = 100, message = "Tên mẫu ca tối đa 100 ký tự")
    private String name;

    @NotNull(message = "Giờ bắt đầu là bắt buộc")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc là bắt buộc")
    private LocalTime endTime;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;
}
