package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Tạo một ca. BR-SCH-04: dùng chung cho cả hai cách tạo — chọn Shift Template
 * (truyền {@code sourceTemplateId}) hoặc Manager tự nhập giờ (ca tự do). Cả hai
 * đều đi qua kiểm tra Schedule Policy.
 */
@Data
public class CreateShiftRequest {

    @NotNull(message = "locationId là bắt buộc")
    private UUID locationId;

    /** Bỏ trống để tạo ca CHƯA PHÂN CÔNG — DM-03. */
    private UUID staffId;

    @NotNull(message = "shiftDate là bắt buộc")
    private LocalDate shiftDate;

    @NotNull(message = "startTime là bắt buộc")
    private LocalTime startTime;

    @NotNull(message = "endTime là bắt buộc")
    private LocalTime endTime;

    /** Bỏ trống nghĩa là ca tự do, không theo mẫu — BR-SCH-04. */
    private UUID sourceTemplateId;
}
