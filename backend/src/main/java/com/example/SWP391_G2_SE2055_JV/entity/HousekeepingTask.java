package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCancelReason;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Task dọn phòng — BR-HK-01..12, DM-04, DM-05.
 *
 * <p>Chỉ còn việc dọn sau khi khách trả phòng (CHECKOUT) — việc dọn hằng ngày đã bỏ (V4).
 * Task gắn với một NHÓM người dọn ({@code assigneeIds}) + {@code assignedDate}, KHÔNG gắn trực
 * tiếp vào Shift — mỗi người chỉ cần có ca trong ngày đó (BR-HK-03). Một phòng nhiều người dọn
 * thay cho "một task một người" của DM-04 (chốt 05/10/2026).
 *
 * <p>BR-HK-11 (mỗi phòng tối đa 1 task đang mở) được ép ở tầng DB bằng cột sinh
 * {@code open_task_key}; cột này CỐ Ý không map vào entity.
 */
@Entity
@Table(name = "housekeeping_tasks")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class HousekeepingTask extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "location_id", length = 36, nullable = false)
    private UUID locationId;

    @Column(name = "room_id", length = 36, nullable = false)
    private UUID roomId;

    /** Chỉ còn CHECKOUT (V4). */
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", length = 20, nullable = false)
    @lombok.Builder.Default
    private HousekeepingTaskType taskType = HousekeepingTaskType.CHECKOUT;

    /** BR-HK-06. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private HousekeepingTaskStatus status;

    /**
     * Nhóm người dọn — rỗng khi task đang UNASSIGNED (BR-HK-07), có từ một người trở lên khi đang
     * làm. Bất kỳ ai trong nhóm bấm "Hoàn thành" là xong cho cả nhóm.
     *
     * <p>LAZY + BatchSize như {@code User.permissions}: danh sách việc dọn nạp người theo lô.
     */
    @ElementCollection
    @CollectionTable(name = "housekeeping_task_assignees", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "staff_id", length = 36, nullable = false)
    @org.hibernate.annotations.BatchSize(size = 100)
    @lombok.Builder.Default
    private Set<UUID> assigneeIds = new LinkedHashSet<>();

    /** Cả nhóm dọn trong đúng ngày này; MỖI người phải có ca ngày đó — BR-HK-03. */
    @Column(name = "assigned_date")
    private LocalDate assignedDate;

    @Column(name = "assigned_by", length = 36)
    private UUID assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** CHECKOUT_AUTO | INSPECTION_FAILED — BR-HK-12. */
    @Enumerated(EnumType.STRING)
    @Column(name = "created_source", length = 30, nullable = false)
    private TaskCreatedSource createdSource;

    /** Trỏ về task gốc khi task này sinh do kiểm tra không đạt — BR-HK-12. */
    @Column(name = "parent_task_id", length = 36)
    private UUID parentTaskId;

    /** Lý do gỡ NGƯỜI — task quay về UNASSIGNED chứ không đóng — BR-HK-07. */
    @Enumerated(EnumType.STRING)
    @Column(name = "unassigned_reason", length = 30)
    private UnassignedReason unassignedReason;

    /** Lý do HỦY task — BR-HK-09 (phòng Không khả dụng). */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    private TaskCancelReason cancelReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** BR-HK-11: task đang mở mới chiếm chỗ của phòng cho loại task tương ứng. */
    public boolean isOpen() {
        return status != null && status.isOpen();
    }

    public boolean isAssigned() {
        return !assigneeIds.isEmpty();
    }

    public boolean isAssignedTo(UUID staffId) {
        return assigneeIds.contains(staffId);
    }
}
