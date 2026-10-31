package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.MyProfileResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Department;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.DepartmentRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.PositionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Hồ sơ cá nhân (BR-PERM-06) — kèm tên vị trí, phòng ban, khách sạn vì nhân viên không đọc được danh mục. */
@ExtendWith(MockitoExtension.class)
class UserServiceProfileTest {

    private static final UUID TENANT_ID     = UUID.randomUUID();
    private static final UUID LOCATION_ID   = UUID.randomUUID();
    private static final UUID POSITION_ID   = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();

    @Mock UserRepository       userRepository;
    @Mock PositionRepository   positionRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock LocationRepository   locationRepository;

    @InjectMocks UserService service;

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    @Test
    void shouldShowStaffProfileWithPositionDepartmentAndHotel() {
        User staff = user(Role.STAFF, LOCATION_ID, POSITION_ID);
        staff.setPermissions(EnumSet.of(StaffPermission.RECEPTION, StaffPermission.HOUSEKEEPING));
        loginAs(staff);
        when(userRepository.findByIdAndTenantId(staff.getId(), TENANT_ID)).thenReturn(Optional.of(staff));
        when(positionRepository.findByIdAndTenantId(POSITION_ID, TENANT_ID)).thenReturn(Optional.of(Position.builder()
            .id(POSITION_ID).tenantId(TENANT_ID).departmentId(DEPARTMENT_ID).name("Lễ tân ca ngày")
            .positionType(PositionType.RECEPTION).build()));
        when(departmentRepository.findByIdAndTenantId(DEPARTMENT_ID, TENANT_ID)).thenReturn(Optional.of(
            Department.builder().id(DEPARTMENT_ID).tenantId(TENANT_ID).name("Tiền sảnh").build()));
        when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(
            Location.builder().id(LOCATION_ID).tenantId(TENANT_ID).name("Sao Mai Hà Nội").build()));

        MyProfileResponse profile = service.getMyProfile();

        assertThat(profile.getFullName()).isEqualTo("Nguyễn Thị Hoa");
        assertThat(profile.getPositionName()).isEqualTo("Lễ tân ca ngày");
        assertThat(profile.getDepartmentName()).isEqualTo("Tiền sảnh");
        assertThat(profile.getLocationName()).isEqualTo("Sao Mai Hà Nội");
        assertThat(profile.getPermissions()).containsExactly(StaffPermission.RECEPTION, StaffPermission.HOUSEKEEPING);
        assertThat(profile.getStartWorkDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    /** DM-01: Manager không có Position — chỉ có khách sạn. */
    @Test
    void shouldShowManagerWithoutPosition() {
        User manager = user(Role.MANAGER, LOCATION_ID, null);
        loginAs(manager);
        when(userRepository.findByIdAndTenantId(manager.getId(), TENANT_ID)).thenReturn(Optional.of(manager));
        when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(
            Location.builder().id(LOCATION_ID).tenantId(TENANT_ID).name("Sao Mai Hà Nội").build()));

        MyProfileResponse profile = service.getMyProfile();

        assertThat(profile.getPositionName()).isNull();
        assertThat(profile.getDepartmentName()).isNull();
        assertThat(profile.getLocationName()).isEqualTo("Sao Mai Hà Nội");
        verifyNoInteractions(positionRepository, departmentRepository);
    }

    @Test
    void shouldShowDirectorWithoutHotel() {
        User director = user(Role.DIRECTOR, null, null);
        loginAs(director);
        when(userRepository.findByIdAndTenantId(director.getId(), TENANT_ID)).thenReturn(Optional.of(director));

        MyProfileResponse profile = service.getMyProfile();

        assertThat(profile.getRole()).isEqualTo(Role.DIRECTOR);
        assertThat(profile.getLocationName()).isNull();
        verifyNoInteractions(positionRepository, departmentRepository, locationRepository);
    }

    @Test
    void shouldReturnNotFoundWhenAccountIsGone() {
        User staff = user(Role.STAFF, LOCATION_ID, null);
        loginAs(staff);
        when(userRepository.findByIdAndTenantId(staff.getId(), TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyProfile()).isInstanceOf(ResourceNotFoundException.class);
    }

    private static void loginAs(User person) {
        PositionType type = person.getRole() == Role.STAFF ? PositionType.RECEPTION : null;
        TestAuth.loginAs(person.getId(), person.getRole(), TENANT_ID, person.getLocationId(), type);
    }

    private static User user(Role role, UUID locationId, UUID positionId) {
        return User.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).locationId(locationId).positionId(positionId).role(role)
            .status(UserStatus.ACTIVE).email(UUID.randomUUID() + "@test.local").passwordHash("x")
            .fullName("Nguyễn Thị Hoa").phone("0900000000").startWorkDate(LocalDate.of(2026, 4, 1))
            .build();
    }
}
