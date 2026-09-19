package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.AssetKind;
import com.example.SWP391_G2_SE2055_JV.enums.AssetPurpose;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Danh mục tài sản — danh mục cấp TENANT, dùng chung cho mọi Location.
 *
 * <p>Một bảng duy nhất cho CẢ HAI loại tài sản, phân biệt bằng {@code assetKind}
 * (BR-ASSET-08, BR-ASSET-09): FIXED quản lý theo cá thể, CONSUMABLE quản lý tồn kho.
 */
@Entity
@Table(name = "asset_categories")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AssetCategory extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    /** Unique trong phạm vi Tenant, không phải toàn hệ thống — BR-ORG-13. */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_kind", length = 20, nullable = false)
    private AssetKind assetKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 30, nullable = false)
    private AssetPurpose purpose;

    /** Đơn vị tính — bắt buộc khi CONSUMABLE, phải NULL khi FIXED (BR-ASSET-08). */
    @Column(name = "unit", length = 20)
    private String unit;

    /** BR-ORG-14 — ẩn thay vì xóa, vì BR-ORG-10 chặn xóa cứng danh mục đang được dùng. */
    @Column(name = "is_active", nullable = false)
    @lombok.Builder.Default
    private boolean active = true;

    public boolean isFixed() {
        return assetKind == AssetKind.FIXED;
    }

    public boolean isConsumable() {
        return assetKind == AssetKind.CONSUMABLE;
    }
}
