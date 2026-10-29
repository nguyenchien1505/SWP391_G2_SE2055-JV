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
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.example.SWP391_G2_SE2055_JV.enums.StaffPermission.HOUSEKEEPING;
import static com.example.SWP391_G2_SE2055_JV.enums.StaffPermission.RECEPTION;
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
     * Một người ĐÚNG mọi điều kiện, và bên cạnh là các biến thể sai đúng một điều: không được
     * tick quyền Dọn dẹp, sai Location, đã nghỉ việc, không có ca.
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

        persistStaffWithShift("Phạm Dọn Dẹp", hanoi, housekeepingPosition, HOUSEKEEPING, UserStatus.ACTIVE, today);
        persistStaffWithShift("Lê Lễ Tân", hanoi, receptionPosition, RECEPTION, UserStatus.ACTIVE, today);
        persistStaffWithShift("Trần Đà Nẵng", danang, housekeepingPosition, HOUSEKEEPING, UserStatus.ACTIVE, today);
        persistStaffWithShift("Nguyễn Nghỉ Việc", hanoi, housekeepingPosition, HOUSEKEEPING, UserStatus.TERMINATED, today);
        persistStaffWithShift("Vũ Không Ca", hanoi, housekeepingPosition, HOUSEKEEPING, UserStatus.ACTIVE, today.plusDays(1));

        em.flush();
        em.clear();
    }

    @Test
    void shouldReturnOnlyActiveHousekeepingStaffOfLocationWithShiftThatDay() {
        assertThat(names(findAssignable(hanoi, today))).containsExactly("Phạm Dọn Dẹp");
    }

    /** Lễ tân có ca cùng ngày nhưng không được tick quyền Dọn dẹp. */
    @Test
    void shouldExcludeStaffWithoutHousekeepingPermission() {
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

    /** Nhân viên đa nhiệm: vị trí Lễ tân, được tick CẢ Lễ tân và Dọn dẹp — nhận được việc dọn. */
    @Test
    void shouldIncludeStaffWithHousekeepingAmongSeveralPermissions() {
        persistStaffWithShift("Đỗ Đa Nhiệm", hanoi, receptionPosition,
            EnumSet.of(StaffPermission.RECEPTION, StaffPermission.HOUSEKEEPING), UserStatus.ACTIVE, today);
        em.flush();
        em.clear();

        assertThat(names(findAssignable(hanoi, today)))
            .containsExactlyInAnyOrder("Phạm Dọn Dẹp", "Đỗ Đa Nhiệm");
    }

    /** Quyền quyết định theo ô Manager tick, KHÔNG theo Loại của vị trí: vị trí Dọn dẹp mà bỏ tick thì không nhận việc. */
    @Test
    void shouldExcludeHousekeepingPositionWithoutTickedPermission() {
        persistStaffWithShift("Hà Bỏ Tick", hanoi, housekeepingPosition,
            EnumSet.noneOf(StaffPermission.class), UserStatus.ACTIVE, today);
        em.flush();
        em.clear();

        assertThat(names(findAssignable(hanoi, today))).doesNotContain("Hà Bỏ Tick");
    }

    @Test
    void shouldNotReturnStaffOfAnotherTenant() {
        assertThat(repository.findAssignable(UUID.randomUUID(), hanoi, today,
            Role.STAFF, UserStatus.ACTIVE, StaffPermission.HOUSEKEEPING)).isEmpty();
    }

    /** Ca phải ở chính khách sạn của phòng — khớp luật giao việc ({@code CleaningWorkDayRule}). */
    @Test
    void shouldExcludeStaffWhoseShiftIsAtAnotherLocation() {
        UUID roaming = persistHousekeeper("Đinh Ca Đà Nẵng");
        persistShift(roaming, danang, today);
        em.flush();
        em.clear();

        assertThat(names(findAssignable(hanoi, today))).doesNotContain("Đinh Ca Đà Nẵng");
    }

    // ── Ca qua đêm bắt đầu hôm qua — BR-SCH-03 (M2, chốt 28/10/2026) ────────

    /**
     * 02:00 sáng: người ca 22:00–06:00 hôm qua còn đang làm; người ca đêm kết thúc lúc 01:00 đã về; người
     * ca ngày hôm qua và Lễ tân ca đêm đều không hiện.
     */
    @Test
    void shouldListOnlyHousekeepersStillOnOvernightShift() {
        LocalDate yesterday = today.minusDays(1);
        persistNightShift(persistHousekeeper("Phan Còn Ca Đêm"), yesterday, LocalTime.of(6, 0));
        persistNightShift(persistHousekeeper("Lý Hết Ca Đêm"), yesterday, LocalTime.of(1, 0));
        persistShift(persistHousekeeper("Mai Ca Ngày"), hanoi, yesterday);
        UUID nightReceptionist = persist(User.builder()
            .tenantId(tenantId).locationId(hanoi).positionId(receptionPosition)
            .permissions(EnumSet.of(RECEPTION))
            .role(Role.STAFF).status(UserStatus.ACTIVE)
            .email(uniqueEmail()).passwordHash("x").fullName("Lê Lễ Tân Đêm").phone("0900000000")
            .build()).getId();
        persistNightShift(nightReceptionist, yesterday, LocalTime.of(6, 0));
        em.flush();
        em.clear();

        List<User> onNightShift = repository.findOnOvernightShift(tenantId, hanoi, yesterday, LocalTime.of(2, 0),
            Role.STAFF, UserStatus.ACTIVE, StaffPermission.HOUSEKEEPING);

        assertThat(names(onNightShift)).containsExactly("Phan Còn Ca Đêm");
    }

    @Test
    void shouldNotListNightStaffOfAnotherLocation() {
        LocalDate yesterday = today.minusDays(1);
        persistNightShift(persistHousekeeper("Phan Còn Ca Đêm"), yesterday, LocalTime.of(6, 0));
        em.flush();
        em.clear();

        assertThat(repository.findOnOvernightShift(tenantId, danang, yesterday, LocalTime.of(2, 0),
            Role.STAFF, UserStatus.ACTIVE, StaffPermission.HOUSEKEEPING)).isEmpty();
    }

    // ── Dữ liệu ─────────────────────────────────────────────────────────────

    private List<User> findAssignable(UUID locationId, LocalDate date) {
        return repository.findAssignable(tenantId, locationId, date,
            Role.STAFF, UserStatus.ACTIVE, StaffPermission.HOUSEKEEPING);
    }

    private static List<String> names(List<User> staff) {
        return staff.stream().map(User::getFullName).toList();
    }

    private void persistStaffWithShift(String fullName, UUID locationId, UUID positionId,
                                       StaffPermission permission, UserStatus status, LocalDate shiftDate) {
        persistStaffWithShift(fullName, locationId, positionId, EnumSet.of(permission), status, shiftDate);
    }

    private void persistStaffWithShift(String fullName, UUID locationId, UUID positionId,
                                       Set<StaffPermission> permissions, UserStatus status, LocalDate shiftDate) {
        User staff = persist(User.builder()
            .tenantId(tenantId).locationId(locationId).positionId(positionId)
            .permissions(permissions.isEmpty() ? EnumSet.noneOf(StaffPermission.class) : EnumSet.copyOf(permissions))
            .role(Role.STAFF).status(status)
            .email(uniqueEmail()).passwordHash("x").fullName(fullName).phone("0900000000")
            .build());
        persistShift(staff.getId(), locationId, shiftDate);
    }

    /** Nhân viên dọn phòng ở Hà Nội, chưa có ca nào — test tự xếp ca. */
    private UUID persistHousekeeper(String fullName) {
        return persist(User.builder()
            .tenantId(tenantId).locationId(hanoi).positionId(housekeepingPosition)
            .permissions(EnumSet.of(HOUSEKEEPING))
            .role(Role.STAFF).status(UserStatus.ACTIVE)
            .email(uniqueEmail()).passwordHash("x").fullName(fullName).phone("0900000000")
            .build()).getId();
    }

    /** Ca qua đêm 22:00 → {@code end} ngày hôm sau, ở Hà Nội — BR-SCH-03: thuộc ngày {@code date}. */
    private void persistNightShift(UUID staffId, LocalDate date, LocalTime end) {
        persist(Shift.builder()
            .tenantId(tenantId).locationId(hanoi).staffId(staffId).shiftDate(date)
            .startTime(LocalTime.of(22, 0)).endTime(end).overnight(true)
            .durationHours(new BigDecimal("8.00"))
            .build());
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
