package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.DamageReport;
import com.example.SWP391_G2_SE2055_JV.enums.DamageReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Báo hỏng tài sản — BR-ASSET-05, BR-ASSET-06, BR-ASSET-11, DM-12, DM-16.
 *
 * <p>Ba phạm vi nhìn khác nhau theo vai trò: Giám đốc toàn Tenant (chỉ đọc), Manager
 * toàn Location, Staff chỉ báo cáo do CHÍNH MÌNH tạo (để biết đã được xử lý chưa).
 */
@Repository
public interface DamageReportRepository extends JpaRepository<DamageReport, UUID> {

    /**
     * Danh sách có lọc. Tham số nào {@code null} thì bỏ qua điều kiện đó, riêng
     * {@code tenantId} luôn bắt buộc. Service ép {@code locationId}/{@code reporterId} theo
     * vai trò — client không tự mở rộng phạm vi được.
     *
     * @param status       DM-16: thường là {@code NEW} ("danh sách đang chờ xử lý");
     *                     {@code null} để xem cả lịch sử {@code RESOLVED}
     * @param fixedAssetId lịch sử báo hỏng của một tài sản (màn chi tiết tài sản / phiếu)
     */
    @Query("""
        SELECT d FROM DamageReport d
        WHERE d.tenantId = :tenantId
          AND (:locationId   IS NULL OR d.locationId   = :locationId)
          AND (:reporterId   IS NULL OR d.reporterId   = :reporterId)
          AND (:fixedAssetId IS NULL OR d.fixedAssetId = :fixedAssetId)
          AND (:status       IS NULL OR d.status       = :status)
        """)
    Page<DamageReport> search(@Param("tenantId") UUID tenantId,
                              @Param("locationId") UUID locationId,
                              @Param("reporterId") UUID reporterId,
                              @Param("fixedAssetId") UUID fixedAssetId,
                              @Param("status") DamageReportStatus status,
                              Pageable pageable);

    // ── Đọc đơn lẻ ─────────────────────────────────────────────────────────
    Optional<DamageReport> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DamageReport> findByIdAndTenantIdAndLocationId(UUID id, UUID tenantId, UUID locationId);

    Optional<DamageReport> findByIdAndTenantIdAndLocationIdAndReporterId(
        UUID id, UUID tenantId, UUID locationId, UUID reporterId);

    /** Tự đóng khi tài sản chuyển sang DISPOSED — xem {@code FixedAssetService.updateStatus}. */
    List<DamageReport> findByFixedAssetIdAndStatus(UUID fixedAssetId, DamageReportStatus status);
}
