package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AssignTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.CreateStayoverTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.HousekeepingTaskResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Department;
import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F5 — chạy TRỌN vòng dọn phòng sau check-out trên MySQL thật (profile "test"):
 * HousekeepingService → RoomStatusService → DB, cùng transaction.
 *
 * <p>Đây là lưới an toàn cho phần tích hợp giai đoạn 2: unit test chỉ kiểm được "có gọi
 * RoomStatusService không", còn những thứ chỉ vỡ khi chạm DB thật thì phải ở đây —
 * {@code uk_hk_open_task_per_room_type} (không sinh task trùng), {@code ck_hk_stayover_no_inspection},
 * và việc phòng với task luôn khớp nhau sau mỗi bước.
 *
 * <p>Mỗi test chạy trong một transaction và tự rollback. {@link #flushAndClear()} ép Hibernate gửi
 * SQL xuống DB rồi đọc lại, để ràng buộc DB được kiểm thật chứ không chỉ trong bộ nhớ.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HousekeepingFlowTest {

    @Autowired HousekeepingService         housekeepingService;
    @Autowired RoomService                 roomService;
    @Autowired HousekeepingTaskRepository  taskRepository;
    @Autowired EntityManager               em;

    private UUID tenantId;
    private UUID hanoi;
    private User manager;
    private User cleaner;
    private Room dirty201;
    private Room occupied102;
    private HousekeepingTask checkoutTaskOf201;
    private LocalDate today;

    /** Dựng đúng tình huống sau khi Lễ tân vừa check-out: phòng Chờ dọn + 1 task chưa phân công. */
    @BeforeEach
    void setUp() {
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        hanoi = persist(Location.builder().tenantId(tenantId).name("Sao Mai Hà Nội")
            .address("Địa chỉ test").phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
        today = ShiftTimeUtils.todayAt("Asia/Ho_Chi_Minh");

        UUID roomTypeId = persist(RoomType.builder().tenantId(tenantId).name("Đôi").build()).getId();
        UUID department = persist(Department.builder().tenantId(tenantId).name("Buồng phòng").build()).getId();
        UUID cleanerPosition = persist(Position.builder().tenantId(tenantId).departmentId(department)
            .name("Nhân viên dọn phòng").positionType(PositionType.HOUSEKEEPING).build()).getId();

        manager = persist(User.builder().tenantId(tenantId).locationId(hanoi).role(Role.MANAGER)
            .email(uniqueEmail()).passwordHash("x").status(UserStatus.ACTIVE)
            .fullName("Trần Quản Lý").phone("0900000000").build());
        cleaner = persist(User.builder().tenantId(tenantId).locationId(hanoi).role(Role.STAFF)
            .positionId(cleanerPosition).email(uniqueEmail()).passwordHash("x").status(UserStatus.ACTIVE)
            .fullName("Phạm Dọn Dẹp").phone("0900000001").build());
        // BR-HK-03: có ca trong ngày mới nhận được task.
        persist(Shift.builder().tenantId(tenantId).locationId(hanoi).staffId(cleaner.getId())
            .shiftDate(today).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(16, 0))
            .durationHours(new BigDecimal("8.00")).build());

        dirty201 = persistRoom(roomTypeId, "201", RoomStatus.DIRTY);
        occupied102 = persistRoom(roomTypeId, "102", RoomStatus.OCCUPIED);
        checkoutTaskOf201 = persist(HousekeepingTask.builder()
            .tenantId(tenantId).locationId(hanoi).roomId(dirty201.getId())
            .taskType(HousekeepingTaskType.CHECKOUT).status(HousekeepingTaskStatus.UNASSIGNED)
            .createdSource(TaskCreatedSource.CHECKOUT_AUTO).build());

        flushAndClear();
        loginAsManager();
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    // ── Checklist F5 §4.3 ───────────────────────────────────────────────────

    /** RM-14 — phân công việc dọn sau check-out kéo phòng sang «Đang dọn», nguồn Hệ thống. */
    @Test
    void shouldMoveRoomToCleaningWhenManagerAssignsCheckoutTask() {
        housekeepingService.assignTask(checkoutTaskOf201.getId(), assignRequest(today));
        flushAndClear();

        HousekeepingTask task = em.find(HousekeepingTask.class, checkoutTaskOf201.getId());
        assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
        assertThat(task.getAssignedStaffId()).isEqualTo(cleaner.getId());
        assertThat(em.find(Room.class, dirty201.getId()).getStatus()).isEqualTo(RoomStatus.CLEANING);

        // BR-ROOM-09: bước này do hệ thống gây ra, không phải Manager tự đổi trạng thái phòng.
        assertThat(historyOf(dirty201)).first().satisfies(row -> {
            assertThat(row.getFromStatus()).isEqualTo(RoomStatus.DIRTY);
            assertThat(row.getToStatus()).isEqualTo(RoomStatus.CLEANING);
            assertThat(row.getChangedByName()).isNull();
        });
    }

    /** RM-15 — gỡ người: phòng về «Chờ dọn», task VẪN MỞ nên không sinh task thứ hai (BR-HK-11). */
    @Test
    void shouldRevertRoomAndKeepSingleOpenTaskWhenManagerUnassigns() {
        housekeepingService.assignTask(checkoutTaskOf201.getId(), assignRequest(today));
        housekeepingService.unassignTask(checkoutTaskOf201.getId());
        flushAndClear();

        HousekeepingTask task = em.find(HousekeepingTask.class, checkoutTaskOf201.getId());
        assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
        assertThat(task.getAssignedStaffId()).isNull();
        assertThat(em.find(Room.class, dirty201.getId()).getStatus()).isEqualTo(RoomStatus.DIRTY);
        assertThat(taskRepository.findByRoomIdAndStatusIn(dirty201.getId(), HousekeepingTaskStatus.OPEN_STATUSES))
            .hasSize(1);
    }

    /** RM-18 — nhân viên bấm hoàn thành: task sang «Chờ kiểm tra», phòng cũng vậy (BR-HK-06). */
    @Test
    void shouldMoveBothTaskAndRoomToPendingInspectionOnComplete() {
        housekeepingService.assignTask(checkoutTaskOf201.getId(), assignRequest(today));
        loginAsCleaner();

        housekeepingService.completeTask(checkoutTaskOf201.getId());
        flushAndClear();

        HousekeepingTask task = em.find(HousekeepingTask.class, checkoutTaskOf201.getId());
        assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.PENDING_INSPECTION);
        assertThat(task.getCompletedAt()).isNull();   // chưa nghiệm thu thì chưa phải là xong
        assertThat(em.find(Room.class, dirty201.getId()).getStatus()).isEqualTo(RoomStatus.INSPECTION);
    }

    /** BR-HK-05 — dọn hằng ngày đi trọn vòng mà KHÔNG đụng tới trạng thái phòng. */
    @Test
    void shouldRunStayoverCycleWithoutTouchingRoomStatus() {
        CreateStayoverTaskRequest request = new CreateStayoverTaskRequest();
        request.setRoomId(occupied102.getId());
        HousekeepingTaskResponse created = housekeepingService.createStayoverTask(request);

        housekeepingService.assignTask(created.getId(), assignRequest(today));
        loginAsCleaner();
        housekeepingService.completeTask(created.getId());
        flushAndClear();

        HousekeepingTask task = em.find(HousekeepingTask.class, created.getId());
        assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isNotNull();
        assertThat(em.find(Room.class, occupied102.getId()).getStatus()).isEqualTo(RoomStatus.OCCUPIED);
        assertThat(historyOf(occupied102)).isEmpty();
    }

    /**
     * Cả hai thay đổi nằm trong MỘT transaction: phòng bị khóa giữa chừng thì lệnh phân công phải
     * hỏng hoàn toàn, không được để task "đang làm" trên một phòng Không khả dụng.
     */
    @Test
    void shouldRollbackAssignWhenRoomIsNoLongerDirty() {
        roomService.changeStatus(dirty201.getId(), lockRequest());   // Manager khóa phòng
        flushAndClear();

        assertThatThrownBy(() -> housekeepingService.assignTask(checkoutTaskOf201.getId(), assignRequest(today)))
            .isInstanceOf(BusinessException.class);
        flushAndClear();

        // Khóa phòng đã hủy task cũ (BR-HK-09) — và lệnh phân công không để lại dấu vết nào.
        HousekeepingTask task = em.find(HousekeepingTask.class, checkoutTaskOf201.getId());
        assertThat(task.getAssignedStaffId()).isNull();
        assertThat(em.find(Room.class, dirty201.getId()).getStatus()).isEqualTo(RoomStatus.UNAVAILABLE);
    }

    /** Q5 — việc dọn sau check-out chỉ gán được cho hôm nay. */
    @Test
    void shouldRejectAssigningCheckoutTaskToTomorrow() {
        assertThatThrownBy(() ->
            housekeepingService.assignTask(checkoutTaskOf201.getId(), assignRequest(today.plusDays(1))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("hôm nay");
    }

    /** S-10 — chỉ người dọn có ca hôm nay mới hiện ra; hôm sau chưa xếp ca nên rỗng. */
    @Test
    void shouldListOnlyStaffWithShiftToday() {
        assertThat(housekeepingService.getAssignableStaff(today))
            .extracting(staff -> staff.getFullName())
            .containsExactly("Phạm Dọn Dẹp");
        assertThat(housekeepingService.getAssignableStaff(today.plusDays(1))).isEmpty();
    }

    // ── Tiện ích ────────────────────────────────────────────────────────────

    private void loginAsManager() {
        TestAuth.loginAs(manager.getId(), Role.MANAGER, tenantId, hanoi, null);
    }

    private void loginAsCleaner() {
        TestAuth.loginAs(cleaner.getId(), Role.STAFF, tenantId, hanoi, PositionType.HOUSEKEEPING);
    }

    private AssignTaskRequest assignRequest(LocalDate date) {
        AssignTaskRequest request = new AssignTaskRequest();
        request.setStaffId(cleaner.getId());
        request.setAssignedDate(date);
        return request;
    }

    private static com.example.SWP391_G2_SE2055_JV.dto.ChangeRoomStatusRequest lockRequest() {
        var request = new com.example.SWP391_G2_SE2055_JV.dto.ChangeRoomStatusRequest();
        request.setTargetStatus(RoomStatus.UNAVAILABLE);
        request.setReason("Vỡ ống nước");
        return request;
    }

    private java.util.List<com.example.SWP391_G2_SE2055_JV.dto.RoomStatusHistoryResponse> historyOf(Room room) {
        return roomService.getHistory(room.getId(), PageRequest.of(0, 20)).getContent();
    }

    private Room persistRoom(UUID roomTypeId, String number, RoomStatus status) {
        return persist(Room.builder().tenantId(tenantId).locationId(hanoi).roomNumber(number)
            .floor(number.substring(0, 1)).roomTypeId(roomTypeId).capacity(2).status(status).build());
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@test.local";
    }
}
