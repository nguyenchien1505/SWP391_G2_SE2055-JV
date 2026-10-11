package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.BillingOverviewResponse;
import com.example.SWP391_G2_SE2055_JV.dto.InvoiceResponse;
import com.example.SWP391_G2_SE2055_JV.dto.PricingConfigResponse;
import com.example.SWP391_G2_SE2055_JV.dto.TenantUsageResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.InvoiceRepository;
import com.example.SWP391_G2_SE2055_JV.repository.SubscriptionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Màn "Gói dịch vụ" của Giám đốc — BR-SAAS-02..08, BR-SAAS-15.
 *
 * <p>Đợt này chỉ ĐỌC: gói đang dùng, hạn dùng, mức sử dụng, lịch sử hóa đơn. Mua gói, mua thêm
 * quota và gia hạn (có thanh toán) làm ở các đợt sau.
 *
 * <p>Tenant luôn lấy từ người đang đăng nhập, không nhận từ request — Giám đốc chỉ xem được gói
 * của chính Tenant mình.
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private final TenantRepository       tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository      invoiceRepository;
    private final TenantUsageService     tenantUsageService;
    private final PlatformConfigService  platformConfigService;

    @Transactional(readOnly = true)
    public BillingOverviewResponse getOverview() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        // DM-09: mỗi Tenant đúng 1 Subscription — thiếu là lỗi dữ liệu.
        Subscription sub = subscriptionRepository.findByTenantId(tenantId)
            .orElseThrow(() -> new BusinessException("Tenant chưa có gói dịch vụ."));

        // Dùng lại cách đếm của màn Admin để hai nơi luôn ra cùng con số.
        TenantUsageResponse usage = tenantUsageService.getUsage(tenantId);
        LocalDate today = ShiftTimeUtils.todayInHanoi();

        Long trialDaysLeft = sub.isTrial() && sub.getTrialEndsAt() != null
            ? Math.max(0, ChronoUnit.DAYS.between(today, sub.getTrialEndsAt()))
            : null;

        // Phí mỗi kỳ tính theo QUOTA đã mua, không theo số đang dùng (BR-SAAS-05).
        long periodFee = sub.getQuotaLocation() * sub.getPricePerLocation()
            + sub.getQuotaUser() * sub.getPricePerUser()
            + sub.getQuotaRoom() * sub.getPricePerRoom();

        return BillingOverviewResponse.builder()
            .tenantName(tenant.getName())
            .status(tenant.getStatus())
            .suspendReason(tenant.getSuspendReason())
            .trial(sub.isTrial())
            .trialEndsAt(sub.getTrialEndsAt())
            .trialDaysLeft(trialDaysLeft)
            .currentPeriodStart(sub.getCurrentPeriodStart())
            .nextBillingDate(sub.getNextBillingDate())
            .quotaLocation(sub.getQuotaLocation())
            .quotaUser(sub.getQuotaUser())
            .quotaRoom(sub.getQuotaRoom())
            .pricePerLocation(sub.getPricePerLocation())
            .pricePerUser(sub.getPricePerUser())
            .pricePerRoom(sub.getPricePerRoom())
            .periodFee(periodFee)
            .locations(usage.getLocations())
            .staff(usage.getStaff())
            .rooms(usage.getRooms())
            .currentPricing(PricingConfigResponse.fromEntity(platformConfigService.resolvePricing(today)))
            .build();
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listInvoices() {
        return invoiceRepository.findByTenantIdOrderByIssuedAtDesc(SecurityUtils.getCurrentTenantId())
            .stream()
            .map(InvoiceResponse::fromEntity)
            .toList();
    }
}
