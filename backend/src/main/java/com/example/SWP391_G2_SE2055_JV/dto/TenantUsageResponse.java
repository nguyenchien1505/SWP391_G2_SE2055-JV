package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Mức sử dụng của MỘT Tenant so với hạn mức (quota) trong Subscription — chỉ Admin Platform xem.
 *
 * <p>Sử dụng ở đây là mức CHIẾM DỤNG hạn mức (Location, Staff, Phòng), không phải hoạt động của
 * người dùng (lần đăng nhập cuối...): hệ thống không lưu dữ liệu đó. Số liệu là ảnh chụp tại lúc
 * xem, không có lịch sử.
 */
@Data
@Builder
public class TenantUsageResponse {

    private UUID         tenantId;
    private String       tenantName;
    private TenantStatus status;

    /** Lấy từ Subscription; {@code false} và {@code null} nếu Tenant thiếu Subscription. */
    private boolean   trial;
    private LocalDate trialEndsAt;

    private UsageMetric locations;
    /** Chỉ Staff — Giám đốc và Manager không tính (BR-SAAS-03). */
    private UsageMetric staff;
    private UsageMetric rooms;

    /**
     * Một chỉ số sử dụng. {@code quota}, {@code remaining}, {@code usedPercent} là {@code null}
     * khi Tenant thiếu Subscription (không có hạn mức để so). {@code remaining} có thể ÂM và
     * {@code overQuota} đúng khi Tenant đang vượt hạn mức — hệ thống chưa chặn việc tạo vượt
     * quota nên đây là thông tin cần theo dõi.
     */
    @Data
    @Builder
    public static class UsageMetric {
        private long    used;
        private Integer quota;
        private Long    remaining;
        private Integer usedPercent;
        private boolean overQuota;

        public static UsageMetric of(long used, Integer quota) {
            if (quota == null) {
                return UsageMetric.builder().used(used).build();
            }
            return UsageMetric.builder()
                .used(used)
                .quota(quota)
                .remaining((long) quota - used)
                // quota = 0 thì không chia được; % có thể vượt 100 khi đang vượt hạn mức.
                .usedPercent(quota > 0 ? (int) (used * 100 / quota) : null)
                .overQuota(used > quota)
                .build();
        }
    }
}
