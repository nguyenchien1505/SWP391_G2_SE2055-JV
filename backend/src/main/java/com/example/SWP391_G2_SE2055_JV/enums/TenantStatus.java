package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Trạng thái hoạt động của Tenant — BR-SAAS-01.
 *
 * <p>Áp ở cấp TENANT, không tách theo Location: Tenant SUSPENDED thì mọi Location
 * bên trong ngừng hoạt động đồng loạt.
 */
public enum TenantStatus {

    /** Dùng thử — BR-SAAS-08. */
    TRIAL,

    ACTIVE,

    /** Thanh toán thất bại, đang trong thời gian ân hạn — BR-SAAS-10. */
    PAYMENT_OVERDUE,

    /** Chặn đăng nhập HOÀN TOÀN mọi user trong Tenant, không có read-only — BR-SAAS-11. */
    SUSPENDED
}
