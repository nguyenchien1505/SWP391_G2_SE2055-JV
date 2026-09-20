package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreatePricingConfigRequest;
import com.example.SWP391_G2_SE2055_JV.dto.PricingConfigResponse;
import com.example.SWP391_G2_SE2055_JV.dto.SystemConfigResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateSystemConfigRequest;
import com.example.SWP391_G2_SE2055_JV.entity.PricingConfig;
import com.example.SWP391_G2_SE2055_JV.entity.SystemConfig;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.PricingConfigRepository;
import com.example.SWP391_G2_SE2055_JV.repository.SystemConfigRepository;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Cấu hình cấp NỀN TẢNG do Admin Platform quản lý — BR-SAAS-02, BR-SAAS-08, BR-SAAS-10.
 *
 * <p>Hai nhóm dữ liệu, cả hai đều không thuộc Tenant nào nên service này KHÔNG gọi
 * {@code SecurityUtils.getCurrentTenantId()} (hàm đó ném lỗi với PLATFORM_ADMIN):
 * <ul>
 *   <li><b>SystemConfig</b> — đúng 1 bản ghi: số ngày dùng thử, số ngày ân hạn. Sửa đè.</li>
 *   <li><b>PricingConfig</b> — nhiều bản ghi theo ngày hiệu lực. Chỉ THÊM, không sửa/xóa,
 *       để mọi hóa đơn cũ luôn giải thích được (Subscription chốt snapshot giá — BR-SAAS-05).</li>
 * </ul>
 *
 * <p>Luồng của một request ghi: {@code SecurityConfig} (/platform/** chỉ PLATFORM_ADMIN) →
 * {@code @PreAuthorize} ở controller → {@code @Valid} kiểm tra DTO → service này →
 * repository. Bốn cột audit ({@code created_by}, {@code updated_by}...) do JPA Auditing
 * tự điền bằng id của Admin (xem {@code JpaConfig.auditorAware}), service không set tay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final PricingConfigRepository pricingConfigRepository;

    // ── SystemConfig ────────────────────────────────────────────────────────

    /** {@code readOnly = true}: Hibernate bỏ qua dirty checking, nhẹ hơn cho luồng chỉ đọc. */
    @Transactional(readOnly = true)
    public SystemConfigResponse getSystemConfig() {
        return SystemConfigResponse.fromEntity(loadSystemConfig());
    }

    /**
     * Giá trị mới chỉ áp cho Tenant đăng ký SAU thời điểm sửa: hạn dùng thử của Tenant đang
     * dùng thử đã được chốt thành ngày cụ thể ở {@code subscriptions.trial_ends_at}.
     *
     * <p><b>Dirty checking:</b> entity được nạp trong transaction nên Hibernate tự phát hiện
     * setter làm đổi trạng thái và ghi UPDATE lúc commit. Vẫn gọi {@code save} cho rõ ý và
     * để nhận lại entity mới nhất.
     */
    @Transactional
    public SystemConfigResponse updateSystemConfig(UpdateSystemConfigRequest request) {
        SystemConfig config = loadSystemConfig();
        config.setTrialDays(request.getTrialDays());
        config.setGracePeriodDays(request.getGracePeriodDays());

        log.info("Cập nhật cấu hình hệ thống: dùng thử {} ngày, ân hạn {} ngày",
            request.getTrialDays(), request.getGracePeriodDays());
        return SystemConfigResponse.fromEntity(systemConfigRepository.save(config));
    }

    // ── PricingConfig ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PricingConfigResponse> listPricing() {
        return pricingConfigRepository.findAllByOrderByEffectiveFromDesc().stream()
            .map(PricingConfigResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public PricingConfigResponse getCurrentPricing() {
        return PricingConfigResponse.fromEntity(resolvePricing(ShiftTimeUtils.todayInHanoi()));
    }

    /**
     * Thêm bảng giá mới. Hai chốt chặn:
     * <ol>
     *   <li>Ngày hiệu lực không được ở quá khứ (theo GIỜ HÀ NỘI — quy ước team). Cho lùi ngày
     *       thì bảng giá mới có thể "chen" vào giai đoạn Subscription đã chốt snapshot, làm
     *       lịch sử giá không còn khớp với hóa đơn.</li>
     *   <li>Không trùng ngày hiệu lực. Kiểm tra trước để trả thông báo tiếng Việt cụ thể; nếu
     *       hai Admin thêm cùng lúc thì UNIQUE ở DB vẫn chặn, và
     *       {@code GlobalExceptionHandler} đổi lỗi đó thành 409 thay vì 500.</li>
     * </ol>
     */
    @Transactional
    public PricingConfigResponse createPricing(CreatePricingConfigRequest request) {
        if (request.getEffectiveFrom().isBefore(ShiftTimeUtils.todayInHanoi())) {
            throw new BusinessException("Ngày hiệu lực không được ở quá khứ.");
        }
        if (pricingConfigRepository.existsByEffectiveFrom(request.getEffectiveFrom())) {
            throw new BusinessException(
                "Đã có bảng giá hiệu lực từ ngày " + request.getEffectiveFrom() + ".");
        }

        PricingConfig saved = pricingConfigRepository.save(PricingConfig.builder()
            .pricePerLocation(request.getPricePerLocation())
            .pricePerUser(request.getPricePerUser())
            .pricePerRoom(request.getPricePerRoom())
            .effectiveFrom(request.getEffectiveFrom())
            .build());

        log.info("Thêm bảng giá hiệu lực từ {}: location={}, user={}, room={}",
            saved.getEffectiveFrom(), saved.getPricePerLocation(),
            saved.getPricePerUser(), saved.getPricePerRoom());
        return PricingConfigResponse.fromEntity(saved);
    }

    // ── Dùng nội bộ bởi các chức năng SaaS khác ─────────────────────────────

    /** Đăng ký Tenant lấy {@code trialDays} ở đây để tính hạn dùng thử — BR-SAAS-08. */
    @Transactional(readOnly = true)
    public SystemConfig loadSystemConfig() {
        return systemConfigRepository.findFirstByOrderByCreatedAtAsc()
            .orElseThrow(() -> new BusinessException(
                "Chưa có cấu hình hệ thống. Đây là lỗi dữ liệu: V1__init_schema.sql phải seed 1 bản ghi."));
    }

    /**
     * Bảng giá hiệu lực tại {@code date}. Dùng để chốt snapshot đơn giá cho Subscription
     * (BR-SAAS-05). Ném lỗi thay vì trả null vì thiếu bảng giá là lỗi dữ liệu, không phải
     * trường hợp nghiệp vụ hợp lệ (seed có sẵn dòng hiệu lực từ 2025-01-01).
     */
    @Transactional(readOnly = true)
    public PricingConfig resolvePricing(LocalDate date) {
        return pricingConfigRepository
            .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)
            .orElseThrow(() -> new BusinessException("Chưa có bảng giá nào hiệu lực tại ngày " + date + "."));
    }
}
