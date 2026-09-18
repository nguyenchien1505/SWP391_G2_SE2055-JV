package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_inventory_checks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AssetInventoryCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "expected_quantity")
    private Integer expectedQuantity;

    @Column(name = "actual_quantity")
    private Integer actualQuantity;

    private Integer difference;

    private String note;

    @Column(name = "checked_by")
    private Long checkedBy;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;
}
