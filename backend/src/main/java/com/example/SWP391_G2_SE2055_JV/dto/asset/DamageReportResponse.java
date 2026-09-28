package com.example.SWP391_G2_SE2055_JV.dto.asset;

import com.example.SWP391_G2_SE2055_JV.entity.DamageReport;
import com.example.SWP391_G2_SE2055_JV.entity.User;
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
    /** Tên/email người báo — để màn hình hiện người thật thay vì UUID. */
    private String reporterName;
    private String reporterEmail;
    private String description;
    private DamageReportStatus status;
    private LocalDateTime reportedAt;
    private UUID resolvedBy;
    private LocalDateTime resolvedAt;

    public static DamageReportResponse fromEntity(DamageReport report) {
        return fromEntity(report, null);
    }

    public static DamageReportResponse fromEntity(DamageReport report, User reporter) {
        if (report == null) return null;
        return DamageReportResponse.builder()
                .id(report.getId())
                .locationId(report.getLocationId())
                .fixedAssetId(report.getFixedAssetId())
                .reporterId(report.getReporterId())
                .reporterName(reporter != null ? reporter.getFullName() : null)
                .reporterEmail(reporter != null ? reporter.getEmail() : null)
                .description(report.getDescription())
                .status(report.getStatus())
                .reportedAt(report.getReportedAt())
                .resolvedBy(report.getResolvedBy())
                .resolvedAt(report.getResolvedAt())
                .build();
    }
}
