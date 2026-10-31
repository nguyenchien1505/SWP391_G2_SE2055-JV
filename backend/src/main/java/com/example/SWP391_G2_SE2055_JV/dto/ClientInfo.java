package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Thiết bị gửi lệnh chấm công — lưu kèm mỗi lần ghi để đối soát.
 *
 * <p>IP lấy từ {@code getRemoteAddr()}: chạy sau reverse proxy thì bật
 * {@code server.forward-headers-strategy: framework} để Spring đọc {@code X-Forwarded-For}; không tự đọc header
 * đó ở đây vì client nào cũng giả được. {@code X-Device-Id} là mã giao diện tự sinh và lưu trong trình duyệt —
 * trình duyệt không cho đọc mã máy thật, nên chỉ để tham khảo.
 */
public record ClientInfo(String ipAddress, String userAgent, String deviceId) {

    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(
            cut(request.getRemoteAddr(), 45),
            cut(request.getHeader("User-Agent"), 512),
            cut(request.getHeader(DEVICE_ID_HEADER), 64));
    }

    private static String cut(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
