package com.example.SWP391_G2_SE2055_JV.asset.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_issue_reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AssetIssueReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "reported_by", nullable = false)
    private Long reportedBy;

    @Column(name = "issue_type")
    private String issueType;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String status;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

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
