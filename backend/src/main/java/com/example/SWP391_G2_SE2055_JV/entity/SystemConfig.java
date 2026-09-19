package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Cấu hình cấp hệ thống do Admin Platform quản lý — BR-SAAS-08, BR-SAAS-10.
 *
 * <p>Bảng chỉ có ĐÚNG 1 bản ghi (được seed sẵn trong migration): đây là tham số toàn
 * nền tảng, không thuộc Tenant nào.
 */
@Entity
@Table(name = "system_config")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SystemConfig extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    /** Số ngày dùng thử cấp cho Tenant mới, mặc định 1 tháng — BR-SAAS-08. */
    @Column(name = "trial_days", nullable = false)
    @lombok.Builder.Default
    private int trialDays = 30;

    /** Số ngày Payment overdue trước khi khóa Tenant — BR-SAAS-10. */
    @Column(name = "grace_period_days", nullable = false)
    @lombok.Builder.Default
    private int gracePeriodDays = 7;
}
