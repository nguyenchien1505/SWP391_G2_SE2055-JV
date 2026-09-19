package com.example.SWP391_G2_SE2055_JV.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Cập nhật một phần thông tin ca. Trường nào để null thì giữ nguyên.
 *
 * <p>Không đổi người phụ trách qua đây — dùng endpoint assign/unassign riêng để
 * lý do gỡ ca luôn được ghi lại (BR-SCH-24).
 */
@Data
public class UpdateShiftRequest {

    private LocalDate shiftDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private UUID      sourceTemplateId;
}
