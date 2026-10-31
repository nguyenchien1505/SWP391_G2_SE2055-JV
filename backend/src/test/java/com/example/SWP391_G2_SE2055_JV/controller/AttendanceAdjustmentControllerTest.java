package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.service.AttendanceAdjustmentService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /scheduling/attendance-adjustments/**} — nạp SecurityConfig thật: nhân viên gửi / hủy đơn, chỉ quản lý
 * khách sạn duyệt / từ chối (chấm công M2).
 */
@SecuredWebMvcTest(AttendanceAdjustmentController.class)
class AttendanceAdjustmentControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String BODY =
        "{\"shiftId\":\"" + UUID.randomUUID() + "\",\"checkInAt\":\"2026-11-02T06:05:00\",\"reason\":\"Quên bấm\"}";

    @Autowired MockMvc mockMvc;

    @MockBean AttendanceAdjustmentService adjustmentService;

    @Test
    @WithMockUser(roles = "STAFF")
    void shouldLetStaffSendAndCancel() throws Exception {
        mockMvc.perform(post("/scheduling/attendance-adjustments").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/scheduling/attendance-adjustments/mine")).andExpect(status().isOk());
        mockMvc.perform(patch("/scheduling/attendance-adjustments/{id}/cancel", ID)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void shouldRequireReason() throws Exception {
        mockMvc.perform(post("/scheduling/attendance-adjustments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"shiftId\":\"" + UUID.randomUUID() + "\",\"checkInAt\":\"2026-11-02T06:05:00\"}"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(adjustmentService);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void shouldForbidStaffReviewing() throws Exception {
        mockMvc.perform(get("/scheduling/attendance-adjustments/review")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/scheduling/attendance-adjustments/{id}/approve", ID)).andExpect(status().isForbidden());
        verifyNoInteractions(adjustmentService);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldLetManagerReview() throws Exception {
        mockMvc.perform(get("/scheduling/attendance-adjustments/review")).andExpect(status().isOk());
        mockMvc.perform(patch("/scheduling/attendance-adjustments/{id}/approve", ID)).andExpect(status().isOk());
        mockMvc.perform(patch("/scheduling/attendance-adjustments/{id}/reject", ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Không đúng\"}"))
            .andExpect(status().isOk());

        verify(adjustmentService).approve(eq(ID), isNull(), any());
        verify(adjustmentService).reject(ID, "Không đúng");
    }

    /** Quản lý không có ca nên không gửi đơn; Giám đốc không duyệt đơn của nhân viên. */
    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldForbidManagerSending() throws Exception {
        mockMvc.perform(post("/scheduling/attendance-adjustments").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void shouldForbidDirectorReviewing() throws Exception {
        mockMvc.perform(get("/scheduling/attendance-adjustments/review")).andExpect(status().isForbidden());
        verifyNoInteractions(adjustmentService);
    }
}
