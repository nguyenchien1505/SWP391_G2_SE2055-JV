package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.AssignTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AssignableStaffResponse;
import com.example.SWP391_G2_SE2055_JV.dto.HousekeepingTaskResponse;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * lịch dọn là chỗ duy nhất mà quyền đi theo Loại Position chứ không theo role (BR-PERM-05).
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
            when(housekeepingService.getTasks(any(), any(), any(), any()))
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
            when(housekeepingService.getTasks(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/housekeeping/tasks")
                    .param("status", "UNASSIGNED")
                    .param("taskType", "CHECKOUT")
                    .param("assignedDate", "2026-09-23"))
                .andExpect(status().isOk());

            verify(housekeepingService).getTasks(eq(HousekeepingTaskStatus.UNASSIGNED),
                eq(HousekeepingTaskType.CHECKOUT), eq(LocalDate.of(2026, 9, 23)), any());
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
            {"staffId": "%s", "assignedDate": "2026-09-23"}
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
            assertThat(request.getValue().getStaffId()).isEqualTo(STAFF_ID);
            assertThat(request.getValue().getAssignedDate()).isEqualTo(LocalDate.of(2026, 9, 23));
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

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnUnprocessableWhenStaffIdMissing() throws Exception {
            mockMvc.perform(patch("/housekeeping/tasks/{id}/assign", TASK_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("""
                        {"assignedDate": "2026-09-23"}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("staffId"));
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
            when(housekeepingService.unassignTask(TASK_ID))
                .thenThrow(new ResourceNotFoundException("Location", "id", UUID.randomUUID()));

            mockMvc.perform(patch("/housekeeping/tasks/{id}/unassign", TASK_ID))
                .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldUnassignTaskForManager() throws Exception {
            when(housekeepingService.unassignTask(TASK_ID)).thenReturn(sampleTask());

            mockMvc.perform(patch("/housekeeping/tasks/{id}/unassign", TASK_ID)).andExpect(status().isOk());
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

        /** Lễ tân có role STAFF nhưng sai Loại Position — chặn ngay ở tầng URL. */
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

        /** BR-PERM-05: người không được phân công thì service chặn → 400. */
        @Test
        @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_HOUSEKEEPING"})
        void shouldReturnBadRequestWhenNotTheAssignedStaff() throws Exception {
            when(housekeepingService.completeTask(TASK_ID))
                .thenThrow(new BusinessException("Chỉ nhân viên được phân công mới bấm hoàn thành task này."));

            mockMvc.perform(patch("/housekeeping/tasks/{id}/complete", TASK_ID))
                .andExpect(status().isBadRequest());
        }
    }

    // ── Tạo task dọn hằng ngày ──────────────────────────────────────────────

    @Nested
    class CreateStayoverTask {

        private static final String BODY = """
            {"roomId": "%s"}
            """;

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldCreateStayoverTaskForManager() throws Exception {
            when(housekeepingService.createStayoverTask(any())).thenReturn(sampleTask());

            mockMvc.perform(post("/housekeeping/tasks")
                    .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(ROOM_ID)))
                .andExpect(status().isCreated());
        }

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_STAFF,POSITION_HOUSEKEEPING", "ROLE_DIRECTOR"})
        void shouldForbidNonManager(String authorities) throws Exception {
            mockMvc.perform(post("/housekeeping/tasks")
                    .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(ROOM_ID))
                    .with(authorities(authorities)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(housekeepingService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnUnprocessableWhenRoomIdMissing() throws Exception {
            mockMvc.perform(post("/housekeeping/tasks")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("roomId"));
        }

        /** BR-HK-05: phòng không có khách thì không tạo được task dọn hằng ngày. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnBadRequestWhenRoomNotOccupied() throws Exception {
            when(housekeepingService.createStayoverTask(any()))
                .thenThrow(new BusinessException("Chỉ tạo task dọn hằng ngày cho phòng đang có khách."));

            mockMvc.perform(post("/housekeeping/tasks")
                    .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(ROOM_ID)))
                .andExpect(status().isBadRequest());
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
}
