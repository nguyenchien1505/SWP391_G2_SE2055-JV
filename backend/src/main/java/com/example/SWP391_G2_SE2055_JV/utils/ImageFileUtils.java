package com.example.SWP391_G2_SE2055_JV.utils;

/**
 * Tệp ảnh người dùng tải lên (ảnh minh chứng đơn nghỉ — chốt 01/11/2026). Kiểu ảnh đọc từ chính các byte đầu tệp:
 * đuôi tên tệp và Content-Type do trình duyệt gửi đều sửa được nên không tin.
 */
public final class ImageFileUtils {

    /** Tên tệp dài nhất lưu được — cột {@code file_name VARCHAR(255)}. */
    private static final int MAX_NAME_LENGTH = 255;

    private ImageFileUtils() {
    }

    /** "image/jpeg", "image/png", "image/webp" hoặc "image/gif"; {@code null} nếu không phải ảnh nhận được. */
    public static String imageTypeOf(byte[] data) {
        if (data == null) {
            return null;
        }
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(data, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(data, 'G', 'I', 'F', '8')) {
            return "image/gif";
        }
        if (data.length >= 12 && startsWith(data, 'R', 'I', 'F', 'F')
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /**
     * Tên để hiện và để tải về: bỏ đường dẫn (trình duyệt cũ gửi cả "C:\..."), bỏ ký tự điều khiển, cắt còn 255 ký
     * tự. Trống thì đặt "anh-dinh-kem".
     */
    public static String displayName(String raw) {
        String name = raw == null ? "" : raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return "anh-dinh-kem";
        }
        return name.codePointCount(0, name.length()) > MAX_NAME_LENGTH
            ? name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH))
            : name;
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
