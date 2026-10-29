package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.BillingOverviewResponse;
import com.example.SWP391_G2_SE2055_JV.dto.InvoiceResponse;
import com.example.SWP391_G2_SE2055_JV.dto.PaymentResultResponse;
import com.example.SWP391_G2_SE2055_JV.dto.PaymentResultResponse.Status;
import com.example.SWP391_G2_SE2055_JV.dto.PaymentUrlResponse;
import com.example.SWP391_G2_SE2055_JV.dto.PricingConfigResponse;
import com.example.SWP391_G2_SE2055_JV.dto.PurchaseRequest;
import com.example.SWP391_G2_SE2055_JV.dto.TenantUsageResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Invoice;
import com.example.SWP391_G2_SE2055_JV.entity.PricingConfig;
import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.InvoiceStatus;
import com.example.SWP391_G2_SE2055_JV.enums.InvoiceType;
import com.example.SWP391_G2_SE2055_JV.enums.SuspendReason;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.InvoiceRepository;
import com.example.SWP391_G2_SE2055_JV.repository.SubscriptionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gói dịch vụ của Giám đốc — BR-SAAS-02..10, BR-SAAS-15. Thanh toán qua VNPay.
 *
 * <pre>
 *   Mua gói lần đầu  dùng thử → trả phí; chọn số lượng; kỳ 30 ngày tính từ ngày thanh toán
 *   Gia hạn          1 kỳ theo quota hiện tại; nối tiếp ngày đến hạn (bị khóa thì tính từ hôm nay)
 *   Mua thêm quota   chỉ tăng; trả chênh lệch cho số ngày còn lại tới ngày đến hạn
 * </pre>
 *
 * <p>Cả ba dùng chung một luồng: tạo hóa đơn Chờ → link VNPay → {@link #handleVnpayReturn} kiểm tra
 * và áp kết quả. Hóa đơn lỗi không được giữ lại: thất bại / hủy thì xóa; bỏ dở (đóng tab) thì hỏi
 * VNPay khi bấm thanh toán lại hoặc qua job đối soát. Chỉ hóa đơn "cần đối soát" (đã có tiền nhưng
 * không áp được) được giữ ở trạng thái FAILED.
 *
 * <p>Tenant luôn lấy từ người đang đăng nhập, không nhận từ request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    /** Mỗi chu kỳ 30 ngày — BR-SAAS-05. */
    private static final int CYCLE_DAYS = 30;
    /** Chỉ gia hạn khi còn tối đa bấy nhiêu ngày tới hạn — trả trước nhiều nhất 2 kỳ. */
    private static final int MAX_PREPAY_DAYS = 60;
    /** Mua thêm quota: số tiền chênh lệch tối thiểu (VND), thấp hơn thì từ chối. Quy định của hệ thống. */
    private static final long MIN_UPGRADE_AMOUNT = 5_000;

    private final TenantRepository       tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository      invoiceRepository;
    private final TenantUsageService     tenantUsageService;
    private final PlatformConfigService  platformConfigService;
    private final VnpayService           vnpayService;

    // ── Xem ─────────────────────────────────────────────────────────────────

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

        return BillingOverviewResponse.builder()
            .tenantName(tenant.getName())
            .status(tenant.getStatus())
            .suspendReason(tenant.getSuspendReason())
            .trial(sub.isTrial())
            .trialEndsAt(sub.getTrialEndsAt())
            .trialDaysLeft(trialDaysLeft)
            .nextBillingDate(sub.getNextBillingDate())
            .quotaLocation(sub.getQuotaLocation())
            .quotaUser(sub.getQuotaUser())
            .quotaRoom(sub.getQuotaRoom())
            .pricePerLocation(sub.getPricePerLocation())
            .pricePerUser(sub.getPricePerUser())
            .pricePerRoom(sub.getPricePerRoom())
            .periodFee(periodFee(sub))
            .locations(usage.getLocations())
            .staff(usage.getStaff())
            .rooms(usage.getRooms())
            .currentPricing(PricingConfigResponse.fromEntity(platformConfigService.resolvePricing(today)))
            .trialDays(platformConfigService.loadSystemConfig().getTrialDays())
            .build();
    }

    /**
     * Lịch sử hóa đơn: CHỈ hóa đơn đã thanh toán. Mua gói và gia hạn cùng là PERIODIC — hóa đơn chu
     * kỳ đầu tiên của Tenant là mua gói, các hóa đơn chu kỳ sau là gia hạn.
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> listInvoices() {
        List<Invoice> paid = invoiceRepository.findByTenantIdAndStatusOrderByIssuedAtDesc(
            SecurityUtils.getCurrentTenantId(), InvoiceStatus.PAID);
        UUID firstPeriodicId = paid.stream()
            .filter(i -> i.getInvoiceType() == InvoiceType.PERIODIC)
            .reduce((newer, older) -> older)          // mới nhất trước → phần tử cuối là đầu tiên
            .map(Invoice::getId)
            .orElse(null);
        return paid.stream()
            .map(i -> InvoiceResponse.fromEntity(i,
                i.getInvoiceType() == InvoiceType.UPGRADE_DIFF ? InvoiceResponse.Kind.MUA_THEM
                    : i.getId().equals(firstPeriodicId) ? InvoiceResponse.Kind.MUA_GOI
                    : InvoiceResponse.Kind.GIA_HAN))
            .toList();
    }

    // ── Chọn gói lần đầu: Dùng thử ──────────────────────────────────────────

    /**
     * Tenant mới (đã xác thực email, chưa chọn gói) chọn Dùng thử: bắt đầu dùng thử từ hôm nay, số
     * ngày theo SystemConfig — BR-SAAS-08. Từ đây dùng chung luồng dùng thử có sẵn (job hết hạn dùng
     * thử, mua gói khi đang dùng thử). Lựa chọn còn lại — Mua gói — đi thẳng {@link #purchase}.
     *
     * <p>Còn giao dịch mua gói dở thì hỏi VNPay trước như {@link #purchase}: nếu giao dịch đó đã
     * thành công thì gói đã được kích hoạt, không bắt đầu dùng thử nữa. Không ném lỗi ở nhánh này để
     * kết quả vừa áp vẫn được lưu.
     */
    @Transactional
    public PaymentUrlResponse startTrial() {
        Locked ctx = lockCurrentTenant();
        Subscription sub = ctx.sub();
        if (!sub.isTrial() || sub.getTrialEndsAt() != null || ctx.tenant().getStatus() != TenantStatus.TRIAL) {
            throw new BusinessException("Bạn đã chọn gói dịch vụ rồi.");
        }
        PaymentUrlResponse blocked = resolvePendingPayments(ctx);
        if (blocked != null) {
            return blocked;
        }

        LocalDate trialEndsAt = ctx.today().plusDays(platformConfigService.loadSystemConfig().getTrialDays());
        sub.setTrialEndsAt(trialEndsAt);
        subscriptionRepository.save(sub);
        log.info("Tenant {} bắt đầu dùng thử, hết hạn {}", ctx.tenant().getId(), trialEndsAt);
        return PaymentUrlResponse.message(String.format(
            "Đã bắt đầu dùng thử. Bạn được dùng miễn phí tới hết ngày %s.", trialEndsAt.minusDays(1)), true);
    }

    // ── Ba thao tác thanh toán ──────────────────────────────────────────────

    /** Mua gói lần đầu. Hóa đơn chưa có kỳ ({@code period_start} = NULL) cho tới khi thanh toán xong. */
    @Transactional
    public PaymentUrlResponse purchase(PurchaseRequest request, String clientIp) {
        Locked ctx = lockCurrentTenant();
        if (!ctx.sub().isTrial()) {
            throw new BusinessException("Gói đã được mua. Dùng chức năng Mua thêm quota hoặc Gia hạn.");
        }
        if (ctx.isSuspendedExcept(SuspendReason.TRIAL_EXPIRED)) {
            throw new BusinessException("Tenant đang bị khóa nên chưa mua gói được.");
        }
        PaymentUrlResponse blocked = resolvePendingPayments(ctx);
        if (blocked != null) {
            return blocked;
        }

        int ql = request.getQuotaLocation(), qu = request.getQuotaUser(), qr = request.getQuotaRoom();
        // Chưa hỗ trợ hạ gói: quota mới không được thấp hơn số đang dùng (BR-SAAS-02, 03).
        TenantUsageResponse usage = tenantUsageService.getUsage(ctx.tenant().getId());
        assertNotBelowUsage("khách sạn", ql, usage.getLocations().getUsed());
        assertNotBelowUsage("nhân viên", qu, usage.getStaff().getUsed());
        assertNotBelowUsage("phòng", qr, usage.getRooms().getUsed());

        // Luôn tự tính lại tiền theo bảng giá hiện hành — không tin số FE gửi lên.
        PricingConfig pricing = platformConfigService.resolvePricing(ctx.today());
        long amount = ql * pricing.getPricePerLocation() + qu * pricing.getPricePerUser() + qr * pricing.getPricePerRoom();
        return createPayment(ctx, InvoiceType.PERIODIC, null, null, ql, qu, qr, amount, clientIp, "Thanh toan goi dich vu");
    }

    /**
     * Gia hạn 1 kỳ 30 ngày theo quota hiện tại và đơn giá đã chốt. Chỉ khi còn ≤ {@value #MAX_PREPAY_DAYS}
     * ngày tới hạn. Kỳ mới nối tiếp ngày đến hạn — thời gian quá hạn trong ân hạn vẫn được dùng nên vẫn
     * tính tiền; riêng khi đã bị khóa vì thanh toán lỗi thì kỳ tính từ hôm nay (thời gian bị khóa không
     * dùng được).
     */
    @Transactional
    public PaymentUrlResponse renew(String clientIp) {
        Locked ctx = lockCurrentTenant();
        Subscription sub = ctx.sub();
        if (sub.isTrial() || sub.getNextBillingDate() == null) {
            throw new BusinessException("Gói đang dùng thử. Dùng chức năng Mua gói.");
        }
        if (ctx.isSuspendedExcept(SuspendReason.PAYMENT_FAILED)) {
            throw new BusinessException("Tenant đang bị khóa nên chưa gia hạn được.");
        }
        PaymentUrlResponse blocked = resolvePendingPayments(ctx);
        if (blocked != null) {
            return blocked;
        }

        LocalDate due = sub.getNextBillingDate();
        if (ChronoUnit.DAYS.between(ctx.today(), due) > MAX_PREPAY_DAYS) {
            throw new BusinessException(String.format(
                "Chỉ gia hạn được khi còn tối đa %d ngày tới ngày đến hạn (%s).", MAX_PREPAY_DAYS, due));
        }
        LocalDate start = ctx.isSuspendedFor(SuspendReason.PAYMENT_FAILED) ? ctx.today() : due;
        return createPayment(ctx, InvoiceType.PERIODIC, start, start.plusDays(CYCLE_DAYS - 1),
            sub.getQuotaLocation(), sub.getQuotaUser(), sub.getQuotaRoom(), periodFee(sub), clientIp, "Gia han goi dich vu");
    }

    /**
     * Mua thêm quota giữa kỳ (BR-SAAS-06): chỉ TĂNG; quota mới có hiệu lực sau khi thanh toán, ngày
     * đến hạn và đơn giá không đổi. Chỉ khi Tenant ACTIVE (quá hạn / bị khóa thì gia hạn trước).
     * <pre>
     *   tiền = Σ(phần tăng × đơn giá đã chốt) × số ngày còn lại / 30   (dưới {@value #MIN_UPGRADE_AMOUNT}đ thì từ chối)
     * </pre>
     */
    @Transactional
    public PaymentUrlResponse upgrade(PurchaseRequest request, String clientIp) {
        Locked ctx = lockCurrentTenant();
        Subscription sub = ctx.sub();
        if (sub.isTrial() || sub.getNextBillingDate() == null) {
            throw new BusinessException("Gói đang dùng thử. Dùng chức năng Mua gói.");
        }
        if (ctx.tenant().getStatus() != TenantStatus.ACTIVE) {
            throw new BusinessException("Gói đang quá hạn hoặc bị khóa. Vui lòng gia hạn trước khi mua thêm quota.");
        }
        PaymentUrlResponse blocked = resolvePendingPayments(ctx);
        if (blocked != null) {
            return blocked;
        }

        long daysLeft = ChronoUnit.DAYS.between(ctx.today(), sub.getNextBillingDate());
        if (daysLeft <= 0) {
            throw new BusinessException("Gói đã đến hạn. Vui lòng gia hạn trước khi mua thêm quota.");
        }
        int ql = request.getQuotaLocation(), qu = request.getQuotaUser(), qr = request.getQuotaRoom();
        if (ql < sub.getQuotaLocation() || qu < sub.getQuotaUser() || qr < sub.getQuotaRoom()) {
            throw new BusinessException("Chưa hỗ trợ giảm quota. Số lượng mới không được thấp hơn gói hiện tại.");
        }
        long fullCycleDiff = (ql - sub.getQuotaLocation()) * sub.getPricePerLocation()
            + (qu - sub.getQuotaUser()) * sub.getPricePerUser()
            + (qr - sub.getQuotaRoom()) * sub.getPricePerRoom();
        if (fullCycleDiff <= 0) {
            throw new BusinessException("Cần tăng ít nhất một loại quota.");
        }
        long amount = Math.round(fullCycleDiff * daysLeft / (double) CYCLE_DAYS);
        if (amount < MIN_UPGRADE_AMOUNT) {
            throw new BusinessException(String.format(
                "Số tiền chênh lệch (%,d đ) thấp hơn mức tối thiểu %,d đ. "
                    + "Vui lòng mua thêm nhiều hơn hoặc mua sau khi gia hạn.", amount, MIN_UPGRADE_AMOUNT));
        }
        // Kỳ của hóa đơn = phần còn lại của thời gian đã trả: hôm nay → trước ngày đến hạn.
        return createPayment(ctx, InvoiceType.UPGRADE_DIFF, ctx.today(), sub.getNextBillingDate().minusDays(1),
            ql, qu, qr, amount, clientIp, "Mua them quota");
    }

    // ── Kết quả VNPay ───────────────────────────────────────────────────────

    /**
     * VNPay đưa Giám đốc về hệ thống. Kiểm tra theo thứ tự, sai bước nào dừng ở bước đó: chữ ký →
     * hóa đơn thuộc đúng Tenant → đã xử lý chưa → số tiền → mã kết quả. Thất bại / hủy thì xóa hóa
     * đơn. Tải lại trang kết quả không áp hai lần.
     */
    @Transactional
    public PaymentResultResponse handleVnpayReturn(Map<String, String> params) {
        if (!vnpayService.verify(params)) {
            log.warn("Kết quả VNPay sai chữ ký: {}", params.get("vnp_TxnRef"));
            return result(Status.INVALID, "Kết quả thanh toán không hợp lệ.", null);
        }
        String code = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");
        boolean success = VnpayService.CODE_SUCCESS.equals(code)
            && VnpayService.CODE_SUCCESS.equals(params.get("vnp_TransactionStatus"));

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID invoiceId = fromTxnRef(params.get("vnp_TxnRef"));
        Invoice invoice = invoiceId == null ? null : invoiceRepository.findById(invoiceId)
            .filter(i -> tenantId.equals(i.getTenantId()))
            .orElse(null);
        if (invoice == null) {
            if (success) {
                log.error("VNPay báo thành công nhưng không tìm thấy hóa đơn: mã giao dịch {}, mã VNPay {}",
                    params.get("vnp_TxnRef"), transactionNo);
                return needsReview(null);
            }
            return failureResult(code, null);    // hóa đơn thất bại đã bị xóa ở lần gọi trước (tải lại trang)
        }

        // Khóa gói trước khi đọc / ghi hóa đơn: hai lần gọi cùng lúc đi lần lượt.
        Subscription sub = subscriptionRepository.findForUpdateByTenantId(tenantId)
            .orElseThrow(() -> new BusinessException("Tenant chưa có gói dịch vụ."));
        if (invoice.isPaid()) {
            return result(Status.SUCCESS, "Thanh toán thành công.", invoice);
        }
        if (invoice.getStatus() == InvoiceStatus.FAILED) {
            return needsReview(invoice);
        }
        if (!success) {
            PaymentResultResponse failed = failureResult(code, invoice);   // lấy số lượng TRƯỚC khi xóa
            deleteUnpaid(invoice, "VNPay mã " + code);
            return failed;
        }
        long paidAmount = parseAmount(params.get("vnp_Amount"));
        if (paidAmount != invoice.getAmount()) {
            markForReview(invoice, "lệch số tiền (VNPay = " + paidAmount + " VND)", transactionNo);
            return needsReview(invoice);
        }
        return applyPayment(invoice, sub, transactionNo);
    }

    // ── Giao dịch bỏ dở ─────────────────────────────────────────────────────

    /** Hóa đơn còn Chờ thanh toán quá {@code minutes} phút — Giám đốc đã bỏ dở (job đối soát). */
    @Transactional(readOnly = true)
    public List<UUID> findAbandonedPayments(int minutes) {
        LocalDateTime before = LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE).minusMinutes(minutes);
        return invoiceRepository.findByStatusAndIssuedAtBefore(InvoiceStatus.PENDING, before).stream()
            .map(Invoice::getId)
            .toList();
    }

    /** Job đối soát: link VNPay của các hóa đơn này đã hết hạn từ lâu. */
    @Transactional
    public void reconcileAbandonedPayment(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null || invoice.getStatus() != InvoiceStatus.PENDING) {
            return;
        }
        subscriptionRepository.findForUpdateByTenantId(invoice.getTenantId())
            .ifPresent(sub -> log.info("Đối soát hóa đơn {}: {}", invoiceId, checkPendingPayment(invoice, sub, false)));
    }

    private enum PendingCheck { PAID, DELETED, REVIEW, IN_PROGRESS, UNKNOWN }

    /**
     * Trước khi mở giao dịch mới: giao dịch cũ còn dở thì hỏi VNPay ngay thay vì bắt Giám đốc chờ.
     * Trả thông báo nếu KHÔNG được mở giao dịch mới, {@code null} nếu được. Không ném lỗi, để các thay
     * đổi vừa làm (kích hoạt gói, xóa hóa đơn, đánh dấu đối soát) vẫn được lưu.
     */
    private PaymentUrlResponse resolvePendingPayments(Locked ctx) {
        for (Invoice old : invoiceRepository.findByTenantIdAndStatus(ctx.tenant().getId(), InvoiceStatus.PENDING)) {
            boolean linkStillValid = old.getIssuedAt().isAfter(ctx.now().minusMinutes(VnpayService.EXPIRE_MINUTES));
            switch (checkPendingPayment(old, ctx.sub(), linkStillValid)) {
                case PAID -> {
                    return PaymentUrlResponse.message(
                        "Giao dịch thanh toán trước đó đã thành công. Gói dịch vụ đã được cập nhật.", true);
                }
                case REVIEW -> {
                    return PaymentUrlResponse.message(
                        "Giao dịch thanh toán trước đó cần được kiểm tra. Vui lòng liên hệ hỗ trợ, không thanh toán lại.", false);
                }
                case IN_PROGRESS -> {
                    return PaymentUrlResponse.message(String.format(
                        "Giao dịch thanh toán trước đó (bắt đầu lúc %s) đang được xử lý. "
                            + "Vui lòng hoàn tất giao dịch đó hoặc thử lại sau ít phút.",
                        old.getIssuedAt().toLocalTime()), false);
                }
                case UNKNOWN -> {
                    if (linkStillValid) {
                        return PaymentUrlResponse.message(
                            "Chưa kiểm tra được giao dịch thanh toán trước đó. Vui lòng thử lại sau ít phút.", false);
                    }
                    // Link cũ đã hết hạn — không trả thêm được nữa; để job đối soát xử lý sau.
                }
                case DELETED -> { /* chưa có tiền, đã xóa — mở giao dịch mới */ }
            }
        }
        return null;
    }

    /**
     * Hỏi VNPay một hóa đơn còn Chờ đã được trả chưa — dùng cho cả bấm thanh toán lại và job đối soát.
     * Chỉ xóa khi VNPay xác nhận chưa có tiền. Nơi gọi phải giữ khóa gói.
     * <pre>
     *   không có giao dịch (chưa nhập thẻ)     → xóa                (DELETED)
     *   đã trả + đúng tiền                     → áp kết quả         (PAID / REVIEW)
     *   đã trả + lệch tiền / nghi gian lận     → cần đối soát       (REVIEW)
     *   chưa hoàn tất: link còn hạn / hết hạn  → chờ / xóa          (IN_PROGRESS / DELETED)
     *   trạng thái khác (thất bại…)            → xóa                (DELETED)
     *   không hỏi được                         → để nguyên          (UNKNOWN)
     * </pre>
     */
    private PendingCheck checkPendingPayment(Invoice invoice, Subscription sub, boolean linkStillValid) {
        VnpayService.QueryResult q = vnpayService.queryTransaction(toTxnRef(invoice), invoice.getIssuedAt());
        if (!q.answered()) {
            return PendingCheck.UNKNOWN;
        }
        if ("91".equals(q.responseCode())) {
            // Nếu sau đó Giám đốc vẫn trả bằng link cũ ở tab khác, kết quả về không thấy hóa đơn và
            // được chuyển sang "cần kiểm tra" — không mất dấu.
            deleteUnpaid(invoice, "VNPay không có giao dịch");
            return PendingCheck.DELETED;
        }
        if (!VnpayService.CODE_SUCCESS.equals(q.responseCode())) {
            log.warn("Tra cứu VNPay cho hóa đơn {} trả mã {} — thử lại sau", invoice.getId(), q.responseCode());
            return PendingCheck.UNKNOWN;
        }
        switch (q.transactionStatus()) {
            case VnpayService.CODE_SUCCESS -> {
                if (q.amountVnd() != invoice.getAmount()) {
                    markForReview(invoice, "lệch số tiền (VNPay = " + q.amountVnd() + " VND)", q.transactionNo());
                    return PendingCheck.REVIEW;
                }
                return applyPayment(invoice, sub, q.transactionNo()).getStatus() == Status.SUCCESS
                    ? PendingCheck.PAID : PendingCheck.REVIEW;
            }
            case "07" -> {
                markForReview(invoice, "VNPay nghi ngờ gian lận", q.transactionNo());
                return PendingCheck.REVIEW;
            }
            case "01", "05" -> {
                if (linkStillValid) {
                    return PendingCheck.IN_PROGRESS;
                }
                deleteUnpaid(invoice, "VNPay chưa hoàn tất, link đã hết hạn");
                return PendingCheck.DELETED;
            }
            default -> {
                deleteUnpaid(invoice, "VNPay trạng thái " + q.transactionStatus());
                return PendingCheck.DELETED;
            }
        }
    }

    // ── Áp kết quả thanh toán thành công ────────────────────────────────────

    /**
     * Hóa đơn đã khớp tiền và VNPay báo thành công. Mỗi loại có một chốt chặn: nếu gói đã đổi kể từ
     * lúc tạo hóa đơn (đã mua / gia hạn / mua thêm ở giao dịch khác) thì KHÔNG áp, đánh dấu cần đối soát
     * — tránh áp hai lần hoặc áp sai số tiền.
     */
    private PaymentResultResponse applyPayment(Invoice invoice, Subscription sub, String transactionNo) {
        if (isFirstPurchase(invoice)) {
            if (!sub.isTrial()) {
                markForReview(invoice, "trả trùng mua gói lần đầu", transactionNo);
                return needsReview(invoice);
            }
            applyFirstPurchase(invoice, sub);
            return result(Status.SUCCESS, "Thanh toán thành công. Gói dịch vụ đã được kích hoạt.", invoice);
        }
        if (invoice.getInvoiceType() == InvoiceType.PERIODIC) {
            // Gia hạn: kỳ không được bắt đầu TRƯỚC ngày đến hạn hiện tại (đã gia hạn ở giao dịch khác).
            if (sub.isTrial() || sub.getNextBillingDate().isAfter(invoice.getPeriodStart())) {
                markForReview(invoice, "kỳ gia hạn không còn khớp ngày đến hạn " + sub.getNextBillingDate(), transactionNo);
                return needsReview(invoice);
            }
            applyRenewal(invoice, sub);
            return result(Status.SUCCESS, "Gia hạn thành công. Gói dịch vụ đã được kéo dài thêm 30 ngày.", invoice);
        }
        // Mua thêm: tiền tính tới ngày đến hạn LÚC TẠO và trên quota lúc đó — phải còn nguyên.
        boolean sameCoverage = !sub.isTrial() && invoice.getPeriodEnd().plusDays(1).equals(sub.getNextBillingDate());
        boolean stillAnIncrease = invoice.getQuotaLocationSnapshot() >= sub.getQuotaLocation()
            && invoice.getQuotaUserSnapshot() >= sub.getQuotaUser()
            && invoice.getQuotaRoomSnapshot() >= sub.getQuotaRoom();
        if (!sameCoverage || !stillAnIncrease) {
            markForReview(invoice, "gói đã thay đổi kể từ lúc tạo hóa đơn mua thêm", transactionNo);
            return needsReview(invoice);
        }
        applyUpgrade(invoice, sub);
        return result(Status.SUCCESS, "Mua thêm quota thành công. Số lượng mới đã có hiệu lực.", invoice);
    }

    /** Gói thôi dùng thử, chốt quota + đơn giá, kỳ 30 ngày từ hôm nay (ngày thử còn dư không cộng); Tenant ACTIVE. */
    private void applyFirstPurchase(Invoice invoice, Subscription sub) {
        LocalDate today = ShiftTimeUtils.todayInHanoi();
        invoice.setPeriodStart(today);
        invoice.setPeriodEnd(today.plusDays(CYCLE_DAYS - 1));
        markPaid(invoice);

        // Đơn giá chốt theo bảng giá lúc phát hành hóa đơn — đúng số tiền đã trả (BR-SAAS-05).
        PricingConfig pricing = platformConfigService.resolvePricing(invoice.getIssuedAt().toLocalDate());
        sub.setTrial(false);
        copyQuota(invoice, sub);
        sub.setPricePerLocation(pricing.getPricePerLocation());
        sub.setPricePerUser(pricing.getPricePerUser());
        sub.setPricePerRoom(pricing.getPricePerRoom());
        sub.setCurrentPeriodStart(today);
        sub.setNextBillingDate(today.plusDays(CYCLE_DAYS));
        subscriptionRepository.save(sub);

        reactivateTenant(sub.getTenantId(), SuspendReason.TRIAL_EXPIRED);   // kể cả đang bị khóa vì hết dùng thử
        log.info("Tenant {} mua gói thành công: hóa đơn {}, đến {}", sub.getTenantId(), invoice.getId(), sub.getNextBillingDate());
    }

    /** Ngày đến hạn = hết kỳ vừa trả + 1. Tenant đang quá hạn / bị khóa do thanh toán lỗi về ACTIVE. */
    private void applyRenewal(Invoice invoice, Subscription sub) {
        markPaid(invoice);
        // Kỳ vừa trả đã bắt đầu (đúng hạn / trễ / bị khóa) thì là kỳ hiện tại; trả trước thì giữ kỳ đang chạy.
        if (!invoice.getPeriodStart().isAfter(ShiftTimeUtils.todayInHanoi())) {
            sub.setCurrentPeriodStart(invoice.getPeriodStart());
        }
        sub.setNextBillingDate(invoice.getPeriodEnd().plusDays(1));
        subscriptionRepository.save(sub);

        reactivateTenant(sub.getTenantId(), SuspendReason.PAYMENT_FAILED);
        log.info("Tenant {} gia hạn thành công: hóa đơn {}, đã trả đến {}", sub.getTenantId(), invoice.getId(), invoice.getPeriodEnd());
    }

    /** Chỉ ghi đè quota; ngày đến hạn và đơn giá đã chốt giữ nguyên. */
    private void applyUpgrade(Invoice invoice, Subscription sub) {
        markPaid(invoice);
        copyQuota(invoice, sub);
        subscriptionRepository.save(sub);
        log.info("Tenant {} mua thêm quota thành công: hóa đơn {}, quota mới {}/{}/{}",
            sub.getTenantId(), invoice.getId(), sub.getQuotaLocation(), sub.getQuotaUser(), sub.getQuotaRoom());
    }

    // ── Quá hạn thanh toán (job) ────────────────────────────────────────────

    /**
     * Theo ngày đến hạn của gói đã trả phí — BR-SAAS-10, không cần hóa đơn sẵn:
     * <pre>
     *   ACTIVE,          hôm nay ≥ ngày đến hạn                  → PAYMENT_OVERDUE (vẫn dùng được)
     *   PAYMENT_OVERDUE, hôm nay ≥ ngày đến hạn + số ngày ân hạn → SUSPENDED (PAYMENT_FAILED)
     * </pre>
     * Tenant bị Admin khóa không bị đụng tới. Chạy lại nhiều lần không sao.
     *
     * @return số Tenant đã đổi trạng thái
     */
    @Transactional
    public int applyOverdueRules() {
        LocalDate today = ShiftTimeUtils.todayInHanoi();
        int graceDays = platformConfigService.loadSystemConfig().getGracePeriodDays();
        int changed = 0;

        for (Subscription sub : subscriptionRepository.findByTrialFalseAndNextBillingDateLessThanEqual(today)) {
            Tenant tenant = tenantRepository.findById(sub.getTenantId()).orElse(null);
            if (tenant == null) {
                continue;
            }
            if (tenant.getStatus() == TenantStatus.ACTIVE) {
                tenant.setStatus(TenantStatus.PAYMENT_OVERDUE);
                changed++;
                log.info("Tenant {} quá hạn thanh toán (đến hạn {})", tenant.getId(), sub.getNextBillingDate());
            }
            if (tenant.getStatus() == TenantStatus.PAYMENT_OVERDUE
                    && !today.isBefore(sub.getNextBillingDate().plusDays(graceDays))) {
                tenant.setStatus(TenantStatus.SUSPENDED);
                tenant.setSuspendReason(SuspendReason.PAYMENT_FAILED);
                tenant.setSuspendedAt(LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE));
                tenant.setReactivatedAt(null);
                changed++;
                log.info("Tenant {} hết ân hạn {} ngày → khóa (PAYMENT_FAILED)", tenant.getId(), graceDays);
            }
            tenantRepository.save(tenant);
        }
        return changed;
    }

    // ── Nội bộ ──────────────────────────────────────────────────────────────

    /** Tenant đang đăng nhập + gói đã KHÓA GHI (hai lần bấm cùng lúc đi lần lượt) + thời điểm tròn giây. */
    private record Locked(Tenant tenant, Subscription sub, LocalDateTime now) {

        LocalDate today() {
            return now.toLocalDate();
        }

        boolean isSuspendedFor(SuspendReason reason) {
            return tenant.getStatus() == TenantStatus.SUSPENDED && tenant.getSuspendReason() == reason;
        }

        /** Bị khóa vì lý do KHÁC lý do được phép thanh toán để mở lại. */
        boolean isSuspendedExcept(SuspendReason allowed) {
            return tenant.getStatus() == TenantStatus.SUSPENDED && tenant.getSuspendReason() != allowed;
        }
    }

    private Locked lockCurrentTenant() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        Subscription sub = subscriptionRepository.findForUpdateByTenantId(tenantId)
            .orElseThrow(() -> new BusinessException("Tenant chưa có gói dịch vụ."));
        // Tròn giây: cột DATETIME không giữ phần lẻ, mà mã giao dịch được dựng lại từ issued_at.
        return new Locked(tenant, sub, LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE).truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * Tạo hóa đơn Chờ thanh toán và link VNPay. Mỗi hóa đơn ứng với ĐÚNG MỘT lần thanh toán: mã giao
     * dịch = id hóa đơn + thời điểm phát hành, nên tra cứu VNPay dựng lại được mà không cần lưu thêm cột.
     *
     * @param periodStart NULL với mua lần đầu (kỳ tính khi thanh toán xong)
     */
    private PaymentUrlResponse createPayment(Locked ctx, InvoiceType type, LocalDate periodStart, LocalDate periodEnd,
                                             int ql, int qu, int qr, long amount, String clientIp, String orderLabel) {
        if (amount <= 0) {
            throw new BusinessException("Số tiền thanh toán phải lớn hơn 0.");
        }
        Invoice invoice = invoiceRepository.save(Invoice.builder()
            .tenantId(ctx.tenant().getId())
            .subscriptionId(ctx.sub().getId())
            .invoiceType(type)
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .quotaLocationSnapshot(ql)
            .quotaUserSnapshot(qu)
            .quotaRoomSnapshot(qr)
            .amount(amount)
            .status(InvoiceStatus.PENDING)
            .issuedAt(ctx.now())
            .build());

        String txnRef = toTxnRef(invoice);
        log.info("Tenant {}: {} — hóa đơn {}, {} VND, mã giao dịch {}", ctx.tenant().getId(), orderLabel, invoice.getId(), amount, txnRef);
        return PaymentUrlResponse.redirect(
            vnpayService.buildPaymentUrl(txnRef, amount, orderLabel + " " + txnRef, clientIp, ctx.now()));
    }

    /** Phí 1 kỳ theo QUOTA đã mua (không theo số đang dùng) và đơn giá đã chốt — BR-SAAS-05. */
    private static long periodFee(Subscription sub) {
        return sub.getQuotaLocation() * sub.getPricePerLocation()
            + sub.getQuotaUser() * sub.getPricePerUser()
            + sub.getQuotaRoom() * sub.getPricePerRoom();
    }

    private static boolean isFirstPurchase(Invoice invoice) {
        return invoice.getInvoiceType() == InvoiceType.PERIODIC && invoice.getPeriodStart() == null;
    }

    private static void copyQuota(Invoice invoice, Subscription sub) {
        sub.setQuotaLocation(invoice.getQuotaLocationSnapshot());
        sub.setQuotaUser(invoice.getQuotaUserSnapshot());
        sub.setQuotaRoom(invoice.getQuotaRoomSnapshot());
    }

    private void markPaid(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE));
        invoiceRepository.save(invoice);
    }

    /** Tenant đang dùng thử / quá hạn / bị khóa vì {@code paidReason} → ACTIVE. Bị Admin khóa thì giữ nguyên. */
    private void reactivateTenant(UUID tenantId, SuspendReason paidReason) {
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            boolean reopen = tenant.getStatus() == TenantStatus.TRIAL
                || tenant.getStatus() == TenantStatus.PAYMENT_OVERDUE
                || (tenant.getStatus() == TenantStatus.SUSPENDED && tenant.getSuspendReason() == paidReason);
            if (reopen) {
                tenant.setStatus(TenantStatus.ACTIVE);
                tenant.setSuspendReason(null);
                tenantRepository.save(tenant);
            }
        });
    }

    /** Hóa đơn chưa trả không liên quan tiền → xóa, chỉ để lại dấu vết trong log. */
    private void deleteUnpaid(Invoice invoice, String reason) {
        log.info("Xóa hóa đơn chưa thanh toán {} (Tenant {}, {} VND, mã giao dịch {}): {}",
            invoice.getId(), invoice.getTenantId(), invoice.getAmount(), toTxnRef(invoice), reason);
        invoiceRepository.delete(invoice);
    }

    /**
     * "Cần đối soát": đã có tiền nhưng không áp được. Dùng FAILED vì hóa đơn thất bại thông thường đã bị
     * xóa — FAILED còn lại luôn nghĩa là cần người kiểm tra, job không xóa.
     */
    private void markForReview(Invoice invoice, String reason, String transactionNo) {
        invoice.setStatus(InvoiceStatus.FAILED);
        invoiceRepository.save(invoice);
        log.error("Hóa đơn {} cần đối soát: {} (Tenant {}, {} VND, mã giao dịch {}, mã VNPay {})",
            invoice.getId(), reason, invoice.getTenantId(), invoice.getAmount(), toTxnRef(invoice), transactionNo);
    }

    private static void assertNotBelowUsage(String label, int quota, long used) {
        if (quota < used) {
            throw new BusinessException(String.format("Số %s (%d) không được ít hơn số đang dùng (%d).", label, quota, used));
        }
    }

    /** Mã giao dịch = id hóa đơn (32 ký tự, bỏ gạch nối) + thời điểm phát hành hóa đơn. */
    private static String toTxnRef(Invoice invoice) {
        return invoice.getId().toString().replace("-", "") + invoice.getIssuedAt().format(VnpayService.VNP_TIME);
    }

    private static UUID fromTxnRef(String txnRef) {
        if (txnRef == null || txnRef.length() < 32) {
            return null;
        }
        String hex = txnRef.substring(0, 32);
        try {
            return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code vnp_Amount} là số tiền × 100. Sai định dạng thì -1 để chắc chắn không khớp. */
    private static long parseAmount(String vnpAmount) {
        try {
            return Long.parseLong(vnpAmount) / 100;
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }

    private static PaymentResultResponse failureResult(String code, Invoice invoice) {
        return VnpayService.CODE_CANCELLED.equals(code)
            ? result(Status.CANCELLED, "Bạn đã hủy thanh toán.", invoice)
            : result(Status.FAILED, "Thanh toán không thành công. Vui lòng thử lại.", invoice);
    }

    private static PaymentResultResponse needsReview(Invoice invoice) {
        return result(Status.NEEDS_REVIEW, "Giao dịch cần được kiểm tra. Vui lòng liên hệ hỗ trợ, không thanh toán lại.", invoice);
    }

    /** Kèm số lượng của hóa đơn để FE điền lại form khi Giám đốc thanh toán lại. */
    private static PaymentResultResponse result(Status status, String message, Invoice invoice) {
        PaymentResultResponse.PaymentResultResponseBuilder b = PaymentResultResponse.builder()
            .status(status)
            .message(message);
        if (invoice != null) {
            b.quotaLocation(invoice.getQuotaLocationSnapshot())
                .quotaUser(invoice.getQuotaUserSnapshot())
                .quotaRoom(invoice.getQuotaRoomSnapshot());
        }
        return b.build();
    }
}
