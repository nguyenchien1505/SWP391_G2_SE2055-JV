package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Tài sản cố định — quản lý theo từng CÁ THỂ, mỗi bản ghi là một vật (BR-ASSET-01).
 *
 * <p>DM-10: tài sản gắn cứng vào một Location, KHÔNG chuyển giữa các Location
 * (BR-ASSET-13) — muốn đổi chỗ thì thanh lý rồi tạo mới.
 */
@Entity
@Table(name = "fixed_assets")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class FixedAsset extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    /** BR-ASSET-13 — không chuyển tài sản giữa các Location. */
    @Column(name = "location_id", length = 36, nullable = false)
    private UUID locationId;

    @Column(name = "category_id", length = 36, nullable = false)
    private UUID categoryId;

    /** Hệ thống tự sinh, Manager sửa được; unique trong phạm vi LOCATION — BR-ASSET-12. */
    @Column(name = "asset_code", length = 50, nullable = false)
    private String assetCode;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * BR-ASSET-03: gắn ĐÚNG 1 trong 2 — {@code roomId} HOẶC {@code areaId}, không cả
     * hai, không để trống. Cả hai nullable ở đây; ràng buộc XOR do CHECK constraint ở
     * DB và validate ở tầng service.
     */
    @Column(name = "room_id", length = 36)
    private UUID roomId;

    /** Xem {@link #roomId} — BR-ASSET-03. */
    @Column(name = "area_id", length = 36)
    private UUID areaId;

    /** BR-ASSET-02, BR-ASSET-14 — DISPOSED là trạng thái cuối, một chiều. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private FixedAssetStatus status;

    @Column(name = "note", length = 500)
    private String note;

    /** BR-ASSET-11, BR-ASSET-14: đã thanh lý thì không cho tạo báo hỏng mới. */
    public boolean isDisposed() {
        return status == FixedAssetStatus.DISPOSED;
    }

    /** BR-ASSET-03: đúng một trong hai vị trí phải có giá trị. */
    public boolean hasValidPlacement() {
        return (roomId == null) != (areaId == null);
    }
}
