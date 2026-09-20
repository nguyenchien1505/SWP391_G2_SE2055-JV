package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.TenantDetailResponse;
import com.example.SWP391_G2_SE2055_JV.dto.TenantResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.SuspendReason;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.SubscriptionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantRepository;
import com.example.SWP391_G2_SE2055_JV.utils.PaginationUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Admin Platform quản lý Tenant và trạng thái hoạt động của Tenant — BR-PERM-01.
 *
 * <p>Gồm: xem danh sách, xem chi tiết, khóa / mở lại Tenant, và job tự khóa Tenant hết hạn
 * dùng thử. Admin KHÔNG sửa được tên công ty và số điện thoại liên hệ của Tenant.
 *
 * <p>Vòng đời trạng thái (BR-SAAS-01):
 * <pre>
 *   TRIAL ──(hết hạn dùng thử, job — BR-SAAS-09)──▶ SUSPENDED (TRIAL_EXPIRED)
 *   TRIAL / ACTIVE / PAYMENT_OVERDUE ──(Admin khóa)──▶ SUSPENDED (ADMIN_LOCKED)
 *   SUSPENDED (ADMIN_LOCKED) ──(Admin mở lại — BR-SAAS-12)──▶ TRIAL   nếu Subscription còn is_trial
 *                                                          └─▶ ACTIVE  nếu đã trả phí (is_trial = false)
 *   SUSPENDED (TRIAL_EXPIRED / PAYMENT_FAILED) ── chỉ thoát khi thanh toán thành công (chưa làm)
 * </pre>
 * Mở lại KHÔNG được đưa Tenant đang dùng thử thẳng lên ACTIVE: nếu vậy Tenant thoát khỏi cả
 * TRIAL lẫn job hết hạn mà chưa từng trả tiền. Trạng thái "đã trả phí" lấy từ
 * {@code subscriptions.is_trial}, không suy từ trạng thái Tenant.
 *
 * <p>Các đường đi qua thanh toán (PAYMENT_OVERDUE, PAYMENT_FAILED, TRIAL → ACTIVE khi trả phí)
 * thuộc phần thanh toán, làm ở milestone sau — chưa có ở đây.
 *
 * <p>Tenant SUSPENDED bị chặn đăng nhập HOÀN TOÀN mọi user bên trong (BR-SAAS-11). Việc chặn đó
 * do {@code UserDetailsServiceImpl} và {@code CurrentUserRefreshFilter} thực thi sẵn; service
 * này chỉ việc đổi trạng thái, có hiệu lực ngay ở request kế tiếp của người dùng.
 *
 * <p>Admin Platform đứng ngoài mọi Tenant (BR-PERM-01), nên service này KHÔNG gọi
 * {@code SecurityUtils.getCurrentTenantId()} — hàm đó ném lỗi với PLATFORM_ADMIN. Việc chặn
 * người không phải Admin do rule URL {@code /platform/**} và {@code @PreAuthorize} ở controller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAdminService {

    private final TenantRepository       tenantRepository;
    private final SubscriptionRepository subscriptionRepository;

    // ── Xem ─────────────────────────────────────────────────────────────────

    /**
     * Danh sách Tenant, lọc tùy chọn theo trạng thái và từ khóa (tên hoặc email liên hệ).
     *
     * <p>Sắp xếp CỐ ĐỊNH mới nhất trước và số dòng mỗi trang bị chặn trần 100 bởi
     * {@link PaginationUtils}: client không tự truyền được tham số {@code sort} sai (gây lỗi
     * 500) hay {@code size} quá lớn.
     */
    @Transactional(readOnly = true)
    public Page<TenantResponse> search(TenantStatus status, String keyword, Integer page, Integer size) {
        // Chuỗi rỗng coi như không lọc: điều kiện ":keyword IS NULL" trong truy vấn mới bỏ qua được.
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Pageable pageable = PaginationUtils.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return tenantRepository.search(status, normalizedKeyword, pageable)
            .map(TenantResponse::fromEntity);
    }

    /** Chi tiết một Tenant kèm tóm tắt Subscription. Không tồn tại thì 404. */
    @Transactional(readOnly = true)
    public TenantDetailResponse getDetail(UUID id) {
        return buildDetail(loadTenant(id));
    }

    // ── Đổi trạng thái (Admin) ──────────────────────────────────────────────

    /**
     * Admin khóa Tenant, lý do {@code ADMIN_LOCKED} — BR-SAAS-16. Khóa được từ TRIAL, ACTIVE
     * hoặc PAYMENT_OVERDUE; Tenant đã SUSPENDED thì không khóa lại (400).
     *
     * <p>Người dùng của Tenant nhận 401 ở request kế tiếp (BR-SAAS-11).
     */
    @Transactional
    public TenantDetailResponse suspendTenant(UUID id) {
        Tenant tenant = loadTenant(id);
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new BusinessException("Tenant đã bị khóa (lý do: " + tenant.getSuspendReason() + ").");
        }

        applySuspension(tenant, SuspendReason.ADMIN_LOCKED);
        log.info("Admin khóa Tenant {} ({})", tenant.getId(), tenant.getName());
        return buildDetail(tenantRepository.save(tenant));
    }

    /**
     * Admin mở lại Tenant từ SUSPENDED — BR-SAAS-12 (chỉ Admin Platform có quyền này, Giám đốc
     * không tự mở lại được), thu hẹp: Admin CHỈ mở lại được khi lý do khóa là
     * {@code ADMIN_LOCKED}, tức chính Admin đã khóa. Tenant bị khóa vì {@code TRIAL_EXPIRED} hoặc
     * {@code PAYMENT_FAILED} chỉ thoát bằng thanh toán thành công — Admin không có quyền này
     * (thanh toán làm ở milestone sau).
     *
     * <p>{@code SUSPENDED} thiếu lý do là dữ liệu lệch (DB không ép phải có); coi như
     * ADMIN_LOCKED để Admin vẫn gỡ được, khớp với {@code TenantAccessPolicy} (thiếu lý do thì
     * chặn tất cả).
     *
     * <p><b>Trạng thái sau khi mở lại</b> phụ thuộc Subscription: còn {@code is_trial} thì về
     * TRIAL (tiếp tục dùng thử, vẫn chịu job hết hạn), đã trả phí thì về ACTIVE. Nếu hạn dùng thử
     * đã qua khi Admin mở lại thì Tenant vẫn về TRIAL rồi bị job khóa lại (TRIAL_EXPIRED) ở lần
     * chạy tới, tức tối đa khoảng một ngày; Admin không gia hạn dùng thử được.
     *
     * <p>Thiếu Subscription là dữ liệu lệch: không có căn cứ để biết đã trả phí hay chưa, nên
     * về ACTIVE và ghi log cảnh báo.
     *
     * <p>Xóa {@code suspendReason} vì trường này chỉ có nghĩa khi đang SUSPENDED (BR-SAAS-16);
     * {@code suspendedAt} giữ nguyên như mốc của lần khóa gần nhất. Không sửa Subscription —
     * chuyển từ dùng thử sang trả phí thuộc phần thanh toán.
     */
    @Transactional
    public TenantDetailResponse reactivateTenant(UUID id) {
        Tenant tenant = loadTenant(id);
        if (tenant.getStatus() != TenantStatus.SUSPENDED) {
            throw new BusinessException(
                "Tenant không ở trạng thái bị khóa (trạng thái hiện tại: " + tenant.getStatus() + ").");
        }

        SuspendReason reason = tenant.getSuspendReason();
        if (reason == SuspendReason.TRIAL_EXPIRED || reason == SuspendReason.PAYMENT_FAILED) {
            throw new BusinessException("Tenant bị khóa do " + reason
                + ": chỉ được kích hoạt lại khi thanh toán thành công, Admin không có quyền mở lại.");
        }

        Subscription subscription = subscriptionRepository.findByTenantId(id).orElse(null);
        if (subscription == null) {
            log.warn("Tenant {} không có Subscription — mở lại về ACTIVE, cần kiểm tra dữ liệu", id);
        }
        // Còn dùng thử thì quay về TRIAL, không được "lên" ACTIVE khi chưa trả phí.
        boolean stillOnTrial = subscription != null && subscription.isTrial();
        TenantStatus restored = stillOnTrial ? TenantStatus.TRIAL : TenantStatus.ACTIVE;

        tenant.setStatus(restored);
        tenant.setSuspendReason(null);
        tenant.setReactivatedAt(LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE));
        log.info("Admin mở lại Tenant {} ({}) về {}", tenant.getId(), tenant.getName(), restored);
        return buildDetail(tenantRepository.save(tenant));
    }

    // ── Job hết hạn dùng thử ────────────────────────────────────────────────

    /**
     * BR-SAAS-09: hết hạn dùng thử mà chưa nâng cấp thì tự động chuyển SUSPENDED
     * ({@code TRIAL_EXPIRED}). KHÔNG có ân hạn — ân hạn chỉ áp dụng cho thanh toán thất bại
     * (BR-SAAS-10).
     *
     * <p>Hết hạn khi {@code trial_ends_at <= hôm nay} (giờ Hà Nội): Tenant đăng ký ngày D với N
     * ngày dùng thử có {@code trial_ends_at = D + N}, dùng đủ N ngày rồi bị khóa lúc 00:00
     * ngày {@code D + N}.
     *
     * <p><b>Chọn theo Subscription, không theo trạng thái Tenant:</b> cột {@code is_trial} mới là
     * thứ cho biết Tenant đã trả phí chưa. Mọi Tenant còn {@code is_trial} mà quá hạn đều bị khóa,
     * TRỪ Tenant đã SUSPENDED (đang bị khóa rồi, giữ nguyên lý do — ví dụ ADMIN_LOCKED không bị
     * đổi thành TRIAL_EXPIRED). Nhờ vậy dữ liệu lệch kiểu "ACTIVE nhưng vẫn is_trial" cũng không
     * lọt qua để dùng miễn phí vô hạn.
     *
     * <p>Idempotent: Tenant đã SUSPENDED bị bỏ qua nên chạy lại không khóa lần hai. Các Tenant
     * được xử lý trong CÙNG một transaction; hôm sau job sẽ thử lại nếu lần này lỗi.
     *
     * @return số Tenant đã bị khóa trong lần chạy này
     */
    @Transactional
    public int expireTrialTenants() {
        LocalDate today = ShiftTimeUtils.todayInHanoi();

        List<UUID> tenantIds = subscriptionRepository
            .findByTrialTrueAndTrialEndsAtLessThanEqual(today).stream()
            .map(Subscription::getTenantId)
            .toList();

        int suspended = 0;
        for (Tenant tenant : tenantRepository.findAllById(tenantIds)) {
            if (tenant.getStatus() != TenantStatus.SUSPENDED) {
                applySuspension(tenant, SuspendReason.TRIAL_EXPIRED);
                suspended++;
                log.info("Tenant {} ({}) hết hạn dùng thử, chuyển SUSPENDED", tenant.getId(), tenant.getName());
            }
        }
        // Không cần gọi save: entity nạp trong transaction nên dirty checking tự ghi UPDATE khi commit.
        return suspended;
    }

    // ── Nội bộ ──────────────────────────────────────────────────────────────

    /** Dùng chung cho Admin khóa tay và job hết hạn dùng thử. */
    private void applySuspension(Tenant tenant, SuspendReason reason) {
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenant.setSuspendReason(reason);
        tenant.setSuspendedAt(LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE));
        // Mốc mở lại chỉ có nghĩa với lần khóa gần nhất: khóa lại thì xóa mốc cũ.
        tenant.setReactivatedAt(null);
    }

    private Tenant loadTenant(UUID id) {
        return tenantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", id));
    }

    private TenantDetailResponse buildDetail(Tenant tenant) {
        // DM-09: mỗi Tenant đúng 1 Subscription. Thiếu thì trả null thay vì ném lỗi để Admin
        // vẫn xem được Tenant có dữ liệu lệch.
        Subscription subscription = subscriptionRepository.findByTenantId(tenant.getId()).orElse(null);
        return TenantDetailResponse.fromEntity(tenant, subscription);
    }
}
