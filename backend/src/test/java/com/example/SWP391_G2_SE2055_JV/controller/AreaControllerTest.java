package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.service.AreaService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /organization/areas/**} — nạp SecurityConfig thật: Manager CRUD khu vực của khách sạn mình (BR-ORG-12);
 * Giám đốc và nhân viên chỉ ĐỌC — nhân viên xem để báo hỏng tài sản khu vực (BR-ASSET-05).
 */
@SecuredWebMvcTest(AreaController.class)
class AreaControllerTest {

    private static final String BODY = "{\"name\":\"Sảnh\"}";

    @Autowired MockMvc mockMvc;

    @MockBean AreaService areaService;

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "DIRECTOR", "MANAGER"})
    void shouldLetTenantRolesReadAreas(String role) throws Exception {
        when(areaService.getAreas(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/organization/areas").with(user("u").roles(role)))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void shouldForbidStaffWriting() throws Exception {
        mockMvc.perform(post("/organization/areas").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/organization/areas/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/organization/areas/{id}", UUID.randomUUID())).andExpect(status().isForbidden());
        verifyNoInteractions(areaService);
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void shouldForbidDirectorWriting() throws Exception {
        mockMvc.perform(post("/organization/areas").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        verifyNoInteractions(areaService);
    }
}
