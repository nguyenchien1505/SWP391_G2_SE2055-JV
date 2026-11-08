package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body (không bắt buộc) của {@code PATCH /housekeeping/tasks/{id}/complete} — lời nhắn cho người kiểm tra,
 * ví dụ "thiếu 1 gối, đã báo kho" (chốt 02/11/2026). Bỏ trống body hay để trống ô đều là không ghi chú.
 * 500 ký tự khớp cột {@code housekeeping_tasks.completion_note}.
 */
@Data
public class CompleteTaskRequest {

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
