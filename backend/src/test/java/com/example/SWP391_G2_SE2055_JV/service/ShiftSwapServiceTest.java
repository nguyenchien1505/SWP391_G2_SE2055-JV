package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftSwapRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftSwapRequestResponse;
import com.example.SWP391_G2_SE2055_JV.dto.SwapCandidateResponse;
import com.example.SWP391_G2_SE2055_JV.entity.SchedulePolicy;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftSwapRequest;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.SwapRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.SwapExpiredException;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftSwapRequestRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đổi ca — BR-SCH-08..12, 18, 19, 21, 25, 26 (Milestone 2). Schedule Policy đã test riêng ở
 * {@link SchedulePolicyValidatorTest}; ở đây validator được mock để kiểm service gọi nó cho ĐÚNG người,
 * với ĐÚNG ca nhận và ca bỏ, và vi phạm thì không ghi gì.
 *
 * <p>Ngày giờ dựng tương đối với "bây giờ" theo giờ Hà Nội, vì service đọc giờ thật.
 */
@ExtendWith(MockitoExtension.class)
class ShiftSwapServiceTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID POSITION_ID = UUID.randomUUID();
    private static final UUID A_ID        = UUID.randomUUID();
    private static final UUID B_ID        = UUID.randomUUID();
    private static final UUID SHIFT_A_ID  = UUID.randomUUID();
    private static final UUID SHIFT_B_ID  = UUID.randomUUID();
    private static final UUID REQUEST_ID  = UUID.randomUUID();

    private static final LocalDate FUTURE = LocalDate.now(ShiftTimeUtils.HANOI_ZONE).plusDays(3);
    private static final List<StaffPermission> RECEPTION = List.of(StaffPermission.RECEPTION);

    @Mock ShiftSwapRequestRepository swapRepository;
    @Mock ShiftRepository            shiftRepository;
    @Mock ShiftTemplateRepository    shiftTemplateRepository;
    @Mock UserRepository             userRepository;
    @Mock SchedulePolicyValidator    policyValidator;
    @Mock SchedulePolicyService      policyService;
    @Mock HousekeepingService        housekeepingService;

    @InjectMocks ShiftSwapService service;

    private Shift shiftA;
    private Shift shiftB;
    private User  staffA;
    private User  staffB;

    @BeforeEach
    void fixtures() {
        shiftA = shift(SHIFT_A_ID, A_ID, FUTURE, 6, 14);
        shiftB = shift(SHIFT_B_ID, B_ID, FUTURE, 14, 22);
        staffA = staff(A_ID, "Trần An");
        staffB = staff(B_ID, "Lê Bình");
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    // ── A gửi yêu cầu ───────────────────────────────────────────────────────

    @Nested
    class Create {

        @BeforeEach
        void happyPath() {
            TestAuth.loginAs(A_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            lenient().when(shiftRepository.findByIdAndTenantId(SHIFT_A_ID, TENANT_ID)).thenReturn(Optional.of(shiftA));
            lenient().when(shiftRepository.findByIdAndTenantId(SHIFT_B_ID, TENANT_ID)).thenReturn(Optional.of(shiftB));
            lenient().when(userRepository.findByIdAndTenantId(A_ID, TENANT_ID)).thenReturn(Optional.of(staffA));
            lenient().when(userRepository.findByIdAndTenantId(B_ID, TENANT_ID)).thenReturn(Optional.of(staffB));
            lenient().when(userRepository.findPermissionsOfUser(A_ID)).thenReturn(RECEPTION);
            lenient().when(userRepository.findPermissionsOfUser(B_ID)).thenReturn(RECEPTION);
            lenient().when(swapRepository.findByShiftAndStatusIn(any(), anyCollection())).thenReturn(List.of());
            lenient().when(policyService.getOrCreate(TENANT_ID))
                .thenReturn(SchedulePolicy.builder().tenantId(TENANT_ID).build());
            lenient().when(swapRepository.saveAndFlush(any(ShiftSwapRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(userRepository.findAllById(any())).thenReturn(List.of(staffA, staffB));
            lenient().when(shiftRepository.findAllById(any())).thenReturn(List.of(shiftA, shiftB));
        }

        @Test
        void shouldWaitForPeerWithDeadlineFromPolicy() {
            LocalDateTime before = LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE);

            ShiftSwapRequestResponse response = service.create(request());

            assertThat(response.getStatus()).isEqualTo(SwapRequestStatus.PENDING_PEER);
            assertThat(response.getRequester().getFullName()).isEqualTo("Trần An");
            assertThat(response.getPeer().getShift().getId()).isEqualTo(SHIFT_B_ID);
            // BR-SCH-21: hạn trả lời = lúc gửi + thời gian chờ của Policy (mặc định 24 giờ).
            assertThat(response.getExpiresAt()).isAfterOrEqualTo(before.plusHours(24))
                .isBefore(before.plusHours(24).plusMinutes(1));
        }

        /** BR-SCH-10: mỗi người kiểm với ca nhận được, bỏ đi ca của chính mình. */
        @Test
        void shouldCheckPolicyForBothPeopleOnTheirScheduleAfterSwap() {
            service.create(request());

            verify(policyValidator).validate(TENANT_ID, A_ID, shiftB, SHIFT_A_ID);
            verify(policyValidator).validate(TENANT_ID, B_ID, shiftA, SHIFT_B_ID);
        }

        @Test
        void shouldNameThePersonWhoWouldBreakPolicy() {
            // lenient: validator được gọi cho A trước, với tham số khác stub này.
            lenient().doThrow(new BusinessException("Vượt 48 giờ trong tuần"))
                .when(policyValidator).validate(TENANT_ID, B_ID, shiftA, SHIFT_B_ID);

            assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Lê Bình")
                .hasMessageContaining("Vượt 48 giờ");
            verify(swapRepository, never()).saveAndFlush(any());
        }

        /** BR-SCH-12 (sửa M2): cùng đúng một Position. */
        @Test
        void shouldRejectPeerOfAnotherPosition() {
            staffB.setPositionId(UUID.randomUUID());

            assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cùng vị trí");
        }

        /** BR-SCH-12 (sửa M2): cùng bộ quyền — A (Lễ tân + Dọn dẹp) không đổi với B (chỉ Lễ tân). */
        @Test
        void shouldRejectPeerWithOtherPermissions() {
            when(userRepository.findPermissionsOfUser(A_ID))
                .thenReturn(List.of(StaffPermission.RECEPTION, StaffPermission.HOUSEKEEPING));

            assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cùng quyền");
        }

        /** BR-SCH-25: chỉ đổi ca chưa tới giờ bắt đầu. */
        @Test
        void shouldRejectShiftThatAlreadyStarted() {
            shiftB.setShiftDate(LocalDate.now(ShiftTimeUtils.HANOI_ZONE).minusDays(1));

            assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã tới giờ bắt đầu");
        }

        /** BR-SCH-25: chỉ đổi ca chưa check-in. */
        @Test
        void shouldRejectCheckedInShift() {
            shiftA.setCheckInAt(LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE));

            assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("check-in");
        }

        /** BR-SCH-19: ca đã nằm trong yêu cầu chờ khác — kể cả ở phía đồng nghiệp của yêu cầu kia. */
        @Test
        void shouldRejectShiftAlreadyHeldByAnotherPendingRequest() {
            when(swapRepository.findByShiftAndStatusIn(eq(SHIFT_B_ID), anyCollection()))
                .thenReturn(List.of(pending(SwapRequestStatus.PENDING_MANAGER)));

            assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa xử lý");
            verify(swapRepository, never()).saveAndFlush(any());
        }

        @Test
        void shouldRejectSwappingAShiftOfSomeoneElse() {
            shiftA.setStaffId(UUID.randomUUID());

            assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ca của chính bạn");
        }

        /** BR-SCH-08: Manager không có ca nên không đổi ca. */
        @Test
        void shouldRejectManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);

            assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chỉ nhân viên");
        }

        private CreateShiftSwapRequest request() {
            CreateShiftSwapRequest request = new CreateShiftSwapRequest();
            request.setRequesterShiftId(SHIFT_A_ID);
            request.setPeerShiftId(SHIFT_B_ID);
            return request;
        }
    }

    // ── B trả lời ───────────────────────────────────────────────────────────

    @Nested
    class Answer {

        @Test
        void shouldWaitForManagerOncePeerAgreesAndPolicyPasses() {
            TestAuth.loginAs(B_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            ShiftSwapRequest request = givenRequest(SwapRequestStatus.PENDING_PEER);
            givenShiftsById();
            givenUsers();
            givenSaveReturnsArgument();

            ShiftSwapRequestResponse response = service.accept(REQUEST_ID);

            assertThat(response.getStatus()).isEqualTo(SwapRequestStatus.PENDING_MANAGER);
            assertThat(request.getPeerRespondedAt()).isNotNull();
            verify(policyValidator).validate(TENANT_ID, A_ID, shiftB, SHIFT_A_ID);
            verify(policyValidator).validate(TENANT_ID, B_ID, shiftA, SHIFT_B_ID);
        }

        /** BR-SCH-11: quá thời gian chờ thì tự hủy; trạng thái mới vẫn được lưu dù thao tác bị từ chối. */
        @Test
        void shouldExpireWhenPeerAnswersTooLate() {
            TestAuth.loginAs(B_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            ShiftSwapRequest request = givenRequest(SwapRequestStatus.PENDING_PEER);
            request.setExpiresAt(LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE).minusMinutes(1));
            givenShiftsById();

            assertThatThrownBy(() -> service.accept(REQUEST_ID))
                .isInstanceOf(SwapExpiredException.class)
                .hasMessageContaining("không trả lời");
            assertThat(request.getStatus()).isEqualTo(SwapRequestStatus.EXPIRED);
            verify(swapRepository).save(request);
        }

        @Test
        void shouldRefuseAnswerFromSomeoneOtherThanThePeer() {
            TestAuth.loginAs(A_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            givenRequest(SwapRequestStatus.PENDING_PEER);

            assertThatThrownBy(() -> service.accept(REQUEST_ID)).isInstanceOf(UnauthorizedException.class);
            assertThatThrownBy(() -> service.decline(REQUEST_ID)).isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void shouldLetRequesterCancelWhilePending() {
            TestAuth.loginAs(A_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            ShiftSwapRequest request = givenRequest(SwapRequestStatus.PENDING_MANAGER);
            givenSaveReturnsArgument();

            service.cancel(REQUEST_ID);

            assertThat(request.getStatus()).isEqualTo(SwapRequestStatus.CANCELLED);
        }
    }

    // ── Manager duyệt ──────────────────────────────────────────────────────

    @Nested
    class Approve {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        /** BR-SCH-18: hoán đổi người của 2 ca; BR-SCH-26: việc dọn đi theo người nhận ca. */
        @Test
        void shouldSwapPeopleAndMoveCleaningTasks() {
            ShiftSwapRequest request = givenRequest(SwapRequestStatus.PENDING_MANAGER);
            givenShiftsLocked();
            givenUsers();
            givenSaveReturnsArgument();

            ShiftSwapRequestResponse response = service.approve(REQUEST_ID);

            assertThat(shiftA.getStaffId()).isEqualTo(B_ID);
            assertThat(shiftB.getStaffId()).isEqualTo(A_ID);
            verify(housekeepingService).transferTasksOnSwap(A_ID, B_ID, FUTURE);
            verify(housekeepingService).transferTasksOnSwap(B_ID, A_ID, FUTURE);
            assertThat(response.getStatus()).isEqualTo(SwapRequestStatus.APPROVED);
            assertThat(request.getApproverId()).isNotNull();
            assertThat(request.getDecidedAt()).isNotNull();
        }

        /** Lịch có thể đã đổi trong lúc chờ — Policy kiểm lại lúc duyệt, vi phạm thì không đổi gì. */
        @Test
        void shouldNotSwapWhenPolicyNowFails() {
            givenRequest(SwapRequestStatus.PENDING_MANAGER);
            givenShiftsLocked();
            givenUsers();
            doThrow(new BusinessException("Không đủ 12 giờ nghỉ giữa 2 ca"))
                .when(policyValidator).validate(TENANT_ID, A_ID, shiftB, SHIFT_A_ID);

            assertThatThrownBy(() -> service.approve(REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Trần An");
            assertThat(shiftA.getStaffId()).isEqualTo(A_ID);
            verify(shiftRepository, never()).saveAllAndFlush(any());
        }

        @Test
        void shouldExpireWhenAShiftChangedHandsMeanwhile() {
            ShiftSwapRequest request = givenRequest(SwapRequestStatus.PENDING_MANAGER);
            shiftB.setStaffId(UUID.randomUUID());
            givenShiftsLocked();

            assertThatThrownBy(() -> service.approve(REQUEST_ID))
                .isInstanceOf(SwapExpiredException.class)
                .hasMessageContaining("đổi người");
            assertThat(request.getStatus()).isEqualTo(SwapRequestStatus.EXPIRED);
            verify(shiftRepository, never()).saveAllAndFlush(any());
        }

        @Test
        void shouldRefuseManagerOfAnotherLocation() {
            TestAuth.loginAsManager(TENANT_ID, UUID.randomUUID());
            givenRequest(SwapRequestStatus.PENDING_MANAGER);

            assertThatThrownBy(() -> service.approve(REQUEST_ID)).isInstanceOf(UnauthorizedException.class);
        }
    }

    // ── Tự hủy ─────────────────────────────────────────────────────────────

    @Nested
    class Expiry {

        /** BR-SCH-25: tới giờ bắt đầu của ca sớm hơn mà chưa duyệt thì tự hủy, kể cả khi chưa hết hạn trả lời. */
        @Test
        void shouldBeInvalidOnceEarlierShiftStarts() {
            ShiftSwapRequest request = pending(SwapRequestStatus.PENDING_MANAGER);
            shiftA.setShiftDate(LocalDate.now(ShiftTimeUtils.HANOI_ZONE).minusDays(1));

            assertThat(ShiftSwapService.invalidReason(request, shiftA, shiftB,
                LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE))).contains("giờ bắt đầu");
        }

        /** Hạn trả lời (BR-SCH-11) chỉ áp khi còn chờ đồng nghiệp, không áp khi đã chờ Manager. */
        @Test
        void shouldIgnorePeerDeadlineOnceWaitingForManager() {
            ShiftSwapRequest request = pending(SwapRequestStatus.PENDING_MANAGER);
            request.setExpiresAt(LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE).minusHours(1));

            assertThat(ShiftSwapService.invalidReason(request, shiftA, shiftB,
                LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE))).isNull();
        }

        @Test
        void shouldExpireOnlyRequestsThatAreNoLongerValid() {
            ShiftSwapRequest stillValid = pending(SwapRequestStatus.PENDING_PEER);
            ShiftSwapRequest late = pending(SwapRequestStatus.PENDING_PEER);
            late.setId(UUID.randomUUID());
            late.setExpiresAt(LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE).minusMinutes(5));
            when(swapRepository.findByStatusIn(SwapRequestStatus.PENDING_STATUSES)).thenReturn(List.of(stillValid, late));
            when(shiftRepository.findAllById(any())).thenReturn(List.of(shiftA, shiftB));

            int expired = service.expireDue();

            assertThat(expired).isEqualTo(1);
            assertThat(late.getStatus()).isEqualTo(SwapRequestStatus.EXPIRED);
            assertThat(stillValid.getStatus()).isEqualTo(SwapRequestStatus.PENDING_PEER);
        }

        /** Phương án a: Manager sửa / gỡ người khỏi ca thì yêu cầu đang chờ của ca đó tự hủy. */
        @Test
        void shouldExpirePendingRequestsOfAnEditedShift() {
            ShiftSwapRequest request = pending(SwapRequestStatus.PENDING_PEER);
            when(swapRepository.findByShiftAndStatusIn(SHIFT_A_ID, SwapRequestStatus.PENDING_STATUSES))
                .thenReturn(List.of(request));

            assertThat(service.expirePendingForShift(SHIFT_A_ID)).isEqualTo(1);
            assertThat(request.getStatus()).isEqualTo(SwapRequestStatus.EXPIRED);
        }

        @Test
        void shouldRefuseDeletingShiftWithSwapHistory() {
            when(swapRepository.existsByRequesterShiftIdOrPeerShiftId(SHIFT_A_ID, SHIFT_A_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.assertNoSwapRequests(SHIFT_A_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("gỡ người");
        }
    }

    // ── Danh sách ca đổi được ───────────────────────────────────────────────

    @Nested
    class Candidates {

        /**
         * Chỉ ca tương lai, chưa check-in, chưa bị giữ chỗ, khác giờ ca của mình, của người cùng vị trí
         * và cùng bộ quyền.
         */
        @Test
        void shouldOfferOnlyFreeFutureShiftsOfMatchingColleagues() {
            TestAuth.loginAs(A_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            UUID cId = UUID.randomUUID();
            User staffC = staff(cId, "Phạm Cường");
            Shift checkedIn = shift(UUID.randomUUID(), B_ID, FUTURE.plusDays(1), 6, 14);
            checkedIn.setCheckInAt(LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE));
            Shift held = shift(UUID.randomUUID(), B_ID, FUTURE.plusDays(2), 6, 14);
            Shift sameHoursAsMine = shift(UUID.randomUUID(), B_ID, FUTURE, 6, 14);
            Shift ofOtherPermissions = shift(UUID.randomUUID(), cId, FUTURE, 14, 22);
            Shift open = shift(UUID.randomUUID(), null, FUTURE, 14, 22);

            when(shiftRepository.findByIdAndTenantId(SHIFT_A_ID, TENANT_ID)).thenReturn(Optional.of(shiftA));
            when(swapRepository.findByShiftAndStatusIn(eq(SHIFT_A_ID), anyCollection())).thenReturn(List.of());
            when(userRepository.findByIdAndTenantId(A_ID, TENANT_ID)).thenReturn(Optional.of(staffA));
            when(userRepository.findPermissionsOfUser(A_ID)).thenReturn(RECEPTION);
            when(userRepository.findPermissionsOfUser(B_ID)).thenReturn(RECEPTION);
            when(userRepository.findPermissionsOfUser(cId))
                .thenReturn(List.of(StaffPermission.RECEPTION, StaffPermission.HOUSEKEEPING));
            when(userRepository.findByPositionId(POSITION_ID)).thenReturn(List.of(staffA, staffB, staffC));
            ShiftSwapRequest holding = pending(SwapRequestStatus.PENDING_PEER);
            holding.setRequesterShiftId(UUID.randomUUID());
            holding.setPeerShiftId(held.getId());
            when(swapRepository.findByLocationIdAndStatusIn(eq(LOCATION_ID), anyCollection())).thenReturn(List.of(holding));
            when(shiftRepository.findByTenantIdAndLocationIdAndShiftDateBetween(
                    eq(TENANT_ID), eq(LOCATION_ID), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shiftA, shiftB, checkedIn, held, sameHoursAsMine, ofOtherPermissions, open)));

            List<SwapCandidateResponse> candidates = service.getCandidates(SHIFT_A_ID);

            assertThat(candidates).extracting(c -> c.getShift().getId()).containsExactly(SHIFT_B_ID);
            assertThat(candidates.get(0).getFullName()).isEqualTo("Lê Bình");
        }
    }

    // ── Tiện ích ───────────────────────────────────────────────────────────

    private ShiftSwapRequest givenRequest(SwapRequestStatus status) {
        ShiftSwapRequest request = pending(status);
        when(swapRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID)).thenReturn(Optional.of(request));
        return request;
    }

    private void givenShiftsById() {
        when(shiftRepository.findById(SHIFT_A_ID)).thenReturn(Optional.of(shiftA));
        when(shiftRepository.findById(SHIFT_B_ID)).thenReturn(Optional.of(shiftB));
    }

    private void givenShiftsLocked() {
        when(shiftRepository.findForUpdate(SHIFT_A_ID, TENANT_ID)).thenReturn(Optional.of(shiftA));
        when(shiftRepository.findForUpdate(SHIFT_B_ID, TENANT_ID)).thenReturn(Optional.of(shiftB));
    }

    private void givenUsers() {
        when(userRepository.findByIdAndTenantId(A_ID, TENANT_ID)).thenReturn(Optional.of(staffA));
        when(userRepository.findByIdAndTenantId(B_ID, TENANT_ID)).thenReturn(Optional.of(staffB));
    }

    private void givenSaveReturnsArgument() {
        when(swapRepository.save(any(ShiftSwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        givenLookups();
    }

    private void givenLookups() {
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of(staffA, staffB));
        lenient().when(shiftRepository.findAllById(any())).thenReturn(List.of(shiftA, shiftB));
    }

    private static ShiftSwapRequest pending(SwapRequestStatus status) {
        return ShiftSwapRequest.builder()
            .id(REQUEST_ID)
            .tenantId(TENANT_ID)
            .locationId(LOCATION_ID)
            .requesterId(A_ID)
            .requesterShiftId(SHIFT_A_ID)
            .peerId(B_ID)
            .peerShiftId(SHIFT_B_ID)
            .status(status)
            .expiresAt(LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE).plusHours(24))
            .build();
    }

    private static Shift shift(UUID id, UUID staffId, LocalDate date, int startHour, int endHour) {
        return Shift.builder()
            .id(id)
            .tenantId(TENANT_ID)
            .locationId(LOCATION_ID)
            .staffId(staffId)
            .shiftDate(date)
            .startTime(LocalTime.of(startHour, 0))
            .endTime(LocalTime.of(endHour, 0))
            .overnight(false)
            .durationHours(BigDecimal.valueOf(endHour - startHour))
            .build();
    }

    private static User staff(UUID id, String fullName) {
        return User.builder()
            .id(id)
            .tenantId(TENANT_ID)
            .role(Role.STAFF)
            .status(UserStatus.ACTIVE)
            .fullName(fullName)
            .locationId(LOCATION_ID)
            .positionId(POSITION_ID)
            .build();
    }
}
