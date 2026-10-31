package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Nhân viên quên mật khẩu — chỉ chuyển yêu cầu cho Quản lý khách sạn của họ, không đổi gì trên tài khoản. Form
 * công khai nên mọi trường hợp không chuyển được đều im lặng (không dò được email nào có tài khoản), và mỗi người
 * không gửi dồn được.
 */
@ExtendWith(MockitoExtension.class)
class AuthServicePasswordResetTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();

    @Mock UserRepository            userRepository;
    @Mock LocationRepository        locationRepository;
    @Mock PasswordEncoder           passwordEncoder;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks AuthService service;

    private User staff;
    private User manager;

    @BeforeEach
    void setUp() {
        staff = user(Role.STAFF, "hoa@test.local", "Nguyễn Thị Hoa");
        manager = user(Role.MANAGER, "quanly@test.local", "Trần Quản Lý");
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        lenient().when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(
            Location.builder().id(LOCATION_ID).tenantId(TENANT_ID).name("Sao Mai Hà Nội").build()));
    }

    @Test
    void shouldForwardRequestToManagerOfStaffLocation() {
        stubStaff(staff);
        stubManager();

        service.requestPasswordReset("  hoa@test.local  ");

        verify(eventPublisher).publishEvent(new PasswordResetRequestedEvent(
            "quanly@test.local", "Trần Quản Lý", "Nguyễn Thị Hoa", "hoa@test.local", "Sao Mai Hà Nội"));
    }

    @Test
    void shouldStaySilentForUnknownEmail() {
        when(userRepository.findByEmail("ai-do@test.local")).thenReturn(Optional.empty());

        service.requestPasswordReset("ai-do@test.local");

        verifyNoInteractions(eventPublisher);
    }

    /** Chỉ nhân viên: Quản lý quên mật khẩu thì liên hệ Giám đốc, không gửi thư cho chính mình. */
    @Test
    void shouldNotForwardForManagerAccount() {
        stubStaff(manager);

        service.requestPasswordReset("quanly@test.local");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldNotForwardForTerminatedStaff() {
        staff.setStatus(UserStatus.TERMINATED);
        stubStaff(staff);

        service.requestPasswordReset("hoa@test.local");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldNotForwardWhenHotelHasNoManager() {
        stubStaff(staff);
        when(userRepository.findFirstByLocationIdAndRoleAndStatus(LOCATION_ID, Role.MANAGER, UserStatus.ACTIVE))
            .thenReturn(Optional.empty());

        service.requestPasswordReset("hoa@test.local");

        verifyNoInteractions(eventPublisher);
    }

    /**
     * Email tắt thì yêu cầu không tới được ai — báo ngay để nhân viên tự liên hệ Quản lý. Kiểm trước khi tìm tài
     * khoản nên câu báo giống nhau với mọi email.
     */
    @Test
    void shouldTellRequesterWhenMailIsDisabled() {
        ReflectionTestUtils.setField(service, "mailEnabled", false);

        assertThatThrownBy(() -> service.requestPasswordReset("hoa@test.local"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("báo trực tiếp Quản lý khách sạn");
        verifyNoInteractions(userRepository, eventPublisher);
    }

    /** Bấm lại liền tay không gửi thêm thư — mỗi người tối đa một yêu cầu trong khoảng chờ. */
    @Test
    void shouldIgnoreRepeatedRequestWithinCooldown() {
        stubStaff(staff);
        stubManager();

        service.requestPasswordReset("hoa@test.local");
        service.requestPasswordReset("hoa@test.local");

        verify(eventPublisher, times(1)).publishEvent(any(PasswordResetRequestedEvent.class));
    }

    /** Khoảng chờ tính riêng từng người: nhân viên khác vẫn gửi được. */
    @Test
    void shouldKeepCooldownPerStaff() {
        User nam = user(Role.STAFF, "nam@test.local", "Trần Văn Nam");
        stubStaff(staff);
        stubStaff(nam);
        stubManager();

        service.requestPasswordReset("hoa@test.local");
        service.requestPasswordReset("nam@test.local");

        verify(eventPublisher, times(2)).publishEvent(any(PasswordResetRequestedEvent.class));
    }

    private void stubStaff(User person) {
        when(userRepository.findByEmail(person.getEmail())).thenReturn(Optional.of(person));
    }

    private void stubManager() {
        when(userRepository.findFirstByLocationIdAndRoleAndStatus(LOCATION_ID, Role.MANAGER, UserStatus.ACTIVE))
            .thenReturn(Optional.of(manager));
    }

    private static User user(Role role, String email, String fullName) {
        return User.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).locationId(LOCATION_ID).role(role).status(UserStatus.ACTIVE)
            .email(email).passwordHash("x").fullName(fullName).phone("0900000000")
            .build();
    }
}
