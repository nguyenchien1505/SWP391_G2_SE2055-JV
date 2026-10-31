package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AttendanceSummaryResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftAttendanceEvent;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceMethod;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceStatus;
import com.example.SWP391_G2_SE2055_JV.enums.NetworkCheckResult;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceStatusResolver.AttendanceView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tổng kết chuyên cần tháng (chấm công M2) — phần cộng dồn, không cần DB. */
class AttendanceSummaryServiceTest {

    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    private final Map<UUID, AttendanceView> views = new HashMap<>();
    private final Map<UUID, List<ShiftAttendanceEvent>> events = new HashMap<>();

    @Test
    void shouldCountEachStatusAndOnTimeRate() {
        Shift onTime = shift(0, AttendanceStatus.COMPLETED, 0L, 0L, at(0, 6, 0), at(0, 14, 0));
        Shift late = shift(1, AttendanceStatus.COMPLETED, 15L, 30L, at(1, 6, 15), at(1, 13, 30));
        Shift absent = shift(2, AttendanceStatus.ABSENT, null, null, null, null);
        Shift leave = shift(3, AttendanceStatus.ON_LEAVE, null, null, null, null);
        Shift future = shift(10, AttendanceStatus.UPCOMING, null, null, null, null);

        AttendanceSummaryResponse summary = AttendanceSummaryService.summarize(STAFF_ID, "Lan",
            List.of(onTime, late, absent, leave, future), views, events);

        assertThat(summary.getTotalShifts()).isEqualTo(5);
        assertThat(summary.getDueShifts()).isEqualTo(4);
        assertThat(summary.getCompleted()).isEqualTo(2);
        assertThat(summary.getAbsent()).isEqualTo(1);
        assertThat(summary.getOnLeave()).isEqualTo(1);
        assertThat(summary.getLate()).isEqualTo(1);
        assertThat(summary.getLateMinutes()).isEqualTo(15);
        assertThat(summary.getEarlyLeave()).isEqualTo(1);
        assertThat(summary.getEarlyLeaveMinutes()).isEqualTo(30);
        assertThat(summary.getWorkedMinutes()).isEqualTo(8 * 60 + 7 * 60 + 15);
        // Phải làm 3 ca (trừ nghỉ phép, ca chưa tới), đúng giờ 1 → 33%.
        assertThat(summary.getOnTimePercent()).isEqualTo(33);
    }

    @Test
    void shouldCountOutsideNetworkAndManagerEntries() {
        Shift shift = shift(0, AttendanceStatus.COMPLETED, 0L, 0L, at(0, 6, 0), at(0, 14, 0));
        events.put(shift.getId(), List.of(
            event(AttendanceMethod.SELF, NetworkCheckResult.OUTSIDE),
            event(AttendanceMethod.MANAGER, NetworkCheckResult.NOT_CHECKED)));

        AttendanceSummaryResponse summary = AttendanceSummaryService.summarize(STAFF_ID, "Lan", List.of(shift), views, events);

        assertThat(summary.getOutsideNetwork()).isEqualTo(1);
        assertThat(summary.getManagerEntered()).isEqualTo(1);
    }

    @Test
    void shouldHaveNoRateWithoutDueShifts() {
        Shift future = shift(10, AttendanceStatus.UPCOMING, null, null, null, null);

        assertThat(AttendanceSummaryService.summarize(STAFF_ID, "Lan", List.of(future), views, events).getOnTimePercent())
            .isNull();
    }

    @Test
    void shouldParseMonth() {
        assertThat(AttendanceSummaryService.parseMonth("2026-11")).isEqualTo(YearMonth.of(2026, 11));
        assertThat(AttendanceSummaryService.parseMonth(null)).isNotNull();
        assertThatThrownBy(() -> AttendanceSummaryService.parseMonth("11/2026"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("YYYY-MM");
    }

    // ── Tiện ích ───────────────────────────────────────────────────────────

    private static LocalDateTime at(int dayOffset, int hour, int minute) {
        return DAY.plusDays(dayOffset).atTime(hour, minute);
    }

    private Shift shift(int dayOffset, AttendanceStatus status, Long late, Long early,
                        LocalDateTime checkIn, LocalDateTime checkOut) {
        Shift shift = Shift.builder()
            .id(UUID.randomUUID())
            .staffId(STAFF_ID)
            .shiftDate(DAY.plusDays(dayOffset))
            .startTime(LocalTime.of(6, 0))
            .endTime(LocalTime.of(14, 0))
            .checkInAt(checkIn)
            .checkOutAt(checkOut)
            .build();
        views.put(shift.getId(), new AttendanceView(status, late, early, null, null));
        return shift;
    }

    private static ShiftAttendanceEvent event(AttendanceMethod method, NetworkCheckResult network) {
        return ShiftAttendanceEvent.builder().method(method).networkCheck(network).build();
    }
}
