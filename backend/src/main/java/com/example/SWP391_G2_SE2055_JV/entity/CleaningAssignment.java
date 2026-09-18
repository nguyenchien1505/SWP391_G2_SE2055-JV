package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.CleaningAssignmentStatus;
import com.example.SWP391_G2_SE2055_JV.enums.CleaningTaskType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cleaning_assignments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CleaningAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /**
     * Loại task dọn — BR-61:
     * POST_CHECKOUT (dọn sau check-out) / STAYOVER (dọn hằng ngày, phòng vẫn OCCUPIED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    @Builder.Default
    private CleaningTaskType taskType = CleaningTaskType.POST_CHECKOUT;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CleaningAssignmentStatus status;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;
}
