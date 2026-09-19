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
import java.util.UUID;

/**
 * Task dọn phòng — BR-HK-01..12, DM-04, DM-05.
 *
 * <p>DM-05: hai loại CHECKOUT và STAYOVER dùng CHUNG một bảng, phân biệt bằng
 * {@code taskType}. DM-04: task gắn với {@code assignedStaffId} + {@code assignedDate},
 * KHÔNG gắn trực tiếp vào Shift — nhân viên chỉ cần có ca trong ngày đó (BR-HK-03).
 *
 * <p>BR-HK-11 (mỗi phòng tối đa 1 task đang mở cho mỗi loại) được ép ở tầng DB bằng
 * cột sinh {@code open_task_key}; cột này CỐ Ý không map vào entity.
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

    /** CHECKOUT | STAYOVER — BR-HK-05. */
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", length = 20, nullable = false)
    private HousekeepingTaskType taskType;

    /** BR-HK-06: STAYOVER bỏ qua PENDING_INSPECTION. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private HousekeepingTaskStatus status;

    /** NULL khi task đang UNASSIGNED — BR-HK-07. */
    @Column(name = "assigned_staff_id", length = 36)
    private UUID assignedStaffId;

    /** Nhân viên được phân công phải có ca làm trong đúng ngày này — BR-HK-03. */
    @Column(name = "assigned_date")
    private LocalDate assignedDate;

    @Column(name = "assigned_by", length = 36)
    private UUID assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** CHECKOUT_AUTO | MANAGER_STAYOVER | INSPECTION_FAILED — BR-HK-12. */
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

    /** Lý do HỦY task — BR-HK-09 (phòng Không khả dụng), BR-HK-10 (khách check-out). */
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
        return assignedStaffId != null;
    }
}
