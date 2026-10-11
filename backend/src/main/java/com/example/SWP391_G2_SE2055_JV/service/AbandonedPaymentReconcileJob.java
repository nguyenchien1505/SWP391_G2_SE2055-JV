package com.example.SWP391_G2_SE2055_JV.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Đối soát các lần thanh toán bị bỏ dở: Giám đốc mở trang VNPay rồi đóng tab / quay lại, hệ thống
 * không nhận được kết quả nên hóa đơn còn Chờ thanh toán.
 *
 * <p>Mỗi 30 phút, với hóa đơn chờ quá {@code app.payment-reconcile.after-minutes} phút (mặc định
 * 60 — lâu hơn hạn 15 phút của link VNPay để không đụng giao dịch đang diễn ra), hỏi VNPay giao
 * dịch đã trả chưa: đã trả thì kích hoạt gói, chưa trả thì xóa hóa đơn. Hỏi trước khi xóa để không
 * bao giờ xóa mất hóa đơn của khách đã trả tiền.
 *
 * <p>Mỗi hóa đơn xử lý trong transaction riêng: một hóa đơn lỗi không chặn các hóa đơn khác.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbandonedPaymentReconcileJob {

    private final BillingService billingService;

    @Value("${app.payment-reconcile.after-minutes:60}")
    private int afterMinutes;

    @Scheduled(cron = "${app.payment-reconcile.cron:0 */30 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        List<UUID> abandoned = billingService.findAbandonedPayments(afterMinutes);
        if (abandoned.isEmpty()) {
            return;
        }
        for (UUID invoiceId : abandoned) {
            try {
                billingService.reconcileAbandonedPayment(invoiceId);
            } catch (RuntimeException e) {
                log.warn("Không đối soát được hóa đơn {}: {}", invoiceId, e.getMessage());
            }
        }
        log.info("Job đối soát thanh toán bỏ dở: đã xử lý {} hóa đơn", abandoned.size());
    }
}
