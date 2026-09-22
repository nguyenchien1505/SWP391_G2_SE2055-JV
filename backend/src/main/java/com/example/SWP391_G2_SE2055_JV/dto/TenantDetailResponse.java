package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.SuspendReason;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Chi tiết một Tenant cho Admin Platform: thông tin Tenant kèm tóm tắt gói dịch vụ hiện hành.
 *
 * <p>Chỉ đọc — Admin không sửa tên công ty và số điện thoại liên hệ của Tenant.
 */
@Data
@Builder
public class TenantDetailResponse {

    private UUID          id;
    private String        name;
    private String        contactEmail;
    private String        contactPhone;
    private TenantStatus  status;
    private SuspendReason suspendReason;
    private LocalDateTime suspendedAt;
    private LocalDateTime reactivatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** NULL nếu Tenant chưa có Subscription (không xảy ra với dữ liệu hợp lệ — DM-09). */
    private SubscriptionSummary subscription;

    /** Tóm tắt Subscription — DM-09, BR-SAAS-05. Các mức giá là snapshot lúc chốt gói. */
    @Data
    @Builder
    public static class SubscriptionSummary {
        private boolean   trial;
        private LocalDate trialEndsAt;
        private int       quotaLocation;
        private int       quotaUser;
        private int       quotaRoom;
        private Long      pricePerLocation;
        private Long      pricePerUser;
        private Long      pricePerRoom;
        private LocalDate currentPeriodStart;
        private LocalDate nextBillingDate;

        public static SubscriptionSummary fromEntity(Subscription s) {
            return SubscriptionSummary.builder()
                .trial(s.isTrial())
                .trialEndsAt(s.getTrialEndsAt())
                .quotaLocation(s.getQuotaLocation())
                .quotaUser(s.getQuotaUser())
                .quotaRoom(s.getQuotaRoom())
                .pricePerLocation(s.getPricePerLocation())
                .pricePerUser(s.getPricePerUser())
                .pricePerRoom(s.getPricePerRoom())
                .currentPeriodStart(s.getCurrentPeriodStart())
                .nextBillingDate(s.getNextBillingDate())
                .build();
        }
    }

    /** {@code subscription} có thể null — xem chú thích ở trường. */
    public static TenantDetailResponse fromEntity(Tenant tenant, Subscription subscription) {
        return TenantDetailResponse.builder()
            .id(tenant.getId())
            .name(tenant.getName())
            .contactEmail(tenant.getContactEmail())
            .contactPhone(tenant.getContactPhone())
            .status(tenant.getStatus())
            .suspendReason(tenant.getSuspendReason())
            .suspendedAt(tenant.getSuspendedAt())
            .reactivatedAt(tenant.getReactivatedAt())
            .createdAt(tenant.getCreatedAt())
            .updatedAt(tenant.getUpdatedAt())
            .subscription(subscription == null ? null : SubscriptionSummary.fromEntity(subscription))
            .build();
    }
}
