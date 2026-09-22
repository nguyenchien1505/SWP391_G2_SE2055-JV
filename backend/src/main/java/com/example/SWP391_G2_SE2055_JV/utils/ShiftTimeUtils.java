package com.example.SWP391_G2_SE2055_JV.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Suy ra {@code isOvernight} và {@code durationHours} từ giờ bắt đầu/kết thúc.
 *
 * <p>BR-SCH-03: ca kết thúc vào hôm sau vẫn tính TRỌN số giờ vào ngày BẮT ĐẦU ca,
 * không chia cắt theo ngày lịch. Hai giá trị này được lưu sẵn trên bảng shifts để
 * mọi phép cộng dồn giờ/ngày và giờ/tuần chỉ cần nhóm theo {@code shift_date}.
 */
public final class ShiftTimeUtils {

    /**
     * Giờ Hà Nội (UTC+7). IANA không có mã "Asia/Hanoi" — cả Việt Nam dùng chung
     * {@code Asia/Ho_Chi_Minh}.
     */
    public static final ZoneId HANOI_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ShiftTimeUtils() {}

    /**
     * "Hôm nay" theo giờ Hà Nội. Chỉ dùng khi KHÔNG biết Location nào — ví dụ tài khoản
     * không gắn Location. Có Location thì luôn dùng {@link #todayAt(String)} theo
     * BR-SCH-17.
     *
     * <p>Không dùng {@code LocalDate.now()} trần vì nó theo múi giờ của server: server
     * chạy UTC thì từ 00:00 đến 07:00 giờ Việt Nam "hôm nay" bị lùi về hôm qua, và ca
     * của chính hôm nay bị gỡ nhầm.
     */
    public static LocalDate todayInHanoi() {
        return LocalDate.now(HANOI_ZONE);
    }

    /**
     * "Hôm nay" theo múi giờ của một Location — mốc xác định "ca tương lai" (BR-SCH-17):
     * ca có ngày LỚN HƠN hôm nay mới bị gỡ tự động, ca của chính hôm nay giữ nguyên.
     *
     * <p>Múi giờ rỗng hoặc không hợp lệ thì lùi về giờ Hà Nội thay vì ném lỗi: đây là
     * đường chạy của nghiệp vụ cho nghỉ việc, không được gãy giữa chừng chỉ vì một bản ghi
     * Location có dữ liệu xấu. Tầng nhập liệu đã chặn múi giờ sai từ đầu.
     */
    public static LocalDate todayAt(String timezone) {
        return LocalDate.now(zoneOrHanoi(timezone));
    }

    private static ZoneId zoneOrHanoi(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return HANOI_ZONE;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            return HANOI_ZONE;
        }
    }

    /** Ca kết thúc vào ngày hôm sau khi giờ kết thúc không lớn hơn giờ bắt đầu. */
    public static boolean isOvernight(LocalTime startTime, LocalTime endTime) {
        return !endTime.isAfter(startTime);
    }

    /** Độ dài ca, làm tròn 2 chữ số thập phân cho khớp cột DECIMAL(4,2). */
    public static BigDecimal durationHours(LocalTime startTime, LocalTime endTime) {
        Duration duration = Duration.between(startTime, endTime);
        if (isOvernight(startTime, endTime)) {
            duration = duration.plusDays(1);
        }
        return BigDecimal.valueOf(duration.toMinutes())
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
