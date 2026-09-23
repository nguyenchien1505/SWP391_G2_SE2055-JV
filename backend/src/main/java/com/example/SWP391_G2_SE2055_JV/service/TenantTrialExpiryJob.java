package com.example.SWP391_G2_SE2055_JV.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tác vụ định kỳ tự khóa Tenant hết hạn dùng thử — BR-SAAS-09.
 *
 * <p>Mặc định chạy 00:00 mỗi ngày theo giờ Hà Nội (cùng quy ước với {@code ShiftTimeUtils}).
 * Muốn đổi lịch khi test mà không sửa code, đặt thuộc tính {@code app.trial-expiry.cron}, ví dụ
 * {@code app.trial-expiry.cron=*}{@code /30 * * * * *} để chạy mỗi 30 giây. Biểu thức cron của
 * Spring có 6 trường: giây phút giờ ngày tháng thứ.
 *
 * <p>Phần logic nằm ở {@link TenantAdminService#expireTrialTenants()}; class này chỉ là "cái
 * đồng hồ" gọi nó. Tác vụ chạy ngoài ngữ cảnh đăng nhập nên các cột {@code updated_by} của
 * Tenant bị khóa để NULL (xem {@code JpaConfig}).
 *
 * <p>Nếu chạy nhiều instance ứng dụng thì mỗi instance đều chạy job, nhưng job idempotent nên
 * kết quả không đổi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantTrialExpiryJob {

    private final TenantAdminService tenantAdminService;

    @Scheduled(cron = "${app.trial-expiry.cron:0 0 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        int suspended = tenantAdminService.expireTrialTenants();
        log.info("Job hết hạn dùng thử: đã khóa {} Tenant", suspended);
    }
}
