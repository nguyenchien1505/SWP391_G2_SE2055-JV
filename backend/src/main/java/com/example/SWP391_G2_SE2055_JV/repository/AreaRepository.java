package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Area;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
<<<<<<< HEAD
 * Khu vực — khác 3 danh mục cấp Tenant, Area thuộc cấp LOCATION (BR-ORG-12) nên truy vấn
 * lọc theo locationId; tenantId vẫn giữ để chặn truy cập chéo Tenant.
=======
 * Khu vực — BR-ORG-12 (Manager CRUD trong Location của mình), BR-ORG-13 (tên unique
 * trong phạm vi LOCATION, khác 4 danh mục cấp Tenant).
 *
 * <p>Hai cặp method đọc song song giống {@code FixedAssetRepository}: Giám đốc đứng
 * trên nhiều Location nên lọc theo Tenant, Manager bị giới hạn trong Location của
 * mình. {@code findByIdAndTenantId} vẫn giữ lại — {@code FixedAssetService} dùng nó
 * để kiểm tra Khu vực có thuộc Tenant khi gắn tài sản (BR-ASSET-03).
>>>>>>> Nguyen
 */
@Repository
public interface AreaRepository extends JpaRepository<Area, UUID> {

<<<<<<< HEAD
    /**
     * Thứ tự sắp xếp do {@code Pageable} quyết định nên KHÔNG đặt {@code OrderBy} vào tên
     * method — hai nguồn sắp xếp gặp nhau sẽ khiến tham số {@code sort} của client bị bỏ qua.
     *
     * <p>Không có biến thể lọc theo trạng thái: Area là danh mục cấp Location, không có cờ
     * is_active như 3 danh mục cấp Tenant (BR-ORG-14 không áp dụng).
     */
    Page<Area> findByLocationId(UUID locationId, Pageable pageable);

    Optional<Area> findByIdAndTenantId(UUID id, UUID tenantId);

    /** BR-ORG-13: tên Khu vực unique trong phạm vi LOCATION, không phải toàn Tenant. */
    boolean existsByLocationIdAndName(UUID locationId, String name);

    boolean existsByLocationIdAndNameAndIdNot(UUID locationId, String name, UUID id);

    /** BR-ORG-05: Location còn Khu vực thì khóa ngoại chặn xóa. */
    boolean existsByLocationId(UUID locationId);
=======
    // ── Phạm vi Tenant — Giám đốc ────────────────────────────────────────────
    Page<Area> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<Area> findByIdAndTenantId(UUID id, UUID tenantId);

    // ── Phạm vi Location — Manager ────────────────────────────────────────────
    Page<Area> findByTenantIdAndLocationId(UUID tenantId, UUID locationId, Pageable pageable);

    Optional<Area> findByIdAndTenantIdAndLocationId(UUID id, UUID tenantId, UUID locationId);

    // ── Tên — BR-ORG-13, unique trong phạm vi LOCATION ───────────────────────
    boolean existsByLocationIdAndName(UUID locationId, String name);

    boolean existsByLocationIdAndNameAndIdNot(UUID locationId, String name, UUID id);
>>>>>>> Nguyen
}
