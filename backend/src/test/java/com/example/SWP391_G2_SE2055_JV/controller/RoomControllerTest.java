package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.ChangeRoomStatusRequest;
import com.example.SWP391_G2_SE2055_JV.dto.CreateRoomRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RoomResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusHistoryResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusSummaryResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateRoomOperationalRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateRoomRequest;
import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import com.example.SWP391_G2_SE2055_JV.service.RoomService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F1 — {@code GET /rooms/**}; F2 — {@code PATCH /rooms/{id}/status}, {@code GET /rooms/{id}/history};
 * F3 — {@code POST}, {@code PUT}, {@code PATCH /rooms/{id}}, {@code DELETE}.
 * Nạp SecurityConfig thật ({@link SecuredWebMvcTest}) nên kiểm được cả 2 trục phân quyền;
 * {@link RoomService} được mock — phạm vi dữ liệu và luật đổi trạng thái đã test ở tầng service.
 */
@SecuredWebMvcTest(RoomController.class)
class RoomControllerTest {

    private static final UUID ROOM_ID      = UUID.randomUUID();
    private static final UUID LOCATION_ID  = UUID.randomUUID();
    private static final UUID ROOM_TYPE_ID = UUID.randomUUID();

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


    // ── F2: đổi trạng thái — PATCH /rooms/{id}/status ───────────────────────

    @Nested
    class ChangeStatus {

        private static final String LOCK_BODY = """
            {"targetStatus": "UNAVAILABLE", "reason": "Hỏng điều hòa"}
            """;

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldChangeStatusForManagerAndReturnUpdatedRoom() throws Exception {
            RoomResponse locked = sampleRoom();
            locked.setStatus(RoomStatus.UNAVAILABLE);
            locked.setUnavailableReason("Hỏng điều hòa");
            locked.setAllowedTargets(EnumSet.of(RoomStatus.AVAILABLE, RoomStatus.DIRTY));
            when(roomService.changeStatus(eq(ROOM_ID), any())).thenReturn(locked);

            mockMvc.perform(patchStatus(LOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.unavailableReason").value("Hỏng điều hòa"))
                .andExpect(jsonPath("$.allowedTargets[0]").value("AVAILABLE"))
                .andExpect(jsonPath("$.allowedTargets[1]").value("DIRTY"));

            ArgumentCaptor<ChangeRoomStatusRequest> request = ArgumentCaptor.forClass(ChangeRoomStatusRequest.class);
            verify(roomService).changeStatus(eq(ROOM_ID), request.capture());
            assertThat(request.getValue().getTargetStatus()).isEqualTo(RoomStatus.UNAVAILABLE);
            assertThat(request.getValue().getReason()).isEqualTo("Hỏng điều hòa");
        }

        /** Trục 2: URL cho Lễ tân qua; bước nào được bấm do service quyết định. */
        @Test
        @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_RECEPTION"})
        void shouldLetReceptionThroughToService() throws Exception {
            when(roomService.changeStatus(eq(ROOM_ID), any())).thenReturn(sampleRoom());

            mockMvc.perform(patchStatus("""
                    {"targetStatus": "OCCUPIED"}
                    """))
                .andExpect(status().isOk());
        }

        /** BR-ROOM-04: Giám đốc CRUD phòng nhưng KHÔNG vận hành trạng thái — chặn ngay ở URL. */
        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldForbidDirectorAtUrlLevel() throws Exception {
            mockMvc.perform(patchStatus(LOCK_BODY)).andExpect(status().isForbidden());
            verifyNoInteractions(roomService);
        }

        @ParameterizedTest
        @ValueSource(strings = {"POSITION_HOUSEKEEPING", "POSITION_OTHER"})
        void shouldForbidHousekeepingAndOtherPosition(String position) throws Exception {
            mockMvc.perform(patchStatus(LOCK_BODY).with(user("staff@test.local").authorities(
                    new SimpleGrantedAuthority("ROLE_STAFF"),
                    new SimpleGrantedAuthority(position))))
                .andExpect(status().isForbidden());
            verifyNoInteractions(roomService);
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(patchStatus(LOCK_BODY)).andExpect(status().isUnauthorized());
            verifyNoInteractions(roomService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnUnprocessableWhenTargetStatusMissing() throws Exception {
            mockMvc.perform(patchStatus("""
                    {"reason": "Hỏng điều hòa"}
                    """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("targetStatus"));
            verifyNoInteractions(roomService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnUnprocessableWhenReasonTooLong() throws Exception {
            String body = "{\"targetStatus\": \"UNAVAILABLE\", \"reason\": \"" + "x".repeat(501) + "\"}";

            mockMvc.perform(patchStatus(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
            verifyNoInteractions(roomService);
        }

        /** Q12: enum sai trong body là lỗi của client → 400, không rơi xuống 500. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnBadRequestWhenTargetStatusUnknown() throws Exception {
            mockMvc.perform(patchStatus("""
                    {"targetStatus": "FOO"}
                    """))
                .andExpect(status().isBadRequest());
            verifyNoInteractions(roomService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnBadRequestWhenBodyIsNotJson() throws Exception {
            mockMvc.perform(patchStatus("khong-phai-json")).andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnNotFoundWhenRoomOutOfScope() throws Exception {
            when(roomService.changeStatus(eq(ROOM_ID), any()))
                .thenThrow(new ResourceNotFoundException("Room", "id", ROOM_ID));

            mockMvc.perform(patchStatus(LOCK_BODY)).andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnBadRequestWithRuleWhenTransitionRejected() throws Exception {
            when(roomService.changeStatus(eq(ROOM_ID), any())).thenThrow(new BusinessException(
                "Không thể chuyển phòng từ «Đang sử dụng» sang «Không khả dụng»."));

            mockMvc.perform(patchStatus(LOCK_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                    "Không thể chuyển phòng từ «Đang sử dụng» sang «Không khả dụng»."));
        }

        /** Lớp 3: URL cho Manager qua nhưng service chặn bước của Lễ tân (chốt 20/09/2026). */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnForbiddenWhenServiceRejectsActor() throws Exception {
            when(roomService.changeStatus(eq(ROOM_ID), any()))
                .thenThrow(new UnauthorizedException("Bạn không có quyền chuyển phòng từ «Trống / Sẵn sàng» sang «Đã đặt»."));

            mockMvc.perform(patchStatus("""
                    {"targetStatus": "RESERVED"}
                    """))
                .andExpect(status().isForbidden());
        }

        private MockHttpServletRequestBuilder patchStatus(String body) {
            return patch("/rooms/{id}/status", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(body);
        }
    }

    // ── F2: lịch sử — GET /rooms/{id}/history ───────────────────────────────

    @Nested
    class History {

        /** BR-ROOM-09: cả 4 vai trò trong Tenant đều xem được lịch sử (trong phạm vi của mình). */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_DIRECTOR", "ROLE_MANAGER", "ROLE_STAFF,POSITION_RECEPTION",
                                "ROLE_STAFF,POSITION_HOUSEKEEPING"})
        void shouldReturnHistoryForEveryTenantRole(String authorities) throws Exception {
            when(roomService.getHistory(eq(ROOM_ID), any())).thenReturn(new PageImpl<>(
                List.of(sampleHistory()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/rooms/{id}/history", ROOM_ID)
                    .with(user("someone@test.local").authorities(
                        Arrays.stream(authorities.split(","))
                            .map(SimpleGrantedAuthority::new)
                            .toList())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fromStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[0].toStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.content[0].changeSource").value("MANAGER"))
                .andExpect(jsonPath("$.content[0].changedByName").value("Trần Quản Lý"));
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldUseTwentyRowsPerPageByDefault() throws Exception {
            when(roomService.getHistory(eq(ROOM_ID), any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/rooms/{id}/history", ROOM_ID)).andExpect(status().isOk());

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(roomService).getHistory(eq(ROOM_ID), pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isZero();
            assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(get("/rooms/{id}/history", ROOM_ID)).andExpect(status().isUnauthorized());
            verifyNoInteractions(roomService);
        }

        @Test
        @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_RECEPTION"})
        void shouldReturnNotFoundWhenRoomOutOfScope() throws Exception {
            when(roomService.getHistory(eq(ROOM_ID), any())).thenThrow(new ResourceNotFoundException("Room", "id", ROOM_ID));

            mockMvc.perform(get("/rooms/{id}/history", ROOM_ID)).andExpect(status().isNotFound());
        }

        private RoomStatusHistoryResponse sampleHistory() {
            return RoomStatusHistoryResponse.builder()
                .id(UUID.randomUUID())
                .fromStatus(RoomStatus.AVAILABLE)
                .toStatus(RoomStatus.UNAVAILABLE)
                .changedBy(UUID.randomUUID())
                .changedByName("Trần Quản Lý")
                .changedAt(LocalDateTime.of(2026, 9, 22, 14, 5))
                .changeSource(ChangeSource.MANAGER)
                .reason("Hỏng điều hòa")
                .build();
        }
    }


    // ── F3: tạo phòng — POST /rooms ─────────────────────────────────────────

    @Nested
    class CreateRoom {

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldCreateRoomForDirector() throws Exception {
            when(roomService.createRoom(any())).thenReturn(sampleRoom());

            mockMvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomNumber").value("101"))
                .andExpect(jsonPath("$.status").value("DIRTY"));

            ArgumentCaptor<CreateRoomRequest> request = ArgumentCaptor.forClass(CreateRoomRequest.class);
            verify(roomService).createRoom(request.capture());
            assertThat(request.getValue().getRoomNumber()).isEqualTo("301");
            assertThat(request.getValue().getCapacity()).isEqualTo(2);
        }

        /** BR-ROOM-04: Manager và Lễ tân không tạo phòng — chặn ngay ở tầng URL. */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_MANAGER", "ROLE_STAFF,POSITION_RECEPTION", "ROLE_STAFF,POSITION_HOUSEKEEPING"})
        void shouldForbidEveryoneButDirector(String authorities) throws Exception {
            mockMvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(createBody())
                    .with(authorities(authorities)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(roomService);
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isUnauthorized());
            verifyNoInteractions(roomService);
        }

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldReturnUnprocessableWhenRoomNumberMissing() throws Exception {
            String body = createBody().replace("\"roomNumber\": \"301\",", "");

            mockMvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("roomNumber"));
            verifyNoInteractions(roomService);
        }

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldReturnUnprocessableWhenCapacityIsZero() throws Exception {
            String body = createBody().replace("\"capacity\": 2", "\"capacity\": 0");

            mockMvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("capacity"));
            verifyNoInteractions(roomService);
        }

        /** Khách sạn hoặc loại phòng thuộc chuỗi khác — service trả 404, không phải 403. */
        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldReturnNotFoundWhenLocationOutOfScope() throws Exception {
            when(roomService.createRoom(any()))
                .thenThrow(new ResourceNotFoundException("Location", "id", UUID.randomUUID()));

            mockMvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldReturnBadRequestWithRuleWhenRoomNumberDuplicated() throws Exception {
            when(roomService.createRoom(any())).thenThrow(new BusinessException(
                "Khách sạn này đã có phòng số 301. Mỗi khách sạn không được có hai phòng trùng số."));

            mockMvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                    "Khách sạn này đã có phòng số 301. Mỗi khách sạn không được có hai phòng trùng số."));
        }
    }

    // ── F3: sửa cấu trúc — PUT /rooms/{id} ──────────────────────────────────

    @Nested
    class UpdateRoom {

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldUpdateRoomForDirector() throws Exception {
            when(roomService.updateRoom(eq(ROOM_ID), any())).thenReturn(sampleRoom());

            mockMvc.perform(put("/rooms/{id}", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(updateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ROOM_ID.toString()));

            ArgumentCaptor<UpdateRoomRequest> request = ArgumentCaptor.forClass(UpdateRoomRequest.class);
            verify(roomService).updateRoom(eq(ROOM_ID), request.capture());
            assertThat(request.getValue().getRoomNumber()).isEqualTo("205");
        }

        /**
         * Tầng URL cho Manager đi qua (rule {@code /rooms/**} phải mở cho {@code PATCH} của
         * Manager), nên {@code @PreAuthorize} mới là chốt chặn BR-ROOM-04.
         */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldForbidManagerAtMethodLevel() throws Exception {
            mockMvc.perform(put("/rooms/{id}", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(updateBody()))
                .andExpect(status().isForbidden());
            verifyNoInteractions(roomService);
        }

        @Test
        @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_RECEPTION"})
        void shouldForbidReceptionAtUrlLevel() throws Exception {
            mockMvc.perform(put("/rooms/{id}", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(updateBody()))
                .andExpect(status().isForbidden());
            verifyNoInteractions(roomService);
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(put("/rooms/{id}", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(updateBody()))
                .andExpect(status().isUnauthorized());
            verifyNoInteractions(roomService);
        }

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldReturnUnprocessableWhenFloorBlank() throws Exception {
            String body = updateBody().replace("\"floor\": \"2\"", "\"floor\": \"  \"");

            mockMvc.perform(put("/rooms/{id}", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("floor"));
            verifyNoInteractions(roomService);
        }

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldReturnNotFoundWhenRoomOutOfScope() throws Exception {
            when(roomService.updateRoom(eq(ROOM_ID), any()))
                .thenThrow(new ResourceNotFoundException("Room", "id", ROOM_ID));

            mockMvc.perform(put("/rooms/{id}", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(updateBody()))
                .andExpect(status().isNotFound());
        }
    }

    // ── F3: sửa ghi chú vận hành — PATCH /rooms/{id} ────────────────────────

    @Nested
    class UpdateOperational {

        private static final String NOTE_BODY = """
            {"note": "Điều hòa mới thay 20/09"}
            """;

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_MANAGER", "ROLE_DIRECTOR"})
        void shouldUpdateNoteForManagerAndDirector(String authorities) throws Exception {
            when(roomService.updateOperational(eq(ROOM_ID), any())).thenReturn(sampleRoom());

            mockMvc.perform(patch("/rooms/{id}", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(NOTE_BODY)
                    .with(authorities(authorities)))
                .andExpect(status().isOk());

            ArgumentCaptor<UpdateRoomOperationalRequest> request =
                ArgumentCaptor.forClass(UpdateRoomOperationalRequest.class);
            verify(roomService).updateOperational(eq(ROOM_ID), request.capture());
            assertThat(request.getValue().getNote()).isEqualTo("Điều hòa mới thay 20/09");
        }

        /** Trục 2: Lễ tân và Dọn dẹp không sửa được thông tin phòng — chặn ở tầng URL. */
        @ParameterizedTest
        @ValueSource(strings = {"POSITION_RECEPTION", "POSITION_HOUSEKEEPING", "POSITION_OTHER"})
        void shouldForbidEveryStaffPosition(String position) throws Exception {
            mockMvc.perform(patch("/rooms/{id}", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(NOTE_BODY)
                    .with(authorities("ROLE_STAFF," + position)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(roomService);
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(patch("/rooms/{id}", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(NOTE_BODY))
                .andExpect(status().isUnauthorized());
            verifyNoInteractions(roomService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnUnprocessableWhenNoteTooLong() throws Exception {
            String body = "{\"note\": \"" + "x".repeat(501) + "\"}";

            mockMvc.perform(patch("/rooms/{id}", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("note"));
            verifyNoInteractions(roomService);
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldReturnNotFoundWhenRoomBelongsToAnotherLocation() throws Exception {
            when(roomService.updateOperational(eq(ROOM_ID), any()))
                .thenThrow(new ResourceNotFoundException("Room", "id", ROOM_ID));

            mockMvc.perform(patch("/rooms/{id}", ROOM_ID).contentType(MediaType.APPLICATION_JSON).content(NOTE_BODY))
                .andExpect(status().isNotFound());
        }
    }

    // ── F3: xóa phòng — DELETE /rooms/{id} ──────────────────────────────────

    @Nested
    class DeleteRoom {

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldDeleteRoomForDirector() throws Exception {
            mockMvc.perform(delete("/rooms/{id}", ROOM_ID)).andExpect(status().isNoContent());
            verify(roomService).deleteRoom(ROOM_ID);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_MANAGER", "ROLE_STAFF,POSITION_RECEPTION"})
        void shouldForbidEveryoneButDirector(String authorities) throws Exception {
            mockMvc.perform(delete("/rooms/{id}", ROOM_ID).with(authorities(authorities)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(roomService);
        }

        @Test
        void shouldReturnUnauthorizedWithoutLogin() throws Exception {
            mockMvc.perform(delete("/rooms/{id}", ROOM_ID)).andExpect(status().isUnauthorized());
            verifyNoInteractions(roomService);
        }

        /** BR-ROOM-08: câu lỗi phải nói rõ điều kiện chưa đạt để Giám đốc biết làm gì tiếp. */
        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldReturnBadRequestWithRuleWhenRoomStillHasOpenTask() throws Exception {
            doThrow(new BusinessException("Phòng 101 còn việc dọn phòng chưa kết thúc. "
                + "Hãy hoàn thành hoặc hủy việc dọn trước khi xóa."))
                .when(roomService).deleteRoom(ROOM_ID);

            mockMvc.perform(delete("/rooms/{id}", ROOM_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phòng 101 còn việc dọn phòng chưa kết thúc. "
                    + "Hãy hoàn thành hoặc hủy việc dọn trước khi xóa."));
        }

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldReturnNotFoundWhenRoomOutOfScope() throws Exception {
            doThrow(new ResourceNotFoundException("Room", "id", ROOM_ID)).when(roomService).deleteRoom(ROOM_ID);

            mockMvc.perform(delete("/rooms/{id}", ROOM_ID)).andExpect(status().isNotFound());
        }
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    /**
     * Người đăng nhập mang đúng danh sách authority truyền vào, ví dụ
     * {@code "ROLE_STAFF,POSITION_RECEPTION"} — dùng cho các test chạy theo tham số, nơi
     * {@code @WithMockUser} không nhận giá trị động.
     */
    private static RequestPostProcessor authorities(String commaSeparated) {
        return user("someone@test.local").authorities(
            Arrays.stream(commaSeparated.split(","))
                .map(SimpleGrantedAuthority::new)
                .toList());
    }

    /** Body hợp lệ của POST /rooms — từng test tự thay một trường để dựng ca lỗi. */
    private static String createBody() {
        return """
            {
              "locationId": "%s",
              "roomNumber": "301",
              "floor": "3",
              "roomTypeId": "%s",
              "capacity": 2,
              "note": "Phòng góc"
            }
            """.formatted(LOCATION_ID, ROOM_TYPE_ID);
    }

    /** Body hợp lệ của PUT /rooms/{id} — không có locationId, không có status. */
    private static String updateBody() {
        return """
            {
              "roomNumber": "205",
              "floor": "2",
              "roomTypeId": "%s",
              "capacity": 4,
              "note": "View biển"
            }
            """.formatted(ROOM_TYPE_ID);
    }

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
