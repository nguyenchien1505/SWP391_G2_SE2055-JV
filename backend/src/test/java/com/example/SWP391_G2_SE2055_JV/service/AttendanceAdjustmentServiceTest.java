package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.dto.ClientInfo;
import com.example.SWP391_G2_SE2055_JV.dto.CreateAttendanceAdjustmentRequest;
import com.example.SWP391_G2_SE2055_JV.entity.AttendanceAdjustmentRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.enums.AdjustmentRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import com.example.SWP391_G2_SE2055_JV.repository.AttendanceAdjustmentRequestRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đơn điều chỉnh công (chấm công M2): ai gửi được cho ca nào, mỗi ca một đơn chờ, và duyệt thì ghi giờ qua đúng
 * đường của quản lý nhập ({@link AttendanceService#applyManagerTimes}). Luật khung giờ đã test ở
 * {@link AttendanceRulesTest}.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceAdjustmentServiceTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID SHIFT_ID    = UUID.randomUUID();
    private static final UUID REQUEST_ID  = UUID.randomUUID();
    private static final ClientInfo CLIENT = new ClientInfo("1.2.3.4", "UA", "dev");

    @Mock AttendanceAdjustmentRequestRepository requestRepository;
    @Mock ShiftRepository                       shiftRepository;
    @Mock LocationRepository                    locationRepository;
    @Mock ShiftTemplateRepository               shiftTemplateRepository;
    @Mock UserRepository                        userRepository;
    @Mock AttendanceStatusResolver              statusResolver;
    @Mock AttendanceService                     attendanceService;

    @InjectMocks AttendanceAdjustmentService service;

    @BeforeEach
    void stubs() {
        lenient().when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(
            Location.builder().id(LOCATION_ID).tenantId(TENANT_ID).timezone("Asia/Ho_Chi_Minh").build()));
        lenient().when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(shiftRepository.findAllById(any())).thenReturn(List.of());
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    @Nested
    class Create {

        @Test
        void shouldCreatePendingRequestForOwnEndedShift() {
            CustomUserDetails staff = loginAsStaff();
            LocalDateTime start = now().minusHours(10);
            givenShift(staff.getId(), start);

            service.create(request(start.plusMinutes(3), start.plusHours(8), "  Quên bấm vào ca  "));

            AttendanceAdjustmentRequest saved = savedRequest();
            assertThat(saved.getStatus()).isEqualTo(AdjustmentRequestStatus.PENDING);
            assertThat(saved.getRequesterId()).isEqualTo(staff.getId());
            assertThat(saved.getLocationId()).isEqualTo(LOCATION_ID);
            assertThat(saved.getReason()).isEqualTo("Quên bấm vào ca");
        }

        @Test
        void shouldRejectSomeoneElsesShift() {
            loginAsStaff();
            LocalDateTime start = now().minusHours(10);
            givenShift(UUID.randomUUID(), start);

            assertThatThrownBy(() -> service.create(request(start, null, "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ca của chính mình");
        }

        @Test
        void shouldRejectFutureShift() {
            CustomUserDetails staff = loginAsStaff();
            LocalDateTime start = now().plusHours(3);
            givenShift(staff.getId(), start);

            assertThatThrownBy(() -> service.create(request(start, null, "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa tới giờ");
        }

        @Test
        void shouldAllowOnlyOnePendingPerShift() {
            CustomUserDetails staff = loginAsStaff();
            LocalDateTime start = now().minusHours(10);
            givenShift(staff.getId(), start);
            when(requestRepository.existsByShiftIdAndStatus(SHIFT_ID, AdjustmentRequestStatus.PENDING)).thenReturn(true);

            assertThatThrownBy(() -> service.create(request(start, null, "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang chờ duyệt");
            verify(requestRepository, never()).save(any());
        }

        /** Giờ sai luật thì người gửi biết ngay, không phải chờ quản lý từ chối. */
        @Test
        void shouldValidateTimesUpFront() {
            CustomUserDetails staff = loginAsStaff();
            LocalDateTime start = now().minusHours(10);
            givenShift(staff.getId(), start);

            assertThatThrownBy(() -> service.create(request(start.minusHours(2), null, "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Giờ vào ca phải trong khoảng");
        }

        @Test
        void shouldRejectRequestWithoutChange() {
            CustomUserDetails staff = loginAsStaff();
            LocalDateTime start = now().minusHours(10).truncatedTo(ChronoUnit.SECONDS);
            Shift shift = givenShift(staff.getId(), start);
            shift.setCheckInAt(start);

            assertThatThrownBy(() -> service.create(request(start, null, "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("trùng với giờ đang có");
        }
    }

    @Nested
    class Review {

        @Test
        void shouldApplyTimesThroughManagerPathWhenApproved() {
            CustomUserDetails manager = TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            UUID requesterId = UUID.randomUUID();
            LocalDateTime start = now().minusHours(10);
            Shift shift = givenShift(requesterId, start);
            AttendanceAdjustmentRequest pending = givenPending(requesterId, start.plusMinutes(3));

            service.approve(REQUEST_ID, " Đã xác nhận qua camera ", CLIENT);

            verify(attendanceService).applyManagerTimes(eq(shift), eq(start.plusMinutes(3)), eq(null),
                eq("Duyệt đơn điều chỉnh công: Quên bấm"), eq(CLIENT));
            verify(shiftRepository).save(shift);
            assertThat(pending.getStatus()).isEqualTo(AdjustmentRequestStatus.APPROVED);
            assertThat(pending.getReviewerId()).isEqualTo(manager.getId());
            assertThat(pending.getReviewNote()).isEqualTo("Đã xác nhận qua camera");
            assertThat(pending.getDecidedAt()).isNotNull();
        }

        /** Luật ghi giờ chặn (ví dụ quản lý vừa sửa tay rồi): đơn vẫn chờ, không đánh dấu đã duyệt. */
        @Test
        void shouldStayPendingWhenTimesCannotBeApplied() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            UUID requesterId = UUID.randomUUID();
            LocalDateTime start = now().minusHours(10);
            givenShift(requesterId, start);
            AttendanceAdjustmentRequest pending = givenPending(requesterId, start.plusMinutes(3));
            org.mockito.Mockito.doThrow(new BusinessException("Giờ nhập trùng với giờ đang có"))
                .when(attendanceService).applyManagerTimes(any(), any(), any(), any(), any());

            assertThatThrownBy(() -> service.approve(REQUEST_ID, null, CLIENT)).isInstanceOf(BusinessException.class);
            assertThat(pending.getStatus()).isEqualTo(AdjustmentRequestStatus.PENDING);
        }

        @Test
        void shouldRefuseWhenShiftChangedHands() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            LocalDateTime start = now().minusHours(10);
            givenShift(UUID.randomUUID(), start);
            givenPending(UUID.randomUUID(), start);

            assertThatThrownBy(() -> service.approve(REQUEST_ID, null, CLIENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không còn là ca của người gửi");
            verify(attendanceService, never()).applyManagerTimes(any(), any(), any(), any(), any());
        }

        @Test
        void shouldRefuseManagerOfOtherLocation() {
            TestAuth.loginAsManager(TENANT_ID, UUID.randomUUID());
            givenPending(UUID.randomUUID(), now());

            assertThatThrownBy(() -> service.approve(REQUEST_ID, null, CLIENT)).isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void shouldRequireReasonToReject() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            AttendanceAdjustmentRequest pending = givenPending(UUID.randomUUID(), now());

            assertThatThrownBy(() -> service.reject(REQUEST_ID, "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lý do từ chối");

            service.reject(REQUEST_ID, "Không có ca này trên camera");
            assertThat(pending.getStatus()).isEqualTo(AdjustmentRequestStatus.REJECTED);
            assertThat(pending.getReviewNote()).isEqualTo("Không có ca này trên camera");
        }

        @Test
        void shouldNotReviewTwice() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            AttendanceAdjustmentRequest done = givenPending(UUID.randomUUID(), now());
            done.setStatus(AdjustmentRequestStatus.REJECTED);

            assertThatThrownBy(() -> service.reject(REQUEST_ID, "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được xử lý");
        }
    }

    @Nested
    class Cancel {

        @Test
        void shouldLetRequesterCancelPending() {
            CustomUserDetails staff = loginAsStaff();
            AttendanceAdjustmentRequest pending = givenPending(staff.getId(), now());

            service.cancel(REQUEST_ID);

            assertThat(pending.getStatus()).isEqualTo(AdjustmentRequestStatus.CANCELLED);
        }

        @Test
        void shouldNotLetOthersCancel() {
            loginAsStaff();
            givenPending(UUID.randomUUID(), now());

            assertThatThrownBy(() -> service.cancel(REQUEST_ID)).isInstanceOf(UnauthorizedException.class);
        }
    }

    // ── Tiện ích ───────────────────────────────────────────────────────────

    private static LocalDateTime now() {
        return LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE);
    }

    private static CustomUserDetails loginAsStaff() {
        return TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
    }

    /** Ca 8 tiếng bắt đầu lúc {@code start}. */
    private Shift givenShift(UUID staffId, LocalDateTime start) {
        LocalDateTime end = start.plus(Duration.ofHours(8));
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

    private AttendanceAdjustmentRequest givenPending(UUID requesterId, LocalDateTime checkIn) {
        AttendanceAdjustmentRequest request = AttendanceAdjustmentRequest.builder()
            .id(REQUEST_ID)
            .tenantId(TENANT_ID)
            .locationId(LOCATION_ID)
            .shiftId(SHIFT_ID)
            .requesterId(requesterId)
            .requestedCheckInAt(checkIn)
            .reason("Quên bấm")
            .status(AdjustmentRequestStatus.PENDING)
            .build();
        when(requestRepository.findForUpdate(REQUEST_ID, TENANT_ID)).thenReturn(Optional.of(request));
        return request;
    }

    private static CreateAttendanceAdjustmentRequest request(LocalDateTime checkIn, LocalDateTime checkOut, String reason) {
        CreateAttendanceAdjustmentRequest request = new CreateAttendanceAdjustmentRequest();
        request.setShiftId(SHIFT_ID);
        request.setCheckInAt(checkIn);
        request.setCheckOutAt(checkOut);
        request.setReason(reason);
        return request;
    }

    private AttendanceAdjustmentRequest savedRequest() {
        ArgumentCaptor<AttendanceAdjustmentRequest> saved = ArgumentCaptor.forClass(AttendanceAdjustmentRequest.class);
        verify(requestRepository).save(saved.capture());
        return saved.getValue();
    }
}
