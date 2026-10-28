package com.example.SWP391_G2_SE2055_JV.service;

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
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 F8 — chống bấm đồng thời khi tự nhận việc (BR-HK-16, BR-HK-18), trên MySQL thật.
 *
 * <p>KHÔNG {@code @Transactional}: mỗi luồng cần transaction RIÊNG và phải thấy dữ liệu đã commit của
 * luồng kia — đúng như hai người bấm trên hai máy. Vì vậy dữ liệu dựng bằng {@link TransactionTemplate}
 * (commit thật) và dọn sạch theo Tenant ở {@code @AfterEach}. Hai luồng chờ ở một chốt rồi xuất phát
 * cùng lúc; lặp vài lần để không phụ thuộc may rủi lịch luồng.
 *
 * <p>Bỏ khóa dòng hoặc bỏ READ_COMMITTED ở {@code HousekeepingService.claimTask} thì test này đỏ — đã
 * thử khi viết F8.
 */
@SpringBootTest
@ActiveProfiles("test")
class OpenTaskConcurrencyTest {

    private static final int REPETITIONS = 5;

    @Autowired HousekeepingService housekeepingService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired EntityManager       em;
    @Autowired JdbcTemplate        jdbc;

    private UUID tenantId;
    private UUID locationId;
    private UUID hoa;
    private UUID nam;
    private UUID room201;
    private UUID room202;
    private UUID taskOf201;
    private UUID taskOf202;

