package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.ChangeRoomStatusRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RoomResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusHistoryResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusSummaryResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomStatusHistory;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository.RoomStatusCount;
import com.example.SWP391_G2_SE2055_JV.repository.RoomStatusHistoryRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomTypeRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F1 — xem phòng (RM-06) và F2 — đổi trạng thái, lịch sử (RM-07, RM-11, RM-12). Trọng tâm là LỚP
 * PHẠM VI DỮ LIỆU: ai được thấy / thao tác phòng nào. Mọi phụ thuộc đều mock (luật đổi trạng
 * thái đã test ở {@code RoomStatusServiceTest}, {@code RoomTransitionPolicyTest}); người đăng
 * nhập dựng bằng {@link TestAuth}.
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final UUID     TENANT_ID         = UUID.randomUUID();
    private static final UUID     LOCATION_ID       = UUID.randomUUID();
    private static final UUID     OTHER_LOCATION_ID = UUID.randomUUID();
    private static final UUID     ROOM_TYPE_ID      = UUID.randomUUID();
    private static final Pageable PAGE              = PageRequest.of(0, 20);

    @Mock RoomRepository              roomRepository;
    @Mock RoomTypeRepository          roomTypeRepository;
    @Mock RoomStatusHistoryRepository historyRepository;
    @Mock UserRepository              userRepository;
    @Mock RoomStatusService           roomStatusService;
    @Mock RoomTransitionPolicy        transitionPolicy;

    @InjectMocks RoomService roomService;

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    // ── Danh sách phòng: phạm vi Location theo vai trò ──────────────────────

    @Nested
    class GetRooms {

        @Test
        void shouldSearchWholeTenantWhenDirectorGivesNoLocation() {
            TestAuth.loginAsDirector(TENANT_ID);
            stubSearchReturning(List.of());

            roomService.getRooms(null, null, null, null, PAGE);

            verify(roomRepository).search(TENANT_ID, null, null, null, null, PAGE);
        }

        @Test
        void shouldFilterByRequestedLocationWhenDirector() {
            TestAuth.loginAsDirector(TENANT_ID);
            stubSearchReturning(List.of());

            roomService.getRooms(OTHER_LOCATION_ID, null, null, null, PAGE);

            verify(roomRepository).search(TENANT_ID, OTHER_LOCATION_ID, null, null, null, PAGE);
        }

        @Test
        void shouldForceOwnLocationWhenManagerRequestsAnotherLocation() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            stubSearchReturning(List.of());

            roomService.getRooms(OTHER_LOCATION_ID, null, null, null, PAGE);

            // Client xin Location khác nhưng truy vấn vẫn chạy trên Location của Manager.
            verify(roomRepository).search(TENANT_ID, LOCATION_ID, null, null, null, PAGE);
        }

        @ParameterizedTest
        @EnumSource(PositionType.class)
        void shouldForceOwnLocationForEveryStaffPosition(PositionType positionType) {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, positionType);
            stubSearchReturning(List.of());

            roomService.getRooms(null, null, null, null, PAGE);

            verify(roomRepository).search(TENANT_ID, LOCATION_ID, null, null, null, PAGE);
        }

        @Test
        void shouldPassFiltersAndTrimFloor() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            stubSearchReturning(List.of());

            roomService.getRooms(null, RoomStatus.DIRTY, "  B1 ", ROOM_TYPE_ID, PAGE);

            verify(roomRepository).search(TENANT_ID, LOCATION_ID, RoomStatus.DIRTY, "B1", ROOM_TYPE_ID, PAGE);
        }

        @Test
        void shouldIgnoreBlankFloorFilter() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            stubSearchReturning(List.of());

            roomService.getRooms(null, null, "   ", null, PAGE);

            verify(roomRepository).search(eq(TENANT_ID), eq(LOCATION_ID), isNull(), isNull(), isNull(), eq(PAGE));
        }

        @Test
        void shouldMapRoomTypeNameIncludingHiddenType() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            stubSearchReturning(List.of(room(LOCATION_ID, RoomStatus.AVAILABLE)));
            // Loại phòng đã ẩn (BR-ORG-14) vẫn phải hiện đúng tên cho phòng cũ.
            RoomType hidden = RoomType.builder().id(ROOM_TYPE_ID).tenantId(TENANT_ID).name("Hạng sang").active(false).build();
            when(roomTypeRepository.findByTenantIdOrderByNameAsc(TENANT_ID)).thenReturn(List.of(hidden));

            Page<RoomResponse> result = roomService.getRooms(null, null, null, null, PAGE);

            assertThat(result.getContent()).singleElement()
                .satisfies(r -> assertThat(r.getRoomTypeName()).isEqualTo("Hạng sang"));
        }

        /** allowedTargets tính theo người đang đăng nhập cho TỪNG phòng, từ trạng thái của phòng đó. */
        @Test
        void shouldAttachAllowedTargetsOfCurrentUserToEveryRoom() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            stubSearchReturning(List.of(room(LOCATION_ID, RoomStatus.AVAILABLE), room(LOCATION_ID, RoomStatus.OCCUPIED)));
            when(transitionPolicy.allowedTargetsForCurrentUser(RoomStatus.AVAILABLE))
                .thenReturn(EnumSet.of(RoomStatus.UNAVAILABLE));
            when(transitionPolicy.allowedTargetsForCurrentUser(RoomStatus.OCCUPIED))
                .thenReturn(EnumSet.noneOf(RoomStatus.class));

            List<RoomResponse> rooms = roomService.getRooms(null, null, null, null, PAGE).getContent();

            assertThat(rooms.get(0).getAllowedTargets()).containsExactly(RoomStatus.UNAVAILABLE);
            assertThat(rooms.get(1).getAllowedTargets()).isEmpty();
        }

        @Test
        void shouldRejectPlatformAdminBecauseItBelongsToNoTenant() {
            TestAuth.loginAs(Role.PLATFORM_ADMIN, null, null, null);

            assertThatThrownBy(() -> roomService.getRooms(null, null, null, null, PAGE))
                .isInstanceOf(UnauthorizedException.class);
            verifyNoInteractions(roomRepository);
        }

        private void stubSearchReturning(List<Room> rooms) {
            when(roomRepository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(rooms, PAGE, rooms.size()));
        }
    }

    // ── Chi tiết phòng: cách ly Tenant và Location ──────────────────────────

    @Nested
    class GetRoomById {

        @Test
        void shouldReturnRoomWithTypeNameWhenInScope() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            Room room = room(LOCATION_ID, RoomStatus.UNAVAILABLE);
            room.setUnavailableReason("Hỏng điều hòa");
            when(roomRepository.findByIdAndTenantIdAndActiveTrue(room.getId(), TENANT_ID)).thenReturn(Optional.of(room));
            when(roomTypeRepository.findByIdAndTenantId(ROOM_TYPE_ID, TENANT_ID))
                .thenReturn(Optional.of(RoomType.builder().id(ROOM_TYPE_ID).name("Đôi").build()));

            RoomResponse response = roomService.getRoomById(room.getId());

            assertThat(response.getRoomNumber()).isEqualTo("101");
            assertThat(response.getRoomTypeName()).isEqualTo("Đôi");
            assertThat(response.getUnavailableReason()).isEqualTo("Hỏng điều hòa");
        }

        /** Phòng của Tenant khác, phòng đã xóa mềm, id bịa: repository đều trả rỗng → 404. */
        @Test
        void shouldThrowNotFoundWhenRoomIsNotInCurrentTenant() {
            TestAuth.loginAsDirector(TENANT_ID);
            UUID foreignRoomId = UUID.randomUUID();
            when(roomRepository.findByIdAndTenantIdAndActiveTrue(foreignRoomId, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roomService.getRoomById(foreignRoomId))
                .isInstanceOf(ResourceNotFoundException.class);
            // Truy vấn phải mang Tenant của người đăng nhập — không bao giờ tìm theo id trần.
            verify(roomRepository).findByIdAndTenantIdAndActiveTrue(foreignRoomId, TENANT_ID);
        }

        @Test
        void shouldThrowNotFoundWhenManagerReadsRoomOfAnotherLocation() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            Room otherLocationRoom = room(OTHER_LOCATION_ID, RoomStatus.AVAILABLE);
            when(roomRepository.findByIdAndTenantIdAndActiveTrue(otherLocationRoom.getId(), TENANT_ID))
                .thenReturn(Optional.of(otherLocationRoom));

            // 404 chứ không phải 403: không để lộ việc phòng đó tồn tại.
            assertThatThrownBy(() -> roomService.getRoomById(otherLocationRoom.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(roomTypeRepository);
        }

        @Test
        void shouldThrowNotFoundWhenStaffReadsRoomOfAnotherLocation() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            Room otherLocationRoom = room(OTHER_LOCATION_ID, RoomStatus.AVAILABLE);
            when(roomRepository.findByIdAndTenantIdAndActiveTrue(otherLocationRoom.getId(), TENANT_ID))
                .thenReturn(Optional.of(otherLocationRoom));

            assertThatThrownBy(() -> roomService.getRoomById(otherLocationRoom.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void shouldLetDirectorReadRoomOfAnyLocationInTenant() {
            TestAuth.loginAsDirector(TENANT_ID);
            Room room = room(OTHER_LOCATION_ID, RoomStatus.OCCUPIED);
            when(roomRepository.findByIdAndTenantIdAndActiveTrue(room.getId(), TENANT_ID)).thenReturn(Optional.of(room));
            when(roomTypeRepository.findByIdAndTenantId(ROOM_TYPE_ID, TENANT_ID)).thenReturn(Optional.empty());

            RoomResponse response = roomService.getRoomById(room.getId());

            assertThat(response.getLocationId()).isEqualTo(OTHER_LOCATION_ID);
            assertThat(response.getRoomTypeName()).isNull();
        }
    }

    // ── Đếm theo trạng thái: BR-DASH-02, BR-DASH-03 ─────────────────────────

    @Nested
    class GetStatusSummary {

        @Test
        void shouldFillZeroForStatusesWithoutRoomsAndSumTotal() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            when(roomRepository.countByStatus(TENANT_ID, LOCATION_ID))
                .thenReturn(List.of(count(RoomStatus.DIRTY, 2), count(RoomStatus.OCCUPIED, 3)));

            RoomStatusSummaryResponse summary = roomService.getStatusSummary(null);

            assertThat(summary.getCounts()).hasSize(RoomStatus.values().length)
                .containsEntry(RoomStatus.DIRTY, 2L)
                .containsEntry(RoomStatus.OCCUPIED, 3L)
                .containsEntry(RoomStatus.RESERVED, 0L);
            assertThat(summary.getTotal()).isEqualTo(5);
            assertThat(summary.getLocationId()).isEqualTo(LOCATION_ID);
        }

        @Test
        void shouldCountWholeTenantWhenDirectorGivesNoLocation() {
            TestAuth.loginAsDirector(TENANT_ID);
            when(roomRepository.countByStatus(TENANT_ID, null)).thenReturn(List.of());

            RoomStatusSummaryResponse summary = roomService.getStatusSummary(null);

            assertThat(summary.getLocationId()).isNull();
            assertThat(summary.getTotal()).isZero();
        }

        @Test
        void shouldForceOwnLocationWhenManagerAsksForAnotherLocation() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            when(roomRepository.countByStatus(TENANT_ID, LOCATION_ID)).thenReturn(List.of());

            roomService.getStatusSummary(OTHER_LOCATION_ID);

            verify(roomRepository).countByStatus(TENANT_ID, LOCATION_ID);
        }
    }


    // ── Đổi trạng thái: PATCH /rooms/{id}/status (F2) ───────────────────────

    @Nested
    class ChangeStatus {

        @Test
        void shouldDelegateToRoomStatusServiceAndReturnNewAllowedTargets() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            Room room = stubOwnedRoom(LOCATION_ID, RoomStatus.UNAVAILABLE);
            ChangeRoomStatusRequest request = new ChangeRoomStatusRequest();
            request.setTargetStatus(RoomStatus.UNAVAILABLE);
            request.setReason("Hỏng điều hòa");
            when(transitionPolicy.allowedTargetsForCurrentUser(RoomStatus.UNAVAILABLE))
                .thenReturn(EnumSet.of(RoomStatus.AVAILABLE, RoomStatus.DIRTY));

            RoomResponse response = roomService.changeStatus(room.getId(), request);

            verify(roomStatusService).changeStatusByUser(room, request);
            // Nút cho trạng thái MỚI của phòng — màn hình vẽ lại ngay, không phải gọi thêm API.
            assertThat(response.getAllowedTargets()).containsExactly(RoomStatus.AVAILABLE, RoomStatus.DIRTY);
        }

        @Test
        void shouldThrowNotFoundWhenChangingStatusOfRoomInAnotherLocation() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            Room otherLocationRoom = stubOwnedRoom(OTHER_LOCATION_ID, RoomStatus.AVAILABLE);

            assertThatThrownBy(() -> roomService.changeStatus(otherLocationRoom.getId(), new ChangeRoomStatusRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(roomStatusService);
        }

        @Test
        void shouldThrowNotFoundWhenChangingStatusOfRoomInAnotherTenant() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            UUID foreignRoomId = UUID.randomUUID();
            when(roomRepository.findByIdAndTenantIdAndActiveTrue(foreignRoomId, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roomService.changeStatus(foreignRoomId, new ChangeRoomStatusRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(roomStatusService);
        }
    }

    // ── Lịch sử trạng thái: GET /rooms/{id}/history (F2, BR-ROOM-09) ────────

    @Nested
    class GetHistory {

        @Test
        void shouldReturnHistoryNewestFirstWithChangedByNames() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            Room room = stubOwnedRoom(LOCATION_ID, RoomStatus.UNAVAILABLE);
            UUID managerId = UUID.randomUUID();
            RoomStatusHistory locked = history(room, RoomStatus.AVAILABLE, RoomStatus.UNAVAILABLE, ChangeSource.MANAGER, managerId);
            RoomStatusHistory created = history(room, null, RoomStatus.DIRTY, ChangeSource.SYSTEM, null);
            stubHistoryPage(room, PageRequest.of(0, 20), List.of(locked, created));
            when(userRepository.findAllById(Set.of(managerId)))
                .thenReturn(List.of(User.builder().id(managerId).fullName("Trần Quản Lý").build()));

            List<RoomStatusHistoryResponse> rows = roomService.getHistory(room.getId(), PageRequest.of(0, 20)).getContent();

            assertThat(rows).extracting(RoomStatusHistoryResponse::getToStatus)
                .containsExactly(RoomStatus.UNAVAILABLE, RoomStatus.DIRTY);
            assertThat(rows.get(0).getChangedByName()).isEqualTo("Trần Quản Lý");
            assertThat(rows.get(1).getChangedByName()).isNull();   // hệ thống — FE hiện "Hệ thống"
            assertThat(rows.get(1).getFromStatus()).isNull();      // dòng đầu của phòng mới (BR-ROOM-10)
        }

        /** Lịch sử luôn mới nhất trước: sort client gửi bị bỏ, trang và cỡ trang được giữ. */
        @Test
        void shouldIgnoreClientSortButKeepPaging() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            Room room = stubOwnedRoom(LOCATION_ID, RoomStatus.AVAILABLE);
            stubHistoryPage(room, PageRequest.of(2, 5), List.of());

            roomService.getHistory(room.getId(), PageRequest.of(2, 5, Sort.by("reason")));

            verify(historyRepository).findByTenantIdAndRoomIdOrderByChangedAtDesc(TENANT_ID, room.getId(), PageRequest.of(2, 5));
        }

        @Test
        void shouldNotLookUpUsersWhenPageHasOnlySystemRows() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            Room room = stubOwnedRoom(LOCATION_ID, RoomStatus.CLEANING);
            stubHistoryPage(room, PageRequest.of(0, 20),
                List.of(history(room, RoomStatus.DIRTY, RoomStatus.CLEANING, ChangeSource.SYSTEM, null)));

            roomService.getHistory(room.getId(), PageRequest.of(0, 20));

            verifyNoInteractions(userRepository);
        }

        @Test
        void shouldThrowNotFoundWhenReadingHistoryOfRoomInAnotherTenant() {
            TestAuth.loginAsDirector(TENANT_ID);
            UUID foreignRoomId = UUID.randomUUID();
            when(roomRepository.findByIdAndTenantIdAndActiveTrue(foreignRoomId, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roomService.getHistory(foreignRoomId, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(historyRepository);
        }

        @Test
        void shouldThrowNotFoundWhenStaffReadsHistoryOfRoomInAnotherLocation() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            Room otherLocationRoom = stubOwnedRoom(OTHER_LOCATION_ID, RoomStatus.AVAILABLE);

            assertThatThrownBy(() -> roomService.getHistory(otherLocationRoom.getId(), PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(historyRepository);
        }

        private void stubHistoryPage(Room room, Pageable pageable, List<RoomStatusHistory> rows) {
            when(historyRepository.findByTenantIdAndRoomIdOrderByChangedAtDesc(TENANT_ID, room.getId(), pageable))
                .thenReturn(new PageImpl<>(rows, pageable, rows.size()));
        }
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    /** Phòng thuộc Tenant hiện tại, repository trả về khi tra theo id. */
    private Room stubOwnedRoom(UUID locationId, RoomStatus status) {
        Room room = room(locationId, status);
        when(roomRepository.findByIdAndTenantIdAndActiveTrue(room.getId(), TENANT_ID)).thenReturn(Optional.of(room));
        return room;
    }

    private static RoomStatusHistory history(Room room, RoomStatus from, RoomStatus to,
                                             ChangeSource source, UUID changedBy) {
        return RoomStatusHistory.builder()
            .id(UUID.randomUUID())
            .tenantId(TENANT_ID)
            .roomId(room.getId())
            .fromStatus(from)
            .toStatus(to)
            .changeSource(source)
            .changedBy(changedBy)
            .changedAt(LocalDateTime.now())
            .build();
    }

    private static Room room(UUID locationId, RoomStatus status) {
        return Room.builder()
            .id(UUID.randomUUID())
            .tenantId(TENANT_ID)
            .locationId(locationId)
            .roomNumber("101")
            .floor("1")
            .roomTypeId(ROOM_TYPE_ID)
            .capacity(2)
            .status(status)
            .build();
    }

    private static RoomStatusCount count(RoomStatus status, long total) {
        return new RoomStatusCount() {
            @Override public RoomStatus getStatus() { return status; }
            @Override public long getTotal() { return total; }
        };
    }
}
