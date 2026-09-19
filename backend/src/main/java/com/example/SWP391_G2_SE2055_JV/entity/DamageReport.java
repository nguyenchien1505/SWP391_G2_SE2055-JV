package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.DamageReportStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Báo hỏng tài sản — DM-12: CHỈ áp dụng cho tài sản cố định, không đa hình sang
 * phòng / khu vực / tài sản tiêu hao.
 *
 * <p>Lễ tân hoặc Dọn dẹp tạo báo hỏng (BR-ASSET-05), Manager xem mô tả rồi quyết
 * định đổi trạng thái tài sản (BR-ASSET-06). Đúng 2 trạng thái NEW / RESOLVED
 * (BR-ASSET-11).
 */
@Entity
@Table(name = "damage_reports")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class DamageReport extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "location_id", length = 36, nullable = false)
    private UUID locationId;

    /** DM-12 — luôn trỏ tới một FixedAsset, không có đối tượng nào khác. */
    @Column(name = "fixed_asset_id", length = 36, nullable = false)
    private UUID fixedAssetId;

    /** Nhân viên Lễ tân hoặc Dọn dẹp — BR-ASSET-05. */
    @Column(name = "reporter_id", length = 36, nullable = false)
    private UUID reporterId;

    /** Bắt buộc: Manager cần biết hỏng gì để quyết định — BR-ASSET-06. */
    @Column(name = "description", length = 500, nullable = false)
    private String description;

    /** BR-ASSET-11 — mặc định NEW khi tạo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @lombok.Builder.Default
    private DamageReportStatus status = DamageReportStatus.NEW;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    /** Chỉ có giá trị khi status = RESOLVED, cùng lúc với {@link #resolvedAt}. */
    @Column(name = "resolved_by", length = 36)
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** DM-16: màn hình tự truy vấn danh sách đang chờ = lọc theo trạng thái này. */
    public boolean isPending() {
        return status == DamageReportStatus.NEW;
    }
}
