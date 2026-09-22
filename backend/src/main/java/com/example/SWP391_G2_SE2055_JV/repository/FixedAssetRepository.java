package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.FixedAsset;
<<<<<<< HEAD
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Chỉ chứa truy vấn mà module Tổ chức cần (BR-ORG-15); nghiệp vụ tài sản đầy đủ thuộc
 * module BR-ASSET.
=======
import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Tài sản cố định — cá thể, gắn cứng vào một Location (BR-ASSET-01, BR-ASSET-13).
 *
 * <p>Có hai cặp method đọc song song vì phạm vi nhìn khác nhau theo vai trò: Giám đốc
 * đứng trên nhiều Location nên lọc theo Tenant, còn Manager và Staff bị giới hạn trong
 * Location của mình.
>>>>>>> Nguyen
 */
@Repository
public interface FixedAssetRepository extends JpaRepository<FixedAsset, UUID> {

<<<<<<< HEAD
    /**
     * BR-ORG-15: chặn xóa Khu vực khi còn tài sản cố định gắn vào. Tính cả tài sản đã
     * thanh lý — bản ghi vẫn giữ khóa ngoại tới khu vực để tra lịch sử (BR-ASSET-14).
     */
    boolean existsByAreaId(UUID areaId);
=======
    // ── Phạm vi Tenant — Giám đốc ────────────────────────────────────────────
    Page<FixedAsset> findByTenantId(UUID tenantId, Pageable pageable);

    Page<FixedAsset> findByTenantIdAndStatusNotIn(
        UUID tenantId, Collection<FixedAssetStatus> statuses, Pageable pageable);

    Optional<FixedAsset> findByIdAndTenantId(UUID id, UUID tenantId);

    // ── Phạm vi Location — Manager, Staff ────────────────────────────────────
    Page<FixedAsset> findByTenantIdAndLocationId(UUID tenantId, UUID locationId, Pageable pageable);

    Page<FixedAsset> findByTenantIdAndLocationIdAndStatusNotIn(
        UUID tenantId, UUID locationId, Collection<FixedAssetStatus> statuses, Pageable pageable);

    Optional<FixedAsset> findByIdAndTenantIdAndLocationId(UUID id, UUID tenantId, UUID locationId);

    // ── Mã tài sản — BR-ASSET-12, unique trong phạm vi LOCATION ──────────────
    boolean existsByLocationIdAndAssetCode(UUID locationId, String assetCode);

    boolean existsByLocationIdAndAssetCodeAndIdNot(UUID locationId, String assetCode, UUID id);

    /**
     * Mã lớn nhất đang dùng theo tiền tố, để sinh số kế tiếp. Dùng max thay vì count vì
     * BR-ASSET-12 cho phép Manager tự đặt mã: đếm số bản ghi sẽ ra số đã bị chiếm và
     * đâm vào ràng buộc unique.
     */
    Optional<FixedAsset> findTopByLocationIdAndAssetCodeStartingWithOrderByAssetCodeDesc(
        UUID locationId, String prefix);

    // ── Chặn xóa khi còn tham chiếu — BR-ORG-15, BR-ROOM-08, BR-ORG-14 ───────
    boolean existsByCategoryId(UUID categoryId);

    boolean existsByAreaId(UUID areaId);

    boolean existsByRoomId(UUID roomId);
>>>>>>> Nguyen
}
