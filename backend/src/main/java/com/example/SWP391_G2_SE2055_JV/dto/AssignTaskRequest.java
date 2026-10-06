package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Giao việc dọn cho MỘT HOẶC NHIỀU người — BR-HK-02, BR-HK-03. Một phòng có thể nhiều người cùng
 * dọn (chốt 05/10/2026, thay DM-04).
 *
 * <p>Việc đang chưa phân công: nhóm này bắt đầu dọn. Việc đang làm: thêm những người này vào nhóm,
 * và {@code assignedDate} phải đúng ngày của việc đó — cả nhóm dọn cùng một ngày.
 *
 * <p>Task gắn với NGÀY chứ không gắn với ca: mỗi người chỉ cần có ca trong {@code assignedDate},
 * không cần khớp khung giờ.
 */
@Data
public class AssignTaskRequest {

    @NotEmpty(message = "Chọn ít nhất một người dọn")
    @Size(max = 10, message = "Mỗi lần giao tối đa 10 người")
    private List<@NotNull(message = "Mã nhân viên không được để trống") UUID> staffIds;

    @NotNull(message = "assignedDate là bắt buộc")
    private LocalDate assignedDate;
}