    /** Hai nhân viên dọn đã VÀO CA hôm nay; hai phòng Chờ dọn, mỗi phòng một việc chưa phân công. */
    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
                .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
            locationId = persist(Location.builder().tenantId(tenantId).name("Sao Mai Hà Nội")
                .address("Địa chỉ test").phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
            UUID roomType = persist(RoomType.builder().tenantId(tenantId).name("Đôi").build()).getId();
            UUID department = persist(Department.builder().tenantId(tenantId).name("Buồng phòng").build()).getId();
            UUID position = persist(Position.builder().tenantId(tenantId).departmentId(department)
                .name("Nhân viên dọn phòng").positionType(PositionType.HOUSEKEEPING).build()).getId();

            hoa = persistCheckedInCleaner(position, "Nguyễn Thị Hoa");
            nam = persistCheckedInCleaner(position, "Trần Văn Nam");
            room201 = persistDirtyRoom(roomType, "201");
            room202 = persistDirtyRoom(roomType, "202");
            taskOf201 = persistUnassignedTask(room201);
            taskOf202 = persistUnassignedTask(room202);
        });
    }

    @AfterEach
    void cleanUp() {
        String byTenant = " WHERE tenant_id = ?";
        String tenant = tenantId.toString();
        jdbc.update("DELETE FROM room_status_history" + byTenant, tenant);
        jdbc.update("DELETE FROM housekeeping_task_assignees WHERE task_id IN "
            + "(SELECT id FROM housekeeping_tasks" + byTenant + ")", tenant);
        jdbc.update("DELETE FROM housekeeping_tasks" + byTenant, tenant);
        jdbc.update("DELETE FROM rooms" + byTenant, tenant);
        jdbc.update("DELETE FROM shifts" + byTenant, tenant);
        jdbc.update("DELETE FROM user_permissions WHERE user_id IN (SELECT id FROM users" + byTenant + ")", tenant);
        for (String table : List.of("users", "positions", "departments", "room_types", "locations")) {
            jdbc.update("DELETE FROM " + table + byTenant, tenant);
        }
        jdbc.update("DELETE FROM tenants WHERE id = ?", tenant);
    }

    /** BR-HK-18: hai người cùng bấm một phòng — đúng MỘT người được, người kia nhận câu báo rõ ràng. */
    @RepeatedTest(REPETITIONS)
    void shouldLetOnlyOneOfTwoCleanersClaimSameTask() throws Exception {
        List<Object> results = claimAtTheSameTime(List.of(hoa, nam), List.of(taskOf201, taskOf201));

        assertThat(results).filteredOn(HousekeepingTaskResponse.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(BusinessException.class::isInstance).singleElement()
            .satisfies(error -> assertThat(((BusinessException) error).getMessage()).contains("vừa có người nhận"));

        assertThat(count("SELECT COUNT(*) FROM housekeeping_task_assignees WHERE task_id = ?", taskOf201))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM rooms WHERE id = ?", String.class, room201.toString()))
            .isEqualTo(RoomStatus.CLEANING.name());
        assertThat(count("SELECT COUNT(*) FROM room_status_history WHERE room_id = ?", room201)).isEqualTo(1);
    }

    /** BR-HK-16: một người bấm hai phòng cùng lúc (bấm đúp, hai tab) — chỉ nhận được MỘT. */
    @RepeatedTest(REPETITIONS)
    void shouldNotLetOneCleanerClaimTwoTasksAtOnce() throws Exception {
        List<Object> results = claimAtTheSameTime(List.of(hoa, hoa), List.of(taskOf201, taskOf202));

        assertThat(results).filteredOn(HousekeepingTaskResponse.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(BusinessException.class::isInstance).singleElement()
            .satisfies(error -> assertThat(((BusinessException) error).getMessage()).contains("Bạn đang dọn phòng"));

        assertThat(count("SELECT COUNT(*) FROM housekeeping_task_assignees a "
            + "JOIN housekeeping_tasks t ON t.id = a.task_id "
            + "WHERE a.staff_id = ? AND t.status = 'IN_PROGRESS'", hoa)).isEqualTo(1);
    }

    // ── Tiện ích ────────────────────────────────────────────────────────────

    /**
     * Hai lệnh "Nhận việc" chờ ở một chốt rồi xuất phát cùng lúc, mỗi lệnh một luồng (SecurityContext gắn
     * theo luồng). Trả về theo thứ tự: response khi nhận được, hoặc exception khi bị từ chối.
     */
    private List<Object> claimAtTheSameTime(List<UUID> staffIds, List<UUID> taskIds) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Object>> outcomes = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                UUID staffId = staffIds.get(i);
                UUID taskId = taskIds.get(i);
                outcomes.add(pool.submit(() -> {
                    TestAuth.loginAs(staffId, Role.STAFF, tenantId, locationId, PositionType.HOUSEKEEPING);
                    ready.countDown();
                    go.await();
                    try {
                        return housekeepingService.claimTask(taskId);
                    } catch (RuntimeException ex) {
                        return ex;
                    } finally {
                        TestAuth.logout();
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> outcome : outcomes) {
                results.add(outcome.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private int count(String sql, UUID id) {
        return jdbc.queryForObject(sql, Integer.class, id.toString());
    }

    private UUID persistCheckedInCleaner(UUID positionId, String fullName) {
        UUID id = persist(User.builder().tenantId(tenantId).locationId(locationId).role(Role.STAFF)
            .positionId(positionId).permissions(EnumSet.of(StaffPermission.HOUSEKEEPING))
            .email(uniqueEmail()).passwordHash("x").status(UserStatus.ACTIVE)
            .fullName(fullName).phone("0900000000").build()).getId();
        LocalDate today = ShiftTimeUtils.todayInHanoi();
        persist(Shift.builder().tenantId(tenantId).locationId(locationId).staffId(id).shiftDate(today)
            .startTime(LocalTime.of(6, 0)).endTime(LocalTime.of(14, 0)).durationHours(new BigDecimal("8.00"))
            .checkInAt(LocalDateTime.now()).build());
        return id;
    }

    private UUID persistDirtyRoom(UUID roomTypeId, String number) {
        return persist(Room.builder().tenantId(tenantId).locationId(locationId).roomNumber(number)
            .floor("2").roomTypeId(roomTypeId).capacity(2).status(RoomStatus.DIRTY).build()).getId();
    }

    private UUID persistUnassignedTask(UUID roomId) {
        return persist(HousekeepingTask.builder().tenantId(tenantId).locationId(locationId).roomId(roomId)
            .status(HousekeepingTaskStatus.UNASSIGNED).createdSource(TaskCreatedSource.CHECKOUT_AUTO).build()).getId();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@test.local";
    }
}
