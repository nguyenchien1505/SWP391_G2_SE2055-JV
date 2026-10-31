package com.example.SWP391_G2_SE2055_JV.enums;

/** Trạng thái đơn điều chỉnh công (chấm công M2). */
public enum AdjustmentRequestStatus {

    PENDING,

    /** Quản lý duyệt — giờ trong đơn đã được ghi vào ca. */
    APPROVED,

    /** Quản lý từ chối, kèm lý do. */
    REJECTED,

    /** Người gửi tự hủy khi đơn còn chờ. */
    CANCELLED
}
