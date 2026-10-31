package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Tình trạng chấm công của một ca có người — SUY RA khi đọc từ giờ ca, giờ vào / ra và giờ hiện tại của
 * khách sạn, không lưu. Manager nhập bù giờ thì ca tự hết «Vắng» mà không cần job nào chạy lại.
 */
public enum AttendanceStatus {

    /** Chưa tới giờ được vào ca (sớm hơn 30 phút trước giờ bắt đầu). */
    UPCOMING,

    /** Đang trong khung vào ca mà chưa vào. */
    OPEN,

    /** Đã vào ca, chưa kết thúc, còn trong hạn kết thúc ca. */
    WORKING,

    /** Đã vào và đã kết thúc ca. */
    COMPLETED,

    /** Hết ca mà chưa vào ca. */
    ABSENT,

    /** Đã vào ca nhưng quá hạn kết thúc ca (hết ca + 4 giờ) mà chưa kết thúc — Manager nhập giờ ra. */
    MISSED_CHECK_OUT,

    /** Chưa vào ca và ngày ca nằm trong đơn nghỉ đã duyệt — không tính vắng. */
    ON_LEAVE
}
