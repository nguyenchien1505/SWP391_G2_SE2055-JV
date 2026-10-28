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
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 F7 — ca làm việc nhìn từ phía lịch dọn (BR-SCH-03, BR-HK-03, BR-HK-04). Chạy trên MySQL thật vì
 * {@code @Query} lọc theo cột {@code is_overnight} và khoảng ngày — xem {@code application-test.yaml}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class HousekeepingShiftRepositoryTest {

    @Autowired TestEntityManager           em;
    @Autowired HousekeepingShiftRepository repository;

    private UUID tenantId;
    private UUID hanoi;
    private UUID danang;
    private UUID cleaner;
    private UUID dayCleaner;
    private LocalDate today;
    private LocalDate yesterday;
    private Shift nightShiftOfYesterday;
    private Shift dayShiftOfToday;

    /**
     * Người dọn ca đêm: ca đêm hôm qua + ca ngày hôm nay ở Hà Nội, thêm một ca ở Đà Nẵng và một ca hôm kia
     * để chứng minh từng điều kiện lọc. Người dọn ca ngày: chỉ một ca ngày hôm qua.
     */
    @BeforeEach
    void setUp() {
        today = ShiftTimeUtils.todayInHanoi();
        yesterday = today.minusDays(1);
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        hanoi = persistLocation("Sao Mai Hà Nội");
        danang = persistLocation("Sao Mai Đà Nẵng");
        UUID department = persist(Department.builder().tenantId(tenantId).name("Buồng phòng").build()).getId();
        UUID position = persist(Position.builder().tenantId(tenantId).departmentId(department)
            .name("Nhân viên dọn phòng").positionType(PositionType.HOUSEKEEPING).build()).getId();
        cleaner = persistStaff("Phạm Ca Đêm", position);
        dayCleaner = persistStaff("Nguyễn Ca Ngày", position);

        nightShiftOfYesterday = persistShift(cleaner, hanoi, yesterday, LocalTime.of(22, 0), LocalTime.of(6, 0), true);
        dayShiftOfToday = persistShift(cleaner, hanoi, today, LocalTime.of(14, 0), LocalTime.of(22, 0), false);
        persistShift(cleaner, danang, today, LocalTime.of(6, 0), LocalTime.of(14, 0), false);
        persistShift(cleaner, hanoi, today.minusDays(2), LocalTime.of(22, 0), LocalTime.of(6, 0), true);
        persistShift(dayCleaner, hanoi, yesterday, LocalTime.of(6, 0), LocalTime.of(14, 0), false);

        em.flush();
        em.clear();
    }

    /** Hôm qua → hôm nay, đúng người, đúng khách sạn, xếp theo ngày rồi giờ bắt đầu. */
    @Test
    void shouldFindShiftsOfStaffAtLocationWithinRange() {
        List<Shift> shifts = repository.findOfStaffBetween(cleaner, hanoi, yesterday, today);

        assertThat(shifts).extracting(Shift::getId)
            .containsExactly(nightShiftOfYesterday.getId(), dayShiftOfToday.getId());
    }

    @Test
    void shouldNotMixShiftsOfAnotherLocation() {
        assertThat(repository.findOfStaffBetween(cleaner, danang, yesterday, today))
            .singleElement()
            .satisfies(shift -> assertThat(shift.getLocationId()).isEqualTo(danang));
    }

    @Test
    void shouldFindOnlyOvernightShiftsOfAskedStaffOnThatDay() {
        List<Shift> shifts = repository.findOvernightOf(Set.of(cleaner, dayCleaner), yesterday);

        assertThat(shifts).extracting(Shift::getId).containsExactly(nightShiftOfYesterday.getId());
    }

    /** Bản khóa dòng (FOR UPDATE, F8) chạy được trên MySQL và trả đúng như bản thường. */
    @Test
    void shouldLockShiftsOfStaffBetween() {
        assertThat(repository.findOfStaffBetweenForUpdate(cleaner, hanoi, yesterday, today))
            .extracting(Shift::getId)
            .containsExactly(nightShiftOfYesterday.getId(), dayShiftOfToday.getId());
    }

    /** Ca ngày hôm qua không bao giờ kéo dài sang hôm nay. */
    @Test
    void shouldIgnoreDayShifts() {
        assertThat(repository.findOvernightOf(Set.of(dayCleaner), yesterday)).isEmpty();
    }

    // ── Dữ liệu ─────────────────────────────────────────────────────────────

    private UUID persistStaff(String fullName, UUID positionId) {
        return persist(User.builder()
            .tenantId(tenantId).locationId(hanoi).positionId(positionId)
            .permissions(EnumSet.of(StaffPermission.HOUSEKEEPING))
            .role(Role.STAFF).status(UserStatus.ACTIVE)
            .email(uniqueEmail()).passwordHash("x").fullName(fullName).phone("0900000000")
            .build()).getId();
    }

    private Shift persistShift(UUID staffId, UUID locationId, LocalDate date,
                               LocalTime start, LocalTime end, boolean overnight) {
        return persist(Shift.builder()
            .tenantId(tenantId).locationId(locationId).staffId(staffId).shiftDate(date)
            .startTime(start).endTime(end).overnight(overnight)
            .durationHours(new BigDecimal("8.00"))
            .build());
    }

    private UUID persistLocation(String name) {
        return persist(Location.builder().tenantId(tenantId).name(name).address("Địa chỉ test")
            .phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@test.local";
    }
}
