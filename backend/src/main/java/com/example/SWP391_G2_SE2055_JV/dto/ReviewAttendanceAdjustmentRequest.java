package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Body (không bắt buộc khi duyệt) của {@code PATCH …/approve} và {@code …/reject}. Từ chối phải có lý do. */
@Data
public class ReviewAttendanceAdjustmentRequest {

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
