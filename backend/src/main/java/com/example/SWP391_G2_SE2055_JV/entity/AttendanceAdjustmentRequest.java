package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.AdjustmentRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Đơn điều chỉnh công — nhân viên đề nghị sửa giờ vào / ra của ca mình (chấm công M2, chốt 01/11/2026). Quản lý
 * của khách sạn duyệt thì giờ được ghi như quản lý nhập; đơn đã xử lý là lịch sử, không xóa.
 */
@Entity
@Table(name = "attendance_adjustment_requests")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AttendanceAdjustmentRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    /** Khách sạn của ca — quản lý khách sạn này duyệt. */
    @Column(name = "location_id", length = 36, nullable = false)
    private UUID locationId;

    @Column(name = "shift_id", length = 36, nullable = false)
    private UUID shiftId;

    @Column(name = "requester_id", length = 36, nullable = false)
    private UUID requesterId;

    /** Giờ khách sạn; null = giữ giờ vào đang có. */
    @Column(name = "requested_check_in_at")
    private LocalDateTime requestedCheckInAt;

    /** Giờ khách sạn; null = giữ giờ ra đang có. */
    @Column(name = "requested_check_out_at")
    private LocalDateTime requestedCheckOutAt;

    @Column(name = "reason", length = 400, nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AdjustmentRequestStatus status;

    @Column(name = "reviewer_id", length = 36)
    private UUID reviewerId;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** Lý do từ chối (bắt buộc) hoặc ghi chú khi duyệt. */
    @Column(name = "review_note", length = 500)
    private String reviewNote;

    public boolean isPending() {
        return status == AdjustmentRequestStatus.PENDING;
    }
}
