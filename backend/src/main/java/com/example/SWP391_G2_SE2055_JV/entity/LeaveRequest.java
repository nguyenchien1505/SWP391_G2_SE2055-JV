package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Đơn xin nghỉ — BR-SCH-06, BR-SCH-07, BR-SCH-16.
 *
 * <p>DM-07: nghỉ tính theo NGÀY NGUYÊN, không hỗ trợ nửa ngày. Duyệt đơn sẽ tự gỡ
 * người khỏi các ca trùng khoảng nghỉ với lý do {@code LEAVE_APPROVED} (BR-SCH-07).
 *
 * <p>Người duyệt theo cấp: Manager duyệt đơn của Staff, Giám đốc duyệt đơn của
 * Manager — BR-SCH-08.
 */
@Entity
@Table(name = "leave_requests")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class LeaveRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "location_id", length = 36, nullable = false)
    private UUID locationId;

    @Column(name = "requester_id", length = 36, nullable = false)
    private UUID requesterId;

    /** Ngày nguyên, không nửa ngày — DM-07. */
    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    /** Bắt buộc nhập — BR-SCH-16. */
    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private LeaveRequestStatus status;

    /** NULL khi đơn còn PENDING hoặc bị người gửi tự hủy — BR-SCH-08. */
    @Column(name = "approver_id", length = 36)
    private UUID approverId;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** BR-SCH-16: chỉ đơn PENDING mới được người gửi tự hủy. */
    public boolean isPending() {
        return status == LeaveRequestStatus.PENDING;
    }
}
