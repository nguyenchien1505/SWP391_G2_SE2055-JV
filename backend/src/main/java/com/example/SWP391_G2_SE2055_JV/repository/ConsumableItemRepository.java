package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.ConsumableItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tồn kho tài sản tiêu hao — DM-11: một dòng cho mỗi cặp (Location, danh mục).
 *
 * <p>Hai cặp method đọc song song giống {@code FixedAssetRepository}: Giám đốc đứng
 * trên nhiều Location nên lọc theo Tenant (chỉ đọc — BR-ASSET-09 giao việc kiểm kê
 * cho Manager), Manager bị giới hạn trong Location của mình.
 */
@Repository
public interface ConsumableItemRepository extends JpaRepository<ConsumableItem, UUID> {

    // ── Phạm vi Tenant — Giám đốc (chỉ đọc) ──────────────────────────────────
    Page<ConsumableItem> findByTenantId(UUID tenantId, Pageable pageable);

    /** {@code quantity <= threshold} — dùng {@code BigDecimal.ZERO} để lọc hết hàng (BR-ASSET-10). */
    Page<ConsumableItem> findByTenantIdAndQuantityLessThanEqual(UUID tenantId, BigDecimal threshold, Pageable pageable);

    Optional<ConsumableItem> findByIdAndTenantId(UUID id, UUID tenantId);

    // ── Phạm vi Location — Manager ────────────────────────────────────────────
    Page<ConsumableItem> findByTenantIdAndLocationId(UUID tenantId, UUID locationId, Pageable pageable);

    Page<ConsumableItem> findByTenantIdAndLocationIdAndQuantityLessThanEqual(
        UUID tenantId, UUID locationId, BigDecimal threshold, Pageable pageable);

    Optional<ConsumableItem> findByIdAndTenantIdAndLocationId(UUID id, UUID tenantId, UUID locationId);

    /** Nạp nhiều dòng cùng lúc cho kiểm kê bulk — tránh N+1 (BR-ASSET-07). */
    List<ConsumableItem> findByLocationIdAndIdIn(UUID locationId, Collection<UUID> ids);

    // ── Một dòng cho mỗi cặp (Location, danh mục) — DM-11 ────────────────────
    boolean existsByLocationIdAndCategoryId(UUID locationId, UUID categoryId);

    // ── Chặn xóa danh mục còn tồn kho tham chiếu — BR-ORG-14 ─────────────────
    boolean existsByCategoryId(UUID categoryId);
}
