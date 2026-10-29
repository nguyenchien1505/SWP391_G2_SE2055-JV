package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Gửi đơn xin nghỉ — BR-SCH-06 bản sửa M2, BR-SCH-07, BR-SCH-16, DM-07 (ngày nguyên, tính cả hai đầu).
 * Người gửi luôn là người đang đăng nhập; các luật theo ngày (báo trước, quỹ phép, trùng đơn) kiểm ở service.
 */
@Data
public class CreateLeaveRequestRequest {

    @NotNull(message = "Chọn loại nghỉ")
    private LeaveType leaveType;

    @NotNull(message = "Chọn ngày bắt đầu nghỉ")
    private LocalDate fromDate;

    @NotNull(message = "Chọn ngày kết thúc nghỉ")
    private LocalDate toDate;

    @NotBlank(message = "Nhập lý do nghỉ")
    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;
}
