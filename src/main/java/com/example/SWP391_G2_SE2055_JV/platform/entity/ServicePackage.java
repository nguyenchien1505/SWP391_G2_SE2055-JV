package com.example.SWP391_G2_SE2055_JV.platform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_packages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServicePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private BigDecimal price;

    private String currency;

    @Column(name = "billing_cycle")
    private String billingCycle;

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "max_locations")
    private Integer maxLocations;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_rooms")
    private Integer maxRooms;

    private String status;

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
