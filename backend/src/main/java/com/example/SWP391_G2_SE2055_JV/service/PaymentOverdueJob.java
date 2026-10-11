package com.example.SWP391_G2_SE2055_JV.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tác vụ hằng ngày chuyển trạng thái Tenant chưa gia hạn kịp — BR-SAAS-10: quá ngày đến hạn →
 * PAYMENT_OVERDUE, hết ân hạn → SUSPENDED (PAYMENT_FAILED). Logic ở
 * {@link BillingService#applyOverdueRules()}.
 *
 * <p>Mặc định 00:10 giờ Hà Nội (sau job hết hạn dùng thử); đổi bằng {@code app.payment-overdue.cron}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOverdueJob {

    private final BillingService billingService;

    @Scheduled(cron = "${app.payment-overdue.cron:0 10 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        int changed = billingService.applyOverdueRules();
        log.info("Job quá hạn thanh toán: đã đổi trạng thái {} Tenant", changed);
    }
}
