package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Body của {@code PUT /scheduling/shifts/{id}/attendance} — Manager nhập bù hoặc sửa giờ vào / ra ca (chấm
 * công M2). Trường bỏ trống là giữ nguyên giờ đang có; M2 không xóa trắng giờ đã ghi.
 */
@Data
public class ManagerAttendanceRequest {

    /** Giờ khách sạn. */
    private LocalDateTime checkInAt;

    /** Giờ khách sạn. */
    private LocalDateTime checkOutAt;

    @NotBlank(message = "Nhập lý do nhập / sửa giờ chấm công")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String note;
}
