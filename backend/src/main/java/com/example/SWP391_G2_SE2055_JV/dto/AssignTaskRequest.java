package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Gán nhân viên dọn cho một task đang chưa phân công — BR-HK-02, BR-HK-03, DM-04.
 *
 * <p>Task gắn với NGÀY chứ không gắn với ca: nhân viên chỉ cần có ca trong
 * {@code assignedDate}, không cần khớp khung giờ.
 */
@Data
public class AssignTaskRequest {

    @NotNull(message = "staffId là bắt buộc")
    private UUID staffId;

    @NotNull(message = "assignedDate là bắt buộc")
    private LocalDate assignedDate;
}
