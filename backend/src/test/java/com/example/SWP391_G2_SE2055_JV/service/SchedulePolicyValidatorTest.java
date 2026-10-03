package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.SchedulePolicy;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bộ kiểm tra Schedule Policy — BR-SCH-02, BR-SCH-03, BR-SCH-05, BR-SCH-13, BR-SCH-14,
 * BR-SCH-15, BR-SCH-20.
 *
 * <p>Mỗi nhóm test NỚI LỎNG mọi giới hạn ({@link #relaxAllLimits()}) rồi chỉ siết đúng quy tắc
 * đang kiểm, để chắc chắn ca bị chặn vì quy tắc đó chứ không phải vì quy tắc khác chạy trước.
 *
 * <p>Mốc thời gian cố định: {@link #MONDAY} là Thứ Hai 05/10/2026, {@code day(n)} là ngày thứ n
 * tính từ đó (âm là tuần trước).
 */
@ExtendWith(MockitoExtension.class)
class SchedulePolicyValidatorTest {

    private static final UUID TENANT_ID         = UUID.randomUUID();
    private static final UUID LOCATION_ID       = UUID.randomUUID();
    private static final UUID OTHER_LOCATION_ID = UUID.randomUUID();
    private static final UUID STAFF_ID          = UUID.randomUUID();

    /** Thứ Hai — đầu tuần lịch theo BR-SCH-13. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Mock SchedulePolicyService policyService;
    @Mock ShiftRepository       shiftRepository;

    @InjectMocks SchedulePolicyValidator validator;

    /** Bản mặc định BR-SCH-20: 8 giờ/ngày, 48 giờ/tuần, 6 ngày liền, nghỉ 12 giờ, 1 ngày nghỉ/tuần. */
    private SchedulePolicy policy;

    @BeforeEach
    void setUp() {
        policy = SchedulePolicy.builder().tenantId(TENANT_ID).build();
        lenient().when(policyService.getOrCreate(TENANT_ID)).thenReturn(policy);
    }

    // ── Phạm vi áp dụng ─────────────────────────────────────────────────────

    @Nested
    class Scope {

        @Test
        void shouldSkipEveryCheckForUnassignedShift() {
            assertThatCode(() -> validator.validate(TENANT_ID, null, candidate(day(0), "06:00", "14:00"), null))
                .doesNotThrowAnyException();

            verifyNoInteractions(policyService, shiftRepository);
        }

        /** BR-SCH-20: Tenant chưa có policy vẫn xếp ca được — getOrCreate tự sinh bản mặc định. */
        @Test
        void shouldReadPolicyOfTheTenantThroughGetOrCreate() {
            givenExisting();

            validator.validate(TENANT_ID, STAFF_ID, candidate(day(0), "06:00", "14:00"), null);

            verify(policyService).getOrCreate(TENANT_ID);
        }

        /** Sửa ca: chính ca đang sửa không được tính là "ca đã có" của nhân viên. */
        @Test
        void shouldIgnoreTheShiftBeingEdited() {
            Shift saved = existing(day(0), "06:00", "14:00");
            givenExisting(saved);

            Shift moved = candidate(day(0), "07:00", "15:00");
            moved.setId(saved.getId());

            assertThatCode(() -> validator.validate(TENANT_ID, STAFF_ID, moved, saved.getId()))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldAcceptDefaultPolicyForAnOrdinaryWeek() {
            givenExisting(
                existing(day(0), "06:00", "14:00"),
                existing(day(1), "06:00", "14:00"),
                existing(day(2), "06:00", "14:00"));

            assertThatCode(() -> validator.validate(TENANT_ID, STAFF_ID, candidate(day(3), "06:00", "14:00"), null))
                .doesNotThrowAnyException();
        }
    }

    // ── BR-SCH-05: trùng ca ─────────────────────────────────────────────────

    @Nested
    class Overlap {

        @BeforeEach
        void relax() {
            relaxAllLimits();
        }

        @Test
        void shouldBlockOverlapInSameLocation() {
            givenExisting(existing(day(0), "06:00", "14:00"));

            assertBlocked(candidate(day(0), "10:00", "18:00"), "Trùng ca", "05/10/2026 06:00–14:00");
        }

        @Test
        void shouldAllowBackToBackShifts() {
            givenExisting(existing(day(0), "06:00", "14:00"));

            assertAllowed(candidate(day(0), "14:00", "22:00"));
        }

        /** Ca đêm hôm trước kết thúc lúc 06:00 hôm nay — ca bắt đầu 05:00 hôm nay là trùng. */
        @Test
        void shouldDetectOverlapWithOvernightShiftOfPreviousDay() {
            givenExisting(existing(day(0), "22:00", "06:00"));

            assertBlocked(candidate(day(1), "05:00", "09:00"), "Trùng ca");
        }

        /**
         * BR-SCH-05: không báo "trùng ca" với ca ở khách sạn khác. Hai ca vẫn không thể chồng giờ
         * nhau, nhưng là vì quy tắc nghỉ giữa 2 ca (BR-SCH-02) — và câu báo phải nói đúng điều đó.
         */
        @Test
        void shouldNotReportOverlapWithShiftOfAnotherLocation() {
            givenExisting(existingAt(OTHER_LOCATION_ID, day(0), "06:00", "14:00"));

            assertThatThrownBy(() -> validator.validate(TENANT_ID, STAFF_ID, candidate(day(0), "10:00", "18:00"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageNotContaining("Trùng ca")
                .hasMessageContaining("Không đủ thời gian nghỉ giữa 2 ca")
                .hasMessageContaining("chồng lên ca 05/10/2026 06:00–14:00");
        }
    }

    // ── BR-SCH-02 + BR-SCH-03: giờ làm tối đa/ngày ──────────────────────────

    @Nested
    class MaxHoursPerDay {

        @BeforeEach
        void tighten() {
            relaxAllLimits();
            policy.setMaxHoursPerDay(new BigDecimal("8.00"));
        }

        @Test
        void shouldBlockWhenDayTotalExceedsLimit() {
            givenExisting(existing(day(0), "06:00", "10:00"));

            assertBlocked(candidate(day(0), "14:00", "19:00"),
                "Vượt giờ làm tối đa/ngày", "ngày 05/10/2026 sẽ có tổng 9 giờ, giới hạn 8 giờ");
        }

        @Test
        void shouldAllowDayTotalExactlyAtLimit() {
            givenExisting(existing(day(0), "06:00", "10:00"));

            assertAllowed(candidate(day(0), "14:00", "18:00"));
        }

        /** BR-SCH-03: 8 giờ của ca đêm 22:00–06:00 thuộc trọn về ngày bắt đầu, không về hôm sau. */
        @Test
        void shouldCountOvernightShiftOnlyOnItsStartDate() {
            givenExisting(existing(day(0), "22:00", "06:00"));

            assertAllowed(candidate(day(1), "14:00", "20:00"));
        }

        @Test
        void shouldCountWholeOvernightCandidateOnItsStartDate() {
            givenExisting(existing(day(0), "10:00", "12:00"));

            assertBlocked(candidate(day(0), "22:00", "06:00"), "Vượt giờ làm tối đa/ngày", "tổng 10 giờ");
        }
    }

    // ── BR-SCH-02 + BR-SCH-13: giờ làm tối đa/tuần ──────────────────────────

    @Nested
    class MaxHoursPerWeek {

        @BeforeEach
        void tighten() {
            relaxAllLimits();
            policy.setMaxHoursPerWeek(new BigDecimal("16.00"));
        }

        @Test
        void shouldBlockWhenWeekTotalExceedsLimit() {
            givenExisting(existing(day(0), "06:00", "14:00"), existing(day(1), "06:00", "14:00"));

            assertBlocked(candidate(day(6), "06:00", "08:00"),
                "Vượt giờ làm tối đa/tuần", "tuần 05/10/2026–11/10/2026 sẽ có tổng 18 giờ, giới hạn 16 giờ");
        }

        /** BR-SCH-13: tuần lịch Thứ Hai – Chủ Nhật — ca của tuần trước không cộng vào. */
        @Test
        void shouldNotCountShiftsOfPreviousWeek() {
            givenExisting(existing(day(-2), "06:00", "14:00"), existing(day(-1), "06:00", "14:00"));

            assertAllowed(candidate(day(0), "06:00", "14:00"));
        }

        /**
         * Ca đêm Chủ Nhật 22:00–06:00 thuộc trọn tuần của NGÀY BẮT ĐẦU (BR-SCH-03, BR-SCH-13):
         * tuần sau chỉ có 8 + 6 = 14 giờ. Nếu chia giờ theo ngày lịch thì tuần sau bị cộng thêm 6
         * giờ của sáng Thứ Hai và vượt 16.
         */
        @Test
        void shouldCountOvernightSundayShiftIntoWeekOfItsStartDate() {
            givenExisting(existing(day(6), "22:00", "06:00"), existing(day(8), "06:00", "14:00"));

            assertAllowed(candidate(day(9), "06:00", "12:00"));
        }
    }

    // ── BR-SCH-02 + BR-SCH-14: số ngày làm liên tiếp tối đa ──────────────────

    @Nested
    class ConsecutiveDays {

        @BeforeEach
        void tighten() {
            relaxAllLimits();
            policy.setMaxConsecutiveShifts(3);
        }

        @Test
        void shouldBlockWhenRunExceedsLimit() {
            givenExisting(
                existing(day(0), "06:00", "10:00"),
                existing(day(1), "06:00", "10:00"),
                existing(day(2), "06:00", "10:00"));

            assertBlocked(candidate(day(3), "06:00", "10:00"),
                "Vượt số ngày làm liên tiếp tối đa", "4 ngày liền, giới hạn 3 ngày");
        }

        /** BR-SCH-14: đếm số NGÀY có ca — hai ca trong cùng một ngày vẫn chỉ là một ngày. */
        @Test
        void shouldCountDaysNotShiftRecords() {
            givenExisting(
                existing(day(0), "06:00", "10:00"),
                existing(day(0), "18:00", "22:00"),
                existing(day(1), "06:00", "10:00"));

            assertAllowed(candidate(day(2), "06:00", "10:00"));
        }

        /** Chuỗi ngày liền vắt qua ranh giới tuần và tính cả những ngày SAU ca đang xếp. */
        @Test
        void shouldCountRunAcrossWeekBoundaryOnBothSides() {
            givenExisting(
                existing(day(-2), "06:00", "10:00"),
                existing(day(-1), "06:00", "10:00"),
                existing(day(1), "06:00", "10:00"));

            assertBlocked(candidate(day(0), "06:00", "10:00"), "4 ngày liền");
        }

        @Test
        void shouldRestartRunAfterADayOff() {
            givenExisting(
                existing(day(0), "06:00", "10:00"),
                existing(day(1), "06:00", "10:00"),
                existing(day(3), "06:00", "10:00"));

            assertAllowed(candidate(day(4), "06:00", "10:00"));
        }
    }

    // ── BR-SCH-02: thời gian nghỉ tối thiểu giữa 2 ca ───────────────────────

    @Nested
    class MinRestBetweenShifts {

        @BeforeEach
        void tighten() {
            relaxAllLimits();
            policy.setMinRestHoursBetweenShifts(new BigDecimal("12.00"));
        }

        @Test
        void shouldBlockTooShortRestAfterPreviousShift() {
            givenExisting(existing(day(0), "14:00", "22:00"));

            assertBlocked(candidate(day(1), "06:00", "14:00"),
                "Không đủ thời gian nghỉ giữa 2 ca", "chỉ nghỉ 8 giờ so với ca 05/10/2026 14:00–22:00",
                "tối thiểu 12 giờ");
        }

        @Test
        void shouldBlockTooShortRestBeforeNextShift() {
            givenExisting(existing(day(1), "06:00", "14:00"));

            assertBlocked(candidate(day(0), "14:00", "22:00"), "Không đủ thời gian nghỉ giữa 2 ca");
        }

        /** Ca đêm kết thúc 06:00 HÔM SAU — giờ nghỉ đo từ mốc đó, không phải 06:00 cùng ngày. */
        @Test
        void shouldMeasureRestFromEndOfOvernightShift() {
            givenExisting(existing(day(0), "22:00", "06:00"));

            assertBlocked(candidate(day(1), "14:00", "22:00"), "chỉ nghỉ 8 giờ", "(qua đêm)");
        }

        @Test
        void shouldAllowRestExactlyAtMinimum() {
            givenExisting(existing(day(0), "06:00", "14:00"));

            assertAllowed(candidate(day(1), "02:00", "06:00"));
        }

        @Test
        void shouldReportFractionalRestWithComma() {
            givenExisting(existing(day(0), "14:00", "22:00"));

            assertBlocked(candidate(day(1), "07:30", "12:00"), "chỉ nghỉ 9,5 giờ");
        }
    }

    // ── BR-SCH-02 + BR-SCH-15: số ngày nghỉ tối thiểu/tuần ──────────────────

    @Nested
    class MinDaysOffPerWeek {

        @BeforeEach
        void tighten() {
            relaxAllLimits();
            policy.setMinDaysOffPerWeek(2);
        }

        @Test
        void shouldBlockWhenWorkDaysExceedWeekLimit() {
            givenExisting(workEveryDay(0, 4));

            assertBlocked(candidate(day(5), "06:00", "10:00"),
                "Không đủ ngày nghỉ trong tuần 05/10/2026–11/10/2026", "6 ngày, tối đa 5 ngày",
                "tối thiểu 2 ngày nghỉ");
        }

        /** BR-SCH-15 đếm số NGÀY riêng biệt có ca — thêm ca thứ hai vào ngày đã làm không tốn ngày nghỉ. */
        @Test
        void shouldCountDistinctDaysNotShifts() {
            givenExisting(workEveryDay(0, 4));

            assertAllowed(candidate(day(4), "18:00", "22:00"));
        }

        @Test
        void shouldOnlyCountTheWeekOfTheCandidate() {
            givenExisting(workEveryDay(-7, -1));

            assertAllowed(candidate(day(0), "06:00", "10:00"));
        }
    }

    // ── Tiện ích ───────────────────────────────────────────────────────────

    /** Bỏ hết giới hạn để mỗi nhóm test chỉ siết đúng quy tắc của nó. */
    private void relaxAllLimits() {
        policy.setMaxHoursPerDay(new BigDecimal("24.00"));
        policy.setMaxHoursPerWeek(new BigDecimal("168.00"));
        policy.setMaxConsecutiveShifts(99);
        policy.setMinRestHoursBetweenShifts(BigDecimal.ZERO);
        policy.setMinDaysOffPerWeek(0);
    }

    private void assertBlocked(Shift candidate, String... messageParts) {
        var assertion = assertThatThrownBy(() -> validator.validate(TENANT_ID, STAFF_ID, candidate, null))
            .isInstanceOf(BusinessException.class);
        for (String part : messageParts) {
            assertion.hasMessageContaining(part);
        }
    }

    private void assertAllowed(Shift candidate) {
        assertThatCode(() -> validator.validate(TENANT_ID, STAFF_ID, candidate, null))
            .doesNotThrowAnyException();
    }

    private void givenExisting(Shift... shifts) {
        when(shiftRepository.findByStaffIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
                eq(STAFF_ID), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(shifts));
    }

    /** Một ca 06:00–10:00 cho mỗi ngày từ day(fromDay) tới day(toDay). */
    private static Shift[] workEveryDay(int fromDay, int toDay) {
        Shift[] shifts = new Shift[toDay - fromDay + 1];
        for (int i = 0; i < shifts.length; i++) {
            shifts[i] = existing(day(fromDay + i), "06:00", "10:00");
        }
        return shifts;
    }

    private static LocalDate day(int offset) {
        return MONDAY.plusDays(offset);
    }

    /** Ca đang xếp — chưa có id, như lúc tạo mới. */
    private static Shift candidate(LocalDate date, String start, String end) {
        return shift(null, LOCATION_ID, date, start, end);
    }

    private static Shift existing(LocalDate date, String start, String end) {
        return existingAt(LOCATION_ID, date, start, end);
    }

    private static Shift existingAt(UUID locationId, LocalDate date, String start, String end) {
        return shift(UUID.randomUUID(), locationId, date, start, end);
    }

    /** Dựng ca giống ShiftService: cờ qua đêm và số giờ suy ra từ giờ bắt đầu/kết thúc (BR-SCH-03). */
    private static Shift shift(UUID id, UUID locationId, LocalDate date, String start, String end) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        return Shift.builder()
            .id(id)
            .tenantId(TENANT_ID)
            .locationId(locationId)
            .staffId(STAFF_ID)
            .shiftDate(date)
            .startTime(startTime)
            .endTime(endTime)
            .overnight(ShiftTimeUtils.isOvernight(startTime, endTime))
            .durationHours(ShiftTimeUtils.durationHours(startTime, endTime))
            .build();
    }
}
