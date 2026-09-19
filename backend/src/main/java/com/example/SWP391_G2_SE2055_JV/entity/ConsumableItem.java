package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tồn kho tài sản tiêu hao — DM-11: một dòng cho mỗi cặp (Location, danh mục).
 *
 * <p>Tồn kho TĨNH (BR-ASSET-04): Manager sửa thẳng số lượng khi kiểm kê. Không có
 * entity "Đợt kiểm kê", không lưu lịch sử chênh lệch, không có ngưỡng cảnh báo
 * (BR-ASSET-10).
 */
@Entity
@Table(name = "consumable_items")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ConsumableItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    /** DM-11 — tài sản tiêu hao gắn ở cấp Location, không gắn vào phòng/khu vực. */
    @Column(name = "location_id", length = 36, nullable = false)
    private UUID locationId;

    /** Trỏ tới AssetCategory có assetKind = CONSUMABLE; đơn vị tính lấy từ đó — BR-ASSET-08. */
    @Column(name = "category_id", length = 36, nullable = false)
    private UUID categoryId;

    /** Cho phép số lẻ (ví dụ 2.5 lít); không âm — BR-ASSET-04. */
    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    @lombok.Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    /** Chỉ lưu mốc kiểm kê GẦN NHẤT, ghi đè mỗi lần kiểm kê — BR-ASSET-07. */
    @Column(name = "last_counted_at")
    private LocalDateTime lastCountedAt;

    @Column(name = "last_counted_by", length = 36)
    private UUID lastCountedBy;

    /** BR-ASSET-10: không có ngưỡng cảnh báo, chỉ phân biệt còn hàng / hết hàng. */
    public boolean isOutOfStock() {
        return quantity == null || quantity.signum() <= 0;
    }
}
