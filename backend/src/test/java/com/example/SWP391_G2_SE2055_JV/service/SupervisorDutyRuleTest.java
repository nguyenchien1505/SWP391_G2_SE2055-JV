package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * BR-PERM-07 (tiếp): Giám sát vận hành chỉ thao tác giám sát trong ngày có ca tại khách sạn của mình.
 * Phần tính thuần ({@link SupervisorDutyRule#isOnDuty}) test với một mốc giờ cố định để kiểm được
 * ca qua đêm mà không phụ thuộc giờ chạy test.
 */
@ExtendWith(MockitoExtension.class)
class SupervisorDutyRuleTest {

    private static final UUID TENANT_ID         = UUID.randomUUID();
    private static final UUID LOCATION_ID       = UUID.randomUUID();
    private static final UUID OTHER_LOCATION_ID = UUID.randomUUID();
    private static final String TIMEZONE        = "Asia/Ho_Chi_Minh";

    /** 02:00 sáng 21/10/2026 — giữa một ca đêm 22:00–06:00 bắt đầu từ hôm qua. */
    private static final LocalDateTime TWO_AM    = LocalDateTime.of(2026, 10, 21, 2, 0);
    private static final LocalDate     TODAY     = TWO_AM.toLocalDate();
    private static final LocalDate     YESTERDAY = TODAY.minusDays(1);

    @Mock ShiftRepository    shiftRepository;
    @Mock LocationRepository locationRepository;

    @InjectMocks SupervisorDutyRule rule;

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    @Nested
    class IsOnDuty {

        /** Có ca trong ngày là đủ — chưa tới giờ ca, chưa check-in vẫn tính. */
        @Test
        void shouldCountAnyShiftOfToday() {
            Shift afternoon = shift(LOCATION_ID, TODAY, "14:00", "22:00");

            assertThat(SupervisorDutyRule.isOnDuty(List.of(afternoon), LOCATION_ID, TWO_AM)).isTrue();
        }

        @Test
        void shouldIgnoreShiftAtAnotherLocation() {
            Shift elsewhere = shift(OTHER_LOCATION_ID, TODAY, "06:00", "14:00");

            assertThat(SupervisorDutyRule.isOnDuty(List.of(elsewhere), LOCATION_ID, TWO_AM)).isFalse();
        }

        /** BR-SCH-03: ca đêm thuộc ngày bắt đầu, nhưng người đó vẫn đang trực tới giờ kết thúc. */
        @Test
        void shouldCountOvernightShiftOfYesterdayUntilItEnds() {
            Shift night = shift(LOCATION_ID, YESTERDAY, "22:00", "06:00");

            assertThat(SupervisorDutyRule.isOnDuty(List.of(night), LOCATION_ID, TWO_AM)).isTrue();
        }

        @Test
        void shouldStopCountingOvernightShiftOnceItEnds() {
            Shift night = shift(LOCATION_ID, YESTERDAY, "22:00", "06:00");

            assertThat(SupervisorDutyRule.isOnDuty(List.of(night), LOCATION_ID, TODAY.atTime(6, 0))).isFalse();
        }

        @Test
        void shouldNotCountDayShiftOfYesterday() {
            Shift morning = shift(LOCATION_ID, YESTERDAY, "06:00", "14:00");

            assertThat(SupervisorDutyRule.isOnDuty(List.of(morning), LOCATION_ID, TWO_AM)).isFalse();
        }

        @Test
        void shouldBeOffDutyWithoutAnyShift() {
            assertThat(SupervisorDutyRule.isOnDuty(List.of(), LOCATION_ID, TWO_AM)).isFalse();
        }
    }

    @Nested
    class AssertOnDuty {

        private CustomUserDetails supervisor;
        private LocalDate today;

        @BeforeEach
        void loginAsSupervisor() {
            supervisor = TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.SUPERVISION);
            today = ShiftTimeUtils.todayAt(TIMEZONE);
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID))
                .thenReturn(Optional.of(Location.builder().id(LOCATION_ID).tenantId(TENANT_ID)
                    .name("Sao Mai Hà Nội").timezone(TIMEZONE).build()));
        }

        @Test
        void shouldPassWhenSupervisorHasShiftToday() {
            when(shiftRepository.findByStaffIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
                supervisor.getId(), today.minusDays(1), today))
                .thenReturn(List.of(shift(LOCATION_ID, today, "06:00", "14:00")));

            assertThatCode(() -> rule.assertOnDuty()).doesNotThrowAnyException();
        }

        @Test
        void shouldRejectWithReadableMessageWhenNoShiftToday() {
            when(shiftRepository.findByStaffIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
                supervisor.getId(), today.minusDays(1), today))
                .thenReturn(List.of());

            assertThatThrownBy(() -> rule.assertOnDuty())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("trong ngày có ca")
                .hasMessageNotContaining("BR-");
        }
    }

    private static Shift shift(UUID locationId, LocalDate date, String start, String end) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        return Shift.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).locationId(locationId)
            .shiftDate(date).startTime(startTime).endTime(endTime)
            .overnight(ShiftTimeUtils.isOvernight(startTime, endTime))
            .durationHours(ShiftTimeUtils.durationHours(startTime, endTime))
            .build();
    }
}
