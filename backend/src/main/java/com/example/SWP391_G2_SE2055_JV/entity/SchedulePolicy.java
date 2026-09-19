package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bộ ràng buộc xếp ca của một Tenant — BR-SCH-01, BR-SCH-02, BR-SCH-20, BR-SCH-21.
 *
 * <p>DM-18: mỗi Tenant ĐÚNG 1 bản ghi (unique trên {@code tenant_id}), sửa đè trực
 * tiếp, KHÔNG lưu lịch sử phiên bản. Giá trị mới chỉ áp cho ca xếp từ thời điểm sửa
 * trở đi, ca đã xếp trước đó giữ nguyên (BR-SCH-20).
 */
@Entity
@Table(name = "schedule_policies")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SchedulePolicy extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    /** DM-18: unique — mỗi Tenant đúng một bộ chính sách. */
    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "max_hours_per_day", nullable = false, precision = 4, scale = 2)
    @lombok.Builder.Default
    private BigDecimal maxHoursPerDay = new BigDecimal("8.00");

    @Column(name = "max_hours_per_week", nullable = false, precision = 5, scale = 2)
    @lombok.Builder.Default
    private BigDecimal maxHoursPerWeek = new BigDecimal("48.00");

    /** Đếm theo SỐ NGÀY liên tiếp có ca, không đếm số ca — BR-SCH-14. */
    @Column(name = "max_consecutive_shifts", nullable = false)
    @lombok.Builder.Default
    private int maxConsecutiveShifts = 6;

    @Column(name = "min_rest_hours_between_shifts", nullable = false, precision = 4, scale = 2)
    @lombok.Builder.Default
    private BigDecimal minRestHoursBetweenShifts = new BigDecimal("12.00");

    @Column(name = "min_days_off_per_week", nullable = false)
    @lombok.Builder.Default
    private int minDaysOffPerWeek = 1;

    /** Hạn phản hồi yêu cầu đổi ca, chốt tại lúc TẠO yêu cầu — BR-SCH-11, BR-SCH-21. */
    @Column(name = "swap_response_timeout_hours", nullable = false)
    @lombok.Builder.Default
    private int swapResponseTimeoutHours = 24;
}
