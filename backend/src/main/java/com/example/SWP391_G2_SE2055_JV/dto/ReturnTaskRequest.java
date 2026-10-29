package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code PATCH /housekeeping/tasks/{id}/return} — người tự nhận trả lại việc (BR-HK-17).
 *
 * <p>Lý do bắt buộc, chữ tự do (không có danh mục — giống lý do kiểm tra không đạt). 500 ký tự khớp cột
 * {@code housekeeping_task_returns.reason} và {@code room_status_history.reason}.
 */
@Data
public class ReturnTaskRequest {

    @NotBlank(message = "Nhập lý do trả lại việc")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
