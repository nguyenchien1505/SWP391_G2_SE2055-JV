package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.LeaveBalanceResponse;
import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequest;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.repository.LeaveRequestRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Quỹ phép năm — BR-SCH-27, BR-SCH-28: tích lũy theo tháng làm đủ trong năm dương lịch, không chuyển năm, còn
 * lại tính cả đơn đang chờ duyệt. Mọi ngày cố định để kết quả không phụ thuộc hôm chạy test.
 */
@ExtendWith(MockitoExtension.class)
class LeaveQuotaTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate DEC_31 = LocalDate.of(2026, 12, 31);

    @Mock LeaveRequestRepository leaveRequestRepository;

    @InjectMocks LeaveQuota quota;

    @Nested
    class FullMonthsWorked {

        /** Vào làm từ năm trước: năm nay tính từ 01/01 — 01/01 tới hết 26/10 là 9 tháng đủ. */
        @Test
        void shouldCountFromJanuaryFirstWhenStartedInEarlierYear() {
            assertThat(LeaveQuota.fullMonthsWorked(LocalDate.of(2025, 6, 10), 2026, LocalDate.of(2026, 10, 26)))
                .isEqualTo(9);
        }

        /** Vào làm 15/01 thì hết ngày 14/02 mới đủ tháng đầu tiên. */
        @Test
        void shouldNeedWholeMonthCountedFromStartDay() {
            LocalDate start = LocalDate.of(2026, 1, 15);

            assertThat(LeaveQuota.fullMonthsWorked(start, 2026, LocalDate.of(2026, 2, 13))).isZero();
            assertThat(LeaveQuota.fullMonthsWorked(start, 2026, LocalDate.of(2026, 2, 14))).isEqualTo(1);
        }

        /** Phép không chuyển năm: một năm tối đa 12 tháng, xét năm đã qua thì tính tới 31/12 của năm đó. */
        @Test
        void shouldCapAtTwelveMonths() {
            assertThat(LeaveQuota.fullMonthsWorked(LocalDate.of(2020, 3, 1), 2026, DEC_31)).isEqualTo(12);
            assertThat(LeaveQuota.fullMonthsWorked(LocalDate.of(2020, 3, 1), 2026, LocalDate.of(2027, 3, 1)))
                .isEqualTo(12);
        }

        /** Chưa tới ngày vào làm, hoặc xét năm sau khi năm đó chưa bắt đầu — chưa tích lũy được gì. */
        @Test
        void shouldBeZeroBeforeStartOrForYearNotStartedYet() {
            assertThat(LeaveQuota.fullMonthsWorked(LocalDate.of(2026, 11, 1), 2026, LocalDate.of(2026, 10, 26)))
                .isZero();
            assertThat(LeaveQuota.fullMonthsWorked(LocalDate.of(2025, 1, 1), 2027, LocalDate.of(2026, 12, 30)))
                .isZero();
        }
    }

    @Nested
    class BalanceOf {

        /** 12 ngày/năm, vào làm 01/02 → tới 26/10 đủ 8 tháng = 8 ngày; đã duyệt 2, chờ duyệt 1 → còn 5. */
        @Test
        void shouldAccrueOneDayPerMonthAndSubtractApprovedAndPending() {
            User person = person(LocalDate.of(2026, 2, 1));
            stubAnnual(person, LeaveRequestStatus.APPROVED, leave(person, LocalDate.of(2026, 5, 4), 2));
            stubAnnual(person, LeaveRequestStatus.PENDING, leave(person, LocalDate.of(2026, 11, 2), 1));

            LeaveBalanceResponse balance = quota.balanceOf(person, 2026, LocalDate.of(2026, 10, 26));

            assertThat(balance.getDaysPerYear()).isEqualTo(12);
            assertThat(balance.getMonthsWorked()).isEqualTo(8);
            assertThat(balance.getEntitledDays()).isEqualTo(8);
            assertThat(balance.getUsedDays()).isEqualTo(2);
            assertThat(balance.getPendingDays()).isEqualTo(1);
            assertThat(balance.getRemainingDays()).isEqualTo(5);
            assertThat(balance.getNoticeDays()).isEqualTo(3);
            assertThat(balance.getEarliestAnnualStart()).isEqualTo(LocalDate.of(2026, 10, 29));
        }

        /** Không có Ngày bắt đầu làm việc thì chưa tính được quỹ — chỉ xin được nghỉ không lương. */
        @Test
        void shouldHaveNoQuotaWithoutStartWorkDate() {
            LeaveBalanceResponse balance = quota.balanceOf(person(null), 2026, LocalDate.of(2026, 10, 26));

            assertThat(balance.getEntitledDays()).isZero();
            assertThat(balance.getRemainingDays()).isZero();
            assertThat(balance.getStartWorkDate()).isNull();
        }

        /** Còn lại không bao giờ âm, kể cả khi dữ liệu cũ đã dùng quá quỹ. */
        @Test
        void shouldNeverReportNegativeRemaining() {
            User person = person(LocalDate.of(2026, 9, 1));
            stubAnnual(person, LeaveRequestStatus.APPROVED, leave(person, LocalDate.of(2026, 9, 10), 3));

            assertThat(quota.balanceOf(person, 2026, LocalDate.of(2026, 10, 26)).getRemainingDays()).isZero();
        }
    }

    private void stubAnnual(User person, LeaveRequestStatus status, LeaveRequest... leaves) {
        when(leaveRequestRepository.findByRequesterIdAndLeaveTypeAndStatusInAndFromDateBetween(
            person.getId(), LeaveType.ANNUAL, EnumSet.of(status), JAN_1, DEC_31))
            .thenReturn(List.of(leaves));
    }

    private static LeaveRequest leave(User person, LocalDate from, int days) {
        return LeaveRequest.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).requesterId(person.getId())
            .leaveType(LeaveType.ANNUAL).fromDate(from).toDate(from.plusDays(days - 1L))
            .reason("Nghỉ phép").status(LeaveRequestStatus.PENDING)
            .build();
    }

    private static User person(LocalDate startWorkDate) {
        return User.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).role(Role.STAFF).status(UserStatus.ACTIVE)
            .email(UUID.randomUUID() + "@test.local").passwordHash("x").fullName("Nhân viên").phone("0900000000")
            .startWorkDate(startWorkDate)
            .build();
    }
}
