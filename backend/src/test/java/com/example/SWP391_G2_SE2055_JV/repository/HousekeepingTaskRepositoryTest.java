package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.config.JpaConfig;
import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F5 — các bất biến của lịch dọn được ép ở tầng DB, kiểm trên MySQL thật.
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

    @BeforeEach
    void setUp() {
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        locationId = persist(Location.builder().tenantId(tenantId).name("Sao Mai Hà Nội")
            .address("Địa chỉ test").phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
        UUID roomTypeId = persist(RoomType.builder().tenantId(tenantId).name("Đôi").build()).getId();
        roomId = persist(Room.builder().tenantId(tenantId).locationId(locationId).roomNumber("201")
            .floor("2").roomTypeId(roomTypeId).capacity(2).status(RoomStatus.DIRTY).build()).getId();
        em.flush();
    }

    /** BR-HK-11: mỗi phòng tối đa MỘT task đang mở cho mỗi loại — cột sinh {@code open_task_key}. */
    @Test
    void shouldRejectSecondOpenCheckoutTaskForSameRoom() {
        repository.saveAndFlush(task(HousekeepingTaskType.CHECKOUT, HousekeepingTaskStatus.UNASSIGNED));

        HousekeepingTask duplicate = task(HousekeepingTaskType.CHECKOUT, HousekeepingTaskStatus.IN_PROGRESS);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Hai LOẠI khác nhau không đụng nhau: phòng có khách vẫn vừa dọn hằng ngày vừa chờ check-out được. */
    @Test
    void shouldAllowOpenCheckoutAndStayoverForSameRoom() {
        repository.saveAndFlush(task(HousekeepingTaskType.CHECKOUT, HousekeepingTaskStatus.UNASSIGNED));

        assertThatCode(() -> repository.saveAndFlush(
            task(HousekeepingTaskType.STAYOVER, HousekeepingTaskStatus.UNASSIGNED)))
            .doesNotThrowAnyException();
    }

    /** Task đã đóng nhả lại chỗ (cột sinh về NULL) — nếu không, phòng dọn xong sẽ không bao giờ dọn lại được. */
    @Test
    void shouldAllowNewCheckoutTaskAfterPreviousCompleted() {
        HousekeepingTask first = task(HousekeepingTaskType.CHECKOUT, HousekeepingTaskStatus.COMPLETED);
        first.setCompletedAt(LocalDateTime.now());
        repository.saveAndFlush(first);

        assertThatCode(() -> repository.saveAndFlush(
            task(HousekeepingTaskType.CHECKOUT, HousekeepingTaskStatus.UNASSIGNED)))
            .doesNotThrowAnyException();
        assertThat(repository.findByRoomIdAndStatusIn(roomId, HousekeepingTaskStatus.OPEN_STATUSES))
            .hasSize(1);
    }

    /**
     * BR-HK-06: dọn hằng ngày KHÔNG có bước chờ kiểm tra. Đây là lưới cuối nếu ai đó viết nhầm
     * nhánh CHECKOUT/STAYOVER trong {@code HousekeepingService.completeTask}.
     *
     * <p>MySQL báo vi phạm CHECK bằng mã 3819, Spring không xếp vào {@code
     * DataIntegrityViolationException} — nên chỉ khẳng định "DB từ chối, đúng ràng buộc này".
     */
    @Test
    void shouldRejectPendingInspectionStayoverAtDbLevel() {
        HousekeepingTask invalid = task(HousekeepingTaskType.STAYOVER,
            HousekeepingTaskStatus.PENDING_INSPECTION);

        assertThatThrownBy(() -> repository.saveAndFlush(invalid))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("ck_hk_stayover_no_inspection");
    }

    /** Task chưa phân công thì không được có người làm — {@code ck_hk_assignment_pair}. */
    @Test
    void shouldRejectUnassignedTaskThatStillHasStaffAtDbLevel() {
        HousekeepingTask invalid = task(HousekeepingTaskType.CHECKOUT, HousekeepingTaskStatus.UNASSIGNED);
        invalid.setAssignedStaffId(UUID.randomUUID());

        assertThatThrownBy(() -> repository.saveAndFlush(invalid)).isInstanceOf(DataAccessException.class);
    }

    // ── Dữ liệu ─────────────────────────────────────────────────────────────

    private HousekeepingTask task(HousekeepingTaskType type, HousekeepingTaskStatus status) {
        return HousekeepingTask.builder()
            .tenantId(tenantId).locationId(locationId).roomId(roomId)
            .taskType(type).status(status)
            .createdSource(type == HousekeepingTaskType.CHECKOUT
                ? TaskCreatedSource.CHECKOUT_AUTO : TaskCreatedSource.MANAGER_STAYOVER)
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
