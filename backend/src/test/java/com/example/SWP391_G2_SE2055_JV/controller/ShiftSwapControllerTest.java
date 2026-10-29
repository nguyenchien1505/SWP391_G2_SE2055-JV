package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftSwapRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftSwapRequestResponse;
import com.example.SWP391_G2_SE2055_JV.dto.SwapCandidateResponse;
import com.example.SWP391_G2_SE2055_JV.enums.SwapRequestStatus;
import com.example.SWP391_G2_SE2055_JV.exception.SwapExpiredException;
import com.example.SWP391_G2_SE2055_JV.service.ShiftSwapService;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
 * {@code /scheduling/shift-swap-requests/**} — nạp SecurityConfig thật ({@link SecuredWebMvcTest}):
 * nhân viên gửi và trả lời yêu cầu đổi ca, Manager duyệt (BR-SCH-08, 09). Ai được làm gì trên
 * từng yêu cầu do {@link ShiftSwapService} kiểm, đã test ở {@code ShiftSwapServiceTest}.
 */
@SecuredWebMvcTest(ShiftSwapController.class)
class ShiftSwapControllerTest {

    private static final String BASE = "/scheduling/shift-swap-requests";

    private static final UUID REQUEST_ID    = UUID.randomUUID();
    private static final UUID SHIFT_ID      = UUID.randomUUID();
    private static final UUID PEER_SHIFT_ID = UUID.randomUUID();

    @Autowired MockMvc mockMvc;

    @MockBean ShiftSwapService shiftSwapService;

    // ── Chọn ca để đổi ─────────────────────────────────────────────────────

    @Nested
    class Candidates {

        @Test
        @WithMockUser(roles = "STAFF")
        void shouldListCandidatesForStaff() throws Exception {
            when(shiftSwapService.getCandidates(SHIFT_ID))
                .thenReturn(List.of(SwapCandidateResponse.builder().fullName("Lê Bình").build()));

            mockMvc.perform(get(BASE + "/candidates").param("shiftId", SHIFT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Lê Bình"));
        }

        /** BR-SCH-08: Manager không có ca nên không đổi ca; Giám đốc cũng vậy. */
        @ParameterizedTest
        @ValueSource(strings = {"MANAGER", "DIRECTOR"})
        void shouldForbidNonStaff(String role) throws Exception {
            mockMvc.perform(get(BASE + "/candidates").param("shiftId", SHIFT_ID.toString()).with(as(role)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(shiftSwapService);
        }
    }

    // ── Danh sách ──────────────────────────────────────────────────────────

    @Nested
    class ListRequests {

        @Test
        @WithMockUser(roles = "STAFF")
        void shouldPassBoxAndStatusesNewestFirst() throws Exception {
            when(shiftSwapService.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(sample())));

            mockMvc.perform(get(BASE).param("box", "RECEIVED").param("status", "PENDING_PEER", "PENDING_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_PEER"));

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(shiftSwapService).list(eq(ShiftSwapService.Box.RECEIVED),
                eq(List.of(SwapRequestStatus.PENDING_PEER, SwapRequestStatus.PENDING_MANAGER)), pageable.capture());
            assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
            assertThat(pageable.getValue().getSort().getOrderFor("createdAt"))
                .isNotNull()
                .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldLetManagerReadLocationBox() throws Exception {
            when(shiftSwapService.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get(BASE).param("box", "LOCATION")).andExpect(status().isOk());

            verify(shiftSwapService).list(eq(ShiftSwapService.Box.LOCATION), isNull(), any());
        }

        /** Tên hộp viết hoa đúng tên enum; thiếu hay sai thì 400, không gọi service. */
        @Test
        @WithMockUser(roles = "STAFF")
        void shouldRejectMissingOrUnknownBox() throws Exception {
            mockMvc.perform(get(BASE)).andExpect(status().isBadRequest());
            mockMvc.perform(get(BASE).param("box", "sent")).andExpect(status().isBadRequest());
            verifyNoInteractions(shiftSwapService);
        }

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldForbidDirector() throws Exception {
            mockMvc.perform(get(BASE).param("box", "LOCATION")).andExpect(status().isForbidden());
            verifyNoInteractions(shiftSwapService);
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(get(BASE).param("box", "SENT")).andExpect(status().isUnauthorized());
            verifyNoInteractions(shiftSwapService);
        }
    }

    // ── Gửi yêu cầu ────────────────────────────────────────────────────────

    @Nested
    class Send {

        private static final String BODY = """
            {"requesterShiftId": "%s", "peerShiftId": "%s"}
            """;

        @Test
        @WithMockUser(roles = "STAFF")
        void shouldCreateRequestForStaff() throws Exception {
            when(shiftSwapService.create(any())).thenReturn(sample());

            mockMvc.perform(post(BASE)
                    .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(SHIFT_ID, PEER_SHIFT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PEER"));

            ArgumentCaptor<CreateShiftSwapRequest> request = ArgumentCaptor.forClass(CreateShiftSwapRequest.class);
            verify(shiftSwapService).create(request.capture());
            assertThat(request.getValue().getRequesterShiftId()).isEqualTo(SHIFT_ID);
            assertThat(request.getValue().getPeerShiftId()).isEqualTo(PEER_SHIFT_ID);
        }

        @Test
        @WithMockUser(roles = "STAFF")
        void shouldRequireBothShifts() throws Exception {
            mockMvc.perform(post(BASE)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"requesterShiftId\": \"%s\"}".formatted(SHIFT_ID)))
                .andExpect(status().isUnprocessableEntity());
            verifyNoInteractions(shiftSwapService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldForbidManager() throws Exception {
            mockMvc.perform(post(BASE)
                    .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(SHIFT_ID, PEER_SHIFT_ID)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(shiftSwapService);
        }
    }

    // ── Nhân viên trả lời / hủy ────────────────────────────────────────────

    @Nested
    class StaffActions {

        @Test
        @WithMockUser(roles = "STAFF")
        void shouldLetStaffAcceptDeclineAndCancel() throws Exception {
            when(shiftSwapService.accept(REQUEST_ID)).thenReturn(sample());
            when(shiftSwapService.decline(REQUEST_ID)).thenReturn(sample());
            when(shiftSwapService.cancel(REQUEST_ID)).thenReturn(sample());

            mockMvc.perform(patch(BASE + "/{id}/accept", REQUEST_ID)).andExpect(status().isOk());
            mockMvc.perform(patch(BASE + "/{id}/decline", REQUEST_ID)).andExpect(status().isOk());
            mockMvc.perform(patch(BASE + "/{id}/cancel", REQUEST_ID)).andExpect(status().isOk());

            verify(shiftSwapService).accept(REQUEST_ID);
            verify(shiftSwapService).decline(REQUEST_ID);
            verify(shiftSwapService).cancel(REQUEST_ID);
        }

        /** Yêu cầu vừa tự hủy thì trả 400 kèm lý do để giao diện hiện nguyên văn. */
        @Test
        @WithMockUser(roles = "STAFF")
        void shouldReturnReasonWhenRequestJustExpired() throws Exception {
            when(shiftSwapService.accept(REQUEST_ID)).thenThrow(new SwapExpiredException(
                "Yêu cầu đổi ca đã tự hủy: đồng nghiệp không trả lời trong thời gian chờ."));

            mockMvc.perform(patch(BASE + "/{id}/accept", REQUEST_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                    "Yêu cầu đổi ca đã tự hủy: đồng nghiệp không trả lời trong thời gian chờ."));
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldForbidManagerFromAnsweringForStaff() throws Exception {
            mockMvc.perform(patch(BASE + "/{id}/accept", REQUEST_ID)).andExpect(status().isForbidden());
            mockMvc.perform(patch(BASE + "/{id}/decline", REQUEST_ID)).andExpect(status().isForbidden());
            mockMvc.perform(patch(BASE + "/{id}/cancel", REQUEST_ID)).andExpect(status().isForbidden());
            verifyNoInteractions(shiftSwapService);
        }
    }

    // ── Manager duyệt ──────────────────────────────────────────────────────

    @Nested
    class ManagerDecision {

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldLetManagerApproveAndReject() throws Exception {
            when(shiftSwapService.approve(REQUEST_ID)).thenReturn(sample());
            when(shiftSwapService.reject(REQUEST_ID)).thenReturn(sample());

            mockMvc.perform(patch(BASE + "/{id}/approve", REQUEST_ID)).andExpect(status().isOk());
            mockMvc.perform(patch(BASE + "/{id}/reject", REQUEST_ID)).andExpect(status().isOk());

            verify(shiftSwapService).approve(REQUEST_ID);
            verify(shiftSwapService).reject(REQUEST_ID);
        }

        /** BR-SCH-09: Manager duyệt sau khi đồng nghiệp đồng ý — nhân viên và Giám đốc không duyệt. */
        @ParameterizedTest
        @ValueSource(strings = {"STAFF", "DIRECTOR"})
        void shouldForbidNonManager(String role) throws Exception {
            mockMvc.perform(patch(BASE + "/{id}/approve", REQUEST_ID).with(as(role))).andExpect(status().isForbidden());
            mockMvc.perform(patch(BASE + "/{id}/reject", REQUEST_ID).with(as(role))).andExpect(status().isForbidden());
            verifyNoInteractions(shiftSwapService);
        }
    }

    // ── Tiện ích ───────────────────────────────────────────────────────────

    private static RequestPostProcessor as(String role) {
        return user("someone@test.local").roles(role);
    }

    private static ShiftSwapRequestResponse sample() {
        return ShiftSwapRequestResponse.builder()
            .id(REQUEST_ID)
            .status(SwapRequestStatus.PENDING_PEER)
            .build();
    }
}
