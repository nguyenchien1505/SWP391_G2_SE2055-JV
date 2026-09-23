package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.AssetCategory;
import com.example.SWP391_G2_SE2055_JV.enums.AssetKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Danh mục tài sản — danh mục cấp Tenant, dùng chung cho cả tài sản cố định và tiêu
 * hao (BR-ASSET-08).
 *
 * <p>KHÔNG method nào lọc sẵn {@code active = true}. Cờ {@code is_active} của
 * BR-ORG-14 dùng để ẩn tạm khỏi danh sách chọn rồi bật lại, nên nếu tầng truy vấn ép
 * cứng {@code ActiveTrue} thì danh mục đã ẩn biến mất khỏi mọi đường API và không còn
 * cách nào bật lại. Việc lọc do service quyết định theo từng màn hình.
 *
 * <p>Tương tự, hai method {@code existsBy...Name} KHÔNG lọc theo {@code active}: ràng
 * buộc {@code uk_asset_categories_tenant_name} ở DB không quan tâm cờ này, nên bỏ qua
 * bản ghi đã ẩn sẽ để lọt tên trùng rồi nhận lỗi 409 khó hiểu từ DB.
 */
@Repository
public interface AssetCategoryRepository extends JpaRepository<AssetCategory, UUID> {

    Page<AssetCategory> findByTenantId(UUID tenantId, Pageable pageable);

    Page<AssetCategory> findByTenantIdAndActive(UUID tenantId, boolean active, Pageable pageable);

    Page<AssetCategory> findByTenantIdAndAssetKind(UUID tenantId, AssetKind assetKind, Pageable pageable);

    Page<AssetCategory> findByTenantIdAndAssetKindAndActive(
        UUID tenantId, AssetKind assetKind, boolean active, Pageable pageable);

    Optional<AssetCategory> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndName(UUID tenantId, String name);

    boolean existsByTenantIdAndNameAndIdNot(UUID tenantId, String name, UUID id);
}
