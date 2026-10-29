package com.example.SWP391_G2_SE2055_JV.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tác vụ định kỳ tự hủy yêu cầu đổi ca không còn hợp lệ — BR-SCH-11 (quá hạn đồng nghiệp trả lời),
 * BR-SCH-25 (tới giờ bắt đầu của ca sớm hơn mà chưa duyệt), và ca đã đổi người / check-in.
 *
 * <p>Mặc định chạy mỗi 5 phút theo giờ Hà Nội: hạn trả lời tính theo giờ, chạy một lần mỗi ngày như
 * {@code TenantTrialExpiryJob} thì yêu cầu quá hạn vẫn treo cả ngày. Đổi lịch khi test bằng
 * {@code app.swap-expiry.cron}, ví dụ {@code *}{@code /30 * * * * *} để chạy mỗi 30 giây.
 *
 * <p>Phần logic ở {@link ShiftSwapService#expireDue()}; class này chỉ là "cái đồng hồ". Job idempotent:
 * chạy lại nhiều lần hay nhiều instance cùng chạy thì kết quả không đổi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShiftSwapExpiryJob {

    private final ShiftSwapService shiftSwapService;

    @Scheduled(cron = "${app.swap-expiry.cron:0 */5 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        int expired = shiftSwapService.expireDue();
        if (expired > 0) {
            log.info("Job đổi ca: tự hủy {} yêu cầu không còn hợp lệ", expired);
        }
    }
}
