package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Gói dịch vụ hiện hành của một Tenant — BR-SAAS-02..08.
 *
 * <p>DM-09: mỗi Tenant có ĐÚNG 1 bản ghi (unique trên {@code tenant_id}); nâng/hạ gói
 * là sửa đè bản ghi này chứ không tạo dòng mới.
 *
 * <p>Ba cột {@code pricePer*} là SNAPSHOT đơn giá tại thời điểm chốt gói (BR-SAAS-05),
 * nên Admin Platform đổi {@link PricingConfig} không ảnh hưởng gói đã bán.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Subscription extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "quota_location", nullable = false)
    private int quotaLocation;

    /** CHỈ đếm Staff — Giám đốc và Manager không tính vào quota (BR-SAAS-03). */
    @Column(name = "quota_user", nullable = false)
    private int quotaUser;

    @Column(name = "quota_room", nullable = false)
    private int quotaRoom;

    /** Snapshot đơn giá lúc chốt gói, VND số nguyên — BR-SAAS-05, BR-SAAS-15. */
    @Column(name = "price_per_location", nullable = false)
    private Long pricePerLocation;

    @Column(name = "price_per_user", nullable = false)
    private Long pricePerUser;

    @Column(name = "price_per_room", nullable = false)
    private Long pricePerRoom;

    /** Tenant mới luôn bắt đầu bằng bản dùng thử — BR-SAAS-08. */
    @Column(name = "is_trial", nullable = false)
    @lombok.Builder.Default
    private boolean trial = true;

    /** Hạn dùng thử, tính theo {@code SystemConfig.trialDays} — BR-SAAS-08. */
    @Column(name = "trial_ends_at")
    private LocalDate trialEndsAt;

    @Column(name = "current_period_start")
    private LocalDate currentPeriodStart;

    /** Chu kỳ 30 ngày — BR-SAAS-05. */
    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;
}
