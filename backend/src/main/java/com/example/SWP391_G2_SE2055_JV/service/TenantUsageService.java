package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.TenantUsageResponse;
import com.example.SWP391_G2_SE2055_JV.dto.TenantUsageResponse.UsageMetric;
import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.SubscriptionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Theo dõi mức sử dụng của một Tenant so với quota — Admin Platform (BR-PERM-01).
 *
 * <p>Luồng: lấy Tenant (404 nếu không có) → lấy Subscription (hạn mức) → đếm Location, Staff,
 * Phòng bằng {@link TenantUsageRepository} → ghép từng cặp (đã dùng, hạn mức).
 *
 * <p>Toàn bộ chạy trong MỘT transaction chỉ đọc: MySQL InnoDB mặc định đọc nhất quán theo một
 * "ảnh chụp" nên ba con số đếm không lệch nhau dù dữ liệu đang được ghi song song.
 *
 * <p>Service này KHÔNG gọi {@code SecurityUtils.getCurrentTenantId()} vì Admin Platform không
 * thuộc Tenant nào; quyền truy cập do rule URL {@code /platform/**} và {@code @PreAuthorize}.
 *
 * <p>Quy tắc đếm (chi tiết ở {@link TenantUsageRepository}): Location — tất cả; Staff — chỉ Staff,
 * chưa nghỉ việc, tính cả INACTIVE; Phòng — tất cả kể cả đã xóa mềm.
 */
@Service
@RequiredArgsConstructor
public class TenantUsageService {

    private final TenantRepository       tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantUsageRepository  tenantUsageRepository;

    @Transactional(readOnly = true)
    public TenantUsageResponse getUsage(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));

        // DM-09: mỗi Tenant đúng 1 Subscription. Thiếu thì vẫn trả số đã dùng, chỉ không có hạn mức.
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId).orElse(null);

        long locations = tenantUsageRepository.countLocations(tenantId);
        long staff     = tenantUsageRepository.countUsers(tenantId, Role.STAFF, UserStatus.TERMINATED);
        long rooms     = tenantUsageRepository.countRooms(tenantId);

        return TenantUsageResponse.builder()
            .tenantId(tenant.getId())
            .tenantName(tenant.getName())
            .status(tenant.getStatus())
            .trial(subscription != null && subscription.isTrial())
            .trialEndsAt(subscription == null ? null : subscription.getTrialEndsAt())
            .locations(UsageMetric.of(locations, subscription == null ? null : subscription.getQuotaLocation()))
            .staff(UsageMetric.of(staff, subscription == null ? null : subscription.getQuotaUser()))
            .rooms(UsageMetric.of(rooms, subscription == null ? null : subscription.getQuotaRoom()))
            .build();
    }
}
