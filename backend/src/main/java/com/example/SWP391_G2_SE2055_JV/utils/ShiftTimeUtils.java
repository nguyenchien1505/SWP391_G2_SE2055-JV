package com.example.SWP391_G2_SE2055_JV.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
     * "Hôm nay" theo giờ Hà Nội — mốc xác định "ca tương lai" (BR-SCH-17): ca có ngày
     * LỚN HƠN hôm nay mới bị gỡ tự động, ca của chính hôm nay giữ nguyên.
     *
     * <p>Không dùng {@code LocalDate.now()} trần vì nó theo múi giờ của server: server
     * chạy UTC thì từ 00:00 đến 07:00 giờ Việt Nam "hôm nay" bị lùi về hôm qua, và ca
     * của chính hôm nay bị gỡ nhầm.
     *
     * <p>Team chốt dùng CỐ ĐỊNH giờ Hà Nội, chưa đọc cột {@code locations.timezone}.
     */
    public static LocalDate todayInHanoi() {
        return LocalDate.now(HANOI_ZONE);
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
