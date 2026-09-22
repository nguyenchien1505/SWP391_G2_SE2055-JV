package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.ChangeRoomStatusRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RoomResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusHistoryResponse;
import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCancelReason;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2 — chạy trọn luồng khóa / mở khóa phòng trên MySQL THẬT (profile "test"): RoomService →
 * RoomTransitionPolicy → RoomStatusService → HousekeepingRoomHooks → DB. Đây là bản tự động của
 * checklist kiểm tra tay F2 §3.3, chứng minh các hệ quả mà unit test chỉ kiểm bằng mock:
 * task bị hủy / sinh thật, CHECK và khóa ngoại của DB đều nhận dữ liệu.
 *
 * <p>Mỗi test chạy trong một transaction và tự rollback; {@link #flushAndClear()} ép Hibernate
 * gửi SQL xuống DB rồi đọc lại từ DB, để ràng buộc DB được kiểm thật chứ không chỉ trong bộ nhớ.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoomStatusFlowTest {

    @Autowired RoomService                roomService;
    @Autowired HousekeepingTaskRepository taskRepository;
    @Autowired EntityManager              em;

    private User manager;
    private Room available101;
    private Room occupied102;
    private Room dirty201;
    private Room locked203;
    private Room otherLocationRoom;
    private HousekeepingTask openTaskOf201;

    /** Dựng lại giống seed R__seed_test_rooms.sql (không dựa vào seed — profile test không nạp). */
    @BeforeEach
    void setUp() {
        UUID tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        UUID hanoi = persistLocation(tenantId, "Sao Mai Hà Nội");
        UUID danang = persistLocation(tenantId, "Sao Mai Đà Nẵng");
        UUID roomTypeId = persist(RoomType.builder().tenantId(tenantId).name("Đôi").build()).getId();

        manager = persist(User.builder().tenantId(tenantId).locationId(hanoi).role(Role.MANAGER)
            .email(uniqueEmail()).passwordHash("x").status(UserStatus.ACTIVE)
            .fullName("Trần Quản Lý").phone("0900000000").build());

        available101 = persistRoom(tenantId, hanoi, roomTypeId, "101", RoomStatus.AVAILABLE, null);
        occupied102 = persistRoom(tenantId, hanoi, roomTypeId, "102", RoomStatus.OCCUPIED, null);
        dirty201 = persistRoom(tenantId, hanoi, roomTypeId, "201", RoomStatus.DIRTY, null);
        locked203 = persistRoom(tenantId, hanoi, roomTypeId, "203", RoomStatus.UNAVAILABLE, "Hỏng điều hòa");
        otherLocationRoom = persistRoom(tenantId, danang, roomTypeId, "101", RoomStatus.AVAILABLE, null);
        openTaskOf201 = persist(HousekeepingTask.builder().tenantId(tenantId).locationId(hanoi)
            .roomId(dirty201.getId()).taskType(HousekeepingTaskType.CHECKOUT)
            .status(HousekeepingTaskStatus.UNASSIGNED).createdSource(TaskCreatedSource.CHECKOUT_AUTO).build());
        flushAndClear();

        TestAuth.loginAs(manager.getId(), Role.MANAGER, tenantId, hanoi, null);
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    // ── Checklist F2 §3.3 ───────────────────────────────────────────────────

    @Test
    void shouldRejectLockWithoutReasonThenLockWithReasonAndWriteOneHistoryRow() {
        assertThatThrownBy(() -> roomService.changeStatus(available101.getId(), request(RoomStatus.UNAVAILABLE, " ")))
            .isInstanceOf(BusinessException.class);

        RoomResponse locked = roomService.changeStatus(available101.getId(), request(RoomStatus.UNAVAILABLE, "Hỏng khóa cửa"));
        flushAndClear();

        assertThat(locked.getAllowedTargets()).containsExactlyInAnyOrder(RoomStatus.AVAILABLE, RoomStatus.DIRTY);
        Room saved = em.find(Room.class, available101.getId());
        assertThat(saved.getStatus()).isEqualTo(RoomStatus.UNAVAILABLE);
        assertThat(saved.getUnavailableReason()).isEqualTo("Hỏng khóa cửa");

        List<RoomStatusHistoryResponse> history = historyOf(available101);
        assertThat(history).singleElement().satisfies(row -> {
            assertThat(row.getFromStatus()).isEqualTo(RoomStatus.AVAILABLE);
            assertThat(row.getToStatus()).isEqualTo(RoomStatus.UNAVAILABLE);
            assertThat(row.getChangeSource()).isEqualTo(ChangeSource.MANAGER);
            assertThat(row.getChangedByName()).isEqualTo("Trần Quản Lý");
            assertThat(row.getReason()).isEqualTo("Hỏng khóa cửa");
        });
    }

    /** BR-HK-09: khóa phòng Chờ dọn đang có task mở → task bị hủy với lý do ROOM_UNAVAILABLE. */
    @Test
    void shouldCancelOpenCleaningTaskWhenLockingDirtyRoom() {
        roomService.changeStatus(dirty201.getId(), request(RoomStatus.UNAVAILABLE, "Thấm trần"));
        flushAndClear();

        HousekeepingTask task = em.find(HousekeepingTask.class, openTaskOf201.getId());
        assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.CANCELLED);
        assertThat(task.getCancelReason()).isEqualTo(TaskCancelReason.ROOM_UNAVAILABLE);
        assertThat(task.getCancelledAt()).isNotNull();
    }

    /** BR-HK-09: mở khóa về Chờ dọn → xóa lý do + sinh đúng 1 task CHECKOUT chưa phân công. */
    @Test
    void shouldClearReasonAndCreateUnassignedTaskWhenUnlockingToDirty() {
        roomService.changeStatus(locked203.getId(), request(RoomStatus.DIRTY, null));
        flushAndClear();

        assertThat(em.find(Room.class, locked203.getId()).getUnavailableReason()).isNull();
        assertThat(taskRepository.findByRoomIdAndStatusIn(locked203.getId(), HousekeepingTaskStatus.OPEN_STATUSES))
            .singleElement().satisfies(task -> {
                assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
                assertThat(task.getTaskType()).isEqualTo(HousekeepingTaskType.CHECKOUT);
                assertThat(task.getCreatedSource()).isEqualTo(TaskCreatedSource.CHECKOUT_AUTO);
            });
    }

    @Test
    void shouldUnlockToAvailableWithoutCreatingTask() {
        roomService.changeStatus(locked203.getId(), request(RoomStatus.AVAILABLE, "Đã sửa xong"));
        flushAndClear();

        assertThat(em.find(Room.class, locked203.getId()).getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        assertThat(taskRepository.findByRoomIdAndStatusIn(locked203.getId(), HousekeepingTaskStatus.OPEN_STATUSES)).isEmpty();
    }

    /** BR-ROOM-03: phòng có khách — Manager không thấy nút khóa; gọi thẳng API thì 400. */
    @Test
    void shouldNotOfferNorAllowLockingOccupiedRoom() {
        assertThat(roomService.getRoomById(occupied102.getId()).getAllowedTargets()).isEmpty();

        assertThatThrownBy(() -> roomService.changeStatus(occupied102.getId(), request(RoomStatus.UNAVAILABLE, "Vỡ ống nước")))
            .isInstanceOf(BusinessException.class);
        flushAndClear();
        assertThat(em.find(Room.class, occupied102.getId()).getStatus()).isEqualTo(RoomStatus.OCCUPIED);
    }

    @Test
    void shouldHideRoomOfAnotherLocationFromManager() {
        assertThatThrownBy(() -> roomService.changeStatus(otherLocationRoom.getId(), request(RoomStatus.UNAVAILABLE, "Thử")))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> roomService.getHistory(otherLocationRoom.getId(), PageRequest.of(0, 20)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Tiện ích ────────────────────────────────────────────────────────────

    private List<RoomStatusHistoryResponse> historyOf(Room room) {
        return roomService.getHistory(room.getId(), PageRequest.of(0, 20)).getContent();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private UUID persistLocation(UUID tenantId, String name) {
        return persist(Location.builder().tenantId(tenantId).name(name).address("Địa chỉ test")
            .phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
    }

    private Room persistRoom(UUID tenantId, UUID locationId, UUID roomTypeId, String number,
                             RoomStatus status, String unavailableReason) {
        return persist(Room.builder().tenantId(tenantId).locationId(locationId).roomNumber(number)
            .floor(number.substring(0, 1)).roomTypeId(roomTypeId).capacity(2)
            .status(status).unavailableReason(unavailableReason).build());
    }

    private static ChangeRoomStatusRequest request(RoomStatus target, String reason) {
        ChangeRoomStatusRequest request = new ChangeRoomStatusRequest();
        request.setTargetStatus(target);
        request.setReason(reason);
        return request;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@test.local";
    }
}
