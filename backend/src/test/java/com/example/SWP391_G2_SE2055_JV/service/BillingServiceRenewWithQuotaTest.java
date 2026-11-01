package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.PurchaseRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Invoice;
import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.InvoiceStatus;
import com.example.SWP391_G2_SE2055_JV.enums.InvoiceType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.InvoiceRepository;
import com.example.SWP391_G2_SE2055_JV.repository.SubscriptionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantUsageRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Gia hạn kèm chọn số lượng cho kỳ tới (giữ nguyên / tăng / giảm). Số lượng mới chỉ có hiệu lực từ kỳ
 * mới: kỳ đã bắt đầu thì ngay khi trả xong, gia hạn trước thì do job đổi kỳ áp khi tới ngày.
 *
 * <p>Dùng {@link TenantUsageService} thật trên repository giả, hóa đơn giữ trong một danh sách, để kiểm
 * tra đúng cách tính "số lượng kỳ tới" mà job, hạn mức tạo mới và màn xem gói cùng dùng.
 *
 * <p>Đơn giá đã chốt: 500.000đ / khách sạn, 50.000đ / nhân viên, 20.000đ / phòng. Gói hiện tại
 * 2 / 30 / 50 → 3.500.000đ / kỳ; đang dùng 2 / 18 / 30.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceRenewWithQuotaTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock TenantRepository       tenantRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock InvoiceRepository      invoiceRepository;
    @Mock TenantUsageRepository  tenantUsageRepository;
    @Mock PlatformConfigService  platformConfigService;
    @Mock VnpayService           vnpayService;

    BillingService      service;
    TenantUsageService  usageService;
    final LocalDate     today    = ShiftTimeUtils.todayInHanoi();
    final List<Invoice> invoices = new ArrayList<>();
    Tenant       tenant;
    Subscription sub;

    @BeforeEach
    void setUp() {
        usageService = new TenantUsageService(tenantRepository, subscriptionRepository, tenantUsageRepository, invoiceRepository);
        service = new BillingService(tenantRepository, subscriptionRepository, invoiceRepository, usageService,
            platformConfigService, vnpayService);
        TestAuth.loginAs(Role.DIRECTOR, TENANT_ID, null, null);

        tenant = Tenant.builder().id(TENANT_ID).name("Sao Mai").status(TenantStatus.ACTIVE).build();
        sub = Subscription.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).trial(false)
            .quotaLocation(2).quotaUser(30).quotaRoom(50)
            .pricePerLocation(500_000L).pricePerUser(50_000L).pricePerRoom(20_000L)
            .currentPeriodStart(today.minusDays(20)).nextBillingDate(today.plusDays(10))
            .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(subscriptionRepository.findByTenantId(TENANT_ID)).thenAnswer(i -> Optional.of(sub));
        when(subscriptionRepository.findForUpdateByTenantId(TENANT_ID)).thenAnswer(i -> Optional.of(sub));
        when(tenantUsageRepository.countLocations(TENANT_ID)).thenReturn(2L);
        when(tenantUsageRepository.countUsers(eq(TENANT_ID), any(), any())).thenReturn(18L);
        when(tenantUsageRepository.countRooms(TENANT_ID)).thenReturn(30L);

        // Hóa đơn: lưu vào danh sách, đọc lại theo Tenant / trạng thái / loại như repository thật.
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            if (inv.getId() == null) {
                inv.setId(UUID.randomUUID());
                invoices.add(inv);
            }
            return inv;
        });
        when(invoiceRepository.findById(any())).thenAnswer(i ->
            invoices.stream().filter(inv -> inv.getId().equals(i.getArgument(0))).findFirst());
        when(invoiceRepository.findByTenantIdAndStatus(any(), any())).thenAnswer(i ->
            invoices.stream().filter(inv -> inv.getTenantId().equals(i.getArgument(0))
                && inv.getStatus() == i.getArgument(1)).toList());
        when(invoiceRepository.findByTenantIdAndStatusAndInvoiceType(any(), any(), any())).thenAnswer(i ->
            invoices.stream().filter(inv -> inv.getTenantId().equals(i.getArgument(0))
                && inv.getStatus() == i.getArgument(1) && inv.getInvoiceType() == i.getArgument(2)).toList());
        when(invoiceRepository.findByStatusAndInvoiceTypeAndPeriodStartBetween(any(), any(), any(), any())).thenAnswer(i ->
            invoices.stream().filter(inv -> inv.getStatus() == i.getArgument(0) && inv.getInvoiceType() == i.getArgument(1)
                && inv.getPeriodStart() != null
                && !inv.getPeriodStart().isBefore(i.getArgument(2)) && !inv.getPeriodStart().isAfter(i.getArgument(3))).toList());

        when(vnpayService.buildPaymentUrl(anyString(), anyLong(), anyString(), anyString(), any())).thenReturn("https://vnpay.test/pay");
        when(vnpayService.verify(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        TestAuth.logout();
    }

    // ── Tạo hóa đơn gia hạn ─────────────────────────────────────────────────

    @Nested
    class CreateRenewal {

        /** Không gửi số lượng = gia hạn như trước đây: quota hiện tại, phí kỳ hiện tại. */
        @Test
        void keepUsesCurrentQuotaAndFee() {
            service.renew(null, "127.0.0.1");

            Invoice inv = onlyPending();
            assertQuota(inv, 2, 30, 50);
            assertThat(inv.getAmount()).isEqualTo(3_500_000L);
            assertThat(inv.getPeriodStart()).isEqualTo(sub.getNextBillingDate());
        }

        @Test
        void decreaseChargesNewQuotaWithLockedPrices() {
            service.renew(request(2, 20, 30), "127.0.0.1");

            Invoice inv = onlyPending();
            assertQuota(inv, 2, 20, 30);
            assertThat(inv.getAmount()).isEqualTo(2 * 500_000L + 20 * 50_000L + 30 * 20_000L);
        }

        @Test
        void increaseIsAllowed() {
            service.renew(request(3, 40, 60), "127.0.0.1");

            assertQuota(onlyPending(), 3, 40, 60);
        }

        /** Đang dùng 30 phòng thì không giảm xuống 25 phòng. */
        @Test
        void cannotDecreaseBelowUsage() {
            assertThatThrownBy(() -> service.renew(request(2, 20, 25), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("25")
                .hasMessageContaining("30");
            assertThat(invoices).isEmpty();
        }

        /** Đã gia hạn trước kỳ tới với 20 / 30 thì "giữ nguyên" cho kỳ sau đó là 20 / 30, không phải gói hiện tại. */
        @Test
        void keepFollowsPrepaidPeriod() {
            Invoice prepaid = paidRenewal(sub.getNextBillingDate(), 2, 20, 30);
            sub.setNextBillingDate(prepaid.getPeriodEnd().plusDays(1));

            service.renew(null, "127.0.0.1");

            assertQuota(onlyPending(), 2, 20, 30);
        }
    }

    // ── Áp kết quả thanh toán ───────────────────────────────────────────────

    @Nested
    class ApplyRenewal {

        /** Gia hạn khi đã tới hạn (kỳ mới đã bắt đầu): số lượng mới có hiệu lực ngay. */
        @Test
        void startedPeriodAppliesNewQuotaImmediately() {
            sub.setNextBillingDate(today);
            tenant.setStatus(TenantStatus.PAYMENT_OVERDUE);
            service.renew(request(2, 20, 30), "127.0.0.1");

            payAll();

            assertQuota(sub, 2, 20, 30);
            assertThat(sub.getCurrentPeriodStart()).isEqualTo(today);
            assertThat(sub.getNextBillingDate()).isEqualTo(today.plusDays(30));
            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        }

        /** Gia hạn trước: kỳ đang dùng giữ nguyên số lượng, chỉ kéo dài ngày đến hạn. */
        @Test
        void prepaidKeepsCurrentQuotaUntilNewPeriod() {
            LocalDate due = sub.getNextBillingDate();
            service.renew(request(2, 20, 30), "127.0.0.1");

            payAll();

            assertQuota(sub, 2, 30, 50);
            assertThat(sub.getCurrentPeriodStart()).isEqualTo(today.minusDays(20));
            assertThat(sub.getNextBillingDate()).isEqualTo(due.plusDays(30));
        }

        /** Gia hạn giữ nguyên khi đã tới hạn: kết quả như trước đây. */
        @Test
        void keepRenewalChangesNothingButDates() {
            sub.setNextBillingDate(today);
            tenant.setStatus(TenantStatus.PAYMENT_OVERDUE);
            service.renew(null, "127.0.0.1");

            payAll();

            assertQuota(sub, 2, 30, 50);
            assertThat(sub.getNextBillingDate()).isEqualTo(today.plusDays(30));
        }
    }

    // ── Job đổi kỳ ──────────────────────────────────────────────────────────

    @Nested
    class StartedRenewalsJob {

        @Test
        void appliesPrepaidQuotaWhenPeriodStarts() {
            paidRenewal(today, 2, 20, 30);

            assertThat(service.applyStartedRenewals()).isEqualTo(1);
            assertQuota(sub, 2, 20, 30);
            assertThat(sub.getCurrentPeriodStart()).isEqualTo(today);
        }

        @Test
        void leavesFuturePeriodAlone() {
            paidRenewal(today.plusDays(1), 2, 20, 30);

            assertThat(service.applyStartedRenewals()).isZero();
            assertQuota(sub, 2, 30, 50);
        }

        /** Chạy lại cùng ngày không áp lần hai. */
        @Test
        void runningTwiceIsHarmless() {
            paidRenewal(today, 2, 20, 30);
            service.applyStartedRenewals();

            assertThat(service.applyStartedRenewals()).isZero();
        }

        /** Lỡ ngày (máy chủ tắt): kỳ đã bắt đầu từ hôm qua vẫn được áp. */
        @Test
        void catchesUpMissedDays() {
            paidRenewal(today.minusDays(1), 2, 20, 30);

            assertThat(service.applyStartedRenewals()).isEqualTo(1);
            assertThat(sub.getCurrentPeriodStart()).isEqualTo(today.minusDays(1));
        }

        /**
         * Gia hạn trước giữ nguyên 50 phòng rồi mua thêm lên 60: phần mua thêm đã trả tới hết kỳ tới,
         * nên khi đổi kỳ vẫn giữ 60 phòng.
         */
        @Test
        void keepsUpgradePaidIntoNewPeriod() {
            Invoice prepaid = paidRenewal(today, 2, 30, 50);
            invoices.add(Invoice.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).invoiceType(InvoiceType.UPGRADE_DIFF)
                .status(InvoiceStatus.PAID).periodStart(today.minusDays(5)).periodEnd(prepaid.getPeriodEnd())
                .quotaLocationSnapshot(2).quotaUserSnapshot(30).quotaRoomSnapshot(60).amount(1L).build());

            service.applyStartedRenewals();

            assertQuota(sub, 2, 30, 60);
        }
    }

    // ── Hạn mức tạo mới và mua thêm khi đã gia hạn trước ────────────────────

    @Nested
    class PrepaidSmallerPlan {

        /** Đã trả trước kỳ tới 30 phòng: trước ngày đổi kỳ không tạo vượt 30 phòng. */
        @Test
        void capsCreationAtPlannedQuota() {
            paidRenewal(sub.getNextBillingDate(), 2, 20, 30);

            OptionalInt limit = usageService.pendingPurchaseLimit(TENANT_ID, Invoice::getQuotaRoomSnapshot);

            assertThat(limit).hasValue(30);
        }

        /** Hóa đơn gia hạn đang chờ với số lượng ít hơn cũng chặn tạo mới. */
        @Test
        void capsCreationWhilePendingRenewal() {
            service.renew(request(2, 20, 35), "127.0.0.1");

            assertThat(usageService.pendingPurchaseLimit(TENANT_ID, Invoice::getQuotaRoomSnapshot)).hasValue(35);
        }

        /** Gia hạn trước giữ nguyên số lượng thì không chặn thêm gì. */
        @Test
        void samePlanDoesNotCapBelowCurrent() {
            paidRenewal(sub.getNextBillingDate(), 2, 30, 50);

            assertThat(usageService.pendingPurchaseLimit(TENANT_ID, Invoice::getQuotaRoomSnapshot)).hasValue(50);
        }

        @Test
        void upgradeBlockedWhenNextPeriodDiffers() {
            paidRenewal(sub.getNextBillingDate(), 2, 20, 30);
            sub.setNextBillingDate(sub.getNextBillingDate().plusDays(30));

            assertThatThrownBy(() -> service.upgrade(request(2, 30, 60), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("gia hạn trước");
        }

        /** Gia hạn trước giữ nguyên thì vẫn mua thêm được như trước đây. */
        @Test
        void upgradeAllowedWhenNextPeriodSame() {
            paidRenewal(sub.getNextBillingDate(), 2, 30, 50);
            sub.setNextBillingDate(sub.getNextBillingDate().plusDays(30));

            service.upgrade(request(2, 30, 60), "127.0.0.1");

            assertThat(invoices).anyMatch(i -> i.getInvoiceType() == InvoiceType.UPGRADE_DIFF && i.isPending());
        }
    }

    // ── Tiện ích ────────────────────────────────────────────────────────────

    private Invoice paidRenewal(LocalDate start, int ql, int qu, int qr) {
        Invoice inv = Invoice.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).subscriptionId(sub.getId())
            .invoiceType(InvoiceType.PERIODIC).status(InvoiceStatus.PAID)
            .periodStart(start).periodEnd(start.plusDays(29))
            .quotaLocationSnapshot(ql).quotaUserSnapshot(qu).quotaRoomSnapshot(qr)
            .amount(1L).issuedAt(LocalDateTime.now()).paidAt(LocalDateTime.now())
            .build();
        invoices.add(inv);
        return inv;
    }

    private Invoice onlyPending() {
        List<Invoice> pending = invoices.stream().filter(Invoice::isPending).toList();
        assertThat(pending).hasSize(1);
        return pending.get(0);
    }

    /** Giả VNPay báo thành công, đúng số tiền, cho mọi hóa đơn đang chờ. */
    private void payAll() {
        for (Invoice inv : List.copyOf(invoices)) {
            if (!inv.isPending()) {
                continue;
            }
            Map<String, String> params = new HashMap<>();
            params.put("vnp_TxnRef", inv.getId().toString().replace("-", "") + "20260101000000");
            params.put("vnp_ResponseCode", VnpayService.CODE_SUCCESS);
            params.put("vnp_TransactionStatus", VnpayService.CODE_SUCCESS);
            params.put("vnp_Amount", String.valueOf(inv.getAmount() * 100));
            params.put("vnp_TransactionNo", "123");
            service.handleVnpayReturn(params);
        }
    }

    private static PurchaseRequest request(int ql, int qu, int qr) {
        PurchaseRequest r = new PurchaseRequest();
        r.setQuotaLocation(ql);
        r.setQuotaUser(qu);
        r.setQuotaRoom(qr);
        return r;
    }

    private static void assertQuota(Invoice inv, int ql, int qu, int qr) {
        assertThat(new int[] {inv.getQuotaLocationSnapshot(), inv.getQuotaUserSnapshot(), inv.getQuotaRoomSnapshot()})
            .containsExactly(ql, qu, qr);
    }

    private static void assertQuota(Subscription s, int ql, int qu, int qr) {
        assertThat(new int[] {s.getQuotaLocation(), s.getQuotaUser(), s.getQuotaRoom()}).containsExactly(ql, qu, qr);
    }
}
