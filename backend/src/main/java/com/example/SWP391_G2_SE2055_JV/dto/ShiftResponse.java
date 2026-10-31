package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceStatusResolver.AttendanceView;
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
    /**
     * Chấm công suy ra lúc đọc (chấm công M2) — null với ca chưa có người. Kèm khung giờ để giao diện hiện
     * «Mở lúc …» và chỉ bật nút «Vào ca» khi {@code OPEN}, không tự tính lại luật.
     */
    private AttendanceStatus attendanceStatus;
    private Long             lateMinutes;
    private Long             earlyLeaveMinutes;
    private LocalDateTime    checkInOpensAt;
    private LocalDateTime    checkOutClosesAt;
    private UnassignedReason unassignedReason;
    private LocalDateTime    unassignedAt;
    private LocalDateTime    createdAt;

    public static ShiftResponse fromEntity(Shift shift) {
        return fromEntity(shift, null);
    }

    /** @param templateName tên mẫu của {@code shift.sourceTemplateId}; null nếu ca không theo mẫu */
    public static ShiftResponse fromEntity(Shift shift, String templateName) {
        return fromEntity(shift, templateName, null);
    }

    /** @param attendance chấm công của ca; null thì bỏ trống các trường chấm công */
    public static ShiftResponse fromEntity(Shift shift, String templateName, AttendanceView attendance) {
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
            .attendanceStatus(attendance == null ? null : attendance.status())
            .lateMinutes(attendance == null ? null : attendance.lateMinutes())
            .earlyLeaveMinutes(attendance == null ? null : attendance.earlyLeaveMinutes())
            .checkInOpensAt(attendance == null ? null : attendance.checkInOpensAt())
            .checkOutClosesAt(attendance == null ? null : attendance.checkOutClosesAt())
            .unassignedReason(shift.getUnassignedReason())
            .unassignedAt(shift.getUnassignedAt())
            .createdAt(shift.getCreatedAt())
            .build();
    }
}
