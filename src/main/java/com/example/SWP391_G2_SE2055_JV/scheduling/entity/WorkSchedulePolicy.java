package com.example.SWP391_G2_SE2055_JV.scheduling.entity;

import com.example.SWP391_G2_SE2055_JV.config.PolicyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_schedule_policies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkSchedulePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    private String name;

    @Column(name = "max_shifts_per_week")
    private Integer maxShiftsPerWeek;

    @Column(name = "max_hours_per_week")
    private Integer maxHoursPerWeek;

    @Column(name = "min_rest_hours")
    private Integer minRestHours;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PolicyStatus status;

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
