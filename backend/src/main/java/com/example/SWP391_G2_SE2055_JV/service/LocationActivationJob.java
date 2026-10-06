package com.example.SWP391_G2_SE2055_JV.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tác vụ định kỳ bật chi nhánh tới ngày quản lý bắt đầu làm — chốt 06/10/2026 (xem
 * {@link LocationActivationService}).
 *
 * <p>Chạy MỖI GIỜ (phút thứ 5) thay vì một lần lúc nửa đêm: mỗi chi nhánh sang ngày theo múi giờ của
 * nó (BR-SCH-17), nên chi nhánh ở múi giờ nào cũng được bật trong vòng một giờ sau khi sang ngày; máy
 * chủ tắt qua đêm thì lần chạy đầu sau khi bật lại sẽ bù. Đổi lịch khi test bằng thuộc tính
 * {@code app.location-activation.cron}, ví dụ {@code *}{@code /30 * * * * *} để chạy mỗi 30 giây.
 *
 * <p>Chạy ngoài ngữ cảnh đăng nhập nên cột {@code updated_by} của chi nhánh để NULL — cùng cách với
 * {@code TenantTrialExpiryJob}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationActivationJob {

    private final LocationActivationService activationService;

    @Scheduled(cron = "${app.location-activation.cron:0 5 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        int activated = activationService.activateDueLocations();
        if (activated > 0) {
            log.info("Job kích hoạt chi nhánh: đã bật {} chi nhánh", activated);
        }
    }
}
