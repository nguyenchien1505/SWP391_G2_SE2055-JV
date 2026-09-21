package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.DamageReport;
import com.example.SWP391_G2_SE2055_JV.enums.DamageReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Báo hỏng tài sản — BR-ASSET-05, BR-ASSET-06, BR-ASSET-11, DM-12, DM-16.
 *
 * <p>Ba phạm vi nhìn khác nhau theo vai trò: Giám đốc toàn Tenant (chỉ đọc), Manager
 * toàn Location, Staff chỉ báo cáo do CHÍNH MÌNH tạo (để biết đã được xử lý chưa).
 * Mỗi phạm vi có 2 biến thể — có lọc {@code status} hoặc không — để phục vụ DM-16
 * (mặc định lọc {@code NEW}, có thể bỏ lọc để xem cả lịch sử {@code RESOLVED}).
 */
@Repository
public interface DamageReportRepository extends JpaRepository<DamageReport, UUID> {

    // ── Phạm vi Tenant — Giám đốc (chỉ đọc) ──────────────────────────────────
    Page<DamageReport> findByTenantId(UUID tenantId, Pageable pageable);

    Page<DamageReport> findByTenantIdAndStatus(UUID tenantId, DamageReportStatus status, Pageable pageable);

    // ── Phạm vi Location — Manager ────────────────────────────────────────────
    Page<DamageReport> findByTenantIdAndLocationId(UUID tenantId, UUID locationId, Pageable pageable);

    Page<DamageReport> findByTenantIdAndLocationIdAndStatus(
        UUID tenantId, UUID locationId, DamageReportStatus status, Pageable pageable);

    // ── Phạm vi cá nhân — Staff chỉ xem báo cáo do chính mình tạo ────────────
    Page<DamageReport> findByTenantIdAndLocationIdAndReporterId(
        UUID tenantId, UUID locationId, UUID reporterId, Pageable pageable);

    Page<DamageReport> findByTenantIdAndLocationIdAndReporterIdAndStatus(
        UUID tenantId, UUID locationId, UUID reporterId, DamageReportStatus status, Pageable pageable);

    // ── Đọc đơn lẻ ─────────────────────────────────────────────────────────
    Optional<DamageReport> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DamageReport> findByIdAndTenantIdAndLocationId(UUID id, UUID tenantId, UUID locationId);

    Optional<DamageReport> findByIdAndTenantIdAndLocationIdAndReporterId(
        UUID id, UUID tenantId, UUID locationId, UUID reporterId);

    /** Auto-resolve khi tài sản chuyển sang DISPOSED — xem {@code FixedAssetService}. */
    List<DamageReport> findByFixedAssetIdAndStatus(UUID fixedAssetId, DamageReportStatus status);
}
