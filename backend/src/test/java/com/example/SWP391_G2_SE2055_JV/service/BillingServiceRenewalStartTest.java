package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.SuspendReason;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ngày bắt đầu kỳ gia hạn ({@link BillingService#renewalStart}) = ngày đến hạn + số ngày bị khóa vì
 * thanh toán lỗi. Ví dụ chung: đến hạn 01/10, ân hạn 7 ngày, job khóa lúc 00:10 ngày 08/10.
 */
class BillingServiceRenewalStartTest {

    private static final LocalDate DUE       = LocalDate.of(2026, 10, 1);
    private static final LocalDateTime LOCKED = LocalDateTime.of(2026, 10, 8, 0, 10);

    @Test
    void activeTenantRenewsFromDueDate() {
        Tenant tenant = tenant(TenantStatus.ACTIVE, null, null);

        assertThat(BillingService.renewalStart(tenant, sub(), LocalDate.of(2026, 9, 20))).isEqualTo(DUE);
    }

    /** Ngày ân hạn vẫn dùng được nên vẫn tính tiền: kỳ nối tiếp ngày đến hạn. */
    @Test
    void overdueTenantRenewsFromDueDate() {
        Tenant tenant = tenant(TenantStatus.PAYMENT_OVERDUE, null, null);

        assertThat(BillingService.renewalStart(tenant, sub(), LocalDate.of(2026, 10, 5))).isEqualTo(DUE);
    }

    /** Lỗi 6: vừa bị khóa rồi mới trả không còn được miễn phí 7 ngày ân hạn. */
    @Test
    void justLockedTenantStillPaysForGraceDays() {
        Tenant tenant = tenant(TenantStatus.SUSPENDED, SuspendReason.PAYMENT_FAILED, LOCKED);

        assertThat(BillingService.renewalStart(tenant, sub(), LocalDate.of(2026, 10, 8))).isEqualTo(DUE);
    }

    /** Bị khóa 08/10 → 19/10 (12 ngày không dùng được) thì bù đúng 12 ngày. */
    @Test
    void lockedDaysAreAddedBack() {
        Tenant tenant = tenant(TenantStatus.SUSPENDED, SuspendReason.PAYMENT_FAILED, LOCKED);

        assertThat(BillingService.renewalStart(tenant, sub(), LocalDate.of(2026, 10, 20)))
            .isEqualTo(LocalDate.of(2026, 10, 13));
    }

    /** Thiếu mốc khóa là dữ liệu lệch: tính từ hôm nay như trước để không tính dư tiền của khách. */
    @Test
    void missingLockTimeFallsBackToToday() {
        Tenant tenant = tenant(TenantStatus.SUSPENDED, SuspendReason.PAYMENT_FAILED, null);
        LocalDate today = LocalDate.of(2026, 10, 20);

        assertThat(BillingService.renewalStart(tenant, sub(), today)).isEqualTo(today);
    }

    private static Subscription sub() {
        return Subscription.builder().trial(false).nextBillingDate(DUE).build();
    }

    private static Tenant tenant(TenantStatus status, SuspendReason reason, LocalDateTime suspendedAt) {
        return Tenant.builder().status(status).suspendReason(reason).suspendedAt(suspendedAt).build();
    }
}
