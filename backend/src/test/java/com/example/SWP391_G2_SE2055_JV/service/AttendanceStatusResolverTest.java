package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.repository.LeaveRequestRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceStatusResolver.AttendanceView;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Cờ {@code ended} gửi kèm ca (chấm công M2) — giao diện dựa vào nó để khóa hộp «Sửa ca» đúng những ca backend sẽ
 * chặn, kể cả ca «Nghỉ phép» đã qua (tình trạng không cho biết ca đã qua hay chưa).
 */
@ExtendWith(MockitoExtension.class)
class AttendanceStatusResolverTest {

    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID STAFF_ID    = UUID.randomUUID();

    @Mock LocationRepository     locationRepository;
    @Mock LeaveRequestRepository leaveRequestRepository;

    @InjectMocks AttendanceStatusResolver resolver;

    @BeforeEach
    void stubs() {
        when(locationRepository.findAllById(any())).thenReturn(List.of(
            Location.builder().id(LOCATION_ID).timezone("Asia/Ho_Chi_Minh").build()));
        lenient().when(leaveRequestRepository.findOverlappingForRequesters(anyCollection(), eq(LeaveRequestStatus.APPROVED),
            any(), any())).thenReturn(List.of());
    }

    @Test
    void shouldFlagEndedAndUpcomingShifts() {
        LocalDate today = ShiftTimeUtils.todayInHanoi();
        Shift past = shift(today.minusDays(3));
        Shift future = shift(today.plusDays(3));

        Map<UUID, AttendanceView> views = resolver.resolve(List.of(past, future));

        assertThat(views.get(past.getId()).ended()).isTrue();
        assertThat(views.get(past.getId()).status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(views.get(future.getId()).ended()).isFalse();
    }

    /** Ca nghỉ phép đã qua: tình trạng là ON_LEAVE nhưng vẫn phải báo đã kết thúc. */
    @Test
    void shouldFlagEndedLeaveShift() {
        LocalDate day = ShiftTimeUtils.todayInHanoi().minusDays(3);
        Shift onLeave = shift(day);
        LeaveRequest leave = new LeaveRequest();
        leave.setRequesterId(STAFF_ID);
        leave.setFromDate(day);
        leave.setToDate(day);
        when(leaveRequestRepository.findOverlappingForRequesters(anyCollection(), eq(LeaveRequestStatus.APPROVED),
            any(), any())).thenReturn(List.of(leave));

        AttendanceView view = resolver.resolve(List.of(onLeave)).get(onLeave.getId());

        assertThat(view.status()).isEqualTo(AttendanceStatus.ON_LEAVE);
        assertThat(view.ended()).isTrue();
    }

    private static Shift shift(LocalDate date) {
        return Shift.builder()
            .id(UUID.randomUUID())
            .locationId(LOCATION_ID)
            .staffId(STAFF_ID)
            .shiftDate(date)
            .startTime(LocalTime.of(6, 0))
            .endTime(LocalTime.of(14, 0))
            .build();
    }
}
