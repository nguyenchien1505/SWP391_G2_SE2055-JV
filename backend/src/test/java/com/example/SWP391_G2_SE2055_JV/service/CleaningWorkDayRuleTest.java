package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingShiftRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * "Ngày làm hiện tại" của việc dọn — BR-SCH-03, BR-HK-03, BR-HK-04 (M2, chốt 28/10/2026).
 *
 * <p>Mọi mốc giờ cố định để kiểm được ca qua đêm mà không phụ thuộc giờ chạy test: phần lớn test đứng ở
 * 02:00 sáng ngày 29/10, giữa một ca 22:00–06:00 bắt đầu từ 28/10.
 */
@ExtendWith(MockitoExtension.class)
class CleaningWorkDayRuleTest {

    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID STAFF_ID    = UUID.randomUUID();
    private static final UUID STAFF_2_ID  = UUID.randomUUID();

    /** 02:00 sáng 29/10/2026 — giữa ca đêm 22:00–06:00 bắt đầu hôm qua. */
    private static final LocalDateTime TWO_AM    = LocalDateTime.of(2026, 10, 29, 2, 0);
    private static final LocalDate     TODAY     = TWO_AM.toLocalDate();
    private static final LocalDate     YESTERDAY = TODAY.minusDays(1);

    @Mock HousekeepingShiftRepository shiftRepository;

    @InjectMocks CleaningWorkDayRule rule;

    // ── Giao việc với ngày làm nào — BR-HK-03, Q5 nới cho ca qua đêm ─────────

    @Nested
    class AssertIsCurrentWorkDay {

