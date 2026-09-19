package com.example.SWP391_G2_SE2055_JV.enums;

/** Lý do Tenant bị khóa — BR-SAAS-16 (đúng 3 giá trị). */
public enum SuspendReason {

    /** Hết hạn dùng thử mà chưa nâng cấp — BR-SAAS-09. */
    TRIAL_EXPIRED,

    /** Hết thời gian ân hạn mà vẫn chưa thanh toán được — BR-SAAS-10. */
    PAYMENT_FAILED,

    /** Admin Platform chủ động khóa. */
    ADMIN_LOCKED
}
