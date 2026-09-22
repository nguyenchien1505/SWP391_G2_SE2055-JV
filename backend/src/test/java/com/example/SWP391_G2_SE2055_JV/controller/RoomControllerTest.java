package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.RoomResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusSummaryResponse;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.service.RoomService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F1 — {@code GET /rooms/**}. Nạp SecurityConfig thật ({@link SecuredWebMvcTest}) nên kiểm
 * được cả 2 trục phân quyền; {@link RoomService} được mock — phạm vi dữ liệu đã test ở
 * {@code RoomServiceTest}.
 */
@SecuredWebMvcTest(RoomController.class)
class RoomControllerTest {

    private static final UUID ROOM_ID = UUID.randomUUID();

    @Autowired MockMvc mockMvc;

    @MockBean RoomService roomService;

    // ── Xác thực và phân quyền ──────────────────────────────────────────────

    @Test
    void shouldReturnUnauthorizedWithoutLogin() throws Exception {
        mockMvc.perform(get("/rooms")).andExpect(status().isUnauthorized());
        verifyNoInteractions(roomService);
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void shouldListRoomsForDirector() throws Exception {
        stubRoomsPage();
        mockMvc.perform(get("/rooms"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].roomNumber").value("101"))
            .andExpect(jsonPath("$.content[0].roomTypeName").value("Đôi"))
            .andExpect(jsonPath("$.content[0].status").value("DIRTY"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldListRoomsForManager() throws Exception {
        stubRoomsPage();
        mockMvc.perform(get("/rooms")).andExpect(status().isOk());
    }

    /** Trục 2: Lễ tân là Loại Position, không phải role (BR-ORG-08). */
    @Test
    @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_RECEPTION"})
    void shouldListRoomsForReceptionStaff() throws Exception {
        stubRoomsPage();
        mockMvc.perform(get("/rooms")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_HOUSEKEEPING"})
    void shouldListRoomsForHousekeepingStaff() throws Exception {
        stubRoomsPage();
        mockMvc.perform(get("/rooms")).andExpect(status().isOk());
    }

    /** Người đã đăng nhập nhưng không mang role nào của hệ thống thì bị chặn ngay ở URL. */
    @Test
    @WithMockUser(authorities = "POSITION_RECEPTION")
    void shouldForbidAuthenticatedUserWithoutAnyRole() throws Exception {
        mockMvc.perform(get("/rooms")).andExpect(status().isForbidden());
        verifyNoInteractions(roomService);
    }

    // ── Tham số và phân trang ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void shouldPassParsedFiltersAndDefaultPagingToService() throws Exception {
        UUID locationId = UUID.randomUUID();
        UUID roomTypeId = UUID.randomUUID();
        stubRoomsPage();

        mockMvc.perform(get("/rooms")
                .param("locationId", locationId.toString())
                .param("status", "DIRTY")
                .param("floor", "B1")
                .param("roomTypeId", roomTypeId.toString()))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(roomService).getRooms(eq(locationId), eq(RoomStatus.DIRTY), eq("B1"), eq(roomTypeId), pageable.capture());
        // Mặc định: 20 phòng/trang, sắp theo tầng rồi số phòng.
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort())
            .isEqualTo(Sort.by(Sort.Direction.ASC, "floor", "roomNumber"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldReturnBadRequestWhenStatusParamInvalid() throws Exception {
        mockMvc.perform(get("/rooms").param("status", "FOO"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Giá trị của tham số 'status' không hợp lệ."));
        verifyNoInteractions(roomService);
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void shouldReturnBadRequestWhenLocationIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/rooms").param("locationId", "abc"))
            .andExpect(status().isBadRequest());
    }

    // ── Chi tiết phòng ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_HOUSEKEEPING"})
    void shouldReturnRoomDetail() throws Exception {
        when(roomService.getRoomById(ROOM_ID)).thenReturn(sampleRoom());

        mockMvc.perform(get("/rooms/{id}", ROOM_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(ROOM_ID.toString()))
            .andExpect(jsonPath("$.floor").value("1"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldReturnNotFoundWhenRoomOutOfScope() throws Exception {
        when(roomService.getRoomById(ROOM_ID)).thenThrow(new ResourceNotFoundException("Room", "id", ROOM_ID));

        mockMvc.perform(get("/rooms/{id}", ROOM_ID)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldReturnBadRequestWhenRoomIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/rooms/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    // ── Đếm theo trạng thái ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void shouldReturnStatusSummaryWithAllSevenStatuses() throws Exception {
        when(roomService.getStatusSummary(isNull())).thenReturn(RoomStatusSummaryResponse.of(null, List.of()));

        mockMvc.perform(get("/rooms/status-summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.counts.length()").value(7))
            .andExpect(jsonPath("$.counts.RESERVED").value(0));
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    private void stubRoomsPage() {
        when(roomService.getRooms(any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(sampleRoom()), PageRequest.of(0, 20), 1));
    }

    private static RoomResponse sampleRoom() {
        return RoomResponse.builder()
            .id(ROOM_ID)
            .locationId(UUID.randomUUID())
            .roomNumber("101")
            .floor("1")
            .roomTypeName("Đôi")
            .capacity(2)
            .status(RoomStatus.DIRTY)
            .build();
    }
}
