package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.TenantUsageResponse;
import com.example.SWP391_G2_SE2055_JV.dto.TenantUsageResponse.UsageMetric;
import com.example.SWP391_G2_SE2055_JV.entity.Invoice;
import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.InvoiceStatus;
import com.example.SWP391_G2_SE2055_JV.enums.InvoiceType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.InvoiceRepository;
import com.example.SWP391_G2_SE2055_JV.repository.SubscriptionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

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
 * chưa nghỉ việc, tính cả INACTIVE; Phòng — chỉ phòng đang dùng, không tính phòng đã xóa mềm.
 */
@Service
@RequiredArgsConstructor
public class TenantUsageService {

    private final TenantRepository       tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantUsageRepository  tenantUsageRepository;
    private final InvoiceRepository      invoiceRepository;

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

    /**
     * Hạn mức tạo mới ngoài quota hiện tại ({@code quota} chọn loại): số NHỎ NHẤT trong
     * <ul>
     *   <li>các hóa đơn mua gói / gia hạn đang chờ thanh toán — đang mua gói nhỏ thì không tạo thêm
     *       theo hạn mức cũ rồi mới trả tiền;</li>
     *   <li>số lượng của các kỳ đã gia hạn trước mà chưa bắt đầu ({@link #plannedQuota}) — đã trả
     *       tiền kỳ tới với số lượng ít hơn thì không tạo thêm vượt số đó trước ngày đổi kỳ.</li>
     * </ul>
     * Rỗng nếu không có. Hóa đơn gia hạn giữ nguyên số lượng thì bằng quota hiện tại nên không chặn
     * thêm gì; lấy số nhỏ nhất vì có thể còn hơn một hóa đơn (link cũ hết hạn nhưng chưa được dọn).
     *
     * <p>Không gắn {@code @Transactional}: nơi gọi đã ở trong transaction và đang giữ khóa gói.
     */
    public OptionalInt pendingPurchaseLimit(UUID tenantId, ToIntFunction<Invoice> quota) {
        IntStream pending = invoiceRepository
            .findByTenantIdAndStatusAndInvoiceType(tenantId, InvoiceStatus.PENDING, InvoiceType.PERIODIC).stream()
            .mapToInt(quota);
        IntStream planned = subscriptionRepository.findByTenantId(tenantId)
            .map(sub -> paidRenewalsNotStarted(sub).stream().mapToInt(r -> plannedQuota(r, quota)))
            .orElseGet(IntStream::empty);
        return IntStream.concat(pending, planned).min();
    }

    /**
     * Hóa đơn gia hạn ĐÃ TRẢ mà kỳ chưa được áp vào gói — gia hạn trước, kỳ bắt đầu sau ngày bắt đầu
     * kỳ hiện tại ({@code current_period_start}). Job đổi kỳ áp chúng khi tới ngày. Cũ trước, mới sau.
     */
    public List<Invoice> paidRenewalsNotStarted(Subscription sub) {
        LocalDate applied = sub.getCurrentPeriodStart();
        return invoiceRepository
            .findByTenantIdAndStatusAndInvoiceType(sub.getTenantId(), InvoiceStatus.PAID, InvoiceType.PERIODIC).stream()
            .filter(i -> i.getPeriodStart() != null && (applied == null || i.getPeriodStart().isAfter(applied)))
            .sorted(Comparator.comparing(Invoice::getPeriodStart))
            .toList();
    }

    /**
     * Số lượng ({@code quota} chọn loại) áp dụng khi kỳ của hóa đơn gia hạn {@code renewal} bắt đầu:
     * số chọn lúc gia hạn, nâng lên theo các lần mua thêm ĐÃ TRẢ có kỳ phủ tới kỳ đó.
     *
     * <p>Mua thêm tính tiền tới ngày đến hạn lúc mua, nên mua thêm sau khi đã gia hạn trước là đã trả
     * cả phần kỳ tới — lấy số lớn hơn để phần đó không mất. Mua thêm trước khi gia hạn có kỳ kết thúc
     * trước kỳ gia hạn nên không tính (số chọn lúc gia hạn đã gồm nó).
     */
    public int plannedQuota(Invoice renewal, ToIntFunction<Invoice> quota) {
        int planned = quota.applyAsInt(renewal);
        for (Invoice upgrade : invoiceRepository.findByTenantIdAndStatusAndInvoiceType(
                renewal.getTenantId(), InvoiceStatus.PAID, InvoiceType.UPGRADE_DIFF)) {
            if (upgrade.getPeriodEnd() != null && !upgrade.getPeriodEnd().isBefore(renewal.getPeriodStart())) {
                planned = Math.max(planned, quota.applyAsInt(upgrade));
            }
        }
        return planned;
    }
}
