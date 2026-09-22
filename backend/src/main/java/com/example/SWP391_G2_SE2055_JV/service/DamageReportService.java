package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.asset.CreateDamageReportRequest;
import com.example.SWP391_G2_SE2055_JV.dto.asset.DamageReportResponse;
import com.example.SWP391_G2_SE2055_JV.entity.DamageReport;
import com.example.SWP391_G2_SE2055_JV.entity.FixedAsset;
import com.example.SWP391_G2_SE2055_JV.enums.DamageReportStatus;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.DamageReportRepository;
import com.example.SWP391_G2_SE2055_JV.repository.FixedAssetRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Báo hỏng tài sản — BR-ASSET-05, BR-ASSET-06, BR-ASSET-11, DM-12, DM-16.
 *
 * <p>Lễ tân/Dọn dẹp tạo báo hỏng, Manager xem mô tả rồi TỰ quyết định có đổi trạng
 * thái tài sản hay không — tạo/xử lý báo hỏng KHÔNG bao giờ tự động đụng tới trạng
 * thái {@code FixedAsset} hay {@code Room} (BR-ASSET-06). Cho phép nhiều báo cáo
 * {@code NEW} cùng lúc trên một tài sản (ví dụ 2 ca cùng báo 1 cái TV) — chỉ chặn tạo
 * mới khi tài sản đã {@code DISPOSED} (BR-ASSET-11).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DamageReportService {

    private final DamageReportRepository damageReportRepository;
    private final FixedAssetRepository   fixedAssetRepository;

    @Transactional
    public DamageReportResponse createDamageReport(CreateDamageReportRequest request) {
        UUID tenantId   = SecurityUtils.getCurrentTenantId();
        UUID locationId = SecurityUtils.getCurrentLocationId();
        UUID reporterId = SecurityUtils.getCurrentUserId();

        // Sai Location thì trả 404, không lộ việc tài sản có tồn tại ở KS khác không.
        FixedAsset asset = fixedAssetRepository
            .findByIdAndTenantIdAndLocationId(request.getFixedAssetId(), tenantId, locationId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài sản cố định"));

        if (asset.isDisposed()) {
            throw new BusinessException(
                "Tài sản đã thanh lý, không tạo báo hỏng mới được (BR-ASSET-11).");
        }

        DamageReport report = DamageReport.builder()
            .tenantId(tenantId)
            .locationId(locationId)
            .fixedAssetId(asset.getId())
            .reporterId(reporterId)
            .description(request.getDescription())
            .status(DamageReportStatus.NEW)
            .reportedAt(LocalDateTime.now())
            .build();

        return DamageReportResponse.fromEntity(damageReportRepository.save(report));
    }

    /**
     * DM-16: mặc định lọc {@code status} do client truyền (thường là {@code NEW} —
     * "màn hình tự truy vấn danh sách đang chờ xử lý"); bỏ trống để xem cả lịch sử.
     *
     * <p>Phạm vi theo vai trò: Giám đốc toàn Tenant, Manager toàn Location, Staff chỉ
     * báo cáo do CHÍNH MÌNH tạo.
     */
    @Transactional(readOnly = true)
    public Page<DamageReportResponse> getDamageReports(DamageReportStatus status, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<DamageReport> page;

        if (SecurityUtils.hasRole(Role.DIRECTOR)) {
            page = status == null
                ? damageReportRepository.findByTenantId(tenantId, pageable)
                : damageReportRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            UUID locationId = SecurityUtils.getCurrentLocationId();
            if (SecurityUtils.hasRole(Role.MANAGER)) {
                page = status == null
                    ? damageReportRepository.findByTenantIdAndLocationId(tenantId, locationId, pageable)
                    : damageReportRepository.findByTenantIdAndLocationIdAndStatus(
                        tenantId, locationId, status, pageable);
            } else {
                UUID reporterId = SecurityUtils.getCurrentUserId();
                page = status == null
                    ? damageReportRepository.findByTenantIdAndLocationIdAndReporterId(
                        tenantId, locationId, reporterId, pageable)
                    : damageReportRepository.findByTenantIdAndLocationIdAndReporterIdAndStatus(
                        tenantId, locationId, reporterId, status, pageable);
            }
        }

        return page.map(DamageReportResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public DamageReportResponse getDamageReportById(UUID id) {
        return DamageReportResponse.fromEntity(getOwnedReport(id));
    }

    /**
     * Manager xử lý — BR-ASSET-06. KHÔNG đụng tới trạng thái {@code FixedAsset}: muốn
     * chuyển tài sản sang Hỏng/Đang sửa thì gọi riêng
     * {@code PATCH /assets/fixed-assets/{id}/status}.
     *
     * <p>Idempotent: báo cáo đã {@code RESOLVED} rồi thì trả về nguyên trạng, không
     * ném lỗi — cùng lối với {@code FixedAssetService.updateStatus}.
     */
    @Transactional
    public DamageReportResponse resolveDamageReport(UUID id) {
        DamageReport report = getOwnedReport(id);

        if (report.getStatus() == DamageReportStatus.NEW) {
            report.setStatus(DamageReportStatus.RESOLVED);
            report.setResolvedBy(SecurityUtils.getCurrentUserId());
            report.setResolvedAt(LocalDateTime.now());
            damageReportRepository.save(report);
            log.info("Báo hỏng {} đã được xử lý", id);
        }

        return DamageReportResponse.fromEntity(report);
    }

    /**
     * Tự đóng mọi báo cáo {@code NEW} còn treo trên một tài sản — gọi khi tài sản
     * chuyển sang {@code DISPOSED} (xem {@code FixedAssetService.updateStatus}). Tránh
     * báo cáo treo vĩnh viễn trên tài sản không còn tồn tại về mặt vận hành.
     */
    @Transactional
    public void autoResolveForDisposedAsset(UUID fixedAssetId, UUID resolvedBy) {
        var pending = damageReportRepository.findByFixedAssetIdAndStatus(fixedAssetId, DamageReportStatus.NEW);
        if (pending.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (DamageReport report : pending) {
            report.setStatus(DamageReportStatus.RESOLVED);
            report.setResolvedBy(resolvedBy);
            report.setResolvedAt(now);
        }
        damageReportRepository.saveAll(pending);
        log.info("Tự đóng {} báo hỏng NEW của tài sản {} do tài sản đã thanh lý", pending.size(), fixedAssetId);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Ngoài phạm vi thì trả 404 chứ không 403, để không lộ việc bản ghi có tồn tại. */
    private DamageReport getOwnedReport(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        if (SecurityUtils.hasRole(Role.DIRECTOR)) {
            return damageReportRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo hỏng"));
        }

        UUID locationId = SecurityUtils.getCurrentLocationId();
        if (SecurityUtils.hasRole(Role.MANAGER)) {
            return damageReportRepository.findByIdAndTenantIdAndLocationId(id, tenantId, locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo hỏng"));
        }

        UUID reporterId = SecurityUtils.getCurrentUserId();
        return damageReportRepository
            .findByIdAndTenantIdAndLocationIdAndReporterId(id, tenantId, locationId, reporterId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo hỏng"));
    }
}
