package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.config.JpaConfig;
import com.example.SWP391_G2_SE2055_JV.entity.Department;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nhân viên đa nhiệm — vị trí kiêm nhiệm ở bảng {@code user_extra_positions}. Chạy trên MySQL thật
 * (xem {@code application-test.yaml}) vì các truy vấn dùng {@code member of} trên element
 * collection, mock không chứng minh được câu SQL sinh ra đúng.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class UserExtraPositionsRepositoryTest {

    @Autowired TestEntityManager  em;
    @Autowired UserRepository     userRepository;
    @Autowired PositionRepository positionRepository;

    private UUID tenantId;
    private UUID receptionPosition;
    private UUID housekeepingPosition;
    private UUID unusedPosition;
    private UUID staffId;

    /** Một nhân viên: vị trí chính Lễ tân, kiêm nhiệm Dọn dẹp. */
    @BeforeEach
    void setUp() {
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        UUID location = persist(Location.builder().tenantId(tenantId).name("Sao Mai Hà Nội")
            .address("Địa chỉ test").phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
        UUID department = persist(Department.builder().tenantId(tenantId).name("Vận hành").build()).getId();

        receptionPosition = persistPosition(department, "Lễ tân", PositionType.RECEPTION);
        housekeepingPosition = persistPosition(department, "Dọn phòng", PositionType.HOUSEKEEPING);
        unusedPosition = persistPosition(department, "Bảo vệ", PositionType.OTHER);

        LinkedHashSet<UUID> extras = new LinkedHashSet<>(List.of(housekeepingPosition));
        staffId = persist(User.builder()
            .tenantId(tenantId).locationId(location).positionId(receptionPosition)
            .extraPositionIds(extras)
            .role(Role.STAFF).status(UserStatus.ACTIVE)
            .email(uniqueEmail()).passwordHash("x").fullName("Đỗ Đa Nhiệm").phone("0900000000")
            .build()).getId();

        em.flush();
        em.clear();
    }

    /** Principal trong session lấy quyền kiêm nhiệm từ đây. */
    @Test
    void shouldLoadExtraPositionTypesOfUser() {
        assertThat(positionRepository.findExtraPositionTypesOfUser(staffId))
            .containsExactly(PositionType.HOUSEKEEPING);
    }

    /** Chặn xóa / đổi Loại chức danh khi có người giữ — làm vị trí chính hay kiêm nhiệm đều tính. */
    @Test
    void shouldTreatPrimaryAndExtraPositionsAsHeld() {
        assertThat(userRepository.isPositionHeld(receptionPosition)).isTrue();
        assertThat(userRepository.isPositionHeld(housekeepingPosition)).isTrue();
        assertThat(userRepository.isPositionHeld(unusedPosition)).isFalse();
    }

    /** Kiêm nhiệm là một phần hồ sơ: xóa vĩnh viễn tài khoản không bị nó chặn, và xóa theo. */
    @Test
    void shouldRemoveExtraPositionsWhenUserIsDeleted() {
        userRepository.delete(userRepository.findById(staffId).orElseThrow());
        userRepository.flush();

        assertThat(userRepository.isPositionHeld(housekeepingPosition)).isFalse();
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
