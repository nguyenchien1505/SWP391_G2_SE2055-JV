package com.example.SWP391_G2_SE2055_JV.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Ca thuộc ngày làm hiện tại" — BR-SCH-03: ca qua đêm thuộc ngày BẮT ĐẦU ca. Hàm dùng chung cho Giám sát
 * trực ca (BR-PERM-07) và việc dọn ca đêm (M2, chốt 28/10/2026), nên mốc biên phải đúng tuyệt đối.
 */
class ShiftTimeUtilsTest {

    /** 02:00 sáng 29/10/2026. */
    private static final LocalDateTime TWO_AM    = LocalDateTime.of(2026, 10, 29, 2, 0);
    private static final LocalDate     TODAY     = TWO_AM.toLocalDate();
    private static final LocalDate     YESTERDAY = TODAY.minusDays(1);
    private static final LocalTime     SIX_AM    = LocalTime.of(6, 0);

    @Test
    void shouldCountAnyShiftOfToday() {
        assertThat(ShiftTimeUtils.isInCurrentWorkDay(TODAY, false, LocalTime.of(14, 0), TWO_AM)).isTrue();
    }

    @Test
    void shouldCountOvernightShiftOfYesterdayBeforeItEnds() {
        assertThat(ShiftTimeUtils.isInCurrentWorkDay(YESTERDAY, true, SIX_AM, TWO_AM)).isTrue();
    }

    /** Đúng giờ kết thúc là ca đã hết. */
    @Test
    void shouldStopCountingExactlyAtEndTime() {
        assertThat(ShiftTimeUtils.isInCurrentWorkDay(YESTERDAY, true, SIX_AM, TODAY.atTime(SIX_AM))).isFalse();
    }

    @Test
    void shouldStopCountingAfterEndTime() {
        assertThat(ShiftTimeUtils.isInCurrentWorkDay(YESTERDAY, true, SIX_AM, TODAY.atTime(9, 0))).isFalse();
    }

    @Test
    void shouldNotCountDayShiftOfYesterday() {
        assertThat(ShiftTimeUtils.isInCurrentWorkDay(YESTERDAY, false, LocalTime.of(14, 0), TWO_AM)).isFalse();
    }

    /** Ca đêm chỉ kéo dài ngày làm thêm một đêm — ca đêm của hôm kia không còn tính. */
    @Test
    void shouldNotCountOvernightShiftOfTwoDaysAgo() {
        assertThat(ShiftTimeUtils.isInCurrentWorkDay(TODAY.minusDays(2), true, SIX_AM, TWO_AM)).isFalse();
    }

    @Test
    void shouldNotCountShiftOfTomorrow() {
        assertThat(ShiftTimeUtils.isInCurrentWorkDay(TODAY.plusDays(1), false, LocalTime.of(14, 0), TWO_AM))
            .isFalse();
    }
}
