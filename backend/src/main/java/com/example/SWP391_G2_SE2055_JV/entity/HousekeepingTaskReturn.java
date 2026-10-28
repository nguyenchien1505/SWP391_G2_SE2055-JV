package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một lần người tự nhận trả lại việc dọn — BR-HK-17 (BR Milestone 2, chốt 28/10/2026).
 *
 * <p>Ghi cho MỌI lần trả. Khi nhóm còn người khác, việc và phòng đứng yên nên lý do không có chỗ nào khác
 * để nằm; khi nhóm hết người ({@code taskReleased}), lý do còn được ghi kèm dòng lịch sử phòng
 * «Đang dọn → Chờ dọn» (BR-ROOM-09).
 */
@Entity
@Table(name = "housekeeping_task_returns")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class HousekeepingTaskReturn extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "task_id", length = 36, nullable = false)
    private UUID taskId;

    /** Người trả — luôn là người đã tự nhận việc này (BR-HK-17). */
    @Column(name = "staff_id", length = 36, nullable = false)
    private UUID staffId;

    /** Bắt buộc, chữ tự do — BR-HK-17. */
    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Column(name = "returned_at", nullable = false)
    private LocalDateTime returnedAt;

    /** {@code true} = nhóm hết người: việc về Chưa phân công, phòng về Chờ dọn. */
    @Column(name = "task_released", nullable = false)
    private boolean taskReleased;
}
