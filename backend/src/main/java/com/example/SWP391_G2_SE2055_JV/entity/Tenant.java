package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.SuspendReason;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một khách hàng SaaS — một công ty / chuỗi khách sạn.
 *
 * <p>Trạng thái hoạt động áp ở cấp TENANT chứ không tách theo Location: Tenant
 * SUSPENDED thì mọi Location bên trong ngừng hoạt động đồng loạt (BR-SAAS-01).
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Tenant extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    /** Vừa là email liên hệ Tenant, vừa là username đăng nhập của Giám đốc — BR-SAAS-13. */
    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30, nullable = false)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TenantStatus status;

    /** Chỉ có giá trị khi status = SUSPENDED — BR-SAAS-16. */
    @Enumerated(EnumType.STRING)
    @Column(name = "suspend_reason", length = 30)
    private SuspendReason suspendReason;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    /** Chỉ Admin Platform mới đặt được mốc này — BR-SAAS-12. */
    @Column(name = "reactivated_at")
    private LocalDateTime reactivatedAt;

    /**
     * Đúng khi Tenant SUSPENDED — điều kiện CẦN để chặn đăng nhập (BR-SAAS-11). Mức chặn cụ thể do
     * {@code TenantAccessPolicy} quyết định theo lý do khóa và vai trò: ADMIN_LOCKED chặn tất cả;
     * TRIAL_EXPIRED / PAYMENT_FAILED chặn Manager, Staff nhưng Giám đốc vẫn vào được (chỉ đọc).
     */
    public boolean isLoginBlocked() {
        return status == TenantStatus.SUSPENDED;
    }
}
