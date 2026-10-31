package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.dto.AttendanceVerification;
import com.example.SWP391_G2_SE2055_JV.dto.CheckInOutRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ClientInfo;
import com.example.SWP391_G2_SE2055_JV.dto.ManagerAttendanceRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftAttendanceEvent;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceEventType;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceMethod;
import com.example.SWP391_G2_SE2055_JV.enums.NetworkCheckResult;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.VerificationType;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftAttendanceEventRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceNetworkService.NetworkCheck;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chấm công M2 ở tầng service: ai được vào / ra ca nào, mỗi lần ghi có thêm dòng log kèm thiết bị, Manager nhập
 * bù / sửa giờ có lý do. Khung giờ chi tiết đã test thuần ở {@link AttendanceRulesTest}; ở đây ca được dựng
 * quanh giờ thật của Hà Nội vì service đọc đồng hồ theo múi giờ khách sạn.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID SHIFT_ID    = UUID.randomUUID();
    private static final ClientInfo CLIENT = new ClientInfo("203.0.113.7", "Mozilla/5.0", "device-1");

    @Mock ShiftRepository                shiftRepository;
    @Mock LocationRepository             locationRepository;
    @Mock ShiftTemplateRepository        shiftTemplateRepository;
    @Mock UserRepository                 userRepository;
    @Mock ShiftAttendanceEventRepository eventRepository;
    @Mock AttendanceStatusResolver       statusResolver;
    @Mock AttendanceNetworkService       networkService;

    @InjectMocks AttendanceService service;

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    @Nested
    class CheckIn {

        @Test
        void shouldRecordCheckInWithDeviceInfo() {
            CustomUserDetails staff = loginAsStaff();
            Shift shift = givenShift(staff.getId(), nowInHanoi().minusMinutes(10), Duration.ofHours(8));

            service.checkIn(SHIFT_ID, null, CLIENT);

            assertThat(shift.getCheckInAt()).isNotNull();
            ShiftAttendanceEvent event = savedEvent();
            assertThat(event.getEventType()).isEqualTo(AttendanceEventType.CHECK_IN);
            assertThat(event.getMethod()).isEqualTo(AttendanceMethod.SELF);
            assertThat(event.getRecordedBy()).isEqualTo(staff.getId());
            assertThat(event.getStaffId()).isEqualTo(staff.getId());
            assertThat(event.getIpAddress()).isEqualTo("203.0.113.7");
            assertThat(event.getUserAgent()).isEqualTo("Mozilla/5.0");
            assertThat(event.getDeviceId()).isEqualTo("device-1");
            assertThat(event.getVerificationType()).isEqualTo(VerificationType.NONE);
            assertThat(event.getOccurredAt()).isEqualTo(shift.getCheckInAt());
            assertThat(event.getNote()).isNull();
        }

        /** Giờ chấm công theo giờ khách sạn, không theo giờ server. */
        @Test
        void shouldUseLocationTimezone() {
            CustomUserDetails staff = loginAsStaff();
            Shift shift = givenShift(staff.getId(), nowInHanoi().minusMinutes(10), Duration.ofHours(8));

            service.checkIn(SHIFT_ID, null, CLIENT);

            assertThat(Duration.between(shift.getCheckInAt(), nowInHanoi()).abs()).isLessThan(Duration.ofMinutes(1));
        }

        @Test
        void shouldRejectSomeoneElsesShift() {
            loginAsStaff();
            givenShiftWithoutTimezone(UUID.randomUUID(), nowInHanoi().minusMinutes(10), Duration.ofHours(8));

            assertThatThrownBy(() -> service.checkIn(SHIFT_ID, null, CLIENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chỉ người được phân công");
            verify(eventRepository, never()).save(any());
        }

        @Test
        void shouldRejectCheckInTooEarly() {
            CustomUserDetails staff = loginAsStaff();
            Shift shift = givenShift(staff.getId(), nowInHanoi().plusHours(2), Duration.ofHours(8));

            assertThatThrownBy(() -> service.checkIn(SHIFT_ID, null, CLIENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chưa tới giờ vào ca");
            assertThat(shift.getCheckInAt()).isNull();
            verify(eventRepository, never()).save(any());
        }

        @Test
        void shouldAcceptEmptyVerification() {
            CustomUserDetails staff = loginAsStaff();
            givenShift(staff.getId(), nowInHanoi().minusMinutes(10), Duration.ofHours(8));
            CheckInOutRequest request = new CheckInOutRequest();
            request.setVerification(new AttendanceVerification());

            service.checkIn(SHIFT_ID, request, CLIENT);

            assertThat(savedEvent().getVerificationType()).isEqualTo(VerificationType.NONE);
        }

        @Test
        void shouldRejectUnsupportedVerification() {
            CustomUserDetails staff = loginAsStaff();
            givenShiftWithoutTimezone(staff.getId(), nowInHanoi().minusMinutes(10), Duration.ofHours(8));
            AttendanceVerification qr = new AttendanceVerification();
            qr.setType("QR");
            qr.setPayload("abc");
            CheckInOutRequest request = new CheckInOutRequest();
            request.setVerification(qr);

            assertThatThrownBy(() -> service.checkIn(SHIFT_ID, request, CLIENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa được hỗ trợ");
            verify(eventRepository, never()).save(any());
        }

        /** Kết quả kiểm mạng khách sạn đi kèm dòng ghi. */
        @Test
        void shouldRecordNetworkCheck() {
            CustomUserDetails staff = loginAsStaff();
            givenShift(staff.getId(), nowInHanoi().minusMinutes(10), Duration.ofHours(8));
            when(networkService.check(any(), any()))
                .thenReturn(new NetworkCheck(NetworkCheckResult.MATCHED, "113.160.92.0/24"));

            service.checkIn(SHIFT_ID, null, CLIENT);

            ShiftAttendanceEvent event = savedEvent();
            assertThat(event.getNetworkCheck()).isEqualTo(NetworkCheckResult.MATCHED);
            assertThat(event.getMatchedCidr()).isEqualTo("113.160.92.0/24");
        }

        /** Chế độ «Bắt buộc» mà ngoài mạng: không vào ca, không ghi gì. */
        @Test
        void shouldNotCheckInOutsideEnforcedNetwork() {
            CustomUserDetails staff = loginAsStaff();
            Shift shift = givenShift(staff.getId(), nowInHanoi().minusMinutes(10), Duration.ofHours(8));
            when(networkService.check(any(), any())).thenThrow(new BusinessException("Bạn đang không dùng mạng của khách sạn"));

            assertThatThrownBy(() -> service.checkIn(SHIFT_ID, null, CLIENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mạng của khách sạn");
            assertThat(shift.getCheckInAt()).isNull();
            verify(eventRepository, never()).save(any());
        }

        /** Quản lý nhập giờ không bị kiểm mạng. */
        @Test
        void shouldNotCheckNetworkForManagerEntry() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            LocalDateTime start = nowInHanoi().minusHours(10);
            givenShift(UUID.randomUUID(), start, Duration.ofHours(8));

            service.updateAttendance(SHIFT_ID, managerRequest(start.plusMinutes(5), null, "Quên bấm"), CLIENT);

            verify(networkService, never()).check(any(), any());
            assertThat(savedEvent().getNetworkCheck()).isEqualTo(NetworkCheckResult.NOT_CHECKED);
        }
    }

    @Nested
    class CheckOut {

        @Test
        void shouldRecordCheckOut() {
            CustomUserDetails staff = loginAsStaff();
            Shift shift = givenShift(staff.getId(), nowInHanoi().minusHours(8), Duration.ofHours(8));
            shift.setCheckInAt(nowInHanoi().minusHours(8));

            service.checkOut(SHIFT_ID, null, CLIENT);

            assertThat(shift.getCheckOutAt()).isNotNull();
            assertThat(savedEvent().getEventType()).isEqualTo(AttendanceEventType.CHECK_OUT);
        }

        @Test
        void shouldRejectCheckOutAfterGrace() {
            CustomUserDetails staff = loginAsStaff();
            Shift shift = givenShift(staff.getId(), nowInHanoi().minusHours(13), Duration.ofHours(8));
            shift.setCheckInAt(nowInHanoi().minusHours(13));

            assertThatThrownBy(() -> service.checkOut(SHIFT_ID, null, CLIENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quá hạn");
            assertThat(shift.getCheckOutAt()).isNull();
        }
    }

    @Nested
    class ManagerUpdate {

        @Test
        void shouldBackfillAbsentShiftWithNote() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            LocalDateTime start = nowInHanoi().minusHours(10);
            Shift shift = givenShift(UUID.randomUUID(), start, Duration.ofHours(8));

            service.updateAttendance(SHIFT_ID, managerRequest(start.plusMinutes(5), start.plusHours(8), "  Quên bấm  "),
                CLIENT);

            assertThat(shift.getCheckInAt()).isEqualTo(start.plusMinutes(5).truncatedTo(ChronoUnit.SECONDS));
            assertThat(shift.getCheckOutAt()).isEqualTo(start.plusHours(8).truncatedTo(ChronoUnit.SECONDS));
            ArgumentCaptor<ShiftAttendanceEvent> events = ArgumentCaptor.forClass(ShiftAttendanceEvent.class);
            verify(eventRepository, times(2)).save(events.capture());
            assertThat(events.getAllValues())
                .extracting(ShiftAttendanceEvent::getEventType, ShiftAttendanceEvent::getMethod, ShiftAttendanceEvent::getNote)
                .containsExactly(
                    tuple(AttendanceEventType.CHECK_IN, AttendanceMethod.MANAGER, "Quên bấm"),
                    tuple(AttendanceEventType.CHECK_OUT, AttendanceMethod.MANAGER, "Quên bấm"));
        }

        /** Chỉ ghi dòng cho giờ thực sự đổi. */
        @Test
        void shouldOnlyLogChangedValue() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            LocalDateTime start = nowInHanoi().minusHours(10);
            Shift shift = givenShift(UUID.randomUUID(), start, Duration.ofHours(8));
            shift.setCheckInAt(start.truncatedTo(ChronoUnit.SECONDS));

            service.updateAttendance(SHIFT_ID, managerRequest(null, start.plusHours(8), "Quên check-out"), CLIENT);

            assertThat(savedEvent().getEventType()).isEqualTo(AttendanceEventType.CHECK_OUT);
        }

        @Test
        void shouldRejectOtherLocation() {
            TestAuth.loginAsManager(TENANT_ID, UUID.randomUUID());
            LocalDateTime start = nowInHanoi().minusHours(10);
            givenShiftWithoutTimezone(UUID.randomUUID(), start, Duration.ofHours(8));

            assertThatThrownBy(() -> service.updateAttendance(SHIFT_ID,
                    managerRequest(start, null, "x"), CLIENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Location khác");
            verify(eventRepository, never()).save(any());
        }

        @Test
        void shouldRejectBlankNote() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            LocalDateTime start = nowInHanoi().minusHours(10);
            givenShiftWithoutTimezone(UUID.randomUUID(), start, Duration.ofHours(8));

            assertThatThrownBy(() -> service.updateAttendance(SHIFT_ID, managerRequest(start, null, "   "), CLIENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lý do");
        }

        @Test
        void shouldRejectEmptySlot() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            LocalDateTime start = nowInHanoi().minusHours(10);
            givenShiftWithoutTimezone(null, start, Duration.ofHours(8));

            assertThatThrownBy(() -> service.updateAttendance(SHIFT_ID, managerRequest(start, null, "x"), CLIENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa có người");
        }

        @Test
        void shouldRejectUnchangedTimes() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            LocalDateTime start = nowInHanoi().minusHours(10).truncatedTo(ChronoUnit.SECONDS);
            Shift shift = givenShift(UUID.randomUUID(), start, Duration.ofHours(8));
            shift.setCheckInAt(start);

            assertThatThrownBy(() -> service.updateAttendance(SHIFT_ID, managerRequest(start, null, "x"), CLIENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không có gì để sửa");
            verify(eventRepository, never()).save(any());
        }
    }

    // ── Tiện ích ───────────────────────────────────────────────────────────

    private static LocalDateTime nowInHanoi() {
        return LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE);
    }

    private static CustomUserDetails loginAsStaff() {
        return TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
    }

    /** Ca bắt đầu lúc {@code start}, dài {@code length}; khách sạn ở giờ Hà Nội. */
    private Shift givenShift(UUID staffId, LocalDateTime start, Duration length) {
        Shift shift = givenShiftWithoutTimezone(staffId, start, length);
        Location location = Location.builder().id(LOCATION_ID).tenantId(TENANT_ID).timezone("Asia/Ho_Chi_Minh").build();
        when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(location));
        lenient().when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(networkService.check(any(), any())).thenReturn(NetworkCheck.NOT_CHECKED);
        return shift;
    }

    private Shift givenShiftWithoutTimezone(UUID staffId, LocalDateTime start, Duration length) {
        LocalDateTime end = start.plus(length);
        Shift shift = Shift.builder()
            .id(SHIFT_ID)
            .tenantId(TENANT_ID)
            .locationId(LOCATION_ID)
            .staffId(staffId)
            .shiftDate(start.toLocalDate())
            .startTime(start.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
            .endTime(end.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
            .overnight(!end.toLocalDate().equals(start.toLocalDate()))
            .build();
        when(shiftRepository.findForUpdate(SHIFT_ID, TENANT_ID)).thenReturn(Optional.of(shift));
        return shift;
    }

    private static ManagerAttendanceRequest managerRequest(LocalDateTime checkIn, LocalDateTime checkOut, String note) {
        ManagerAttendanceRequest request = new ManagerAttendanceRequest();
        request.setCheckInAt(checkIn);
        request.setCheckOutAt(checkOut);
        request.setNote(note);
        return request;
    }

    private ShiftAttendanceEvent savedEvent() {
        ArgumentCaptor<ShiftAttendanceEvent> event = ArgumentCaptor.forClass(ShiftAttendanceEvent.class);
        verify(eventRepository).save(event.capture());
        return event.getValue();
    }
}
