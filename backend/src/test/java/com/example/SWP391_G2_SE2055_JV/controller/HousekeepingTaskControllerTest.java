package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.AssignTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AssignableStaffResponse;
import com.example.SWP391_G2_SE2055_JV.dto.HousekeepingTaskResponse;
import com.example.SWP391_G2_SE2055_JV.dto.InspectTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.InspectionRecordResponse;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.InspectionResult;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.service.HousekeepingService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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
 * F5 — {@code /housekeeping/tasks/**}. Nạp SecurityConfig thật ({@link SecuredWebMvcTest}) nên
 * kiểm được CẢ HAI tầng phân quyền và cả hai trục {@code ROLE_*} / {@code POSITION_*}:
 * hoàn thành việc dọn đòi quyền Dọn dẹp Manager đã tick cho người đó ({@code POSITION_HOUSEKEEPING}),
 * không theo role (BR-PERM-05).
 *
 * <p>{@link HousekeepingService} được mock — luật nghiệp vụ đã test ở {@code HousekeepingServiceTest}.
 */
@SecuredWebMvcTest(HousekeepingTaskController.class)
class HousekeepingTaskControllerTest {

    private static final UUID TASK_ID  = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final UUID ROOM_ID  = UUID.randomUUID();

    @Autowired MockMvc mockMvc;

    @MockBean HousekeepingService housekeepingService;

    // ── Xem lịch dọn: cả 4 vai trò, phạm vi do service lọc ──────────────────

    @Nested
    class GetTasks {

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_DIRECTOR", "ROLE_MANAGER", "ROLE_STAFF,POSITION_HOUSEKEEPING",
                                "ROLE_STAFF,POSITION_RECEPTION"})
        void shouldListTasksForEveryTenantRole(String authorities) throws Exception {
            when(housekeepingService.getTasks(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleTask()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/housekeeping/tasks").with(authorities(authorities)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roomNumber").value("201"))
                .andExpect(jsonPath("$.content[0].status").value("UNASSIGNED"));
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(get("/housekeeping/tasks")).andExpect(status().isUnauthorized());
            verifyNoInteractions(housekeepingService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldPassParsedFiltersToService() throws Exception {
            when(housekeepingService.getTasks(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/housekeeping/tasks")
                    .param("status", "UNASSIGNED")
                    .param("taskType", "CHECKOUT")
                    .param("assignedDate", "2026-09-23"))
                .andExpect(status().isOk());

            verify(housekeepingService).getTasks(eq(HousekeepingTaskStatus.UNASSIGNED),
                eq(HousekeepingTaskType.CHECKOUT), eq(LocalDate.of(2026, 9, 23)), isNull(), isNull(), any());
        }

        /** Lịch dọn theo tuần: lọc ngày làm theo khoảng from–to. */
        @Test
        @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_HOUSEKEEPING"})
        void shouldPassWeekRangeToService() throws Exception {
            when(housekeepingService.getTasks(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/housekeeping/tasks").param("from", "2026-10-05").param("to", "2026-10-11"))
                .andExpect(status().isOk());

            verify(housekeepingService).getTasks(isNull(), isNull(), isNull(),
                eq(LocalDate.of(2026, 10, 5)), eq(LocalDate.of(2026, 10, 11)), any());
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnNotFoundWhenTaskOutOfScope() throws Exception {
            when(housekeepingService.getTask(TASK_ID))
                .thenThrow(new ResourceNotFoundException("HousekeepingTask", "id", TASK_ID));

            mockMvc.perform(get("/housekeeping/tasks/{id}", TASK_ID)).andExpect(status().isNotFound());
        }
    }

    // ── S-10: ứng viên nhận task (Q10) ──────────────────────────────────────

    @Nested
    class AssignableStaff {

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldListAssignableStaffForManager() throws Exception {
            when(housekeepingService.getAssignableStaff(LocalDate.of(2026, 9, 23)))
                .thenReturn(List.of(AssignableStaffResponse.builder()
                    .id(STAFF_ID).fullName("Phạm Dọn Dẹp").phone("0900000000").build()));

            mockMvc.perform(get("/housekeeping/tasks/assignable-staff").param("date", "2026-09-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Phạm Dọn Dẹp"))
                .andExpect(jsonPath("$[0].phone").value("0900000000"));
        }

        /** Path cố định phải thắng {@code /{id}} — nếu không, "assignable-staff" sẽ bị hiểu là một UUID. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldNotBeSwallowedByTaskIdRoute() throws Exception {
            when(housekeepingService.getAssignableStaff(any())).thenReturn(List.of());

            mockMvc.perform(get("/housekeeping/tasks/assignable-staff").param("date", "2026-09-23"))
                .andExpect(status().isOk());

            verify(housekeepingService).getAssignableStaff(LocalDate.of(2026, 9, 23));
            verify(housekeepingService, never()).getTask(any());
        }

        /** Danh sách nhân sự không dành cho nhân viên — chặn ở {@code @PreAuthorize}. */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_STAFF,POSITION_HOUSEKEEPING", "ROLE_DIRECTOR"})
        void shouldForbidNonManager(String authorities) throws Exception {
            mockMvc.perform(get("/housekeeping/tasks/assignable-staff").param("date", "2026-09-23")
                    .with(authorities(authorities)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(housekeepingService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnBadRequestWhenDateMissing() throws Exception {
            mockMvc.perform(get("/housekeeping/tasks/assignable-staff")).andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnBadRequestWhenDateMalformed() throws Exception {
            mockMvc.perform(get("/housekeeping/tasks/assignable-staff").param("date", "23-09-2026"))
                .andExpect(status().isBadRequest());
        }
    }

    // ── Phân công / gỡ người: chỉ Manager ───────────────────────────────────

    @Nested
    class AssignAndUnassign {

        private static final String ASSIGN_BODY = """
            {"staffIds": ["%s"], "assignedDate": "2026-09-23"}
            """;

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldAssignTaskForManager() throws Exception {
            when(housekeepingService.assignTask(eq(TASK_ID), any())).thenReturn(sampleTask());

            mockMvc.perform(patch("/housekeeping/tasks/{id}/assign", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content(ASSIGN_BODY.formatted(STAFF_ID)))
                .andExpect(status().isOk());

            ArgumentCaptor<AssignTaskRequest> request = ArgumentCaptor.forClass(AssignTaskRequest.class);
            verify(housekeepingService).assignTask(eq(TASK_ID), request.capture());
            assertThat(request.getValue().getStaffIds()).containsExactly(STAFF_ID);
            assertThat(request.getValue().getAssignedDate()).isEqualTo(LocalDate.of(2026, 9, 23));
        }

        /** Một phòng nhiều người dọn: một lần gửi được cả nhóm. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldPassWholeTeamToService() throws Exception {
            UUID second = UUID.randomUUID();
            when(housekeepingService.assignTask(eq(TASK_ID), any())).thenReturn(sampleTask());

            mockMvc.perform(patch("/housekeeping/tasks/{id}/assign", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("""
                        {"staffIds": ["%s", "%s"], "assignedDate": "2026-09-23"}
                        """.formatted(STAFF_ID, second)))
                .andExpect(status().isOk());

            ArgumentCaptor<AssignTaskRequest> request = ArgumentCaptor.forClass(AssignTaskRequest.class);
            verify(housekeepingService).assignTask(eq(TASK_ID), request.capture());
            assertThat(request.getValue().getStaffIds()).containsExactly(STAFF_ID, second);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_STAFF,POSITION_HOUSEKEEPING", "ROLE_DIRECTOR"})
        void shouldForbidNonManagerFromAssigning(String authorities) throws Exception {
            mockMvc.perform(patch("/housekeeping/tasks/{id}/assign", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content(ASSIGN_BODY.formatted(STAFF_ID))
                    .with(authorities(authorities)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(housekeepingService);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "{\"assignedDate\": \"2026-09-23\"}",
            "{\"staffIds\": [], \"assignedDate\": \"2026-09-23\"}"
        })
        @WithMockUser(roles = "MANAGER")
        void shouldReturnUnprocessableWhenNobodyChosen(String body) throws Exception {
            mockMvc.perform(patch("/housekeeping/tasks/{id}/assign", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("staffIds"));
            verifyNoInteractions(housekeepingService);
        }

        /** BR-HK-03 / Q5: điều kiện gán hỏng thì service ném 400 kèm câu nêu rõ lý do. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnBadRequestWithRuleWhenStaffHasNoShift() throws Exception {
            when(housekeepingService.assignTask(eq(TASK_ID), any())).thenThrow(new BusinessException(
                "Nhân viên không có ca làm việc ngày 2026-09-23 — chỉ gán task cho người có ca trong ngày."));

            mockMvc.perform(patch("/housekeeping/tasks/{id}/assign", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content(ASSIGN_BODY.formatted(STAFF_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                    "Nhân viên không có ca làm việc ngày 2026-09-23 — chỉ gán task cho người có ca trong ngày."));
        }

        /** Q8: task ở Location khác trả 404, không phải 400. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnNotFoundWhenTaskInAnotherLocation() throws Exception {
            when(housekeepingService.unassignTask(TASK_ID, null))
                .thenThrow(new ResourceNotFoundException("Location", "id", UUID.randomUUID()));

            mockMvc.perform(patch("/housekeeping/tasks/{id}/unassign", TASK_ID))
                .andExpect(status().isNotFound());
        }

        /** Không gửi staffId = gỡ cả nhóm. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldUnassignWholeTeamForManager() throws Exception {
            when(housekeepingService.unassignTask(TASK_ID, null)).thenReturn(sampleTask());

            mockMvc.perform(patch("/housekeeping/tasks/{id}/unassign", TASK_ID)).andExpect(status().isOk());

            verify(housekeepingService).unassignTask(TASK_ID, null);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldUnassignOnePersonWhenStaffIdGiven() throws Exception {
            when(housekeepingService.unassignTask(TASK_ID, STAFF_ID)).thenReturn(sampleTask());

            mockMvc.perform(patch("/housekeeping/tasks/{id}/unassign", TASK_ID).param("staffId", STAFF_ID.toString()))
                .andExpect(status().isOk());

            verify(housekeepingService).unassignTask(TASK_ID, STAFF_ID);
        }
    }

    // ── Hoàn thành: trục Position, và Q7 ────────────────────────────────────

    @Nested
    class CompleteTask {

        @Test
        @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_HOUSEKEEPING"})
        void shouldCompleteTaskForHousekeepingStaff() throws Exception {
            HousekeepingTaskResponse done = sampleTask();
            done.setStatus(HousekeepingTaskStatus.PENDING_INSPECTION);
            when(housekeepingService.completeTask(TASK_ID)).thenReturn(done);

            mockMvc.perform(patch("/housekeeping/tasks/{id}/complete", TASK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_INSPECTION"));
        }

        /** Nhân viên chỉ có quyền Lễ tân (không có quyền Dọn dẹp) — chặn ngay ở tầng URL. */
        @Test
        @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_RECEPTION"})
        void shouldForbidReceptionAtUrlLevel() throws Exception {
            mockMvc.perform(patch("/housekeeping/tasks/{id}/complete", TASK_ID))
                .andExpect(status().isForbidden());
            verifyNoInteractions(housekeepingService);
        }

        /**
         * Q7: Manager KHÔNG hoàn thành thay nhân viên. Tầng URL vẫn cho Manager qua (rule cũ của
         * SecurityConfig, file dùng chung), nên {@code @PreAuthorize} mới là chốt chặn.
         */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldForbidManagerAtMethodLevel() throws Exception {
            mockMvc.perform(patch("/housekeeping/tasks/{id}/complete", TASK_ID))
                .andExpect(status().isForbidden());
            verifyNoInteractions(housekeepingService);
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(patch("/housekeeping/tasks/{id}/complete", TASK_ID))
                .andExpect(status().isUnauthorized());
            verifyNoInteractions(housekeepingService);
        }

        /** BR-PERM-05: người ngoài nhóm dọn thì service chặn → 400. */
        @Test
        @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_HOUSEKEEPING"})
        void shouldReturnBadRequestWhenNotInTeam() throws Exception {
            when(housekeepingService.completeTask(TASK_ID))
                .thenThrow(new BusinessException("Chỉ người trong nhóm dọn mới bấm hoàn thành task này."));

            mockMvc.perform(patch("/housekeeping/tasks/{id}/complete", TASK_ID))
                .andExpect(status().isBadRequest());
        }
    }

    // ── F6: nghiệm thu phòng — chỉ Manager ─────────────────────

    @Nested
    class Inspection {

        private static final String PASS_BODY = """
            {"result": "PASS"}
            """;

        /** Mỗi lần kiểm tra sinh một biên bản mới → 201, không phải 200. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnCreatedWhenManagerInspects() throws Exception {
            when(housekeepingService.inspectTask(eq(TASK_ID), any())).thenReturn(sampleInspection());

            mockMvc.perform(post("/housekeeping/tasks/{id}/inspection", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content(PASS_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("PASS"))
                .andExpect(jsonPath("$.nextTaskId").doesNotExist());
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldPassResultAndReasonToService() throws Exception {
            when(housekeepingService.inspectTask(eq(TASK_ID), any())).thenReturn(sampleInspection());

            mockMvc.perform(post("/housekeeping/tasks/{id}/inspection", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("""
                        {"result": "FAIL", "reason": "Nhà tắm còn bẩn"}
                        """))
                .andExpect(status().isCreated());

            ArgumentCaptor<InspectTaskRequest> request = ArgumentCaptor.forClass(InspectTaskRequest.class);
            verify(housekeepingService).inspectTask(eq(TASK_ID), request.capture());
            assertThat(request.getValue().getResult()).isEqualTo(InspectionResult.FAIL);
            assertThat(request.getValue().getReason()).isEqualTo("Nhà tắm còn bẩn");
        }

        /**
         * Nghiệm thu là việc của Quản lý (BR-ROOM-02): người dọn không tự duyệt phòng mình
         * vừa dọn. Chặn ngay ở tầng URL — POST /housekeeping/** chỉ dành cho Admin và Manager.
         */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_STAFF,POSITION_HOUSEKEEPING", "ROLE_STAFF,POSITION_RECEPTION",
                                "ROLE_DIRECTOR"})
        void shouldForbidNonManagerFromInspecting(String authorities) throws Exception {
            mockMvc.perform(post("/housekeeping/tasks/{id}/inspection", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content(PASS_BODY)
                    .with(authorities(authorities)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(housekeepingService);
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(post("/housekeeping/tasks/{id}/inspection", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content(PASS_BODY))
                .andExpect(status().isUnauthorized());
            verifyNoInteractions(housekeepingService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnUnprocessableWhenResultMissing() throws Exception {
            mockMvc.perform(post("/housekeeping/tasks/{id}/inspection", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("result"));
            verifyNoInteractions(housekeepingService);
        }

        /** Q12: enum sai trong body là JSON không đọc được → 400, không phải 500. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnBadRequestWhenResultIsNotAnEnumValue() throws Exception {
            mockMvc.perform(post("/housekeeping/tasks/{id}/inspection", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("""
                        {"result": "MAYBE"}
                        """))
                .andExpect(status().isBadRequest());
            verifyNoInteractions(housekeepingService);
        }

        /** BR-HK-08: thiếu lý do khi không đạt là luật có điều kiện → service chặn, trả 400. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnBadRequestWhenFailHasNoReason() throws Exception {
            when(housekeepingService.inspectTask(eq(TASK_ID), any()))
                .thenThrow(new BusinessException("Kiểm tra không đạt thì bắt buộc nhập lý do."));

            mockMvc.perform(post("/housekeeping/tasks/{id}/inspection", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("""
                        {"result": "FAIL"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Kiểm tra không đạt thì bắt buộc nhập lý do."));
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnNotFoundWhenTaskOutOfScope() throws Exception {
            when(housekeepingService.inspectTask(eq(TASK_ID), any()))
                .thenThrow(new ResourceNotFoundException("HousekeepingTask", "id", TASK_ID));

            mockMvc.perform(post("/housekeeping/tasks/{id}/inspection", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content(PASS_BODY))
                .andExpect(status().isNotFound());
        }

        /** Đọc biên bản: GET /housekeeping/** mở cho cả 4 vai trò, phạm vi do service lọc. */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_DIRECTOR", "ROLE_MANAGER", "ROLE_STAFF,POSITION_HOUSEKEEPING",
                                "ROLE_STAFF,POSITION_RECEPTION"})
        void shouldReadInspectionForEveryTenantRole(String authorities) throws Exception {
            when(housekeepingService.getInspection(TASK_ID)).thenReturn(sampleInspection());

            mockMvc.perform(get("/housekeeping/tasks/{id}/inspection", TASK_ID)
                    .with(authorities(authorities)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(TASK_ID.toString()));
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnNotFoundWhenTaskHasNoInspection() throws Exception {
            when(housekeepingService.getInspection(TASK_ID))
                .thenThrow(new ResourceNotFoundException("InspectionRecord", "taskId", TASK_ID));

            mockMvc.perform(get("/housekeeping/tasks/{id}/inspection", TASK_ID))
                .andExpect(status().isNotFound());
        }
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    /** Người đăng nhập mang đúng danh sách authority truyền vào — dùng cho test chạy theo tham số. */
    private static RequestPostProcessor authorities(String commaSeparated) {
        return user("someone@test.local").authorities(
            Arrays.stream(commaSeparated.split(","))
                .map(SimpleGrantedAuthority::new)
                .toList());
    }

    private static HousekeepingTaskResponse sampleTask() {
        return HousekeepingTaskResponse.builder()
            .id(TASK_ID)
            .roomId(ROOM_ID)
            .roomNumber("201")
            .floor("2")
            .taskType(HousekeepingTaskType.CHECKOUT)
            .status(HousekeepingTaskStatus.UNASSIGNED)
            .createdSource(TaskCreatedSource.CHECKOUT_AUTO)
            .build();
    }

    private static InspectionRecordResponse sampleInspection() {
        return InspectionRecordResponse.builder()
            .id(UUID.randomUUID())
            .taskId(TASK_ID)
            .roomId(ROOM_ID)
            .inspectorId(UUID.randomUUID())
            .result(InspectionResult.PASS)
            .inspectedAt(LocalDateTime.now())
            .build();
    }
}
