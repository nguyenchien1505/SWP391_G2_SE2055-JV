package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.SwapRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Yêu cầu đổi ca giữa hai nhân viên — BR-SCH-09..12, BR-SCH-18, BR-SCH-19.
 *
 * <p>Luồng hai bước: đồng nghiệp đồng ý trước (PENDING_PEER → PENDING_MANAGER), sau
 * đó Manager duyệt. Duyệt xong là hoán đổi trực tiếp người được gán của 2 ca, không
 * tạo ca mới (BR-SCH-18).
 *
 * <p>Tên cột theo ERD ({@code requester_*} / {@code peer_*}) thay cho
 * {@code staff_a}/{@code staff_b} của DM-08 — cùng ngữ nghĩa, tên ERD rõ hơn.
 *
 * <p>BR-SCH-19 (mỗi ca tối đa 1 yêu cầu đang treo) được ép ở tầng DB bằng 2 cột sinh
 * {@code pending_requester_shift_id} / {@code pending_peer_shift_id} — CỐ Ý không map
 * vào entity.
 */
@Entity
@Table(name = "shift_swap_requests")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ShiftSwapRequest extends AuditableEntity {

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

    @Column(name = "requester_shift_id", length = 36, nullable = false)
    private UUID requesterShiftId;

    /** Phải cùng ĐÚNG MỘT Position với người gửi — BR-SCH-12. */
    @Column(name = "peer_id", length = 36, nullable = false)
    private UUID peerId;

    @Column(name = "peer_shift_id", length = 36, nullable = false)
    private UUID peerShiftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private SwapRequestStatus status;

    @Column(name = "peer_responded_at")
    private LocalDateTime peerRespondedAt;

    /** Chốt tại thời điểm TẠO yêu cầu theo policy; quá hạn thì job set EXPIRED — BR-SCH-11, BR-SCH-21. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "approver_id", length = 36)
    private UUID approverId;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** BR-SCH-19: yêu cầu còn treo (PENDING_PEER hoặc PENDING_MANAGER). */
    public boolean isPending() {
        return status != null && status.isPending();
    }
}
