package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class ShiftResponse {

    private UUID             id;
    private UUID             locationId;
    private UUID             staffId;
    private LocalDate        shiftDate;
    private LocalTime        startTime;
    private LocalTime        endTime;
    private boolean          overnight;
    private BigDecimal       durationHours;
    private UUID             sourceTemplateId;
    /**
     * Tên mẫu ca của ca này (null với ca tự nhập giờ) — trả kèm để nhân viên, vốn không đọc được danh
     * mục mẫu ca, vẫn hiện được "Ca sáng" trên lịch của mình.
     */
    private String           sourceTemplateName;
    private LocalDateTime    checkInAt;
    private LocalDateTime    checkOutAt;
    private UnassignedReason unassignedReason;
    private LocalDateTime    unassignedAt;
    private LocalDateTime    createdAt;

    public static ShiftResponse fromEntity(Shift shift) {
        return fromEntity(shift, null);
    }

    /** @param templateName tên mẫu của {@code shift.sourceTemplateId}; null nếu ca không theo mẫu */
    public static ShiftResponse fromEntity(Shift shift, String templateName) {
        return ShiftResponse.builder()
            .id(shift.getId())
            .locationId(shift.getLocationId())
            .staffId(shift.getStaffId())
            .shiftDate(shift.getShiftDate())
            .startTime(shift.getStartTime())
            .endTime(shift.getEndTime())
            .overnight(shift.isOvernight())
            .durationHours(shift.getDurationHours())
            .sourceTemplateId(shift.getSourceTemplateId())
            .sourceTemplateName(templateName)
            .checkInAt(shift.getCheckInAt())
            .checkOutAt(shift.getCheckOutAt())
            .unassignedReason(shift.getUnassignedReason())
            .unassignedAt(shift.getUnassignedAt())
            .createdAt(shift.getCreatedAt())
            .build();
    }
}
