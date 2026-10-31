package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.ApproveLeaveRequestRequest.ReceptionReplacement;
import com.example.SWP391_G2_SE2055_JV.dto.CreateLeaveRequestRequest;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse.Candidate;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse.ShiftImpact;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveBalanceResponse;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveRequestResponse;
import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveType;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LeaveRequestRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.PositionRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Đơn xin nghỉ — BR-SCH-06..08, BR-SCH-16, BR-SCH-17, BR-SCH-27..31.
 *
 * <p>{@link LeaveQuota} được mock: cách tích lũy quỹ đã test riêng ở {@code LeaveQuotaTest} với ngày cố định,
 * ở đây chỉ cần quỹ còn bao nhiêu. Ngày nghỉ tính theo "hôm nay" của khách sạn (BR-SCH-17).
 */
@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    private static final UUID TENANT_ID         = UUID.randomUUID();
    private static final UUID LOCATION_ID       = UUID.randomUUID();
    private static final UUID OTHER_LOCATION_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID       = UUID.randomUUID();
    private static final UUID RECEPTION_POSITION_ID = UUID.randomUUID();
    private static final String TIMEZONE        = "Asia/Ho_Chi_Minh";
    private static final DateTimeFormatter DAY  = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pageable PAGE          = PageRequest.of(0, 20);

    @Mock LeaveRequestRepository  leaveRequestRepository;
    @Mock UserRepository          userRepository;
    @Mock PositionRepository      positionRepository;
    @Mock LocationRepository      locationRepository;
    @Mock ShiftRepository         shiftRepository;
    @Mock ShiftTemplateRepository shiftTemplateRepository;
    @Mock ReceptionCoverageRule   receptionCoverage;
    @Mock LeaveQuota              leaveQuota;
    @Mock SchedulePolicyValidator policyValidator;

    @InjectMocks LeaveRequestService service;

    private LocalDate today;
    private User staff;
    private User manager;
    private User director;

    @BeforeEach
    void setUp() {
        today = ShiftTimeUtils.todayAt(TIMEZONE);
        staff = user(Role.STAFF, LOCATION_ID, "Lê Thị Lan");
        manager = user(Role.MANAGER, LOCATION_ID, "Trần Quản Lý");
        director = user(Role.DIRECTOR, null, "Ngô Giám Đốc");

        lenient().when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(
            Location.builder().id(LOCATION_ID).tenantId(TENANT_ID).name("Sao Mai Hà Nội").timezone(TIMEZONE).build()));
        lenient().when(locationRepository.findNamesByIds(any())).thenReturn(Map.of(LOCATION_ID, "Sao Mai Hà Nội"));
        lenient().when(leaveQuota.noticeDays(TENANT_ID)).thenReturn(3);
        lenient().when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest leave = invocation.getArgument(0);
            if (leave.getId() == null) {
                leave.setId(UUID.randomUUID());
            }
            return leave;
        });
        List<User> everyone = List.of(staff, manager, director);
        lenient().when(userRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            return StreamSupport.stream(ids.spliterator(), false)
                .flatMap(id -> everyone.stream().filter(person -> person.getId().equals(id)))
                .toList();
        });
        for (User person : everyone) {
            lenient().when(userRepository.findByIdAndTenantId(person.getId(), TENANT_ID)).thenReturn(Optional.of(person));
        }
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    // ── Gửi đơn — BR-SCH-06, BR-SCH-07, BR-SCH-16, BR-SCH-28 ────────────────

    @Nested
    class Create {

        @BeforeEach
        void loginAsStaff() {
            loginAs(staff);
        }

        @Test
        void shouldCreatePendingAnnualLeaveWithinQuota() {
            LocalDate from = annualStart();
            when(leaveQuota.balanceOf(staff, from.getYear(), today)).thenReturn(balance(5, 0));

            LeaveRequestResponse response =
                service.createLeaveRequest(request(LeaveType.ANNUAL, from, from.plusDays(1), "  Về quê  "));

            ArgumentCaptor<LeaveRequest> saved = ArgumentCaptor.forClass(LeaveRequest.class);
            verify(leaveRequestRepository).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
            assertThat(saved.getValue().getLeaveType()).isEqualTo(LeaveType.ANNUAL);
            assertThat(saved.getValue().getRequesterId()).isEqualTo(staff.getId());
            assertThat(saved.getValue().getLocationId()).isEqualTo(LOCATION_ID);
            assertThat(saved.getValue().getReason()).isEqualTo("Về quê");
            assertThat(response.getDays()).isEqualTo(2);
            assertThat(response.getRequesterName()).isEqualTo("Lê Thị Lan");
            assertThat(response.getLocationName()).isEqualTo("Sao Mai Hà Nội");
        }

        /** BR-SCH-07 bản sửa: nghỉ không lương gửi được cả cho hôm nay, không đụng tới quỹ phép. */
        @Test
        void shouldAllowUnpaidLeaveForToday() {
            service.createLeaveRequest(request(LeaveType.UNPAID, today, today, "Ốm"));

            verify(leaveRequestRepository).save(any(LeaveRequest.class));
            verify(leaveQuota, never()).balanceOf(any(), anyInt(), any());
        }

        @Test
        void shouldRejectPastDate() {
            assertThatThrownBy(() ->
                service.createLeaveRequest(request(LeaveType.UNPAID, today.minusDays(1), today, "Ốm")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngày đã qua")
                .hasMessageNotContaining("BR-");
            verify(leaveRequestRepository, never()).save(any());
        }

        @Test
        void shouldRejectEndBeforeStart() {
            assertThatThrownBy(() ->
                service.createLeaveRequest(request(LeaveType.UNPAID, today.plusDays(5), today.plusDays(4), "Ốm")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được trước ngày bắt đầu");
        }

        @Test
        void shouldRequireNoticeForAnnualLeave() {
            assertThatThrownBy(() -> service.createLeaveRequest(
                request(LeaveType.ANNUAL, today.plusDays(1), today.plusDays(1), "Việc riêng")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 3 ngày")
                .hasMessageContaining(today.plusDays(3).format(DAY))
                .hasMessageContaining("Nghỉ không lương");
            verify(leaveRequestRepository, never()).save(any());
        }

        @Test
        void shouldRejectAnnualLeaveAcrossTwoYears() {
            LocalDate newYearsEve = LocalDate.of(today.getYear() + 1, 12, 31);

            assertThatThrownBy(() -> service.createLeaveRequest(
                request(LeaveType.ANNUAL, newYearsEve, newYearsEve.plusDays(1), "Đi du lịch")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hai năm");
        }

        @Test
        void shouldRejectAnnualLeaveWithoutStartWorkDate() {
            staff.setStartWorkDate(null);
            LocalDate from = annualStart();

            assertThatThrownBy(() -> service.createLeaveRequest(request(LeaveType.ANNUAL, from, from, "Việc riêng")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngày bắt đầu làm việc");
        }

        /** BR-SCH-28: tính cả đơn phép năm đang chờ duyệt khi xét số ngày còn lại. */
        @Test
        void shouldRejectAnnualLeaveBeyondRemainingQuota() {
            LocalDate from = annualStart();
            when(leaveQuota.balanceOf(staff, from.getYear(), today)).thenReturn(balance(2, 1));

            assertThatThrownBy(() -> service.createLeaveRequest(
                request(LeaveType.ANNUAL, from, from.plusDays(2), "Về quê")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("còn 2 ngày")
                .hasMessageContaining("1 ngày đang chờ duyệt")
                .hasMessageContaining("cần 3 ngày");
            verify(leaveRequestRepository, never()).save(any());
        }

        @Test
        void shouldRejectOverlappingRequest() {
            LocalDate from = today.plusDays(4);
            LeaveRequest existing = leave(staff, LeaveType.UNPAID, from.plusDays(1), from.plusDays(2));
            when(leaveRequestRepository.findOverlapping(any(), any(), any(), any())).thenReturn(List.of(existing));

            assertThatThrownBy(() -> service.createLeaveRequest(
                request(LeaveType.UNPAID, from, from.plusDays(1), "Việc nhà")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang chờ duyệt")
                .hasMessageContaining(from.plusDays(1).format(DAY));
        }

        @Test
        void shouldRequireLocation() {
            staff.setLocationId(null);

            assertThatThrownBy(() -> service.createLeaveRequest(request(LeaveType.UNPAID, today, today, "Ốm")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa được gán khách sạn");
        }
    }

    // ── Tự hủy — BR-SCH-16 ──────────────────────────────────────────────────

    @Nested
    class Cancel {

        @Test
        void shouldCancelOwnPendingRequest() {
            loginAs(staff);
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2)));

            LeaveRequestResponse response = service.cancelLeaveRequest(leave.getId());

            assertThat(response.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
            assertThat(leave.getApproverId()).isNull();
            assertThat(leave.getDecidedAt()).isNull();
        }

        @Test
        void shouldNotCancelDecidedRequest() {
            loginAs(staff);
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2)));
            leave.setStatus(LeaveRequestStatus.APPROVED);

            assertThatThrownBy(() -> service.cancelLeaveRequest(leave.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang chờ duyệt");
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        }

        @Test
        void shouldHideOtherPeoplesRequest() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2)));

            assertThatThrownBy(() -> service.cancelLeaveRequest(leave.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Duyệt — BR-SCH-07, BR-SCH-08, BR-SCH-17, BR-SCH-29 ──────────────────

    @Nested
    class Approve {

        /** BR-SCH-17: gỡ ca từ ngày mai; ca hôm nay và ca đã check-in giữ nguyên; ca đã qua không đụng. */
        @Test
        void shouldReleaseShiftsFromTomorrowAndKeepTodaysShift() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.UNPAID, today.minusDays(1), today.plusDays(3)));
            Shift past = shift(staff, today.minusDays(1));
            Shift todays = shift(staff, today);
            Shift tomorrow = shift(staff, today.plusDays(1));
            Shift checkedIn = shift(staff, today.plusDays(2));
            checkedIn.setCheckInAt(LocalDateTime.now());
            stubShifts(leave, past, todays, tomorrow, checkedIn);

            LeaveApprovalResponse result = service.approveLeaveRequest(leave.getId(), List.of());

            assertThat(tomorrow.getStaffId()).isNull();
            assertThat(tomorrow.getUnassignedReason()).isEqualTo(UnassignedReason.LEAVE_APPROVED);
            assertThat(tomorrow.getUnassignedAt()).isNotNull();
            assertThat(todays.getStaffId()).isEqualTo(staff.getId());
            assertThat(checkedIn.getStaffId()).isEqualTo(staff.getId());
            assertThat(past.getStaffId()).isEqualTo(staff.getId());
            assertThat(result.getReleasedShifts()).extracting(ShiftImpact::shiftId).containsExactly(tomorrow.getId());
            assertThat(result.getKeptShifts()).extracting(ShiftImpact::shiftId)
                .containsExactly(todays.getId(), checkedIn.getId());
            assertThat(result.getKeptShifts()).allSatisfy(kept -> assertThat(kept.note()).isNotBlank());
            assertThat(result.getReleasedShifts().get(0).templateName()).isEqualTo("Ca sáng");
            assertThat(result.getReceptionGaps()).isEmpty();
            assertThat(result.getReplacements()).isEmpty();
            assertThat(result.getBalance()).isNull();

            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
            assertThat(leave.getApproverId()).isEqualTo(manager.getId());
            assertThat(leave.getDecidedAt()).isNotNull();
            assertThat(result.getRequest().getApproverName()).isEqualTo("Trần Quản Lý");
            verify(shiftRepository).saveAll(List.of(tomorrow));
        }

        /** Quỹ chỉ bị trừ khi duyệt: lúc duyệt mà quỹ chưa dùng không đủ thì chặn, không đổi gì. */
        @Test
        void shouldBlockAnnualApprovalWhenQuotaNoLongerCovers() {
            loginAs(manager);
            LocalDate from = annualStart();
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.ANNUAL, from, from.plusDays(1)));
            when(leaveQuota.balanceOf(staff, from.getYear(), today))
                .thenReturn(LeaveBalanceResponse.builder().year(from.getYear()).entitledDays(1).usedDays(0).build());

            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chỉ còn 1 ngày");
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
            verify(shiftRepository, never()).saveAll(anyList());
        }

        @Test
        void shouldNotDecideTwice() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2)));
            leave.setStatus(LeaveRequestStatus.REJECTED);

            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được xử lý");
        }

        @Test
        void shouldNotApproveRequestOfTerminatedStaff() {
            loginAs(manager);
            staff.setStatus(UserStatus.TERMINATED);
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2)));

            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã nghỉ việc");
        }

        /** Q8 cho đơn nghỉ: khách sạn khác trả 404, không lộ đơn có tồn tại. */
        @Test
        void shouldHideRequestOfAnotherLocationFromManager() {
            loginAs(manager);
            LeaveRequest leave = leave(staff, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2));
            leave.setLocationId(OTHER_LOCATION_ID);
            stubLeave(leave);

            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(), List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(shiftRepository);
        }

        /** BR-SCH-08: Manager không tự duyệt đơn của mình — đơn đó do Giám đốc duyệt. */
        @Test
        void shouldNotLetManagerApproveOwnRequest() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(leave(manager, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2)));

            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(), List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        }

        @Test
        void shouldLetDirectorApproveManagersRequest() {
            loginAs(director);
            LeaveRequest leave = stubLeave(leave(manager, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2)));

            LeaveApprovalResponse result = service.approveLeaveRequest(leave.getId(), List.of());

            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
            assertThat(leave.getApproverId()).isEqualTo(director.getId());
            assertThat(result.getReleasedShifts()).isEmpty();
        }

        @Test
        void shouldHideStaffRequestFromDirector() {
            loginAs(director);
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2)));

            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(), List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Lễ tân thay — BR-SCH-29 sửa (chốt 31/10/2026) ───────────────────────

    /**
     * Lan (người gửi, lễ tân) xin nghỉ ngày mai và là lễ tân duy nhất của "Ca sáng" hôm đó: duyệt phải kèm một lễ tân
     * thay. Khách sạn còn bốn nhân viên, chỉ Mai thay được.
     */
    @Nested
    class ReplaceReceptionist {

        private LocalDate day;
        private LeaveRequest leave;
        private Shift gap;
        private User mai;   // lễ tân rảnh — thay được
        private User hung;  // dọn phòng, không có quyền Lễ tân
        private User thu;   // lễ tân đã được duyệt nghỉ đúng ngày đó
        private User khoa;  // lễ tân nhưng vướng Schedule Policy

        @BeforeEach
        void setUpReceptionGap() {
            loginAs(manager);
            day = today.plusDays(1);
            staff.getPermissions().add(StaffPermission.RECEPTION);
            mai = receptionist("Đỗ Thị Mai");
            hung = user(Role.STAFF, LOCATION_ID, "Hoàng Văn Hùng");
            thu = receptionist("Vũ Thị Thu");
            khoa = receptionist("Ngô Văn Khoa");

            leave = stubLeave(leave(staff, LeaveType.UNPAID, day, day));
            gap = shift(staff, day);
            stubShifts(leave, gap);
            when(receptionCoverage.losesOnlyReceptionist(gap)).thenReturn(true);

            lenient().when(userRepository.findByTenantIdAndLocationIdAndRole(eq(TENANT_ID), eq(LOCATION_ID),
                    eq(Role.STAFF), any()))
                .thenReturn(new PageImpl<>(List.of(staff, mai, hung, thu, khoa)));
            for (User person : List.of(mai, hung, thu, khoa)) {
                lenient().when(userRepository.findByIdAndTenantId(person.getId(), TENANT_ID))
                    .thenReturn(Optional.of(person));
            }
            lenient().when(leaveRequestRepository.findOverlapping(eq(thu.getId()), any(), eq(day), eq(day)))
                .thenReturn(List.of(leave(thu, LeaveType.ANNUAL, day, day)));
            lenient().doThrow(new BusinessException("Trùng ca: nhân viên đã có ca 06:00–14:00 tại khách sạn này."))
                .when(policyValidator).validate(eq(TENANT_ID), eq(khoa.getId()), any(), any());
            lenient().when(positionRepository.findAllById(any())).thenReturn(List.of(Position.builder()
                .id(RECEPTION_POSITION_ID).tenantId(TENANT_ID).name("Nhân viên lễ tân").build()));
        }

        /** Gợi ý chỉ có người xếp được: bỏ người gửi, người không có quyền Lễ tân, người đang nghỉ, người vướng Policy. */
        @Test
        void previewListsOnlyReceptionistsWhoCanTakeTheShift() {
            LeaveApprovalResponse preview = service.previewApproval(leave.getId());

            assertThat(preview.getReceptionGaps()).singleElement().satisfies(view -> {
                assertThat(view.shift().shiftId()).isEqualTo(gap.getId());
                assertThat(view.shift().templateName()).isEqualTo("Ca sáng");
                assertThat(view.candidates()).extracting(Candidate::fullName).containsExactly("Đỗ Thị Mai");
                assertThat(view.candidates().get(0).positionName()).isEqualTo("Nhân viên lễ tân");
            });
            assertThat(preview.getReplacements()).isEmpty();
            assertThat(gap.getStaffId()).isEqualTo(staff.getId());
            verify(shiftRepository, never()).saveAndFlush(any());
        }

        @Test
        void shouldNotApproveWithoutReplacement() {
            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ca sáng ngày " + day.format(DAY))
                .hasMessageContaining("Chọn lễ tân thay");
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
            assertThat(gap.getStaffId()).isEqualTo(staff.getId());
            verify(shiftRepository, never()).saveAll(anyList());
        }

        /** Lễ tân thay vào CHÍNH ca của người nghỉ — ca có người ngay, không còn mang lý do gỡ. */
        @Test
        void shouldPutChosenReceptionistIntoTheLeaversShift() {
            LeaveApprovalResponse result = service.approveLeaveRequest(leave.getId(), List.of(replace(gap, mai)));

            assertThat(gap.getStaffId()).isEqualTo(mai.getId());
            assertThat(gap.getUnassignedReason()).isNull();
            assertThat(gap.getUnassignedAt()).isNull();
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
            assertThat(result.getReplacements()).singleElement().satisfies(done -> {
                assertThat(done.shift().shiftId()).isEqualTo(gap.getId());
                assertThat(done.staffId()).isEqualTo(mai.getId());
                assertThat(done.fullName()).isEqualTo("Đỗ Thị Mai");
            });
            assertThat(result.getReceptionGaps()).isEmpty();
            assertThat(result.getReleasedShifts()).extracting(ShiftImpact::shiftId).containsExactly(gap.getId());
            verify(policyValidator).validate(TENANT_ID, mai.getId(), gap, gap.getId());
            verify(shiftRepository).saveAndFlush(gap);
        }

        @Test
        void shouldRefuseReplacementWithoutReceptionPermission() {
            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(), List.of(replace(gap, hung))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Hoàng Văn Hùng không thay được ở Ca sáng ngày " + day.format(DAY))
                .hasMessageContaining("Không có quyền Lễ tân");
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        }

        @Test
        void shouldRefuseReplacementOnApprovedLeaveThatDay() {
            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(), List.of(replace(gap, thu))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Đã được duyệt nghỉ ngày " + day.format(DAY));
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        }

        /** Lễ tân thay cũng qua đủ Schedule Policy như khi xếp ca tay (BR-SCH-02) — vướng là không duyệt. */
        @Test
        void shouldRefuseReplacementWhoBreaksSchedulePolicy() {
            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(), List.of(replace(gap, khoa))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngô Văn Khoa không thay được")
                .hasMessageContaining("Trùng ca");
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        }

        @Test
        void shouldRefuseRequesterAsOwnReplacement() {
            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(), List.of(replace(gap, staff))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chính người xin nghỉ");
        }

        /** Lịch đổi giữa lúc xem trước và lúc duyệt: ca không cần người thay thì không xếp bừa vào. */
        @Test
        void shouldRefuseReplacementForShiftThatDoesNotNeedOne() {
            Shift notAGap = shift(staff, day.plusDays(5));

            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(),
                    List.of(replace(gap, mai), replace(notAGap, mai))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Lịch làm việc vừa thay đổi");
            assertThat(gap.getStaffId()).isEqualTo(staff.getId());
        }

        @Test
        void shouldRefuseTwoReplacementsForOneShift() {
            assertThatThrownBy(() -> service.approveLeaveRequest(leave.getId(),
                    List.of(replace(gap, mai), replace(gap, khoa))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mỗi ca chỉ chọn một lễ tân thay");
        }

        private User receptionist(String fullName) {
            User person = user(Role.STAFF, LOCATION_ID, fullName);
            person.setPositionId(RECEPTION_POSITION_ID);
            person.getPermissions().add(StaffPermission.RECEPTION);
            return person;
        }

        private ReceptionReplacement replace(Shift shift, User person) {
            ReceptionReplacement replacement = new ReceptionReplacement();
            replacement.setShiftId(shift.getId());
            replacement.setStaffId(person.getId());
            return replacement;
        }
    }

    // ── Xem trước, từ chối, hàng duyệt ──────────────────────────────────────

    @Nested
    class PreviewRejectAndQueue {

        @Test
        void shouldPreviewWithoutChangingAnything() {
            loginAs(manager);
            LocalDate from = annualStart();
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.ANNUAL, from, from));
            Shift onLeaveDay = shift(staff, from);
            stubShifts(leave, onLeaveDay);
            when(leaveQuota.balanceOf(staff, from.getYear(), today)).thenReturn(balance(4, 1));

            LeaveApprovalResponse preview = service.previewApproval(leave.getId());

            assertThat(preview.getReleasedShifts()).extracting(ShiftImpact::shiftId).containsExactly(onLeaveDay.getId());
            assertThat(preview.getBalance().getRemainingDays()).isEqualTo(4);
            assertThat(preview.getRequest().getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
            assertThat(onLeaveDay.getStaffId()).isEqualTo(staff.getId());
            verify(shiftRepository, never()).saveAll(anyList());
            verify(leaveRequestRepository, never()).save(any());
        }

        @Test
        void shouldRejectPendingRequestWithoutTouchingSchedule() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2)));

            LeaveRequestResponse response = service.rejectLeaveRequest(leave.getId(), "  Cuối tuần thiếu người dọn  ");

            assertThat(response.getStatus()).isEqualTo(LeaveRequestStatus.REJECTED);
            assertThat(leave.getApproverId()).isEqualTo(manager.getId());
            assertThat(leave.getDecidedAt()).isNotNull();
            assertThat(leave.getRejectReason()).isEqualTo("Cuối tuần thiếu người dọn");
            assertThat(response.getRejectReason()).isEqualTo("Cuối tuần thiếu người dọn");
            assertThat(response.getApproverName()).isEqualTo("Trần Quản Lý");
            verifyNoInteractions(shiftRepository);
        }

        /** Chốt 31/10/2026: từ chối phải kèm lý do — người gửi đọc được vì sao đơn không được duyệt. */
        @Test
        void shouldRequireReasonToReject() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2)));

            assertThatThrownBy(() -> service.rejectLeaveRequest(leave.getId(), "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lý do từ chối");
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
            assertThat(leave.getApproverId()).isNull();
            verify(leaveRequestRepository, never()).save(any());
        }

        @Test
        void shouldNotRejectDecidedRequest() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.UNPAID, today.plusDays(2), today.plusDays(2)));
            leave.setStatus(LeaveRequestStatus.CANCELLED);

            assertThatThrownBy(() -> service.rejectLeaveRequest(leave.getId(), "Thiếu người"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được xử lý");
            assertThat(leave.getRejectReason()).isNull();
        }

        @Test
        void managerQueueIsStaffOfOwnLocation() {
            loginAs(manager);
            when(leaveRequestRepository.findForReview(TENANT_ID, LOCATION_ID, Role.STAFF, LeaveRequestStatus.PENDING, PAGE))
                .thenReturn(Page.empty());

            service.getReviewQueue(LeaveRequestStatus.PENDING, PAGE);

            verify(leaveRequestRepository).findForReview(TENANT_ID, LOCATION_ID, Role.STAFF, LeaveRequestStatus.PENDING, PAGE);
        }

        @Test
        void directorQueueIsManagersOfWholeTenant() {
            loginAs(director);
            when(leaveRequestRepository.findForReview(TENANT_ID, null, Role.MANAGER, null, PAGE)).thenReturn(Page.empty());

            service.getReviewQueue(null, PAGE);

            verify(leaveRequestRepository).findForReview(TENANT_ID, null, Role.MANAGER, null, PAGE);
        }
    }

    // ── Hủy phần ngày chưa tới — BR-SCH-16 bản sửa 28/10/2026 ───────────────

    @Nested
    class Revoke {

        /** Chưa tới ngày nghỉ: hủy cả đơn, vẫn giữ người duyệt; ca đã gỡ lúc duyệt không tự gán lại. */
        @Test
        void shouldCancelWholeLeaveThatHasNotStarted() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(approved(leave(staff, LeaveType.ANNUAL, today.plusDays(5), today.plusDays(6))));

            LeaveRequestResponse response = service.revokeLeaveRequest(leave.getId(), "  Khách đoàn đến sớm, cần người  ");

            assertThat(response.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
            assertThat(leave.getApproverId()).isEqualTo(manager.getId());
            assertThat(leave.getRevokedBy()).isEqualTo(manager.getId());
            assertThat(leave.getRevokedAt()).isNotNull();
            assertThat(leave.getRevokeReason()).isEqualTo("Khách đoàn đến sớm, cần người");
            assertThat(leave.getOriginalToDate()).isNull();
            assertThat(leave.getToDate()).isEqualTo(today.plusDays(6));
            assertThat(response.getRevokedByName()).isEqualTo("Trần Quản Lý");
            verifyNoInteractions(shiftRepository);
        }

        /** Đang nghỉ dở: rút ngắn tới hết hôm nay — hôm nay coi như đã tới (BR-SCH-17); đơn vẫn đã duyệt. */
        @Test
        void shouldShortenOngoingLeaveToToday() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(approved(leave(staff, LeaveType.UNPAID, today.minusDays(1), today.plusDays(3))));

            LeaveRequestResponse response = service.revokeLeaveRequest(leave.getId(), "Xin đi làm lại sớm");

            assertThat(response.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
            assertThat(response.getToDate()).isEqualTo(today);
            assertThat(response.getOriginalToDate()).isEqualTo(today.plusDays(3));
            assertThat(response.getDays()).isEqualTo(2);
        }

        @Test
        void shouldRejectWhenNoFutureDayLeft() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(approved(leave(staff, LeaveType.ANNUAL, today.minusDays(2), today)));

            assertThatThrownBy(() -> service.revokeLeaveRequest(leave.getId(), "Đi làm lại"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không còn ngày nghỉ nào chưa tới");
            assertThat(leave.getRevokedBy()).isNull();
        }

        /** Đơn đang chờ thì người duyệt từ chối, không "hủy". */
        @Test
        void shouldRejectRevokingPendingRequest() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(leave(staff, LeaveType.ANNUAL, today.plusDays(5), today.plusDays(6)));

            assertThatThrownBy(() -> service.revokeLeaveRequest(leave.getId(), "Không cần"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chỉ hủy được đơn đã duyệt");
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        }

        @Test
        void shouldRequireReason() {
            loginAs(manager);
            LeaveRequest leave = stubLeave(approved(leave(staff, LeaveType.ANNUAL, today.plusDays(5), today.plusDays(6))));

            assertThatThrownBy(() -> service.revokeLeaveRequest(leave.getId(), "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lý do");
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        }

        /** Cùng phạm vi với duyệt (BR-SCH-08): Giám đốc không đụng đơn của Staff — 404. */
        @Test
        void directorCannotRevokeStaffLeave() {
            loginAs(director);
            LeaveRequest leave = stubLeave(approved(leave(staff, LeaveType.ANNUAL, today.plusDays(5), today.plusDays(6))));

            assertThatThrownBy(() -> service.revokeLeaveRequest(leave.getId(), "Đi làm lại"))
                .isInstanceOf(ResourceNotFoundException.class);
            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        }

        @Test
        void directorRevokesManagerLeave() {
            loginAs(director);
            LeaveRequest leave = stubLeave(approved(leave(manager, LeaveType.ANNUAL, today.plusDays(5), today.plusDays(6))));

            assertThat(service.revokeLeaveRequest(leave.getId(), "Chi nhánh cần quản lý tuần này").getStatus())
                .isEqualTo(LeaveRequestStatus.CANCELLED);
            assertThat(leave.getRevokedBy()).isEqualTo(director.getId());
        }

        /** Đơn đã duyệt: người duyệt là cấp trên của người gửi, duyệt cách đây một tuần. */
        private LeaveRequest approved(LeaveRequest leave) {
            User requester = leave.getRequesterId().equals(manager.getId()) ? manager : staff;
            leave.setStatus(LeaveRequestStatus.APPROVED);
            leave.setApproverId(requester == manager ? director.getId() : manager.getId());
            leave.setDecidedAt(today.minusDays(7).atTime(9, 0));
            return leave;
        }
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    /** Ngày bắt đầu phép năm hợp lệ bất kể hôm nay: đủ báo trước, và hai ngày liền sau vẫn cùng năm. */
    private LocalDate annualStart() {
        LocalDate from = today.plusDays(10);
        return from.getMonthValue() == 12 && from.getDayOfMonth() >= 30 ? from.plusDays(3) : from;
    }

    private void loginAs(User person) {
        PositionType type = person.getRole() == Role.STAFF ? PositionType.RECEPTION : null;
        TestAuth.loginAs(person.getId(), person.getRole(), TENANT_ID, person.getLocationId(), type);
    }

    private LeaveRequest stubLeave(LeaveRequest leave) {
        when(leaveRequestRepository.findByIdAndTenantId(leave.getId(), TENANT_ID)).thenReturn(Optional.of(leave));
        return leave;
    }

    private void stubShifts(LeaveRequest leave, Shift... shifts) {
        when(shiftRepository.findByStaffIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
            leave.getRequesterId(), leave.getFromDate(), leave.getToDate()))
            .thenReturn(List.of(shifts));
        lenient().when(shiftTemplateRepository.findAllById(any())).thenReturn(List.of(ShiftTemplate.builder()
            .id(TEMPLATE_ID).tenantId(TENANT_ID).name("Ca sáng")
            .startTime(LocalTime.of(6, 0)).endTime(LocalTime.of(14, 0)).build()));
    }

    private static LeaveBalanceResponse balance(int remaining, int pending) {
        return LeaveBalanceResponse.builder().year(2026).daysPerYear(12).entitledDays(remaining + pending)
            .usedDays(0).pendingDays(pending).remainingDays(remaining).noticeDays(3).build();
    }

    private static CreateLeaveRequestRequest request(LeaveType type, LocalDate from, LocalDate to, String reason) {
        CreateLeaveRequestRequest request = new CreateLeaveRequestRequest();
        request.setLeaveType(type);
        request.setFromDate(from);
        request.setToDate(to);
        request.setReason(reason);
        return request;
    }

    private static LeaveRequest leave(User requester, LeaveType type, LocalDate from, LocalDate to) {
        return LeaveRequest.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).locationId(LOCATION_ID).requesterId(requester.getId())
            .leaveType(type).fromDate(from).toDate(to).reason("Việc riêng").status(LeaveRequestStatus.PENDING)
            .build();
    }

    private static Shift shift(User person, LocalDate date) {
        return Shift.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).locationId(LOCATION_ID).staffId(person.getId())
            .shiftDate(date).startTime(LocalTime.of(6, 0)).endTime(LocalTime.of(14, 0))
            .durationHours(new BigDecimal("8.00")).sourceTemplateId(TEMPLATE_ID)
            .build();
    }

    private static User user(Role role, UUID locationId, String fullName) {
        return User.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).locationId(locationId).role(role).status(UserStatus.ACTIVE)
            .email(UUID.randomUUID() + "@test.local").passwordHash("x").fullName(fullName).phone("0900000000")
            .startWorkDate(LocalDate.of(2025, 1, 1))
            .build();
    }
}
