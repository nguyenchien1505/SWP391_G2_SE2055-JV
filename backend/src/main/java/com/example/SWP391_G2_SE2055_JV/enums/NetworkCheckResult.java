package com.example.SWP391_G2_SE2055_JV.enums;

/** Kết quả kiểm IP của một lần ghi chấm công. */
public enum NetworkCheckResult {

    /** Khách sạn tắt kiểm IP, hoặc quản lý nhập giờ. */
    NOT_CHECKED,

    /** IP nằm trong danh sách mạng của khách sạn. */
    MATCHED,

    /** IP ngoài danh sách — chỉ ghi được khi chế độ là {@link AttendanceIpMode#WARN}. */
    OUTSIDE
}
