package com.example.SWP391_G2_SE2055_JV.enums;

/** Loại hóa đơn nội bộ — BR-SAAS-15 (đúng 2 loại). */
public enum InvoiceType {

    /** Hóa đơn chu kỳ 30 ngày, tính theo quota đã mua — BR-SAAS-05. */
    PERIODIC,

    /** Hóa đơn chênh lệch khi tăng gói giữa kỳ — BR-SAAS-06. */
    UPGRADE_DIFF
}
