package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Giao CÙNG MỘT ca (cùng ngày, cùng giờ) cho một hoặc nhiều người.
 *
 * <p>DM-03 vẫn giữ nguyên: 1 ca = 1 slot cho đúng 1 người, nên N người là N bản ghi ca riêng, mỗi
 * người được kiểm tra Schedule Policy riêng (BR-SCH-02). Giờ chọn đúng một trong hai cách như
 * {@link CreateShiftRequest} (BR-SCH-04): {@code sourceTemplateId} hoặc {@code startTime} +
 * {@code endTime}.
 *
 * <p>Tất cả hoặc không: một người vi phạm thì không lưu ca nào.
 */
@Data
public class CreateShiftBatchRequest {

    @NotNull(message = "locationId là bắt buộc")
    private UUID locationId;

    @NotNull(message = "shiftDate là bắt buộc")
    private LocalDate shiftDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private UUID sourceTemplateId;

    /** Người được giao ca; trùng id thì chỉ tính một lần. Khách sạn 2–3 sao không quá 50 người một ca. */
    @Size(max = 50, message = "Mỗi lần giao tối đa 50 người")
    private List<@NotNull(message = "Mã nhân viên không được để trống") UUID> staffIds = new ArrayList<>();

    /** Số chỗ trống chưa giao người (DM-03) tạo thêm cùng lúc — không qua kiểm tra Policy. */
    @Min(value = 0, message = "Số chỗ trống không được âm")
    @Max(value = 20, message = "Mỗi lần mở tối đa 20 chỗ trống")
    private int openSlots;
}
