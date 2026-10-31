package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Cách xác thực khi vào / ra ca. M2 chỉ có {@link #NONE}; thêm QR hay IP sau này là thêm giá trị ở đây cùng
 * phần kiểm tra ở {@code AttendanceService}, giao diện cũ không gửi trường xác thực vẫn chạy như {@link #NONE}.
 */
public enum VerificationType {
    NONE
}
