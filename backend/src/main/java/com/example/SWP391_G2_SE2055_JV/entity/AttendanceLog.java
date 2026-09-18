package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "check_in_at")
    private LocalDateTime checkInAt;

    @Column(name = "check_out_at")
    private LocalDateTime checkOutAt;

    @Column(name = "check_method")
    private String checkMethod;

    @Column(name = "is_late", nullable = false)
    @Builder.Default
    private boolean late = false;

    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
