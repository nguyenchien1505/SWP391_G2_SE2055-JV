package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateUserRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateUserRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Gender;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chi nhánh chỉ "Đang vận hành" từ ngày quản lý được giao bắt đầu làm (chốt 06/10/2026) — chạy trên
 * MySQL thật qua {@link UserService}: tạo quản lý, đổi ngày bắt đầu làm, và tác vụ định kỳ bật đúng ngày.
 *
 * <p>Test chạy trong transaction và rollback, nên email mật khẩu tạm (gửi SAU commit) không bao giờ đi.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ManagerStartDateFlowTest {

    @Autowired UserService               userService;
    @Autowired LocationActivationService activationService;
    @Autowired EntityManager             em;

    private UUID tenantId;
    private Location hanoi;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        hanoi = persist(Location.builder().tenantId(tenantId).name("Sao Mai Hà Nội").address("Địa chỉ test")
            .phone("0900000000").status(LocationStatus.NOT_OPERATIONAL).build());
        User director = persist(User.builder().tenantId(tenantId).role(Role.DIRECTOR).email(uniqueEmail())
            .passwordHash("x").status(UserStatus.ACTIVE).fullName("Giám đốc").phone("0900000001").build());
        today = ShiftTimeUtils.todayAt(hanoi.getTimezone());
        flushAndClear();
        TestAuth.loginAs(director.getId(), Role.DIRECTOR, tenantId, null, null);
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    @Test
    void shouldOperateRightAwayWhenManagerStartsToday() {
        userService.createUser(managerRequest(today));
        flushAndClear();

        assertThat(statusOfHanoi()).isEqualTo(LocationStatus.OPERATIONAL);
    }

    /** Có quản lý nhưng chưa tới ngày bắt đầu làm: vẫn "Chưa vận hành". */
    @Test
    void shouldWaitForManagerStartDate() {
        userService.createUser(managerRequest(today.plusDays(3)));
        flushAndClear();

        assertThat(statusOfHanoi()).isEqualTo(LocationStatus.NOT_OPERATIONAL);
    }

    /** Đổi ngày bắt đầu làm thì trạng thái đi theo — cả hai chiều. */
    @Test
    void shouldFollowManagerStartDateWhenItChanges() {
        UUID managerId = userService.createUser(managerRequest(today.plusDays(3))).getUser().getId();
        flushAndClear();

        userService.updateUser(managerId, startDate(today));
        flushAndClear();
        assertThat(statusOfHanoi()).isEqualTo(LocationStatus.OPERATIONAL);

        userService.updateUser(managerId, startDate(today.plusDays(7)));
        flushAndClear();
        assertThat(statusOfHanoi()).isEqualTo(LocationStatus.NOT_OPERATIONAL);
    }

    /** Tới đúng ngày bắt đầu làm thì tác vụ định kỳ bật chi nhánh. */
    @Test
    void shouldBeActivatedByJobOnStartDate() {
        UUID managerId = userService.createUser(managerRequest(today.plusDays(1))).getUser().getId();
        flushAndClear();
        // Giả lập "đã sang ngày đó": dời ngày bắt đầu về hôm nay thẳng trong DB, không qua UserService.
        em.find(User.class, managerId).setStartWorkDate(today);
        flushAndClear();
        assertThat(statusOfHanoi()).isEqualTo(LocationStatus.NOT_OPERATIONAL);

        int activated = activationService.activateDueLocations();
        flushAndClear();

        assertThat(activated).isPositive();
        assertThat(statusOfHanoi()).isEqualTo(LocationStatus.OPERATIONAL);
    }

    private CreateUserRequest managerRequest(LocalDate startWorkDate) {
        CreateUserRequest request = new CreateUserRequest();
        request.setRole(Role.MANAGER);
        request.setFullName("Trần Quản Lý");
        request.setEmail(uniqueEmail());
        request.setPhone("0900000002");
        request.setLocationId(hanoi.getId());
        request.setStartWorkDate(startWorkDate);
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        request.setGender(Gender.MALE);
        request.setAddress("Hà Nội");
        request.setAvatarUrl("https://ui-avatars.com/api/?name=QL");
        return request;
    }

    private static UpdateUserRequest startDate(LocalDate startWorkDate) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setStartWorkDate(startWorkDate);
        return request;
    }

    private LocationStatus statusOfHanoi() {
        return em.find(Location.class, hanoi.getId()).getStatus();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@test.local";
    }
}
