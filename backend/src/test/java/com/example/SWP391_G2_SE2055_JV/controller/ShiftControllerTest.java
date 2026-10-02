package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.service.ShiftService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /scheduling/shifts/**} — nạp SecurityConfig thật ({@link SecuredWebMvcTest}) nên kiểm được
 * cả rule URL lẫn {@code @PreAuthorize}: Manager xếp ca (BR-PERM-03), mọi vai trò trong Tenant xem
 * lịch, người lao động check-in/out (BR-PERM-04..06).
 *
 * <p>{@link ShiftService} được mock — luật nghiệp vụ đã test ở {@code ShiftServiceTest} và
 * {@code SchedulePolicyValidatorTest}.
 */
@SecuredWebMvcTest(ShiftController.class)
class ShiftControllerTest {

    private static final UUID SHIFT_ID    = UUID.randomUUID();
    private static final UUID STAFF_ID    = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();

    @Autowired MockMvc mockMvc;

    @MockBean ShiftService shiftService;

    // ── Xem lịch ───────────────────────────────────────────────────────────

    @Nested
    class GetShifts {

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldPassWeekRangeToService() throws Exception {
            when(shiftService.getShifts(any(), any(), any())).thenReturn(new PageImpl<>(List.of(sampleShift())));

            mockMvc.perform(get("/scheduling/shifts")
                    .param("from", "2026-10-05")
                    .param("to", "2026-10-11")
                    .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(SHIFT_ID.toString()))
                .andExpect(jsonPath("$.content[0].shiftDate").value("2026-10-05"));

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(shiftService).getShifts(eq(LocalDate.of(2026, 10, 5)), eq(LocalDate.of(2026, 10, 11)),
                pageable.capture());
            assertThat(pageable.getValue().getPageSize()).isEqualTo(500);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_DIRECTOR", "ROLE_MANAGER", "ROLE_STAFF", "ROLE_STAFF,POSITION_HOUSEKEEPING"})
        void shouldLetEveryTenantRoleReadSchedule(String authorities) throws Exception {
            when(shiftService.getShifts(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/scheduling/shifts").with(authorities(authorities)))
                .andExpect(status().isOk());
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(get("/scheduling/shifts")).andExpect(status().isUnauthorized());
            verifyNoInteractions(shiftService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnBadRequestWhenDateMalformed() throws Exception {
            mockMvc.perform(get("/scheduling/shifts").param("from", "05/10/2026").param("to", "2026-10-11"))
                .andExpect(status().isBadRequest());
            verifyNoInteractions(shiftService);
        }
    }

    // ── Xếp ca: chỉ Manager ────────────────────────────────────────────────

    @Nested
    class CreateShift {

        private static final String BODY = """
            {"locationId": "%s", "staffId": "%s", "shiftDate": "2026-10-05",
             "startTime": "22:00", "endTime": "06:00"}
            """;

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldCreateShiftForManager() throws Exception {
            when(shiftService.createShift(any())).thenReturn(sampleShift());

            mockMvc.perform(post("/scheduling/shifts")
                    .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(LOCATION_ID, STAFF_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overnight").value(true));

            ArgumentCaptor<CreateShiftRequest> request = ArgumentCaptor.forClass(CreateShiftRequest.class);
            verify(shiftService).createShift(request.capture());
            assertThat(request.getValue().getStartTime()).isEqualTo(LocalTime.of(22, 0));
            assertThat(request.getValue().getEndTime()).isEqualTo(LocalTime.of(6, 0));
            assertThat(request.getValue().getShiftDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        }

        /** Giám đốc chỉ đặt quy định và mẫu ca; xếp ca là việc của Manager (BR-PERM-02/03). */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_DIRECTOR", "ROLE_STAFF", "ROLE_STAFF,POSITION_RECEPTION"})
        void shouldForbidNonManager(String authorities) throws Exception {
            mockMvc.perform(post("/scheduling/shifts").with(authorities(authorities))
                    .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(LOCATION_ID, STAFF_ID)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(shiftService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldRequireLocationAndDate() throws Exception {
            mockMvc.perform(post("/scheduling/shifts")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"startTime\": \"06:00\"}"))
                .andExpect(status().isUnprocessableEntity());
            verifyNoInteractions(shiftService);
        }

        /** BR-SCH-02: vi phạm Policy trả 400 kèm câu báo để hộp thoại xếp ca hiện nguyên văn. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnPolicyViolationMessage() throws Exception {
            when(shiftService.createShift(any()))
                .thenThrow(new BusinessException("Vượt giờ làm tối đa/ngày: ngày 05/10/2026 sẽ có tổng 10 giờ, giới hạn 8 giờ."));

            mockMvc.perform(post("/scheduling/shifts")
                    .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(LOCATION_ID, STAFF_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                    "Vượt giờ làm tối đa/ngày: ngày 05/10/2026 sẽ có tổng 10 giờ, giới hạn 8 giờ."));
        }
    }

    // ── Gán / gỡ người, xóa ────────────────────────────────────────────────

    @Nested
    class AssignUnassignDelete {

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldAssignStaff() throws Exception {
            when(shiftService.assignStaff(SHIFT_ID, STAFF_ID)).thenReturn(sampleShift());

            mockMvc.perform(patch("/scheduling/shifts/{id}/assign", SHIFT_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"staffId\": \"%s\"}".formatted(STAFF_ID)))
                .andExpect(status().isOk());

            verify(shiftService).assignStaff(SHIFT_ID, STAFF_ID);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldRequireStaffIdToAssign() throws Exception {
            mockMvc.perform(patch("/scheduling/shifts/{id}/assign", SHIFT_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
            verifyNoInteractions(shiftService);
        }

        /** BR-SCH-24: Manager gỡ tay luôn mang lý do MANAGER_MANUAL — client không tự chọn lý do. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldUnassignWithManualReason() throws Exception {
            when(shiftService.unassignStaff(SHIFT_ID, UnassignedReason.MANAGER_MANUAL)).thenReturn(sampleShift());

            mockMvc.perform(patch("/scheduling/shifts/{id}/unassign", SHIFT_ID))
                .andExpect(status().isOk());

            verify(shiftService).unassignStaff(SHIFT_ID, UnassignedReason.MANAGER_MANUAL);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldDeleteShift() throws Exception {
            mockMvc.perform(delete("/scheduling/shifts/{id}", SHIFT_ID))
                .andExpect(status().isNoContent());

            verify(shiftService).deleteShift(SHIFT_ID);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_DIRECTOR", "ROLE_STAFF"})
        void shouldForbidNonManagerFromChangingShifts(String authorities) throws Exception {
            mockMvc.perform(patch("/scheduling/shifts/{id}/unassign", SHIFT_ID).with(authorities(authorities)))
                .andExpect(status().isForbidden());
            mockMvc.perform(delete("/scheduling/shifts/{id}", SHIFT_ID).with(authorities(authorities)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(shiftService);
        }
    }

    // ── Check-in / check-out: người lao động (BR-DASH-01) ───────────────────

    @Nested
    class Attendance {

        @Test
        void shouldLetStaffCheckIn() throws Exception {
            when(shiftService.checkIn(SHIFT_ID)).thenReturn(sampleShift());

            mockMvc.perform(post("/scheduling/shifts/{id}/check-in", SHIFT_ID).with(authorities("ROLE_STAFF")))
                .andExpect(status().isOk());

            verify(shiftService).checkIn(SHIFT_ID);
        }
    }

    // ── Tiện ích ───────────────────────────────────────────────────────────

    private static RequestPostProcessor authorities(String commaSeparated) {
        return user("someone@test.local").authorities(
            Arrays.stream(commaSeparated.split(","))
                .map(SimpleGrantedAuthority::new)
                .toList());
    }

    /** Ca đêm 22:00–06:00, tính trọn 8 giờ vào ngày bắt đầu (BR-SCH-03). */
    private static ShiftResponse sampleShift() {
        return ShiftResponse.builder()
            .id(SHIFT_ID)
            .locationId(LOCATION_ID)
            .staffId(STAFF_ID)
            .shiftDate(LocalDate.of(2026, 10, 5))
            .startTime(LocalTime.of(22, 0))
            .endTime(LocalTime.of(6, 0))
            .overnight(true)
            .durationHours(new BigDecimal("8.00"))
            .build();
    }
}
