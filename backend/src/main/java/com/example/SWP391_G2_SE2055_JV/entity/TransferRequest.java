package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.TransferRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Yêu cầu điều chuyển nhân sự giữa hai Location trong cùng Tenant — BR-TRF-01..07.
 *
 * <p>Manager gửi, Giám đốc duyệt. Duyệt xong KHÔNG chuyển ngay: chờ tới
 * {@code effectiveDate} mới có job đổi Location và gỡ ca tương lai ở Location cũ
 * (BR-TRF-04, BR-TRF-05).
 */
@Entity
@Table(name = "transfer_requests")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class TransferRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "target_user_id", length = 36, nullable = false)
    private UUID targetUserId;

    @Column(name = "from_location_id", length = 36, nullable = false)
    private UUID fromLocationId;

    @Column(name = "to_location_id", length = 36, nullable = false)
    private UUID toLocationId;

    /** BẮT BUỘC khi người được chuyển là Manager — BR-TRF-03, DM-14. */
    @Column(name = "replacement_manager_id", length = 36)
    private UUID replacementManagerId;

    /** Phải ở tương lai tại thời điểm tạo — BR-TRF-02. */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TransferRequestStatus status;

    @Column(name = "requester_id", length = 36, nullable = false)
    private UUID requesterId;

    @Column(name = "approver_id", length = 36)
    private UUID approverId;

    /** Mốc Giám đốc duyệt / từ chối. */
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** Mốc job chạy tại effective date — dùng để bảo đảm idempotent, BR-TRF-05. */
    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    /** Manager chỉ được tự hủy yêu cầu của mình khi còn PENDING — BR-TRF-07. */
    public boolean isPending() {
        return status == TransferRequestStatus.PENDING;
    }

    /** BR-TRF-05: job bỏ qua bản ghi đã chạy để không chuyển hai lần. */
    public boolean isExecuted() {
        return executedAt != null;
    }
}
