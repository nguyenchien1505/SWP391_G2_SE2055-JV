package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Trạng thái báo hỏng tài sản — BR-ASSET-11 (đúng 2 trạng thái).
 *
 * <p>DM-16 bỏ entity Notification, các màn hình tự truy vấn danh sách đang chờ xử lý
 * — tức là lọc theo {@link #NEW}.
 */
public enum DamageReportStatus {
    NEW,
    RESOLVED
}
