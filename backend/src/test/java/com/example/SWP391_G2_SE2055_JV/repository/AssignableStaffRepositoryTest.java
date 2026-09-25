package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.config.JpaConfig;
import com.example.SWP391_G2_SE2055_JV.entity.Department;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F5 — ai hiện ra trong hộp thoại phân công (S-10): BR-HK-02, BR-HK-03, BR-PERM-05.
 *
 * <p>Truy vấn này nối 3 bảng (users, positions, shifts) nên phải chạy trên MySQL thật thay vì
 * mock — xem {@code application-test.yaml}. Mỗi test dựng đúng MỘT người sai điều kiện để chứng
 * minh từng mệnh đề của {@code @Query} đều có tác dụng.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class AssignableStaffRepositoryTest {

    @Autowired TestEntityManager            em;
    @Autowired AssignableStaffRepository    repository;

    private UUID tenantId;
    private UUID hanoi;
    private UUID danang;
    private UUID department;
    private UUID housekeepingPosition;
    private UUID receptionPosition;
    private LocalDate today;

    /**
     * Một người ĐÚNG mọi điều kiện, và bên cạnh là các biến thể sai đúng một điều: sai Loại
     * Position, sai Location, đã nghỉ việc, không có ca.
     */
    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        hanoi = persistLocation("Sao Mai Hà Nội");
        danang = persistLocation("Sao Mai Đà Nẵng");

        department = persist(Department.builder().tenantId(tenantId).name("Buồng phòng").build()).getId();
        housekeepingPosition = persistPosition(department, "Nhân viên dọn phòng", PositionType.HOUSEKEEPING);
        receptionPosition = persistPosition(department, "Nhân viên lễ tân", PositionType.RECEPTION);

        persistStaffWithShift("Phạm Dọn Dẹp", hanoi, housekeepingPosition, UserStatus.ACTIVE, today);
        persistStaffWithShift("Lê Lễ Tân", hanoi, receptionPosition, UserStatus.ACTIVE, today);
        persistStaffWithShift("Trần Đà Nẵng", danang, housekeepingPosition, UserStatus.ACTIVE, today);
        persistStaffWithShift("Nguyễn Nghỉ Việc", hanoi, housekeepingPosition, UserStatus.TERMINATED, today);
        persistStaffWithShift("Vũ Không Ca", hanoi, housekeepingPosition, UserStatus.ACTIVE, today.plusDays(1));

        em.flush();
        em.clear();
    }

    @Test
    void shouldReturnOnlyActiveHousekeepingStaffOfLocationWithShiftThatDay() {
        assertThat(names(findAssignable(hanoi, today))).containsExactly("Phạm Dọn Dẹp");
    }

    /** Lễ tân có ca cùng ngày nhưng sai Loại Position — BR-ORG-08: quyền theo LOẠI, không theo tên. */
    @Test
    void shouldExcludeStaffOfOtherPositionType() {
        assertThat(names(findAssignable(hanoi, today))).doesNotContain("Lê Lễ Tân");
    }

    /** Người dọn ở khách sạn khác không được gán — BR-PERM-03. */
    @Test
    void shouldExcludeStaffOfAnotherLocation() {
        assertThat(names(findAssignable(danang, today))).containsExactly("Trần Đà Nẵng");
        assertThat(names(findAssignable(hanoi, today))).doesNotContain("Trần Đà Nẵng");
    }

    @Test
    void shouldExcludeTerminatedStaff() {
        assertThat(names(findAssignable(hanoi, today))).doesNotContain("Nguyễn Nghỉ Việc");
    }

    /** BR-HK-03: không có ca đúng ngày thì không gán được — hôm sau mới hiện ra. */
    @Test
    void shouldExcludeStaffWithoutShiftOnThatDay() {
        assertThat(names(findAssignable(hanoi, today))).doesNotContain("Vũ Không Ca");
        assertThat(names(findAssignable(hanoi, today.plusDays(1)))).containsExactly("Vũ Không Ca");
    }

    /** Một người có 2 ca trong ngày vẫn chỉ hiện MỘT lần — lý do dùng `exists` thay vì `join`. */
    @Test
    void shouldNotDuplicateStaffWithTwoShiftsInOneDay() {
        User staff = em.getEntityManager()
            .createQuery("select u from User u where u.fullName = 'Phạm Dọn Dẹp'", User.class)
            .getSingleResult();
        persistShift(staff.getId(), hanoi, today);
        em.flush();
        em.clear();

        assertThat(findAssignable(hanoi, today)).hasSize(1);
    }

    /** Nhân viên đa nhiệm: vị trí chính Lễ tân, KIÊM NHIỆM Dọn dẹp — vẫn nhận được việc dọn. */
    @Test
    void shouldIncludeStaffHoldingHousekeepingAsExtraPosition() {
        persistStaffWithShift("Đỗ Đa Nhiệm", hanoi, receptionPosition, Set.of(housekeepingPosition),
            UserStatus.ACTIVE, today);
        em.flush();
        em.clear();

        assertThat(names(findAssignable(hanoi, today)))
            .containsExactlyInAnyOrder("Phạm Dọn Dẹp", "Đỗ Đa Nhiệm");
    }

    /** Giữ HAI vị trí cùng Loại Dọn dẹp (chính + kiêm nhiệm) vẫn chỉ hiện MỘT lần. */
    @Test
    void shouldNotDuplicateStaffHoldingTwoHousekeepingPositions() {
        UUID supervisor = persistPosition(department, "Giám sát buồng", PositionType.HOUSEKEEPING);
        persistStaffWithShift("Hồ Hai Vị Trí", hanoi, housekeepingPosition, Set.of(supervisor),
            UserStatus.ACTIVE, today);
        em.flush();
        em.clear();

        assertThat(names(findAssignable(hanoi, today)))
            .containsExactlyInAnyOrder("Phạm Dọn Dẹp", "Hồ Hai Vị Trí");
    }

    @Test
    void shouldNotReturnStaffOfAnotherTenant() {
        assertThat(repository.findAssignable(UUID.randomUUID(), hanoi, today,
            Role.STAFF, UserStatus.ACTIVE, PositionType.HOUSEKEEPING)).isEmpty();
    }

    // ── Dữ liệu ─────────────────────────────────────────────────────────────

    private List<User> findAssignable(UUID locationId, LocalDate date) {
        return repository.findAssignable(tenantId, locationId, date,
            Role.STAFF, UserStatus.ACTIVE, PositionType.HOUSEKEEPING);
    }

    private static List<String> names(List<User> staff) {
        return staff.stream().map(User::getFullName).toList();
    }

    private void persistStaffWithShift(String fullName, UUID locationId, UUID positionId,
                                       UserStatus status, LocalDate shiftDate) {
        persistStaffWithShift(fullName, locationId, positionId, Set.of(), status, shiftDate);
    }

    private void persistStaffWithShift(String fullName, UUID locationId, UUID positionId,
                                       Set<UUID> extraPositionIds, UserStatus status, LocalDate shiftDate) {
        User staff = persist(User.builder()
            .tenantId(tenantId).locationId(locationId).positionId(positionId)
            .extraPositionIds(new LinkedHashSet<>(extraPositionIds))
            .role(Role.STAFF).status(status)
            .email(uniqueEmail()).passwordHash("x").fullName(fullName).phone("0900000000")
            .build());
        persistShift(staff.getId(), locationId, shiftDate);
    }

    private void persistShift(UUID staffId, UUID locationId, LocalDate date) {
        persist(Shift.builder()
            .tenantId(tenantId).locationId(locationId).staffId(staffId).shiftDate(date)
            .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(16, 0))
            .durationHours(new BigDecimal("8.00"))
            .build());
    }

    private UUID persistLocation(String name) {
        return persist(Location.builder().tenantId(tenantId).name(name).address("Địa chỉ test")
            .phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
    }

    private UUID persistPosition(UUID departmentId, String name, PositionType type) {
        return persist(Position.builder().tenantId(tenantId).departmentId(departmentId)
            .name(name).positionType(type).build()).getId();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@test.local";
    }
}
