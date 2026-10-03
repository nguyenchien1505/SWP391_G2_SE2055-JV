package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftTemplateRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftTemplateResponse;
import com.example.SWP391_G2_SE2055_JV.service.ShiftTemplateService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /scheduling/shift-templates} — BR-SCH-04, BR-SCH-22: Giám đốc quản lý danh mục mẫu ca cấp
 * Tenant; Manager chỉ đọc để chọn khi xếp ca.
 */
@SecuredWebMvcTest(ShiftTemplateController.class)
class ShiftTemplateControllerTest {

    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    private static final String BODY = """
        {"name": "Ca đêm", "startTime": "22:00", "endTime": "06:00", "description": "Qua đêm"}
        """;

    @Autowired MockMvc mockMvc;

    @MockBean ShiftTemplateService shiftTemplateService;

    @Nested
    class Read {

        /** Màn xếp ca của Manager chỉ cần mẫu đang dùng — mặc định không kèm mẫu đã tắt (BR-SCH-22). */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldListActiveTemplatesForManagerByDefault() throws Exception {
            when(shiftTemplateService.getTemplates(eq(false), any())).thenReturn(new PageImpl<>(List.of(nightTemplate())));

            mockMvc.perform(get("/scheduling/shift-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Ca đêm"))
                .andExpect(jsonPath("$.content[0].overnight").value(true));

            verify(shiftTemplateService).getTemplates(eq(false), any());
        }

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldIncludeDeactivatedTemplatesWhenAsked() throws Exception {
            when(shiftTemplateService.getTemplates(eq(true), any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/scheduling/shift-templates").param("includeInactive", "true"))
                .andExpect(status().isOk());

            verify(shiftTemplateService).getTemplates(eq(true), any());
        }

        @Test
        @WithMockUser(roles = "STAFF")
        void shouldForbidStaff() throws Exception {
            mockMvc.perform(get("/scheduling/shift-templates")).andExpect(status().isForbidden());
            verifyNoInteractions(shiftTemplateService);
        }
    }

    @Nested
    class Write {

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldLetDirectorCreateTemplate() throws Exception {
            when(shiftTemplateService.createTemplate(any())).thenReturn(nightTemplate());

            mockMvc.perform(post("/scheduling/shift-templates")
                    .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

            ArgumentCaptor<CreateShiftTemplateRequest> request =
                ArgumentCaptor.forClass(CreateShiftTemplateRequest.class);
            verify(shiftTemplateService).createTemplate(request.capture());
            assertThat(request.getValue().getStartTime()).isEqualTo(LocalTime.of(22, 0));
            assertThat(request.getValue().getEndTime()).isEqualTo(LocalTime.of(6, 0));
        }

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldRequireNameAndHours() throws Exception {
            mockMvc.perform(post("/scheduling/shift-templates")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"  \"}"))
                .andExpect(status().isUnprocessableEntity());
            verifyNoInteractions(shiftTemplateService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldForbidManagerFromCreatingOrEditing() throws Exception {
            mockMvc.perform(post("/scheduling/shift-templates")
                    .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
            mockMvc.perform(put("/scheduling/shift-templates/{id}", TEMPLATE_ID)
                    .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
            verifyNoInteractions(shiftTemplateService);
        }

        /** BR-SCH-22: không có xóa — tắt mẫu bằng cờ active. */
        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldDeactivateTemplate() throws Exception {
            when(shiftTemplateService.setActive(TEMPLATE_ID, false)).thenReturn(nightTemplate());

            mockMvc.perform(patch("/scheduling/shift-templates/{id}/active", TEMPLATE_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isOk());

            verify(shiftTemplateService).setActive(TEMPLATE_ID, false);
        }
    }

    private static ShiftTemplateResponse nightTemplate() {
        return ShiftTemplateResponse.builder()
            .id(TEMPLATE_ID)
            .name("Ca đêm")
            .startTime(LocalTime.of(22, 0))
            .endTime(LocalTime.of(6, 0))
            .active(true)
            .overnight(true)
            .durationHours(new BigDecimal("8.00"))
            .build();
    }
}
