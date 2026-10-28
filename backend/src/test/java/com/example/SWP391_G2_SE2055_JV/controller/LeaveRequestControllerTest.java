package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveBalanceResponse;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveRequestResponse;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveType;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.service.LeaveRequestService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /scheduling/leave-requests/**} — phân quyền thật ({@link SecuredWebMvcTest}): Staff và Manager gửi
 * đơn của mình; chỉ Manager và Giám đốc duyệt (BR-SCH-08) — Giám sát vận hành là Staff nên bị chặn ngay ở tầng
 * URL (BR-PERM-07). {@link LeaveRequestService} được mock — luật nghiệp vụ test ở {@code LeaveRequestServiceTest}.
 */
@SecuredWebMvcTest(LeaveRequestController.class)
class LeaveRequestControllerTest {

    private static final UUID LEAVE_ID = UUID.randomUUID();

    private static final String BODY = """
        {"leaveType": "UNPAID", "fromDate": "2026-11-02", "toDate": "2026-11-03", "reason": "Đưa con đi khám"}
        """;

    @Autowired MockMvc mockMvc;

    @MockBean LeaveRequestService leaveRequestService;

    // ── Gửi đơn ─────────────────────────────────────────────────────────────

    @Nested
    class Create {

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_STAFF", "ROLE_STAFF,POSITION_SUPERVISION", "ROLE_MANAGER"})
        void shouldLetStaffAndManagersSubmitOwnLeave(String authorities) throws Exception {
            when(leaveRequestService.createLeaveRequest(any())).thenReturn(sample(LeaveRequestStatus.PENDING));

            mockMvc.perform(post("/scheduling/leave-requests").with(authorities(authorities))
                    .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.days").value(2));
        }

        /** Giám đốc không có cấp trên duyệt đơn; Admin đứng ngoài Tenant. */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_DIRECTOR", "ROLE_PLATFORM_ADMIN"})
        void shouldNotLetDirectorOrAdminSubmit(String authorities) throws Exception {
            mockMvc.perform(post("/scheduling/leave-requests").with(authorities(authorities))
                    .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
            verifyNoInteractions(leaveRequestService);
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(post("/scheduling/leave-requests").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
            verifyNoInteractions(leaveRequestService);
        }

        /** BR-SCH-16: lý do bắt buộc. */
        @Test
        @WithMockUser(roles = "STAFF")
        void shouldRequireReason() throws Exception {
            mockMvc.perform(post("/scheduling/leave-requests").contentType(MediaType.APPLICATION_JSON).content("""
                    {"leaveType": "UNPAID", "fromDate": "2026-11-02", "toDate": "2026-11-02", "reason": "  "}
                    """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
            verifyNoInteractions(leaveRequestService);
        }

        @Test
        @WithMockUser(roles = "STAFF")
        void shouldReturnBadRequestForUnknownLeaveType() throws Exception {
            mockMvc.perform(post("/scheduling/leave-requests").contentType(MediaType.APPLICATION_JSON).content("""
                    {"leaveType": "SICK", "fromDate": "2026-11-02", "toDate": "2026-11-02", "reason": "Ốm"}
                    """))
                .andExpect(status().isBadRequest());
            verifyNoInteractions(leaveRequestService);
        }

        /** Luật theo ngày (báo trước, quỹ phép…) lỗi thì 400 kèm câu nêu rõ lý do. */
        @Test
        @WithMockUser(roles = "STAFF")
        void shouldReturnBadRequestWithRuleMessage() throws Exception {
            when(leaveRequestService.createLeaveRequest(any()))
                .thenThrow(new BusinessException("Phép năm phải gửi trước ít nhất 3 ngày"));

            mockMvc.perform(post("/scheduling/leave-requests").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phép năm phải gửi trước ít nhất 3 ngày"));
        }
    }

    // ── Đơn của chính mình ──────────────────────────────────────────────────

    @Nested
    class Mine {

        @Test
        @WithMockUser(roles = "STAFF")
        void shouldListOwnRequestsFilteredByStatus() throws Exception {
            when(leaveRequestService.getMyLeaveRequests(any(), any()))
                .thenReturn(new PageImpl<>(List.of(sample(LeaveRequestStatus.PENDING))));

            mockMvc.perform(get("/scheduling/leave-requests/mine").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].leaveType").value("UNPAID"));

            verify(leaveRequestService).getMyLeaveRequests(eq(LeaveRequestStatus.PENDING), any());
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnOwnBalance() throws Exception {
            when(leaveRequestService.getMyBalance()).thenReturn(LeaveBalanceResponse.builder()
                .year(2026).daysPerYear(12).entitledDays(9).remainingDays(7).build());

            mockMvc.perform(get("/scheduling/leave-requests/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingDays").value(7));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        void shouldCancelOwnRequest() throws Exception {
            when(leaveRequestService.cancelLeaveRequest(LEAVE_ID)).thenReturn(sample(LeaveRequestStatus.CANCELLED));

            mockMvc.perform(patch("/scheduling/leave-requests/{id}/cancel", LEAVE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldNotGiveDirectorOwnRequestList() throws Exception {
            mockMvc.perform(get("/scheduling/leave-requests/mine")).andExpect(status().isForbidden());
            verifyNoInteractions(leaveRequestService);
        }
    }

    // ── Duyệt — BR-SCH-08, BR-PERM-07 ───────────────────────────────────────

    @Nested
    class Review {

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_MANAGER", "ROLE_DIRECTOR"})
        void shouldShowQueueToReviewers(String authorities) throws Exception {
            when(leaveRequestService.getReviewQueue(any(), any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/scheduling/leave-requests/review").param("status", "PENDING")
                    .with(authorities(authorities)))
                .andExpect(status().isOk());

            verify(leaveRequestService).getReviewQueue(eq(LeaveRequestStatus.PENDING), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_STAFF", "ROLE_STAFF,POSITION_SUPERVISION"})
        void shouldHideQueueFromStaffAndSupervisors(String authorities) throws Exception {
            mockMvc.perform(get("/scheduling/leave-requests/review").with(authorities(authorities)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(leaveRequestService);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_MANAGER", "ROLE_DIRECTOR"})
        void shouldLetReviewersApprove(String authorities) throws Exception {
            when(leaveRequestService.approveLeaveRequest(LEAVE_ID)).thenReturn(LeaveApprovalResponse.builder()
                .request(sample(LeaveRequestStatus.APPROVED))
                .releasedShifts(List.of()).keptShifts(List.of())
                .receptionWarnings(List.of("Ca chiều ngày 03/11/2026"))
                .build());

            mockMvc.perform(patch("/scheduling/leave-requests/{id}/approve", LEAVE_ID).with(authorities(authorities)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("APPROVED"))
                .andExpect(jsonPath("$.receptionWarnings[0]").value("Ca chiều ngày 03/11/2026"));
        }

        /** Giám sát vận hành không duyệt đơn (BR-PERM-07) — chặn ở tầng URL, service không được gọi. */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_STAFF", "ROLE_STAFF,POSITION_SUPERVISION", "ROLE_STAFF,POSITION_RECEPTION"})
        void shouldForbidStaffAndSupervisorsFromDeciding(String authorities) throws Exception {
            mockMvc.perform(patch("/scheduling/leave-requests/{id}/approve", LEAVE_ID).with(authorities(authorities)))
                .andExpect(status().isForbidden());
            mockMvc.perform(patch("/scheduling/leave-requests/{id}/reject", LEAVE_ID).with(authorities(authorities)))
                .andExpect(status().isForbidden());
            mockMvc.perform(get("/scheduling/leave-requests/{id}/approval-preview", LEAVE_ID)
                    .with(authorities(authorities)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(leaveRequestService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldRejectForManager() throws Exception {
            when(leaveRequestService.rejectLeaveRequest(LEAVE_ID)).thenReturn(sample(LeaveRequestStatus.REJECTED));

            mockMvc.perform(patch("/scheduling/leave-requests/{id}/reject", LEAVE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        }

        /** Đơn ngoài phạm vi duyệt (khách sạn khác, đơn của chính mình) trả 404. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnNotFoundOutOfScope() throws Exception {
            when(leaveRequestService.previewApproval(LEAVE_ID))
                .thenThrow(new ResourceNotFoundException("LeaveRequest", "id", LEAVE_ID));

            mockMvc.perform(get("/scheduling/leave-requests/{id}/approval-preview", LEAVE_ID))
                .andExpect(status().isNotFound());
        }
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    private static RequestPostProcessor authorities(String commaSeparated) {
        return user("someone@test.local").authorities(
            Arrays.stream(commaSeparated.split(","))
                .map(SimpleGrantedAuthority::new)
                .toList());
    }

    private static LeaveRequestResponse sample(LeaveRequestStatus status) {
        return LeaveRequestResponse.builder()
            .id(LEAVE_ID).leaveType(LeaveType.UNPAID)
            .fromDate(LocalDate.of(2026, 11, 2)).toDate(LocalDate.of(2026, 11, 3)).days(2)
            .reason("Đưa con đi khám").status(status)
            .build();
    }
}
