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

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quyền nghiệp vụ Manager tick cho từng nhân viên — bảng {@code user_permissions}. Chạy trên MySQL
 * thật (xem {@code application-test.yaml}) vì truy vấn đọc element collection và ràng buộc
 * {@code ON DELETE CASCADE} nằm ở DB, mock không chứng minh được.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class UserPermissionsRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired UserRepository    userRepository;

    private UUID tenantId;
    private UUID location;
    private UUID receptionPosition;
    private UUID housekeepingPosition;
    private UUID multiRoleStaff;
    private UUID plainStaff;

    /** Hai nhân viên cùng vị trí Lễ tân: một người được tick thêm Dọn dẹp, một người không tick gì. */
    @BeforeEach
    void setUp() {
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        location = persist(Location.builder().tenantId(tenantId).name("Sao Mai Hà Nội")
            .address("Địa chỉ test").phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
        UUID department = persist(Department.builder().tenantId(tenantId).name("Vận hành").build()).getId();

        receptionPosition = persistPosition(department, "Lễ tân", PositionType.RECEPTION);
        housekeepingPosition = persistPosition(department, "Dọn phòng", PositionType.HOUSEKEEPING);

        multiRoleStaff = persistStaff("Đỗ Đa Nhiệm",
            EnumSet.of(StaffPermission.RECEPTION, StaffPermission.HOUSEKEEPING));
        plainStaff = persistStaff("Lê Chỉ Xem", EnumSet.noneOf(StaffPermission.class));

        em.flush();
        em.clear();
    }

    /** Principal trong session lấy quyền từ đây — đủ mọi ô đã tick. */
    @Test
    void shouldLoadEveryTickedPermission() {
        assertThat(userRepository.findPermissionsOfUser(multiRoleStaff))
            .containsExactlyInAnyOrder(StaffPermission.RECEPTION, StaffPermission.HOUSEKEEPING);
    }

    /** Không tick ô nào = chỉ có quyền chung, dù vị trí là Lễ tân. */
    @Test
    void shouldReturnNoPermissionWhenNothingTicked() {
        assertThat(userRepository.findPermissionsOfUser(plainStaff)).isEmpty();
    }

    /** Chặn xóa chức danh chỉ tính vị trí thật sự được gán — quyền tick thêm không làm "giữ" chức danh khác. */
    @Test
    void shouldTreatOnlyAssignedPositionAsHeld() {
        assertThat(userRepository.isPositionHeld(receptionPosition)).isTrue();
        assertThat(userRepository.isPositionHeld(housekeepingPosition)).isFalse();
    }

    /** Quyền là một phần hồ sơ: xóa vĩnh viễn tài khoản không bị nó chặn, và quyền bị xóa theo. */
    @Test
    void shouldRemovePermissionsWhenUserIsDeleted() {
        userRepository.delete(userRepository.findById(multiRoleStaff).orElseThrow());
        userRepository.flush();

        Number remaining = (Number) em.getEntityManager()
            .createNativeQuery("select count(*) from user_permissions where user_id = :id")
            .setParameter("id", multiRoleStaff.toString())
            .getSingleResult();
        assertThat(remaining.longValue()).isZero();
    }

    private UUID persistStaff(String fullName, Set<StaffPermission> permissions) {
        return persist(User.builder()
            .tenantId(tenantId).locationId(location).positionId(receptionPosition)
            .permissions(permissions)
            .role(Role.STAFF).status(UserStatus.ACTIVE)
            .email(uniqueEmail()).passwordHash("x").fullName(fullName).phone("0900000000")
            .build()).getId();
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
