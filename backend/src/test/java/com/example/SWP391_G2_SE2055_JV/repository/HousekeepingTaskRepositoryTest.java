package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.config.JpaConfig;
import com.example.SWP391_G2_SE2055_JV.entity.Department;
import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Các bất biến của lịch dọn được ép ở tầng DB, và các truy vấn trên bảng nhóm dọn
 * {@code housekeeping_task_assignees} (V4) — kiểm trên MySQL thật.
 *
 * <p>Service vẫn phải tự chặn trước để trả câu lỗi dễ hiểu; những test này chứng minh rằng kể cả
 * khi service sót (hoặc hai request chạy song song) thì DB vẫn không nhận dữ liệu sai — đúng tinh
 * thần "lưới an toàn" đã dùng cho {@code rooms} ở F2/F3.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class HousekeepingTaskRepositoryTest {

    @Autowired TestEntityManager             em;
    @Autowired HousekeepingTaskRepository    repository;

    private UUID tenantId;
    private UUID locationId;
    private UUID roomId;
    private UUID hoa;
    private UUID nam;
    private final LocalDate today = LocalDate.of(2026, 10, 5);

    @BeforeEach
    void setUp() {
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        locationId = persist(Location.builder().tenantId(tenantId).name("Sao Mai Hà Nội")
            .address("Địa chỉ test").phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
        UUID roomTypeId = persist(RoomType.builder().tenantId(tenantId).name("Đôi").build()).getId();
        roomId = persist(Room.builder().tenantId(tenantId).locationId(locationId).roomNumber("201")
            .floor("2").roomTypeId(roomTypeId).capacity(2).status(RoomStatus.DIRTY).build()).getId();

        UUID department = persist(Department.builder().tenantId(tenantId).name("Buồng phòng").build()).getId();
        UUID position = persist(Position.builder().tenantId(tenantId).departmentId(department)
            .name("Nhân viên dọn phòng").positionType(PositionType.HOUSEKEEPING).build()).getId();
        hoa = persistCleaner(position, "Nguyễn Thị Hoa");
        nam = persistCleaner(position, "Trần Văn Nam");
        em.flush();
    }

    /** BR-HK-11: mỗi phòng tối đa MỘT việc dọn đang mở — cột sinh {@code open_task_key}. */
    @Test
    void shouldRejectSecondOpenTaskForSameRoom() {
        repository.saveAndFlush(task(HousekeepingTaskStatus.UNASSIGNED));

        HousekeepingTask duplicate = task(HousekeepingTaskStatus.IN_PROGRESS);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Task đã đóng nhả lại chỗ (cột sinh về NULL) — nếu không, phòng dọn xong sẽ không bao giờ dọn lại được. */
    @Test
    void shouldAllowNewTaskAfterPreviousCompleted() {
        HousekeepingTask first = task(HousekeepingTaskStatus.COMPLETED);
        first.setCompletedAt(LocalDateTime.now());
        repository.saveAndFlush(first);

        assertThatCode(() -> repository.saveAndFlush(task(HousekeepingTaskStatus.UNASSIGNED)))
            .doesNotThrowAnyException();
        assertThat(repository.findByRoomIdAndStatusIn(roomId, HousekeepingTaskStatus.OPEN_STATUSES))
            .hasSize(1);
    }

    /**
     * Đã bỏ việc dọn hằng ngày (V4): {@code ck_hk_task_type} chỉ còn nhận CHECKOUT. Ghi bằng SQL thô
     * vì enum Java không còn giá trị STAYOVER.
     *
     * <p>MySQL báo vi phạm CHECK bằng mã 3819, Spring không xếp vào {@code
     * DataIntegrityViolationException} — nên chỉ khẳng định "DB từ chối, đúng ràng buộc này".
     */
    @Test
    void shouldRejectStayoverTaskAtDbLevel() {
        assertThatThrownBy(() -> em.getEntityManager().createNativeQuery("""
                INSERT INTO housekeeping_tasks (id, tenant_id, location_id, room_id, task_type, status, created_source)
                VALUES (?, ?, ?, ?, 'STAYOVER', 'UNASSIGNED', 'CHECKOUT_AUTO')
                """)
                .setParameter(1, UUID.randomUUID().toString())
                .setParameter(2, tenantId.toString())
                .setParameter(3, locationId.toString())
                .setParameter(4, roomId.toString())
                .executeUpdate())
            .hasMessageContaining("ck_hk_task_type");
    }

    /** Một việc nhiều người dọn: cả nhóm ghi xuống và đọc lại đủ. */
    @Test
    void shouldStoreWholeTeamOfTask() {
        HousekeepingTask task = repository.saveAndFlush(inProgress(hoa, nam));
        em.clear();

        assertThat(repository.findById(task.getId()).orElseThrow().getAssigneeIds())
            .containsExactlyInAnyOrder(hoa, nam);
    }

    /** Lọc "việc có mình trong nhóm" (BR-PERM-05) và tham số null = không lọc theo người. */
    @Test
    void shouldSearchByTeamMember() {
        repository.saveAndFlush(inProgress(hoa));
        em.clear();

        assertThat(repository.search(tenantId, null, hoa, null, null, null, null, null, PageRequest.of(0, 20)))
            .hasSize(1);
        assertThat(repository.search(tenantId, null, nam, null, null, null, null, null, PageRequest.of(0, 20)))
            .isEmpty();
        assertThat(repository.search(tenantId, locationId, null, null, null, null, null, null, PageRequest.of(0, 20)))
            .hasSize(1);
    }

    /** Lịch dọn theo tuần: khoảng ngày tính cả hai đầu, việc chưa giao (không có ngày) không lọt vào. */
    @Test
    void shouldSearchByAssignedDateRange() {
        repository.saveAndFlush(inProgress(hoa));
        repository.saveAndFlush(task(HousekeepingTaskStatus.COMPLETED, today.minusDays(3)));
        em.clear();

        assertThat(repository.search(tenantId, locationId, null, null, null, null,
            today.minusDays(3), today, PageRequest.of(0, 20))).hasSize(2);
        assertThat(repository.search(tenantId, locationId, null, null, null, null,
            today.minusDays(2), today.plusDays(4), PageRequest.of(0, 20))).hasSize(1);
        assertThat(repository.search(tenantId, locationId, null, null, null, null,
            today.plusDays(1), today.plusDays(7), PageRequest.of(0, 20))).isEmpty();
    }

    /** BR-HK-03 và BR-HK-07 đi qua bảng nhóm: theo đúng ngày, sau một ngày, và đếm. */
    @Test
    void shouldFindTasksOfStaffThroughTeam() {
        repository.saveAndFlush(inProgress(hoa, nam));
        em.clear();

        assertThat(repository.findOfStaffOn(nam, today, HousekeepingTaskStatus.IN_PROGRESS)).hasSize(1);
        assertThat(repository.findOfStaffOn(nam, today.plusDays(1), HousekeepingTaskStatus.IN_PROGRESS)).isEmpty();
        assertThat(repository.findOfStaffAfter(hoa, HousekeepingTaskStatus.IN_PROGRESS, today.minusDays(1)))
            .hasSize(1);
        // "Sau hôm nay" là LỚN HƠN — việc của chính hôm nay không tính (BR-SCH-17).
        assertThat(repository.findOfStaffAfter(hoa, HousekeepingTaskStatus.IN_PROGRESS, today)).isEmpty();
        assertThat(repository.countOfStaff(hoa, HousekeepingTaskStatus.IN_PROGRESS)).isEqualTo(1);
        assertThat(repository.countOfStaff(hoa, HousekeepingTaskStatus.PENDING_INSPECTION)).isZero();
    }

    /** Người trong nhóm phải là tài khoản có thật — {@code fk_hk_assignees_staff}. */
    @Test
    void shouldRejectUnknownTeamMemberAtDbLevel() {
        HousekeepingTask task = inProgress(UUID.randomUUID());

        assertThatThrownBy(() -> repository.saveAndFlush(task))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Dữ liệu ─────────────────────────────────────────────────────────────

    private HousekeepingTask task(HousekeepingTaskStatus status) {
        return HousekeepingTask.builder()
            .tenantId(tenantId).locationId(locationId).roomId(roomId)
            .status(status)
            .createdSource(TaskCreatedSource.CHECKOUT_AUTO)
            .build();
    }

    /** Việc đã đóng của một ngày cũ — không chiếm chỗ "việc đang mở" nên đứng chung phòng được. */
    private HousekeepingTask task(HousekeepingTaskStatus status, LocalDate assignedDate) {
        HousekeepingTask task = task(status);
        task.setAssignedDate(assignedDate);
        task.setCompletedAt(LocalDateTime.now());
        task.getAssigneeIds().add(nam);
        return task;
    }

    /** Việc đang làm hôm nay của nhóm {@code team}. */
    private HousekeepingTask inProgress(UUID... team) {
        HousekeepingTask task = task(HousekeepingTaskStatus.IN_PROGRESS);
        task.setAssignedDate(today);
        task.getAssigneeIds().addAll(java.util.List.of(team));
        return task;
    }

    private UUID persistCleaner(UUID positionId, String fullName) {
        return persist(User.builder().tenantId(tenantId).locationId(locationId).role(Role.STAFF)
            .positionId(positionId).email(uniqueEmail()).passwordHash("x").status(UserStatus.ACTIVE)
            .fullName(fullName).phone("0900000000").build()).getId();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@test.local";
    }
}
