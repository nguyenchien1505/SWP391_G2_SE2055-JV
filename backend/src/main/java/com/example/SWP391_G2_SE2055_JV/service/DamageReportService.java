package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.asset.CreateDamageReportRequest;
import com.example.SWP391_G2_SE2055_JV.dto.asset.DamageReportResponse;
import com.example.SWP391_G2_SE2055_JV.dto.asset.ResolveDamageReportRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Area;
import com.example.SWP391_G2_SE2055_JV.entity.DamageReport;
import com.example.SWP391_G2_SE2055_JV.entity.FixedAsset;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.DamageReportStatus;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AreaRepository;
import com.example.SWP391_G2_SE2055_JV.repository.DamageReportRepository;
import com.example.SWP391_G2_SE2055_JV.repository.FixedAssetRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Báo hỏng tài sản — BR-ASSET-05, BR-ASSET-06, BR-ASSET-11, DM-12, DM-16.
 *
 * <p>Lễ tân/Dọn dẹp tạo báo hỏng, Manager xem mô tả rồi TỰ quyết định có đổi trạng
 * thái tài sản hay không — tạo báo hỏng KHÔNG bao giờ tự động đụng tới trạng thái
 * {@code FixedAsset} hay {@code Room} (BR-ASSET-06). Cho phép nhiều báo cáo {@code NEW}
 * cùng lúc trên một tài sản (ví dụ 2 ca cùng báo 1 cái TV) — chỉ chặn tạo mới khi tài sản
 * đã {@code DISPOSED} (BR-ASSET-11).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DamageReportService {

    private final DamageReportRepository damageReportRepository;
    private final FixedAssetRepository   fixedAssetRepository;
    /** Đổi trạng thái tài sản khi Manager chọn lúc đóng phiếu — giữ nguyên luật BR-ASSET-14. */
    private final FixedAssetService      fixedAssetService;
    /** Các repository dưới chỉ để gắn tên người/vị trí vào response. */
    private final UserRepository         userRepository;
    private final RoomRepository         roomRepository;
    private final AreaRepository         areaRepository;

    /**
     * Quyền tạo (chỉ nhân viên có quyền Lễ tân/Dọn dẹp — BR-ASSET-05) đã chặn ở
     * {@code SecurityConfig}; ở đây chỉ còn phạm vi Location và BR-ASSET-11.
     */
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
            .description(request.getDescription().trim())
            .status(DamageReportStatus.NEW)
            .reportedAt(LocalDateTime.now())
            .build();

        return toResponse(damageReportRepository.save(report));
    }

    /**
     * Phạm vi theo vai trò: Giám đốc toàn Tenant, Manager toàn Location, Staff chỉ báo cáo
     * do CHÍNH MÌNH tạo.
     *
     * @param status       DM-16: thường là {@code NEW}; {@code null} để xem cả lịch sử
     * @param fixedAssetId lịch sử báo hỏng của một tài sản, {@code null} = không lọc
     */
    @Transactional(readOnly = true)
    public Page<DamageReportResponse> getDamageReports(DamageReportStatus status, UUID fixedAssetId,
                                                       Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = SecurityUtils.hasRole(Role.DIRECTOR) ? null : SecurityUtils.getCurrentLocationId();
        UUID reporterId = isReporterScoped() ? SecurityUtils.getCurrentUserId() : null;

        Page<DamageReport> page = damageReportRepository.search(
            tenantId, locationId, reporterId, fixedAssetId, status, pageable);

        return new PageImpl<>(toResponses(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DamageReportResponse getDamageReportById(UUID id) {
        return toResponse(getOwnedReport(id));
    }

    /**
     * Manager đóng phiếu — BR-ASSET-06. Nếu Manager chọn {@code newAssetStatus} thì đổi trạng
     * thái tài sản TRONG CÙNG transaction: lỗi ở bước nào thì cả phiếu lẫn tài sản cùng giữ
     * nguyên, không có cảnh phiếu đã đóng mà tài sản sai trạng thái.
     *
     * <p>Đóng phiếu này TRƯỚC rồi mới đổi trạng thái tài sản: chọn {@code DISPOSED} sẽ tự đóng
     * mọi phiếu NEW còn lại của tài sản (xem {@code FixedAssetService.updateStatus}), và phiếu
     * đang xử lý phải giữ được ghi chú cùng người đóng của chính nó.
     *
     * <p>Phiếu đã đóng thì báo lỗi thay vì lặng lẽ trả về: Manager vừa chọn trạng thái và ghi
     * chú, bỏ qua mà không báo thì họ tưởng đã lưu.
     */
    @Transactional
    public DamageReportResponse resolveDamageReport(UUID id, ResolveDamageReportRequest request) {
        DamageReport report = getOwnedReport(id);

        if (report.getStatus() != DamageReportStatus.NEW) {
            throw new BusinessException("Báo hỏng này đã được xử lý trước đó.");
        }

        report.setStatus(DamageReportStatus.RESOLVED);
        report.setResolvedBy(SecurityUtils.getCurrentUserId());
        report.setResolvedAt(LocalDateTime.now());
        report.setResolutionNote(request == null ? null : StringUtils.trimToNull(request.getResolutionNote()));
        damageReportRepository.save(report);

        if (request != null && request.getNewAssetStatus() != null) {
            fixedAssetService.updateStatus(report.getFixedAssetId(), request.getNewAssetStatus());
        }

        log.info("Báo hỏng {} đã được xử lý", id);
        return toResponse(report);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Nhân viên chỉ thấy báo cáo do chính mình tạo; Manager/Giám đốc thấy cả phạm vi. */
    private boolean isReporterScoped() {
        return !SecurityUtils.hasRole(Role.DIRECTOR) && !SecurityUtils.hasRole(Role.MANAGER);
    }

    private DamageReportResponse toResponse(DamageReport report) {
        return toResponses(List.of(report)).get(0);
    }

    /**
     * Nạp tài sản, phòng, khu vực và người liên quan theo lô — mỗi loại 1 query cho cả trang,
     * tránh N+1.
     */
    private List<DamageReportResponse> toResponses(List<DamageReport> reports) {
        Map<UUID, FixedAsset> assets = loadById(
            reports.stream().map(DamageReport::getFixedAssetId), fixedAssetRepository::findAllById,
            FixedAsset::getId);
        Map<UUID, Room> rooms = loadById(
            assets.values().stream().map(FixedAsset::getRoomId), roomRepository::findAllById, Room::getId);
        Map<UUID, Area> areas = loadById(
            assets.values().stream().map(FixedAsset::getAreaId), areaRepository::findAllById, Area::getId);
        Map<UUID, User> users = loadById(
            reports.stream().flatMap(r -> Stream.of(r.getReporterId(), r.getResolvedBy())),
            userRepository::findAllById, User::getId);

        return reports.stream().map(report -> {
            FixedAsset asset = assets.get(report.getFixedAssetId());
            Room room = asset == null || asset.getRoomId() == null ? null : rooms.get(asset.getRoomId());
            Area area = asset == null || asset.getAreaId() == null ? null : areas.get(asset.getAreaId());
            return DamageReportResponse.fromEntity(
                report, asset,
                room == null ? null : "Phòng " + room.getRoomNumber(),
                area == null ? null : area.getName(),
                users.get(report.getReporterId()),
                report.getResolvedBy() == null ? null : users.get(report.getResolvedBy()));
        }).toList();
    }

    private static <T> Map<UUID, T> loadById(Stream<UUID> ids, Function<Set<UUID>, ? extends Iterable<T>> finder,
                                             Function<T, UUID> idOf) {
        Set<UUID> distinct = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, T> result = new HashMap<>();
        if (!distinct.isEmpty()) {
            finder.apply(distinct).forEach(item -> result.put(idOf.apply(item), item));
        }
        return result;
    }

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
