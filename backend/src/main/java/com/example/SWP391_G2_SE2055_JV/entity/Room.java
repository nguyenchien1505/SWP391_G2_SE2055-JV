package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "location_room_type_id", nullable = false)
    private Long locationRoomTypeId;

    @Column(name = "current_status_id", nullable = false)
    private Long currentStatusId;

    @Column(name = "room_number", nullable = false)
    private String roomNumber;

    /** Sức chứa riêng của phòng (override room_type.capacity) */
    private Integer capacity;

    private String floor;

    /** Ghi chú vận hành */
    @Column(length = 500)
    private String note;

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