        /** BR-HK-03: có ca hôm nay là đủ, không cần khớp khung giờ. */
        @Test
        void shouldAllowTodayWhenStaffHasShiftToday() {
            stubShifts(TODAY, dayShift(TODAY));

            assertThatCode(() -> rule.assertIsCurrentWorkDay(cleaner(), LOCATION_ID, TODAY, TWO_AM))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldRejectTodayWithoutShiftToday() {
            stubShifts(TODAY);

            assertThatThrownBy(() -> rule.assertIsCurrentWorkDay(cleaner(), LOCATION_ID, TODAY, TWO_AM))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phạm Dọn Dẹp không có ca làm việc ngày 29/10/2026")
                .hasMessageNotContaining("BR-");
        }

        /** Ca kiểm theo đúng khách sạn của phòng — ca ở khách sạn khác không tính. */
        @Test
        void shouldAskForShiftsAtLocationOfTheRoomOnly() {
            stubShifts(TODAY, dayShift(TODAY));

            rule.assertIsCurrentWorkDay(cleaner(), LOCATION_ID, TODAY, TWO_AM);

            verify(shiftRepository).findOfStaffBetween(STAFF_ID, LOCATION_ID, TODAY, TODAY);
        }

        /** Ca qua đêm bắt đầu hôm qua, chưa tới 06:00 — người đó vẫn đang trong ngày làm hôm qua. */
        @Test
        void shouldAllowYesterdayWhileOvernightShiftGoesOn() {
            stubShifts(YESTERDAY, nightShift(YESTERDAY));

            assertThatCode(() -> rule.assertIsCurrentWorkDay(cleaner(), LOCATION_ID, YESTERDAY, TWO_AM))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldRejectYesterdayOnceOvernightShiftEnded() {
            stubShifts(YESTERDAY, nightShift(YESTERDAY));

            assertThatThrownBy(() -> rule.assertIsCurrentWorkDay(
                cleaner(), LOCATION_ID, YESTERDAY, TODAY.atTime(7, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã qua")
                .hasMessageContaining("Phạm Dọn Dẹp")
                .hasMessageNotContaining("BR-");
        }

        /** Đúng giờ kết thúc ca là ca đã hết. */
        @Test
        void shouldRejectYesterdayExactlyAtEndOfOvernightShift() {
            stubShifts(YESTERDAY, nightShift(YESTERDAY));

            assertThatThrownBy(() -> rule.assertIsCurrentWorkDay(
                cleaner(), LOCATION_ID, YESTERDAY, TODAY.atTime(6, 0)))
                .isInstanceOf(BusinessException.class);
        }

        /** Ca NGÀY của hôm qua đã xong từ chiều qua — ngày làm đó đã khép lại. */
        @Test
        void shouldRejectYesterdayForDayShift() {
            stubShifts(YESTERDAY, dayShift(YESTERDAY));

            assertThatThrownBy(() -> rule.assertIsCurrentWorkDay(cleaner(), LOCATION_ID, YESTERDAY, TWO_AM))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã qua");
        }

        @Test
        void shouldRejectDaysBeforeYesterdayWithoutAskingShifts() {
            assertThatThrownBy(() -> rule.assertIsCurrentWorkDay(
                cleaner(), LOCATION_ID, TODAY.minusDays(2), TWO_AM))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngày đã qua");
            verifyNoInteractions(shiftRepository);
        }

        /**
         * Q5: giao việc dọn là phòng sang «Đang dọn» NGAY, nên giao trước cho ngày mai sẽ khiến phòng hiện
         * sai trạng thái suốt hôm nay.
         */
        @Test
        void shouldRejectTomorrowWithoutAskingShifts() {
            assertThatThrownBy(() -> rule.assertIsCurrentWorkDay(
                cleaner(), LOCATION_ID, TODAY.plusDays(1), TWO_AM))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chỉ gán được cho hôm nay")
                .hasMessageNotContaining("BR-");
            verifyNoInteractions(shiftRepository);
        }

        private void stubShifts(LocalDate date, Shift... shifts) {
            when(shiftRepository.findOfStaffBetween(STAFF_ID, LOCATION_ID, date, date)).thenReturn(List.of(shifts));
        }
    }

    // ── Ca đang trong ngày làm — BR-HK-15 (tự nhận việc) ────────────────────

    @Nested
    class CurrentWorkShifts {

        /** Lệnh tự nhận khóa ca của mình (BR-HK-16): hỏi bằng bản FOR UPDATE, hôm qua → hôm nay. */
        @Test
        void shouldUseLockingQueryWhenAsked() {
            Shift night = nightShift(YESTERDAY);
            when(shiftRepository.findOfStaffBetweenForUpdate(STAFF_ID, LOCATION_ID, YESTERDAY, TODAY))
                .thenReturn(List.of(night));

            assertThat(rule.currentWorkShifts(STAFF_ID, LOCATION_ID, TWO_AM, true)).containsExactly(night);
            verify(shiftRepository, never()).findOfStaffBetween(any(), any(), any(), any());
        }

        /** Ca ngày hôm qua và ca đêm hôm qua đã hết giờ đều không còn thuộc ngày làm hiện tại. */
        @Test
        void shouldKeepOnlyShiftsOfCurrentWorkDay() {
            Shift endedNight = nightShift(YESTERDAY);
            endedNight.setEndTime(LocalTime.of(1, 0));
            Shift today = dayShift(TODAY);
            when(shiftRepository.findOfStaffBetween(STAFF_ID, LOCATION_ID, YESTERDAY, TODAY))
                .thenReturn(List.of(dayShift(YESTERDAY), endedNight, today));

            assertThat(rule.currentWorkShifts(STAFF_ID, LOCATION_ID, TWO_AM, false)).containsExactly(today);
            verify(shiftRepository, never()).findOfStaffBetweenForUpdate(any(), any(), any(), any());
        }
    }

    // ── «Tồn đọng» — BR-HK-04 ───────────────────────────────────────────────

    @Nested
    class Overdue {

        @Test
        void shouldNotFlagTaskOfToday() {
            assertThat(CleaningWorkDayRule.isOverdue(inProgress(TODAY, STAFF_ID), TWO_AM, Set.of())).isFalse();
        }

        /** Ca đêm bắt đầu hôm qua chưa hết — việc của "ngày làm hôm qua" chưa tồn đọng. */
        @Test
        void shouldNotFlagTaskOfYesterdayWhileCleanerIsStillOnNightShift() {
            assertThat(CleaningWorkDayRule.isOverdue(inProgress(YESTERDAY, STAFF_ID), TWO_AM, Set.of(STAFF_ID)))
                .isFalse();
        }

        /** Một người trong nhóm còn ca đêm là cả việc chưa tồn đọng — nhóm vẫn đang có người làm. */
        @Test
        void shouldNotFlagWhenAnyTeammateIsStillOnNightShift() {
            HousekeepingTask task = inProgress(YESTERDAY, STAFF_ID, STAFF_2_ID);

            assertThat(CleaningWorkDayRule.isOverdue(task, TWO_AM, Set.of(STAFF_2_ID))).isFalse();
        }

        @Test
        void shouldFlagTaskOfYesterdayWhenNobodyIsOnNightShift() {
            assertThat(CleaningWorkDayRule.isOverdue(inProgress(YESTERDAY, STAFF_ID), TWO_AM, Set.of())).isTrue();
        }

        /** Ca đêm chỉ kéo dài ngày làm thêm MỘT đêm — việc của hôm kia luôn tồn đọng. */
        @Test
        void shouldFlagTaskOfTwoDaysAgoEvenIfCleanerIsOnNightShift() {
            HousekeepingTask task = inProgress(TODAY.minusDays(2), STAFF_ID);

            assertThat(CleaningWorkDayRule.isOverdue(task, TWO_AM, Set.of(STAFF_ID))).isTrue();
        }

        /** Đã báo xong (chờ kiểm tra) thì phần của người dọn đã hết — không còn gì tồn đọng. */
        @Test
        void shouldNotFlagTaskPendingInspection() {
            HousekeepingTask task = inProgress(TODAY.minusDays(2), STAFF_ID);
            task.setStatus(HousekeepingTaskStatus.PENDING_INSPECTION);

            assertThat(CleaningWorkDayRule.isOverdue(task, TWO_AM, Set.of())).isFalse();
        }

        /** Trang không có việc đang làm của hôm qua thì không phải hỏi ca nào. */
        @Test
        void shouldNotQueryShiftsWithoutTaskOfYesterday() {
            HousekeepingTask old = inProgress(TODAY.minusDays(3), STAFF_ID);
            HousekeepingTask fresh = inProgress(TODAY, STAFF_2_ID);

            assertThat(rule.overdueTaskIds(List.of(old, fresh), TWO_AM)).containsExactly(old.getId());
            verify(shiftRepository, never()).findOvernightOf(anyCollection(), any());
        }

        /** Một câu truy vấn cho cả trang; ca đêm đã hết giờ không cứu được việc khỏi tồn đọng. */
        @Test
        void shouldAskOnceForAllNightCleanersOfYesterday() {
            HousekeepingTask stillCleaning = inProgress(YESTERDAY, STAFF_ID);
            HousekeepingTask abandoned = inProgress(YESTERDAY, STAFF_2_ID);
            Shift ended = nightShift(YESTERDAY);
            ended.setStaffId(STAFF_2_ID);
            ended.setEndTime(LocalTime.of(1, 0));
            when(shiftRepository.findOvernightOf(eq(Set.of(STAFF_ID, STAFF_2_ID)), eq(YESTERDAY)))
                .thenReturn(List.of(nightShift(YESTERDAY), ended));

            assertThat(rule.overdueTaskIds(List.of(stillCleaning, abandoned), TWO_AM))
                .containsExactly(abandoned.getId());
        }
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    private static User cleaner() {
        return User.builder()
            .id(STAFF_ID).locationId(LOCATION_ID).role(Role.STAFF).status(UserStatus.ACTIVE)
            .fullName("Phạm Dọn Dẹp").email("dondep@test.local").passwordHash("x").phone("0900000000")
            .build();
    }

    /** Ca 06:00–14:00 của người dọn tại khách sạn mẫu. */
    private static Shift dayShift(LocalDate date) {
        return shift(date, LocalTime.of(6, 0), LocalTime.of(14, 0), false);
    }

    /** Ca 22:00–06:00 bắt đầu ngày {@code date} — BR-SCH-03: thuộc về ngày bắt đầu. */
    private static Shift nightShift(LocalDate date) {
        return shift(date, LocalTime.of(22, 0), LocalTime.of(6, 0), true);
    }

    private static Shift shift(LocalDate date, LocalTime start, LocalTime end, boolean overnight) {
        return Shift.builder()
            .id(UUID.randomUUID()).locationId(LOCATION_ID).staffId(STAFF_ID).shiftDate(date)
            .startTime(start).endTime(end).overnight(overnight).durationHours(new BigDecimal("8.00"))
            .build();
    }

    private static HousekeepingTask inProgress(LocalDate workDay, UUID... team) {
        HousekeepingTask task = HousekeepingTask.builder()
            .id(UUID.randomUUID()).locationId(LOCATION_ID).roomId(UUID.randomUUID())
            .status(HousekeepingTaskStatus.IN_PROGRESS).assignedDate(workDay)
            .createdSource(TaskCreatedSource.CHECKOUT_AUTO)
            .build();
        task.getAssigneeIds().addAll(List.of(team));
        return task;
    }
}
