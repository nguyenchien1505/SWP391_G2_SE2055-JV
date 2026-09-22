package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.dto.ChangeRoomStatusRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomStatusHistory;
import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
import com.example.SWP391_G2_SE2055_JV.enums.InspectionResult;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomStatusHistoryRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * F2 — lõi đổi trạng thái phòng (BR-ROOM-02, BR-ROOM-03, BR-ROOM-07, BR-ROOM-09, BR-HK-09).
 *
 * <p>Repository và hook của Housekeeping được mock; {@link RoomTransitionPolicy} dùng bản THẬT để
 * kiểm được đúng thứ tự 4 bước kiểm tra như khi chạy thật. Phạm vi Tenant/Location không test ở
 * đây: phòng truyền vào đã được {@code RoomService} tải đúng phạm vi (xem {@code RoomServiceTest}).
 */
@ExtendWith(MockitoExtension.class)
class RoomStatusServiceTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID TASK_ID     = UUID.randomUUID();

    @Mock RoomRepository              roomRepository;
    @Mock RoomStatusHistoryRepository historyRepository;
    @Mock HousekeepingRoomHooks       hooks;

    private RoomStatusService service;

    @BeforeEach
    void setUp() {
        service = new RoomStatusService(roomRepository, historyRepository, new RoomTransitionPolicy(), hooks);
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    // ── Lõi: đổi trạng thái + đúng 1 dòng lịch sử (BR-ROOM-09) ──────────────

    @Nested
    class Core {

        @Test
        void shouldWriteExactlyOneHistoryRowWhenChangingStatus() {
            CustomUserDetails manager = TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            Room room = room(RoomStatus.AVAILABLE);

            service.changeStatusByUser(room, request(RoomStatus.UNAVAILABLE, "  Hỏng điều hòa  "));

            verify(roomRepository).save(room);
            RoomStatusHistory row = savedHistory();
            assertThat(row.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(row.getRoomId()).isEqualTo(room.getId());
            assertThat(row.getFromStatus()).isEqualTo(RoomStatus.AVAILABLE);
            assertThat(row.getToStatus()).isEqualTo(RoomStatus.UNAVAILABLE);
            assertThat(row.getChangeSource()).isEqualTo(ChangeSource.MANAGER);
            assertThat(row.getChangedBy()).isEqualTo(manager.getId());
            assertThat(row.getChangedAt()).isNotNull();
            assertThat(row.getReason()).isEqualTo("Hỏng điều hòa");   // đã cắt khoảng trắng
            assertThat(row.getRelatedTaskId()).isNull();
        }

        @Test
        void shouldSetChangedByNullWhenSourceIsSystem() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);   // có người đăng nhập vẫn phải là null

            service.startCleaning(room(RoomStatus.DIRTY), TASK_ID);

            RoomStatusHistory row = savedHistory();
            assertThat(row.getChangeSource()).isEqualTo(ChangeSource.SYSTEM);
            assertThat(row.getChangedBy()).isNull();
        }

        @Test
        void shouldRejectTransitionOutsideMatrix() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            Room room = room(RoomStatus.OCCUPIED);

            // Không nhảy tắt: phòng có khách phải qua check-out (→ Chờ dọn), không về thẳng Sẵn sàng.
            assertThatThrownBy(() -> service.changeStatusByUser(room, request(RoomStatus.AVAILABLE, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("«Đang sử dụng»").hasMessageContaining("«Trống / Sẵn sàng»");
            assertNothingWritten(room, RoomStatus.OCCUPIED);
        }

        /** BR-ROOM-03: bước 1 (ma trận) đứng trước bước 3 (quyền) nên Manager nhận 400, không phải 403. */
        @Test
        void shouldRejectUnavailableWhenRoomOccupied() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            Room room = room(RoomStatus.OCCUPIED);

            assertThatThrownBy(() -> service.changeStatusByUser(room, request(RoomStatus.UNAVAILABLE, "Vỡ ống nước")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể chuyển phòng")
                .hasMessageNotContaining("BR-");   // câu lỗi hiện cho người dùng: không lộ mã tài liệu
            assertNothingWritten(room, RoomStatus.OCCUPIED);
        }

        @Test
        void shouldRejectChangingToSameStatus() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            Room room = room(RoomStatus.UNAVAILABLE);

            assertThatThrownBy(() -> service.changeStatusByUser(room, request(RoomStatus.UNAVAILABLE, "Lý do mới")))
                .isInstanceOf(BusinessException.class);
            assertNothingWritten(room, RoomStatus.UNAVAILABLE);
        }

        /** Bước 2 (bước của task dọn) đứng trước bước 3 (quyền): ai gọi cũng 400. */
        @Test
        void shouldRejectTaskDrivenTransitionBeforeCheckingActor() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            Room room = room(RoomStatus.CLEANING);

            assertThatThrownBy(() -> service.changeStatusByUser(room, request(RoomStatus.INSPECTION, null)))
                .isInstanceOf(BusinessException.class);
            assertNothingWritten(room, RoomStatus.CLEANING);
        }

        @Test
        void shouldForbidReceptionFromLockingAndWriteNothing() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            Room room = room(RoomStatus.AVAILABLE);

            assertThatThrownBy(() -> service.changeStatusByUser(room, request(RoomStatus.UNAVAILABLE, "Hỏng khóa cửa")))
                .isInstanceOf(UnauthorizedException.class);
            assertNothingWritten(room, RoomStatus.AVAILABLE);
        }
    }

    // ── Khóa / mở khóa: RM-11, RM-12 (BR-ROOM-03, BR-ROOM-07, BR-HK-09) ─────

    @Nested
    class LockAndUnlock {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void shouldRequireReasonWhenLockingRoom(String blank) {
            Room room = room(RoomStatus.AVAILABLE);

            assertThatThrownBy(() -> service.changeStatusByUser(room, request(RoomStatus.UNAVAILABLE, blank)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phải nhập lý do")
                .hasMessageNotContaining("BR-");
            assertNothingWritten(room, RoomStatus.AVAILABLE);
        }

        @Test
        void shouldRequireReasonWhenLockingRoomWithoutReasonField() {
            Room room = room(RoomStatus.DIRTY);

            assertThatThrownBy(() -> service.changeStatusByUser(room, request(RoomStatus.UNAVAILABLE, null)))
                .isInstanceOf(BusinessException.class);
            assertNothingWritten(room, RoomStatus.DIRTY);
        }

        @Test
        void shouldCancelOpenTasksWhenRoomBecomesUnavailable() {
            Room room = room(RoomStatus.DIRTY);

            service.changeStatusByUser(room, request(RoomStatus.UNAVAILABLE, "Thấm trần"));

            assertThat(room.getStatus()).isEqualTo(RoomStatus.UNAVAILABLE);
            assertThat(room.getUnavailableReason()).isEqualTo("Thấm trần");
            verify(hooks).onRoomBecameUnavailable(room);
            verify(hooks, never()).onRoomBecameDirty(any());
        }

        /** Lý do khóa lưu ở phòng; ra khỏi Không khả dụng thì phải xóa (ck_rooms_unavailable_reason). */
        @Test
        void shouldClearUnavailableReasonWhenUnlocking() {
            Room room = lockedRoom();

            service.changeStatusByUser(room, request(RoomStatus.AVAILABLE, null));

            assertThat(room.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
            assertThat(room.getUnavailableReason()).isNull();
        }

        @Test
        void shouldCreateCleaningTaskWhenUnlockingToDirty() {
            Room room = lockedRoom();

            service.changeStatusByUser(room, request(RoomStatus.DIRTY, null));

            assertThat(room.getStatus()).isEqualTo(RoomStatus.DIRTY);
            assertThat(room.getUnavailableReason()).isNull();
            verify(hooks).onRoomBecameDirty(room);
            verify(hooks, never()).onRoomBecameUnavailable(any());
        }

        @Test
        void shouldNotCreateTaskWhenUnlockingToAvailable() {
            service.changeStatusByUser(lockedRoom(), request(RoomStatus.AVAILABLE, null));

            verifyNoInteractions(hooks);
        }

        /** Lý do tùy chọn khi mở khóa vẫn vào lịch sử, nhưng KHÔNG nằm lại trên phòng. */
        @Test
        void shouldKeepOptionalUnlockReasonInHistoryOnly() {
            Room room = lockedRoom();

            service.changeStatusByUser(room, request(RoomStatus.AVAILABLE, "Đã thay điều hòa"));

            assertThat(room.getUnavailableReason()).isNull();
            assertThat(savedHistory().getReason()).isEqualTo("Đã thay điều hòa");
        }
    }

    // ── Nhánh Lễ tân: hệ quả gắn theo sự kiện phòng nên đúng ngay từ F2 ─────

    @Nested
    class Reception {

        @BeforeEach
        void loginAsReception() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
        }

        @Test
        void shouldRecordReceptionSourceWithoutTouchingTasksWhenReserving() {
            Room room = room(RoomStatus.AVAILABLE);

            service.changeStatusByUser(room, request(RoomStatus.RESERVED, null));

            assertThat(room.getStatus()).isEqualTo(RoomStatus.RESERVED);
            assertThat(savedHistory().getChangeSource()).isEqualTo(ChangeSource.RECEPTION);
            verifyNoInteractions(hooks);
        }

        /** BR-HK-10 rồi BR-HK-01: hủy task dọn hằng ngày TRƯỚC, sinh task check-out SAU. */
        @Test
        void shouldCancelStayoverBeforeCreatingCheckoutTaskWhenGuestChecksOut() {
            Room room = room(RoomStatus.OCCUPIED);

            service.changeStatusByUser(room, request(RoomStatus.DIRTY, null));

            InOrder order = inOrder(historyRepository, hooks);
            order.verify(historyRepository).save(any(RoomStatusHistory.class));
            order.verify(hooks).onGuestCheckedOut(room);
            order.verify(hooks).onRoomBecameDirty(room);
        }
    }

    // ── Phòng mới tạo (dùng ở F3): BR-ROOM-10 ───────────────────────────────

    @Nested
    class InitialStatus {

        @Test
        void shouldRecordFirstHistoryRowAndCreateTaskForNewRoom() {
            Room room = room(RoomStatus.DIRTY);

            service.recordInitialStatus(room);

            RoomStatusHistory row = savedHistory();
            assertThat(row.getFromStatus()).isNull();
            assertThat(row.getToStatus()).isEqualTo(RoomStatus.DIRTY);
            assertThat(row.getChangeSource()).isEqualTo(ChangeSource.SYSTEM);
            assertThat(row.getChangedBy()).isNull();
            verify(hooks).onRoomBecameDirty(room);
            verifyNoInteractions(roomRepository);   // phòng do F3 lưu, ở đây chỉ ghi lịch sử
        }

        @Test
        void shouldRejectNewRoomThatIsNotDirty() {
            Room room = room(RoomStatus.AVAILABLE);

            assertThatThrownBy(() -> service.recordInitialStatus(room)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(historyRepository, hooks);
        }
    }

    // ── API công bố cho Housekeeping (F5, F6) ───────────────────────────────

    @Nested
    class HousekeepingApi {

        @Test
        void shouldMoveDirtyToCleaningAsSystemWhenStartCleaning() {
            Room room = room(RoomStatus.DIRTY);

            service.startCleaning(room, TASK_ID);

            assertThat(room.getStatus()).isEqualTo(RoomStatus.CLEANING);
            RoomStatusHistory row = savedHistory();
            assertThat(row.getFromStatus()).isEqualTo(RoomStatus.DIRTY);
            assertThat(row.getRelatedTaskId()).isEqualTo(TASK_ID);
        }

        @Test
        void shouldRejectStartCleaningWhenRoomNotDirty() {
            Room room = room(RoomStatus.AVAILABLE);

            assertThatThrownBy(() -> service.startCleaning(room, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cần «Chờ dọn»");
            assertNothingWritten(room, RoomStatus.AVAILABLE);
        }

        @Test
        void shouldMarkPendingInspectionWithHousekeepingSource() {
            CustomUserDetails cleaner = TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            Room room = room(RoomStatus.CLEANING);

            service.markPendingInspection(room, TASK_ID);

            assertThat(room.getStatus()).isEqualTo(RoomStatus.INSPECTION);
            RoomStatusHistory row = savedHistory();
            assertThat(row.getChangeSource()).isEqualTo(ChangeSource.HOUSEKEEPING);
            assertThat(row.getChangedBy()).isEqualTo(cleaner.getId());
        }

        @Test
        void shouldRevertCleaningRoomToDirtyAsSystemWithReason() {
            Room room = room(RoomStatus.CLEANING);

            service.revertToDirty(room, TASK_ID, "Nhân viên nghỉ việc");

            assertThat(room.getStatus()).isEqualTo(RoomStatus.DIRTY);
            RoomStatusHistory row = savedHistory();
            assertThat(row.getChangeSource()).isEqualTo(ChangeSource.SYSTEM);
            assertThat(row.getReason()).isEqualTo("Nhân viên nghỉ việc");
        }

        /** OCCUPIED → DIRTY có trong ma trận (check-out) nhưng KHÔNG phải bước "gỡ người". */
        @Test
        void shouldRejectRevertToDirtyWhenRoomNotCleaning() {
            Room room = room(RoomStatus.OCCUPIED);

            assertThatThrownBy(() -> service.revertToDirty(room, TASK_ID, null))
                .isInstanceOf(BusinessException.class);
            assertNothingWritten(room, RoomStatus.OCCUPIED);
        }

        @Test
        void shouldApplyPassToAvailableAndFailToDirtyWithReason() {
            CustomUserDetails manager = TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            Room passed = room(RoomStatus.INSPECTION);
            Room failed = room(RoomStatus.INSPECTION);

            service.applyInspection(passed, InspectionResult.PASS, null, TASK_ID);
            service.applyInspection(failed, InspectionResult.FAIL, "Nhà tắm chưa sạch", TASK_ID);

            assertThat(passed.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
            assertThat(failed.getStatus()).isEqualTo(RoomStatus.DIRTY);
            ArgumentCaptor<RoomStatusHistory> rows = ArgumentCaptor.forClass(RoomStatusHistory.class);
            verify(historyRepository, times(2)).save(rows.capture());
            assertThat(rows.getAllValues()).allSatisfy(row -> {
                assertThat(row.getChangeSource()).isEqualTo(ChangeSource.MANAGER);
                assertThat(row.getChangedBy()).isEqualTo(manager.getId());
            });
            assertThat(rows.getAllValues().get(1).getReason()).isEqualTo("Nhà tắm chưa sạch");
        }

        /** RESERVED → AVAILABLE có trong ma trận (hủy đặt) nhưng KHÔNG phải kết quả kiểm tra. */
        @Test
        void shouldRejectInspectionWhenRoomNotPendingInspection() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            Room room = room(RoomStatus.RESERVED);

            assertThatThrownBy(() -> service.applyInspection(room, InspectionResult.PASS, null, TASK_ID))
                .isInstanceOf(BusinessException.class);
            assertNothingWritten(room, RoomStatus.RESERVED);
        }

        /** Task dọn do Housekeeping tự lo; gọi hook ở đây sẽ sinh / hủy task trùng. */
        @Test
        void shouldNeverCallHooksFromHousekeepingWrappers() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);

            service.startCleaning(room(RoomStatus.DIRTY), TASK_ID);
            service.markPendingInspection(room(RoomStatus.CLEANING), TASK_ID);
            service.revertToDirty(room(RoomStatus.CLEANING), TASK_ID, "Gỡ người");
            service.applyInspection(room(RoomStatus.INSPECTION), InspectionResult.FAIL, "Chưa đạt", TASK_ID);

            verifyNoInteractions(hooks);
        }
    }

    // ── Tiện ích ────────────────────────────────────────────────────────────

    private RoomStatusHistory savedHistory() {
        ArgumentCaptor<RoomStatusHistory> captor = ArgumentCaptor.forClass(RoomStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        return captor.getValue();
    }

    /** Bị chặn thì không được để lại dấu vết nào: phòng giữ trạng thái, không ghi gì, không đụng task. */
    private void assertNothingWritten(Room room, RoomStatus unchanged) {
        assertThat(room.getStatus()).isEqualTo(unchanged);
        verifyNoInteractions(roomRepository, historyRepository, hooks);
    }

    private static ChangeRoomStatusRequest request(RoomStatus target, String reason) {
        ChangeRoomStatusRequest request = new ChangeRoomStatusRequest();
        request.setTargetStatus(target);
        request.setReason(reason);
        return request;
    }

    private static Room lockedRoom() {
        Room room = room(RoomStatus.UNAVAILABLE);
        room.setUnavailableReason("Hỏng điều hòa");
        return room;
    }

    private static Room room(RoomStatus status) {
        return Room.builder()
            .id(UUID.randomUUID())
            .tenantId(TENANT_ID)
            .locationId(LOCATION_ID)
            .roomNumber("101")
            .floor("1")
            .roomTypeId(UUID.randomUUID())
            .capacity(2)
            .status(status)
            .build();
    }
}
