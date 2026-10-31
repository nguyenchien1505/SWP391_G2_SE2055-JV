package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Body của {@code POST /scheduling/attendance-adjustments} — nhân viên đề nghị sửa giờ vào / ra ca của mình. Giờ
 * bỏ trống là giữ giờ đang có; phải có ít nhất một giờ.
 */
@Data
public class CreateAttendanceAdjustmentRequest {

    @NotNull(message = "Chọn ca cần điều chỉnh")
    private UUID shiftId;

    /** Giờ khách sạn. */
    private LocalDateTime checkInAt;

    /** Giờ khách sạn. */
    private LocalDateTime checkOutAt;

    /** 400 ký tự: khi duyệt, lý do được ghi kèm tiền tố vào cột note (500) của lịch sử chấm công. */
    @NotBlank(message = "Nhập lý do điều chỉnh")
    @Size(max = 400, message = "Lý do tối đa 400 ký tự")
    private String reason;
}
