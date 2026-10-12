package com.example.SWP391_G2_SE2055_JV.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Tác vụ định kỳ xóa các đăng ký Tenant quá hạn mà Giám đốc không bấm link xác thực email,
 * để email được giải phóng (gõ nhầm email, hoặc người khác đăng ký thay).
 *
 * <p>Mặc định chạy 01:00 mỗi ngày theo giờ Hà Nội, sau job hết hạn dùng thử. Đổi lịch bằng
 * {@code app.unverified-cleanup.cron}; số ngày giữ lại bằng {@code app.unverified-cleanup.days}
 * (mặc định 7).
 *
 * <p>Mỗi đăng ký được xóa trong transaction RIÊNG: một đăng ký lỗi không chặn các đăng ký khác,
 * lần chạy sau sẽ thử lại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnverifiedRegistrationCleanupJob {

    private final EmailVerificationService emailVerificationService;

    @Scheduled(cron = "${app.unverified-cleanup.cron:0 0 1 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        List<UUID> expired = emailVerificationService.findExpiredRegistrations();
        int deleted = 0;
        for (UUID tokenId : expired) {
            try {
                emailVerificationService.deleteUnverifiedRegistration(tokenId);
                deleted++;
            } catch (RuntimeException e) {
                log.warn("Không xóa được đăng ký chưa xác thực (mã {}): {}", tokenId, e.getMessage());
            }
        }
        log.info("Job dọn đăng ký chưa xác thực: đã xóa {}/{}", deleted, expired.size());
    }
}
