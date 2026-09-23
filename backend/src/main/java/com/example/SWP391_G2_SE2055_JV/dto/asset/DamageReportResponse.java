package com.example.SWP391_G2_SE2055_JV.dto.asset;

import com.example.SWP391_G2_SE2055_JV.entity.DamageReport;
import com.example.SWP391_G2_SE2055_JV.enums.DamageReportStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DamageReportResponse {
    private UUID id;
    private UUID locationId;
    private UUID fixedAssetId;
    private UUID reporterId;
    private String description;
    private DamageReportStatus status;
    private LocalDateTime reportedAt;
    private UUID resolvedBy;
    private LocalDateTime resolvedAt;

    public static DamageReportResponse fromEntity(DamageReport report) {
        if (report == null) return null;
        return DamageReportResponse.builder()
                .id(report.getId())
                .locationId(report.getLocationId())
                .fixedAssetId(report.getFixedAssetId())
                .reporterId(report.getReporterId())
                .description(report.getDescription())
                .status(report.getStatus())
                .reportedAt(report.getReportedAt())
                .resolvedBy(report.getResolvedBy())
                .resolvedAt(report.getResolvedAt())
                .build();
    }
}
