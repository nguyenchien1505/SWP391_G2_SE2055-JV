package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Khung giờ chấm công M2: vào ca từ 30 phút trước giờ bắt đầu tới trước giờ kết thúc, kết thúc ca tới hết ca
 * + 4 giờ, ca qua đêm kết thúc vào hôm sau (BR-SCH-03).
 */
class AttendanceRulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    @Nested
    class CheckIn {

        @Test
        void shouldOpenThirtyMinutesBeforeStart() {
            Shift shift = morningShift();

            assertThatThrownBy(() -> AttendanceRules.assertCanCheckIn(shift, at(5, 29)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chưa tới giờ vào ca")
                .hasMessageContaining("05:30");
            assertThatCode(() -> AttendanceRules.assertCanCheckIn(shift, at(5, 30))).doesNotThrowAnyException();
        }

        @Test
        void shouldAllowLateCheckInUntilShiftEnds() {
            Shift shift = morningShift();

            assertThatCode(() -> AttendanceRules.assertCanCheckIn(shift, at(13, 59))).doesNotThrowAnyException();
            assertThatThrownBy(() -> AttendanceRules.assertCanCheckIn(shift, at(14, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ca đã kết thúc");
        }

        @Test
        void shouldRejectSecondCheckIn() {
            Shift shift = morningShift();
            shift.setCheckInAt(at(6, 0));

            assertThatThrownBy(() -> AttendanceRules.assertCanCheckIn(shift, at(6, 5)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được check-in");
        }

        /** Ca 22:00–06:00 ngày D kết thúc lúc 06:00 ngày D+1 — 01:00 hôm sau vẫn vào ca được. */
        @Test
        void shouldTreatOvernightShiftAsEndingNextDay() {
            Shift shift = nightShift();

            assertThatCode(() -> AttendanceRules.assertCanCheckIn(shift, DAY.plusDays(1).atTime(1, 0)))
                .doesNotThrowAnyException();
            assertThatThrownBy(() -> AttendanceRules.assertCanCheckIn(shift, DAY.plusDays(1).atTime(6, 0)))
                .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    class CheckOut {

        @Test
        void shouldRequireCheckInFirst() {
            assertThatThrownBy(() -> AttendanceRules.assertCanCheckOut(morningShift(), at(14, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("check-in trước");
        }

        @Test
        void shouldAllowCheckOutUntilFourHoursAfterEnd() {
            Shift shift = morningShift();
            shift.setCheckInAt(at(6, 0));

            assertThatCode(() -> AttendanceRules.assertCanCheckOut(shift, at(18, 0))).doesNotThrowAnyException();
            assertThatThrownBy(() -> AttendanceRules.assertCanCheckOut(shift, at(18, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quá hạn");
        }

        @Test
        void shouldRejectSecondCheckOut() {
            Shift shift = morningShift();
            shift.setCheckInAt(at(6, 0));
            shift.setCheckOutAt(at(14, 0));

            assertThatThrownBy(() -> AttendanceRules.assertCanCheckOut(shift, at(14, 5)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được check-out");
        }
    }

    @Nested
    class Status {

        @Test
        void shouldFollowShiftTimeline() {
            Shift shift = morningShift();

            assertThat(AttendanceRules.status(shift, false, at(5, 0))).isEqualTo(AttendanceStatus.UPCOMING);
            assertThat(AttendanceRules.status(shift, false, at(5, 30))).isEqualTo(AttendanceStatus.OPEN);
            assertThat(AttendanceRules.status(shift, false, at(14, 0))).isEqualTo(AttendanceStatus.ABSENT);

            shift.setCheckInAt(at(6, 10));
            assertThat(AttendanceRules.status(shift, false, at(12, 0))).isEqualTo(AttendanceStatus.WORKING);
            assertThat(AttendanceRules.status(shift, false, at(18, 0))).isEqualTo(AttendanceStatus.WORKING);
            assertThat(AttendanceRules.status(shift, false, at(18, 1))).isEqualTo(AttendanceStatus.MISSED_CHECK_OUT);

            shift.setCheckOutAt(at(13, 45));
            assertThat(AttendanceRules.status(shift, false, at(20, 0))).isEqualTo(AttendanceStatus.COMPLETED);
            assertThat(AttendanceRules.lateMinutes(shift)).isEqualTo(10);
            assertThat(AttendanceRules.earlyLeaveMinutes(shift)).isEqualTo(15);
        }

        /** Ngày nghỉ đã duyệt mà ca còn giữ (BR-SCH-17 giữ ca hôm nay) thì không tính vắng. */
        @Test
        void shouldShowLeaveInsteadOfAbsent() {
            assertThat(AttendanceRules.status(morningShift(), true, at(15, 0))).isEqualTo(AttendanceStatus.ON_LEAVE);
        }

        /** Có nghỉ phép nhưng vẫn đi làm thật thì chấm công theo giờ thật. */
        @Test
        void shouldIgnoreLeaveOnceCheckedIn() {
            Shift shift = morningShift();
            shift.setCheckInAt(at(6, 0));

            assertThat(AttendanceRules.status(shift, true, at(10, 0))).isEqualTo(AttendanceStatus.WORKING);
        }

        @Test
        void shouldHaveNoStatusForEmptySlot() {
            Shift shift = morningShift();
            shift.setStaffId(null);

            assertThat(AttendanceRules.status(shift, false, at(15, 0))).isNull();
        }

        @Test
        void shouldNotCountEarlyCheckInAsNegativeLateness() {
            Shift shift = morningShift();
            shift.setCheckInAt(at(5, 40));
            shift.setCheckOutAt(at(14, 30));

            assertThat(AttendanceRules.lateMinutes(shift)).isZero();
            assertThat(AttendanceRules.earlyLeaveMinutes(shift)).isZero();
        }
    }

    @Nested
    class ManagerTimes {

        private final LocalDateTime evening = at(20, 0);

        @Test
        void shouldAcceptTimesInsideWindow() {
            assertThatCode(() -> AttendanceRules.assertManagerTimes(morningShift(), at(6, 5), at(14, 0), evening))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldRejectCheckInOutsideWindow() {
            assertThatThrownBy(() -> AttendanceRules.assertManagerTimes(morningShift(), at(5, 0), null, evening))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Giờ vào ca phải trong khoảng");
        }

        @Test
        void shouldRejectCheckOutBeforeCheckIn() {
            assertThatThrownBy(() -> AttendanceRules.assertManagerTimes(morningShift(), at(8, 0), at(7, 0), evening))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sau giờ vào ca");
        }

        @Test
        void shouldRejectCheckOutWithoutCheckIn() {
            assertThatThrownBy(() -> AttendanceRules.assertManagerTimes(morningShift(), null, at(14, 0), evening))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa có giờ vào");
        }

        @Test
        void shouldRejectFutureTimes() {
            assertThatThrownBy(() -> AttendanceRules.assertManagerTimes(morningShift(), at(6, 0), at(14, 0), at(10, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tương lai");
        }

        @Test
        void shouldRejectCheckOutAfterGrace() {
            LocalDateTime nextDay = DAY.plusDays(1).atTime(9, 0);
            assertThatThrownBy(() -> AttendanceRules.assertManagerTimes(morningShift(), at(6, 0), at(18, 1), nextDay))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("muộn nhất");
        }
    }

    // ── Tiện ích ───────────────────────────────────────────────────────────

    private static LocalDateTime at(int hour, int minute) {
        return DAY.atTime(hour, minute);
    }

    /** Ca sáng 06:00–14:00, đã có người. */
    private static Shift morningShift() {
        return Shift.builder()
            .id(UUID.randomUUID())
            .staffId(UUID.randomUUID())
            .shiftDate(DAY)
            .startTime(LocalTime.of(6, 0))
            .endTime(LocalTime.of(14, 0))
            .overnight(false)
            .build();
    }

    private static Shift nightShift() {
        return Shift.builder()
            .id(UUID.randomUUID())
            .staffId(UUID.randomUUID())
            .shiftDate(DAY)
            .startTime(LocalTime.of(22, 0))
            .endTime(LocalTime.of(6, 0))
            .overnight(true)
            .build();
    }
}
