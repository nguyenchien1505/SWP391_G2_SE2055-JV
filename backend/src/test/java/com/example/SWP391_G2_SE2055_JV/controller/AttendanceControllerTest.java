package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.service.AttendanceNetworkService;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceService;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceSummaryService;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceTodayService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /scheduling/attendance} — bảng chấm công chỉ cho Manager / Giám đốc / Admin (chấm công M2). */
@SecuredWebMvcTest(AttendanceController.class)
class AttendanceControllerTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 2);
    private static final LocalDate TO   = LocalDate.of(2026, 11, 8);

    @Autowired MockMvc mockMvc;

    @MockBean AttendanceService        attendanceService;
    @MockBean AttendanceNetworkService networkService;
    @MockBean AttendanceTodayService   todayService;
    @MockBean AttendanceSummaryService summaryService;

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldLetManagerReadAttendance() throws Exception {
        when(attendanceService.getAttendance(FROM, TO, null)).thenReturn(List.of());

        mockMvc.perform(get("/scheduling/attendance").param("from", "2026-11-02").param("to", "2026-11-08"))
            .andExpect(status().isOk());

        verify(attendanceService).getAttendance(FROM, TO, null);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void shouldForbidStaff() throws Exception {
        mockMvc.perform(get("/scheduling/attendance").param("from", "2026-11-02").param("to", "2026-11-08"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(attendanceService);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldRequireDateRange() throws Exception {
        mockMvc.perform(get("/scheduling/attendance").param("from", "2026-11-02"))
            .andExpect(status().isBadRequest());
    }

    // ── Mạng chấm công: Manager sửa, Giám đốc chỉ xem, nhân viên không đụng ─

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void shouldLetDirectorReadButNotChangeNetwork() throws Exception {
        mockMvc.perform(get("/scheduling/attendance/network")).andExpect(status().isOk());
        mockMvc.perform(put("/scheduling/attendance/network/mode")
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"WARN\"}"))
            .andExpect(status().isForbidden());
        verify(networkService, never()).setMode(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void shouldForbidStaffNetworkSettings() throws Exception {
        mockMvc.perform(get("/scheduling/attendance/network")).andExpect(status().isForbidden());
        mockMvc.perform(post("/scheduling/attendance/network/ips")
                .contentType(MediaType.APPLICATION_JSON).content("{\"cidr\":\"1.2.3.4\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(networkService);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldLetManagerAddAndRemoveNetwork() throws Exception {
        mockMvc.perform(post("/scheduling/attendance/network/ips")
                .contentType(MediaType.APPLICATION_JSON).content("{\"cidr\":\"113.160.92.0/24\",\"label\":\"Wifi\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/scheduling/attendance/network/ips/{id}", UUID.randomUUID()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldRejectUnknownMode() throws Exception {
        mockMvc.perform(put("/scheduling/attendance/network/mode")
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"STRICT\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(networkService);
    }

    // ── Tổng kết chuyên cần: quản lý xem cả khách sạn, nhân viên xem của mình ─

    @Test
    @WithMockUser(roles = "STAFF")
    void shouldLetStaffReadOnlyOwnSummary() throws Exception {
        mockMvc.perform(get("/scheduling/attendance/summary/me").param("month", "2026-11")).andExpect(status().isOk());
        mockMvc.perform(get("/scheduling/attendance/summary").param("month", "2026-11")).andExpect(status().isForbidden());
        verify(summaryService).getMySummary("2026-11");
        verify(summaryService, never()).getLocationSummary(any(), any());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldLetManagerReadLocationSummaryAndToday() throws Exception {
        mockMvc.perform(get("/scheduling/attendance/summary")).andExpect(status().isOk());
        mockMvc.perform(get("/scheduling/attendance/today")).andExpect(status().isOk());
        mockMvc.perform(get("/scheduling/attendance/summary/me")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void shouldForbidStaffToday() throws Exception {
        mockMvc.perform(get("/scheduling/attendance/today")).andExpect(status().isForbidden());
        verifyNoInteractions(todayService);
    }
}
