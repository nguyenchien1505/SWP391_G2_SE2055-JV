package com.example.SWP391_G2_SE2055_JV.scheduling.entity;

import com.example.SWP391_G2_SE2055_JV.config.ShiftChangeRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "shift_change_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShiftChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @Column(name = "requested_start_time")
    private LocalTime requestedStartTime;

    @Column(name = "requested_end_time")
    private LocalTime requestedEndTime;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ShiftChangeRequestStatus status;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;
}
