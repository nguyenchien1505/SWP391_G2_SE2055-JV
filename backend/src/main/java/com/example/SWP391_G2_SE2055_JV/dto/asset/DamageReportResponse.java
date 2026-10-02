package com.example.SWP391_G2_SE2055_JV.dto.asset;

import com.example.SWP391_G2_SE2055_JV.entity.DamageReport;
import com.example.SWP391_G2_SE2055_JV.entity.FixedAsset;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.DamageReportStatus;
import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kèm sẵn thông tin tài sản và vị trí để màn hình không phải tải toàn bộ danh sách tài sản
 * chỉ để tra mã/tên — service nạp theo lô cho cả trang.
 */
@Data
@Builder
public class DamageReportResponse {
    private UUID id;
    private UUID locationId;

    private UUID fixedAssetId;
    private String assetCode;
    private String assetName;
    /** Trạng thái tài sản HIỆN TẠI — để Manager thấy trước khi quyết định (BR-ASSET-06). */
    private FixedAssetStatus assetStatus;
    private UUID roomId;
    /** "Phòng 101" — cùng định dạng với {@code FixedAssetResponse}. */
    private String roomName;
    private UUID areaId;
    private String areaName;

    private UUID reporterId;
    /** Tên/email người báo — để màn hình hiện người thật thay vì UUID. */
    private String reporterName;
    private String reporterEmail;
    private String description;
    private DamageReportStatus status;
    private LocalDateTime reportedAt;

    private UUID resolvedBy;
    private String resolverName;
    private LocalDateTime resolvedAt;
    private String resolutionNote;

    /**
     * @param asset    null nếu không còn tra được (không xảy ra với khóa ngoại hiện tại)
     * @param roomName đã định dạng sẵn, null nếu tài sản gắn khu vực
     */
    public static DamageReportResponse fromEntity(DamageReport report, FixedAsset asset,
                                                  String roomName, String areaName,
                                                  User reporter, User resolver) {
        if (report == null) return null;
        return DamageReportResponse.builder()
                .id(report.getId())
                .locationId(report.getLocationId())
                .fixedAssetId(report.getFixedAssetId())
                .assetCode(asset != null ? asset.getAssetCode() : null)
                .assetName(asset != null ? asset.getName() : null)
                .assetStatus(asset != null ? asset.getStatus() : null)
                .roomId(asset != null ? asset.getRoomId() : null)
                .roomName(roomName)
                .areaId(asset != null ? asset.getAreaId() : null)
                .areaName(areaName)
                .reporterId(report.getReporterId())
                .reporterName(reporter != null ? reporter.getFullName() : null)
                .reporterEmail(reporter != null ? reporter.getEmail() : null)
                .description(report.getDescription())
                .status(report.getStatus())
                .reportedAt(report.getReportedAt())
                .resolvedBy(report.getResolvedBy())
                .resolverName(resolver != null ? resolver.getFullName() : null)
                .resolvedAt(report.getResolvedAt())
                .resolutionNote(report.getResolutionNote())
                .build();
    }
}
