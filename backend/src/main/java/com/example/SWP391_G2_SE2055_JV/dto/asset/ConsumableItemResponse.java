package com.example.SWP391_G2_SE2055_JV.dto.asset;

import com.example.SWP391_G2_SE2055_JV.entity.AssetCategory;
import com.example.SWP391_G2_SE2055_JV.entity.ConsumableItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ConsumableItemResponse {
    private UUID id;
    private UUID locationId;
    private UUID categoryId;
    private String categoryName;
    private String unit;
    private BigDecimal quantity;
    private boolean outOfStock;
    private LocalDateTime lastCountedAt;
    private UUID lastCountedBy;

    /**
     * @param category danh mục tương ứng — service nạp sẵn để tránh N+1 khi trả về cả
     *                  trang; không suy ra tên/đơn vị từ {@code categoryId} ở đây.
     */
    public static ConsumableItemResponse fromEntity(ConsumableItem item, AssetCategory category) {
        if (item == null) return null;
        return ConsumableItemResponse.builder()
                .id(item.getId())
                .locationId(item.getLocationId())
                .categoryId(item.getCategoryId())
                .categoryName(category == null ? null : category.getName())
                .unit(category == null ? null : category.getUnit())
                .quantity(item.getQuantity())
                .outOfStock(item.isOutOfStock())
                .lastCountedAt(item.getLastCountedAt())
                .lastCountedBy(item.getLastCountedBy())
                .build();
    }
}
