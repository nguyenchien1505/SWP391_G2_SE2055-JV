package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Một ca làm việc — DM-03: 1 ca = 1 slot cho ĐÚNG 1 người.
 *
 * <p>Không có bảng ShiftAssignment và không có trạng thái nháp: gỡ người khỏi ca là
 * set {@code staffId = null} kèm {@code unassignedReason}, KHÔNG xóa bản ghi
 * (BR-SCH-24). Kiểm tra trùng ca giới hạn trong phạm vi một Location (BR-SCH-05).
 *
 * <p>DM-15: mốc check-in/check-out nằm ngay trên bảng này, không có bảng AttendanceLog.
 */
@Entity
@Table(name = "shifts")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Shift extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    /** Phạm vi kiểm tra trùng ca — BR-SCH-05. */
    @Column(name = "location_id", length = 36, nullable = false)
    private UUID locationId;

    /** NULL = ca chưa phân công hoặc đã bị gỡ người — DM-03. */
    @Column(name = "staff_id", length = 36)
    private UUID staffId;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "is_overnight", nullable = false)
    @lombok.Builder.Default
    private boolean overnight = false;

    /** Ca qua đêm tính TRỌN số giờ vào ngày bắt đầu — BR-SCH-03. */
    @Column(name = "duration_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal durationHours;

    /** NULL = Manager tạo ca tự do, không theo mẫu — BR-SCH-04. */
    @Column(name = "source_template_id", length = 36)
    private UUID sourceTemplateId;

    /** Chỉ ghi nhận timestamp, Milestone 1 không tính đi muộn — BR-DASH-01. */
    @Column(name = "check_in_at")
    private LocalDateTime checkInAt;

    @Column(name = "check_out_at")
    private LocalDateTime checkOutAt;

    /** Bắt buộc có khi staffId = null — BR-SCH-24. */
    @Enumerated(EnumType.STRING)
    @Column(name = "unassigned_reason", length = 30)
    private UnassignedReason unassignedReason;

    @Column(name = "unassigned_at")
    private LocalDateTime unassignedAt;

    /** DM-03: ca đã có người phụ trách. */
    public boolean isAssigned() {
        return staffId != null;
    }

    public boolean isCheckedIn() {
        return checkInAt != null;
    }
}
