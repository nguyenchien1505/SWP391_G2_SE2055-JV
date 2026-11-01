package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Người duyệt từ chối đơn nghỉ đang chờ duyệt — BR-SCH-08, BR-SCH-23. Lý do bắt buộc (chốt 31/10/2026): người gửi đọc
 * được vì sao đơn không được duyệt.
 */
@Data
public class RejectLeaveRequestRequest {

    @NotBlank(message = "Nhập lý do từ chối")
    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;
}
