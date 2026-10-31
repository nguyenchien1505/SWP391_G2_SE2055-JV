package com.example.SWP391_G2_SE2055_JV.enums;

/** Kiểm IP mạng khách sạn khi nhân viên tự vào / ra ca (chấm công M2, chốt 01/11/2026). */
public enum AttendanceIpMode {

    /** Không kiểm — mặc định, khách sạn chưa cấu hình chạy như trước. */
    OFF,

    /** Vẫn cho vào / ra ca, chỉ đánh dấu «ngoài mạng khách sạn» cho quản lý xem. */
    WARN,

    /** Sai mạng thì không cho vào / ra ca. */
    ENFORCE
}
