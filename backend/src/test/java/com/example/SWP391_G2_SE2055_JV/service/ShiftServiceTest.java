package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Xếp ca — BR-SCH-02, BR-SCH-03, BR-SCH-04, BR-SCH-05, BR-SCH-22, BR-SCH-24, BR-PERM-03, DM-03, DM-15.
 *
 * <p>Luật của Schedule Policy đã test riêng ở {@link SchedulePolicyValidatorTest}; ở đây validator
 * được mock để kiểm đúng điều service chịu trách nhiệm: đường ghi nào phải gọi validator, với ai,
 * và vi phạm thì không lưu.
 */
@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    private static final UUID TENANT_ID         = UUID.randomUUID();
    private static final UUID LOCATION_ID       = UUID.randomUUID();
    private static final UUID OTHER_LOCATION_ID = UUID.randomUUID();
    private static final UUID STAFF_ID          = UUID.randomUUID();
    private static final UUID SHIFT_ID          = UUID.randomUUID();
    private static final UUID TEMPLATE_ID       = UUID.randomUUID();

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);

    @Mock ShiftRepository         shiftRepository;
    @Mock UserRepository          userRepository;
    @Mock LocationRepository      locationRepository;
    @Mock ShiftTemplateRepository shiftTemplateRepository;
    @Mock SchedulePolicyValidator policyValidator;

    @InjectMocks ShiftService service;

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    // ── Xem lịch theo khoảng ngày — phạm vi theo vai trò ────────────────────

    @Nested
    class GetShifts {

        private final Pageable pageable = PageRequest.of(0, 500);

        @Test
        void shouldListWeekOfOwnLocationForManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            when(shiftRepository.findByTenantIdAndLocationIdAndShiftDateBetween(
                    TENANT_ID, LOCATION_ID, MONDAY, SUNDAY, pageable))
                .thenReturn(new PageImpl<>(List.of(assignedShift())));

            Page<ShiftResponse> page = service.getShifts(MONDAY, SUNDAY, pageable);

            assertThat(page.getContent()).extracting(ShiftResponse::getId).containsExactly(SHIFT_ID);
        }

        @Test
        void shouldListOnlyOwnShiftsForStaff() {
            CustomUserDetails staff = TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            when(shiftRepository.findByStaffIdAndShiftDateBetween(staff.getId(), MONDAY, SUNDAY, pageable))
                .thenReturn(new PageImpl<>(List.of()));

            service.getShifts(MONDAY, SUNDAY, pageable);

            verify(shiftRepository).findByStaffIdAndShiftDateBetween(staff.getId(), MONDAY, SUNDAY, pageable);
        }

        @Test
        void shouldListWholeTenantForDirector() {
            TestAuth.loginAsDirector(TENANT_ID);
            when(shiftRepository.findByTenantIdAndShiftDateBetween(TENANT_ID, MONDAY, SUNDAY, pageable))
                .thenReturn(new PageImpl<>(List.of()));

            service.getShifts(MONDAY, SUNDAY, pageable);

            verify(shiftRepository).findByTenantIdAndShiftDateBetween(TENANT_ID, MONDAY, SUNDAY, pageable);
        }

        @Test
        void shouldKeepUnfilteredListingWhenNoDatesGiven() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            when(shiftRepository.findByTenantIdAndLocationId(TENANT_ID, LOCATION_ID, pageable))
                .thenReturn(new PageImpl<>(List.of()));

            service.getShifts(null, null, pageable);

            verify(shiftRepository).findByTenantIdAndLocationId(TENANT_ID, LOCATION_ID, pageable);
        }

        @Test
        void shouldRejectHalfOpenRange() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);

            assertThatThrownBy(() -> service.getShifts(MONDAY, null, pageable))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cả from và to");
            verifyNoInteractions(shiftRepository);
        }

        @Test
        void shouldRejectReversedRange() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);

            assertThatThrownBy(() -> service.getShifts(SUNDAY, MONDAY, pageable))
                .isInstanceOf(BusinessException.class);
            verifyNoInteractions(shiftRepository);
        }
    }

    // ── Tạo ca — BR-SCH-04: theo mẫu hoặc tự do, cả hai qua Schedule Policy ──

    @Nested
    class CreateShift {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        /** BR-SCH-03: mẫu ca đêm 22:00–06:00 → cờ qua đêm và 8 giờ tính trọn vào ngày bắt đầu. */
        @Test
        void shouldCopyHoursFromTemplateAndValidateForStaff() {
            givenLocationInTenant();
            givenTemplate(nightTemplate());
            givenStaff(activeStaff());
            givenSaveReturnsArgument();

            ShiftResponse created = service.createShift(request(STAFF_ID, TEMPLATE_ID, null, null));

            Shift saved = captureSaved();
            assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(22, 0));
            assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(6, 0));
            assertThat(saved.isOvernight()).isTrue();
            assertThat(saved.getDurationHours()).isEqualByComparingTo("8");
            assertThat(saved.getSourceTemplateId()).isEqualTo(TEMPLATE_ID);
            assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(created.getStaffId()).isEqualTo(STAFF_ID);
            verify(policyValidator).validate(eq(TENANT_ID), eq(STAFF_ID), any(Shift.class), isNull());
        }

        @Test
        void shouldCreateFreeShiftWithTypedHours() {
            givenLocationInTenant();
            givenStaff(activeStaff());
            givenSaveReturnsArgument();

            service.createShift(request(STAFF_ID, null, "07:30", "12:00"));

            Shift saved = captureSaved();
            assertThat(saved.getSourceTemplateId()).isNull();
            assertThat(saved.isOvernight()).isFalse();
            assertThat(saved.getDurationHours()).isEqualByComparingTo("4.5");
            verify(policyValidator).validate(eq(TENANT_ID), eq(STAFF_ID), any(Shift.class), isNull());
        }

        /** DM-03: ca chưa phân công không ràng buộc ai nên không chạy kiểm tra Policy. */
        @Test
        void shouldCreateUnassignedShiftWithoutPolicyCheck() {
            givenLocationInTenant();
            givenSaveReturnsArgument();

            service.createShift(request(null, null, "06:00", "14:00"));

            assertThat(captureSaved().getStaffId()).isNull();
            verifyNoInteractions(policyValidator, userRepository);
        }

        @Test
        void shouldRejectTemplateTogetherWithTypedHours() {
            givenLocationInTenant();

            assertThatThrownBy(() -> service.createShift(request(STAFF_ID, TEMPLATE_ID, "06:00", "14:00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("giờ lấy theo mẫu");
            verify(shiftRepository, never()).save(any());
        }

        @Test
        void shouldRejectFreeShiftWithoutBothHours() {
            givenLocationInTenant();

            assertThatThrownBy(() -> service.createShift(request(STAFF_ID, null, "06:00", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("giờ bắt đầu và giờ kết thúc");
            verify(shiftRepository, never()).save(any());
        }

        /** BR-SCH-22: mẫu đã vô hiệu hóa chỉ còn để tra lịch sử, không xếp ca mới được. */
        @Test
        void shouldRejectDeactivatedTemplate() {
            givenLocationInTenant();
            ShiftTemplate template = nightTemplate();
            template.setActive(false);
            givenTemplate(template);

            assertThatThrownBy(() -> service.createShift(request(STAFF_ID, TEMPLATE_ID, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vô hiệu hóa");
            verify(shiftRepository, never()).save(any());
        }

        @Test
        void shouldNotUseTemplateOfAnotherTenant() {
            givenLocationInTenant();
            when(shiftTemplateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createShift(request(STAFF_ID, TEMPLATE_ID, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
            verify(shiftRepository, never()).save(any());
        }

        /** BR-PERM-03: Manager chỉ xếp ca trong khách sạn của mình. */
        @Test
        void shouldRejectShiftAtAnotherLocation() {
            CreateShiftRequest request = request(STAFF_ID, null, "06:00", "14:00");
            request.setLocationId(OTHER_LOCATION_ID);

            assertThatThrownBy(() -> service.createShift(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Location khác");
            verifyNoInteractions(shiftRepository, policyValidator);
        }

        /** BR-SCH-05: phạm vi trùng ca là khách sạn hiện tại — chỉ xếp người của khách sạn đó. */
        @Test
        void shouldRejectStaffOfAnotherLocation() {
            givenLocationInTenant();
            User staff = activeStaff();
            staff.setLocationId(OTHER_LOCATION_ID);
            givenStaff(staff);

            assertThatThrownBy(() -> service.createShift(request(STAFF_ID, null, "06:00", "14:00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thuộc");
            verify(shiftRepository, never()).save(any());
        }

        @Test
        void shouldRejectTerminatedStaff() {
            givenLocationInTenant();
            User staff = activeStaff();
            staff.setStatus(UserStatus.TERMINATED);
            givenStaff(staff);

            assertThatThrownBy(() -> service.createShift(request(STAFF_ID, null, "06:00", "14:00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nghỉ việc");
            verify(shiftRepository, never()).save(any());
        }

        /** BR-SCH-02: vi phạm là chặn cứng — không lưu, không có đường vòng. */
        @Test
        void shouldNotSaveWhenPolicyIsViolated() {
            givenLocationInTenant();
            givenStaff(activeStaff());
            doThrow(new BusinessException("Vượt giờ làm tối đa/ngày"))
                .when(policyValidator).validate(eq(TENANT_ID), eq(STAFF_ID), any(Shift.class), isNull());

            assertThatThrownBy(() -> service.createShift(request(STAFF_ID, null, "06:00", "18:00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vượt giờ làm tối đa/ngày");
            verify(shiftRepository, never()).save(any());
        }
    }

    // ── Sửa ngày giờ ca ─────────────────────────────────────────────────────

    @Nested
    class UpdateShift {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        @Test
        void shouldDropTemplateLinkAndRecomputeHoursWhenTypingNewHours() {
            Shift shift = assignedShift();
            shift.setSourceTemplateId(TEMPLATE_ID);
            givenOwnedShift(shift);
            givenSaveReturnsArgument();

            UpdateShiftRequest request = new UpdateShiftRequest();
            request.setStartTime(LocalTime.of(22, 0));
            request.setEndTime(LocalTime.of(6, 0));
            service.updateShift(SHIFT_ID, request);

            assertThat(shift.getSourceTemplateId()).isNull();
            assertThat(shift.isOvernight()).isTrue();
            assertThat(shift.getDurationHours()).isEqualByComparingTo("8");
            // Loại chính ca đang sửa khỏi phép tính, nếu không nó tự "trùng" với bản cũ của mình.
            verify(policyValidator).validate(TENANT_ID, STAFF_ID, shift, SHIFT_ID);
        }

        @Test
        void shouldTakeHoursFromNewTemplate() {
            givenOwnedShift(assignedShift());
            givenTemplate(nightTemplate());
            givenSaveReturnsArgument();

            UpdateShiftRequest request = new UpdateShiftRequest();
            request.setSourceTemplateId(TEMPLATE_ID);
            ShiftResponse updated = service.updateShift(SHIFT_ID, request);

            assertThat(updated.getStartTime()).isEqualTo(LocalTime.of(22, 0));
            assertThat(updated.getEndTime()).isEqualTo(LocalTime.of(6, 0));
            assertThat(updated.getSourceTemplateId()).isEqualTo(TEMPLATE_ID);
        }

        @Test
        void shouldKeepTemplateLinkWhenOnlyDateChanges() {
            Shift shift = assignedShift();
            shift.setSourceTemplateId(TEMPLATE_ID);
            givenOwnedShift(shift);
            givenSaveReturnsArgument();

            UpdateShiftRequest request = new UpdateShiftRequest();
            request.setShiftDate(MONDAY.plusDays(2));
            service.updateShift(SHIFT_ID, request);

            assertThat(shift.getShiftDate()).isEqualTo(MONDAY.plusDays(2));
            assertThat(shift.getSourceTemplateId()).isEqualTo(TEMPLATE_ID);
            verify(policyValidator).validate(TENANT_ID, STAFF_ID, shift, SHIFT_ID);
        }

        /** DM-15: giờ check-in nằm trên bản ghi ca — đổi giờ ca sau đó là lệch dữ liệu chấm công. */
        @Test
        void shouldRejectEditingCheckedInShift() {
            Shift shift = assignedShift();
            shift.setCheckInAt(LocalDateTime.of(2026, 10, 5, 6, 2));
            givenOwnedShift(shift);

            UpdateShiftRequest request = new UpdateShiftRequest();
            request.setEndTime(LocalTime.of(15, 0));

            assertThatThrownBy(() -> service.updateShift(SHIFT_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("check-in");
            assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(14, 0));
            verifyNoInteractions(policyValidator);
            verify(shiftRepository, never()).save(any());
        }

        @Test
        void shouldRejectShiftOfAnotherLocation() {
            Shift shift = assignedShift();
            shift.setLocationId(OTHER_LOCATION_ID);
            givenOwnedShift(shift);

            assertThatThrownBy(() -> service.updateShift(SHIFT_ID, new UpdateShiftRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Location khác");
            verify(shiftRepository, never()).save(any());
        }

        /** Cách ly Tenant: ca của Tenant khác coi như không tồn tại. */
        @Test
        void shouldNotFindShiftOfAnotherTenant() {
            when(shiftRepository.findByIdAndTenantId(SHIFT_ID, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateShift(SHIFT_ID, new UpdateShiftRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Gán / gỡ người — DM-03, BR-SCH-24 ───────────────────────────────────

    @Nested
    class AssignAndUnassign {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        @Test
        void shouldAssignStaffAndClearPreviousUnassignReason() {
            Shift shift = unassignedShift();
            shift.setUnassignedReason(UnassignedReason.TERMINATION);
            shift.setUnassignedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
            givenOwnedShift(shift);
            givenStaff(activeStaff());
            givenSaveReturnsArgument();

            service.assignStaff(SHIFT_ID, STAFF_ID);

            assertThat(shift.getStaffId()).isEqualTo(STAFF_ID);
            assertThat(shift.getUnassignedReason()).isNull();
            assertThat(shift.getUnassignedAt()).isNull();
            verify(policyValidator).validate(TENANT_ID, STAFF_ID, shift, SHIFT_ID);
        }

        @Test
        void shouldRejectAssigningAShiftThatAlreadyHasSomeone() {
            givenOwnedShift(assignedShift());

            assertThatThrownBy(() -> service.assignStaff(SHIFT_ID, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã có người");
            verifyNoInteractions(policyValidator);
        }

        @Test
        void shouldNotAssignWhenPolicyIsViolated() {
            Shift shift = unassignedShift();
            givenOwnedShift(shift);
            givenStaff(activeStaff());
            doThrow(new BusinessException("Không đủ thời gian nghỉ giữa 2 ca"))
                .when(policyValidator).validate(TENANT_ID, STAFF_ID, shift, SHIFT_ID);

            assertThatThrownBy(() -> service.assignStaff(SHIFT_ID, STAFF_ID))
                .isInstanceOf(BusinessException.class);
            assertThat(shift.getStaffId()).isNull();
            verify(shiftRepository, never()).save(any());
        }

        /** BR-SCH-24: gỡ người là đưa ca về "chưa phân công" kèm lý do — không xóa bản ghi. */
        @Test
        void shouldUnassignWithReasonAndKeepTheShift() {
            Shift shift = assignedShift();
            givenOwnedShift(shift);
            givenSaveReturnsArgument();

            service.unassignStaff(SHIFT_ID, UnassignedReason.MANAGER_MANUAL);

            assertThat(shift.getStaffId()).isNull();
            assertThat(shift.getUnassignedReason()).isEqualTo(UnassignedReason.MANAGER_MANUAL);
            assertThat(shift.getUnassignedAt()).isNotNull();
            verify(shiftRepository, never()).delete(any());
        }

        @Test
        void shouldRejectUnassigningCheckedInShift() {
            Shift shift = assignedShift();
            shift.setCheckInAt(LocalDateTime.of(2026, 10, 5, 6, 2));
            givenOwnedShift(shift);

            assertThatThrownBy(() -> service.unassignStaff(SHIFT_ID, UnassignedReason.MANAGER_MANUAL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("check-in");
            assertThat(shift.getStaffId()).isEqualTo(STAFF_ID);
            verify(shiftRepository, never()).save(any());
        }

        @Test
        void shouldRejectUnassigningEmptyShift() {
            givenOwnedShift(unassignedShift());

            assertThatThrownBy(() -> service.unassignStaff(SHIFT_ID, UnassignedReason.MANAGER_MANUAL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa phân công");
        }
    }

    // ── Xóa ca ─────────────────────────────────────────────────────────────

    @Nested
    class DeleteShift {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        @Test
        void shouldDeleteShiftNotYetCheckedIn() {
            Shift shift = assignedShift();
            givenOwnedShift(shift);

            service.deleteShift(SHIFT_ID);

            verify(shiftRepository).delete(shift);
        }

        @Test
        void shouldRejectDeletingCheckedInShift() {
            Shift shift = assignedShift();
            shift.setCheckInAt(LocalDateTime.of(2026, 10, 5, 6, 2));
            givenOwnedShift(shift);

            assertThatThrownBy(() -> service.deleteShift(SHIFT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("check-in");
            verify(shiftRepository, never()).delete(any());
        }
    }

    // ── Tiện ích ───────────────────────────────────────────────────────────

    private void givenLocationInTenant() {
        when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(
            Location.builder().id(LOCATION_ID).tenantId(TENANT_ID).name("Khách sạn Test")
                .status(LocationStatus.OPERATIONAL).build()));
    }

    private void givenTemplate(ShiftTemplate template) {
        when(shiftTemplateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(template));
    }

    private void givenStaff(User staff) {
        when(userRepository.findByIdAndTenantId(STAFF_ID, TENANT_ID)).thenReturn(Optional.of(staff));
    }

    private void givenOwnedShift(Shift shift) {
        when(shiftRepository.findByIdAndTenantId(SHIFT_ID, TENANT_ID)).thenReturn(Optional.of(shift));
    }

    private void givenSaveReturnsArgument() {
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Shift captureSaved() {
        ArgumentCaptor<Shift> captor = ArgumentCaptor.forClass(Shift.class);
        verify(shiftRepository).save(captor.capture());
        return captor.getValue();
    }

    private static CreateShiftRequest request(UUID staffId, UUID templateId, String start, String end) {
        CreateShiftRequest request = new CreateShiftRequest();
        request.setLocationId(LOCATION_ID);
        request.setStaffId(staffId);
        request.setShiftDate(MONDAY);
        request.setSourceTemplateId(templateId);
        request.setStartTime(start == null ? null : LocalTime.parse(start));
        request.setEndTime(end == null ? null : LocalTime.parse(end));
        return request;
    }

    private static ShiftTemplate nightTemplate() {
        return ShiftTemplate.builder()
            .id(TEMPLATE_ID)
            .tenantId(TENANT_ID)
            .name("Ca đêm")
            .startTime(LocalTime.of(22, 0))
            .endTime(LocalTime.of(6, 0))
            .build();
    }

    private static User activeStaff() {
        return User.builder()
            .id(STAFF_ID)
            .tenantId(TENANT_ID)
            .role(Role.STAFF)
            .status(UserStatus.ACTIVE)
            .fullName("Nguyễn Thị Lan")
            .locationId(LOCATION_ID)
            .build();
    }

    /** Ca sáng 06:00–14:00 Thứ Hai, đã có người. */
    private static Shift assignedShift() {
        return Shift.builder()
            .id(SHIFT_ID)
            .tenantId(TENANT_ID)
            .locationId(LOCATION_ID)
            .staffId(STAFF_ID)
            .shiftDate(MONDAY)
            .startTime(LocalTime.of(6, 0))
            .endTime(LocalTime.of(14, 0))
            .overnight(false)
            .durationHours(new BigDecimal("8.00"))
            .build();
    }

    private static Shift unassignedShift() {
        Shift shift = assignedShift();
        shift.setStaffId(null);
        return shift;
    }
}
