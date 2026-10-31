package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Người duyệt hủy phần ngày chưa tới của đơn nghỉ đã duyệt — BR-SCH-16 bản sửa 28/10/2026. Lý do bắt buộc: người
 * gửi đọc được vì sao kỳ nghỉ đã duyệt bị cắt.
 */
@Data
public class RevokeLeaveRequestRequest {

    @NotBlank(message = "Nhập lý do hủy")
    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;
}
