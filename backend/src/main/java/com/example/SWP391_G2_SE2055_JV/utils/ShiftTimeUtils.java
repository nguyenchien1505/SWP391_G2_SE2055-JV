package com.example.SWP391_G2_SE2055_JV.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;

/**
 * Suy ra {@code isOvernight} và {@code durationHours} từ giờ bắt đầu/kết thúc.
 *
 * <p>BR-SCH-03: ca kết thúc vào hôm sau vẫn tính TRỌN số giờ vào ngày BẮT ĐẦU ca,
 * không chia cắt theo ngày lịch. Hai giá trị này được lưu sẵn trên bảng shifts để
 * mọi phép cộng dồn giờ/ngày và giờ/tuần chỉ cần nhóm theo {@code shift_date}.
 */
public final class ShiftTimeUtils {

    private ShiftTimeUtils() {}

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
