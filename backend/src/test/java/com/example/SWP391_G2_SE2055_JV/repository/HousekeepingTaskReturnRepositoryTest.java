package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.config.JpaConfig;
import com.example.SWP391_G2_SE2055_JV.entity.Department;
import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTaskReturn;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskReturnRepository.TaskReturnCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lịch sử trả lại việc dọn — BR-HK-17, bảng {@code housekeeping_task_returns} (V11) — kiểm trên MySQL thật:
 * CHECK lý do, câu đếm theo lô cho thẻ việc và thứ tự mới nhất trước.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class HousekeepingTaskReturnRepositoryTest {

    @Autowired TestEntityManager                em;
    @Autowired HousekeepingTaskReturnRepository repository;

    private UUID tenantId;
    private UUID locationId;
    private UUID roomTypeId;
    private UUID hoa;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 28, 9, 0);

    @BeforeEach
    void setUp() {
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        locationId = persist(Location.builder().tenantId(tenantId).name("Sao Mai Hà Nội")
            .address("Địa chỉ test").phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
        roomTypeId = persist(RoomType.builder().tenantId(tenantId).name("Đôi").build()).getId();
        UUID department = persist(Department.builder().tenantId(tenantId).name("Buồng phòng").build()).getId();
        UUID position = persist(Position.builder().tenantId(tenantId).departmentId(department)
            .name("Nhân viên dọn phòng").positionType(PositionType.HOUSEKEEPING).build()).getId();
        hoa = persist(User.builder().tenantId(tenantId).locationId(locationId).role(Role.STAFF)
            .positionId(position).email(uniqueEmail()).passwordHash("x").status(UserStatus.ACTIVE)
            .fullName("Nguyễn Thị Hoa").phone("0900000000").build()).getId();
        em.flush();
    }

    /**
     * Lý do toàn khoảng trắng bị {@code ck_hk_returns_reason} chặn — lưới sau lớp cắt khoảng trắng của service.
     * MySQL báo vi phạm CHECK bằng mã 3819, Spring không xếp vào {@code DataIntegrityViolationException}.
     */
    @Test
    void shouldRejectBlankReasonAtDbLevel() {
        HousekeepingTaskReturn blank = taskReturn(task("201"), "   ", now);

        assertThatThrownBy(() -> repository.saveAndFlush(blank))
            .isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("ck_hk_returns_reason");
    }

    /** Thẻ việc đọc số lần bị trả bằng MỘT câu cho cả trang; việc chưa bị trả không có mặt. */
    @Test
    void shouldCountReturnsPerTask() {
        UUID twice = task("201");
        UUID once = task("202");
        UUID never = task("203");
        repository.saveAndFlush(taskReturn(twice, "Thiếu hóa chất", now));
        repository.saveAndFlush(taskReturn(twice, "Phải đi họp", now.plusHours(1)));
        repository.saveAndFlush(taskReturn(once, "Đau lưng", now));
        em.clear();

        Map<UUID, Long> counts = repository.countGroupedByTask(List.of(twice, once, never)).stream()
            .collect(Collectors.toMap(TaskReturnCount::getTaskId, TaskReturnCount::getTotal));

        assertThat(counts).containsOnly(Map.entry(twice, 2L), Map.entry(once, 1L));
    }

    @Test
    void shouldListNewestFirstOnlyWithinTenant() {
        UUID taskId = task("201");
        repository.saveAndFlush(taskReturn(taskId, "Lần đầu", now));
        repository.saveAndFlush(taskReturn(taskId, "Lần sau", now.plusHours(2)));
        em.clear();

        assertThat(repository.findByTenantIdAndTaskIdOrderByReturnedAtDesc(tenantId, taskId))
            .extracting(HousekeepingTaskReturn::getReason)
            .containsExactly("Lần sau", "Lần đầu");
        assertThat(repository.findByTenantIdAndTaskIdOrderByReturnedAtDesc(UUID.randomUUID(), taskId)).isEmpty();
    }

    // ── Dữ liệu ─────────────────────────────────────────────────────────────

    /** Một việc chưa phân công ở một phòng riêng — mỗi phòng một việc đang mở (BR-HK-11). */
    private UUID task(String roomNumber) {
        UUID roomId = persist(Room.builder().tenantId(tenantId).locationId(locationId).roomNumber(roomNumber)
            .floor("2").roomTypeId(roomTypeId).capacity(2).status(RoomStatus.DIRTY).build()).getId();
        return persist(HousekeepingTask.builder()
            .tenantId(tenantId).locationId(locationId).roomId(roomId)
            .status(HousekeepingTaskStatus.UNASSIGNED)
            .createdSource(TaskCreatedSource.CHECKOUT_AUTO)
            .build()).getId();
    }

    private HousekeepingTaskReturn taskReturn(UUID taskId, String reason, LocalDateTime returnedAt) {
        return HousekeepingTaskReturn.builder()
            .tenantId(tenantId).taskId(taskId).staffId(hoa)
            .reason(reason).returnedAt(returnedAt).taskReleased(true)
            .build();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@test.local";
    }
}
