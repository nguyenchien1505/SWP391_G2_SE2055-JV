package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chi nhánh chỉ "Đang vận hành" từ NGÀY BẮT ĐẦU LÀM của quản lý được giao (BR-ORG-02, DM-13, chốt
 * 06/10/2026); "ngày" theo múi giờ của chính chi nhánh (BR-SCH-17).
 */
@ExtendWith(MockitoExtension.class)
class LocationActivationServiceTest {

    private static final String TIMEZONE = "Asia/Ho_Chi_Minh";

    @Mock LocationRepository locationRepository;
    @Mock UserRepository     userRepository;

    @InjectMocks LocationActivationService service;

    private final LocalDate today = ShiftTimeUtils.todayAt(TIMEZONE);

    // ── Khi gán quản lý / đổi ngày bắt đầu làm ──────────────────────────────

    @Nested
    class Apply {

        @Test
        void shouldOperateRightAwayWhenManagerStartsToday() {
            Location location = location(LocationStatus.NOT_OPERATIONAL);

            service.apply(location, manager(today));

            assertThat(location.getStatus()).isEqualTo(LocationStatus.OPERATIONAL);
            verify(locationRepository).save(location);
        }

        /** Khách sạn mới lên hệ thống nhập quản lý đã đi làm từ trước — vận hành ngay. */
        @Test
        void shouldOperateWhenManagerStartedEarlier() {
            Location location = location(LocationStatus.NOT_OPERATIONAL);

            service.apply(location, manager(today.minusMonths(3)));

            assertThat(location.getStatus()).isEqualTo(LocationStatus.OPERATIONAL);
        }

        /** Có quản lý nhưng chưa tới ngày bắt đầu làm: vẫn "Chưa vận hành" — job bật đúng ngày đó. */
        @Test
        void shouldStayNotOperationalUntilManagerStartDate() {
            Location location = location(LocationStatus.NOT_OPERATIONAL);

            service.apply(location, manager(today.plusDays(5)));

            assertThat(location.getStatus()).isEqualTo(LocationStatus.NOT_OPERATIONAL);
            verify(locationRepository, never()).save(any());
        }

        /** Người nhận bàn giao bắt đầu sau hôm nay, hoặc ngày bắt đầu bị dời: quay về "Chưa vận hành". */
        @Test
        void shouldStopOperatingWhenNewManagerStartsLater() {
            Location location = location(LocationStatus.OPERATIONAL);

            service.apply(location, manager(today.plusDays(1)));

            assertThat(location.getStatus()).isEqualTo(LocationStatus.NOT_OPERATIONAL);
            verify(locationRepository).save(location);
        }

        /** Dữ liệu cũ không có ngày bắt đầu: coi như đã làm, không tắt chi nhánh đang chạy. */
        @Test
        void shouldTreatMissingStartDateAsStarted() {
            Location location = location(LocationStatus.OPERATIONAL);

            service.apply(location, manager(null));

            assertThat(location.getStatus()).isEqualTo(LocationStatus.OPERATIONAL);
            verify(locationRepository, never()).save(any());
        }
    }

    // ── Tác vụ định kỳ ──────────────────────────────────────────────────────

    @Nested
    class ActivateDue {

        @Test
        void shouldActivateOnlyLocationsWhoseManagerHasStarted() {
            Location started = location(LocationStatus.NOT_OPERATIONAL);
            Location startsTomorrow = location(LocationStatus.NOT_OPERATIONAL);
            Location noManager = location(LocationStatus.NOT_OPERATIONAL);
            when(locationRepository.findByStatus(LocationStatus.NOT_OPERATIONAL))
                .thenReturn(List.of(started, startsTomorrow, noManager));
            when(userRepository.findFirstByLocationIdAndRoleAndStatusNot(started.getId(), Role.MANAGER, UserStatus.TERMINATED))
                .thenReturn(Optional.of(manager(today)));
            when(userRepository.findFirstByLocationIdAndRoleAndStatusNot(startsTomorrow.getId(), Role.MANAGER, UserStatus.TERMINATED))
                .thenReturn(Optional.of(manager(today.plusDays(1))));
            when(userRepository.findFirstByLocationIdAndRoleAndStatusNot(noManager.getId(), Role.MANAGER, UserStatus.TERMINATED))
                .thenReturn(Optional.empty());

            int activated = service.activateDueLocations();

            assertThat(activated).isEqualTo(1);
            assertThat(started.getStatus()).isEqualTo(LocationStatus.OPERATIONAL);
            assertThat(startsTomorrow.getStatus()).isEqualTo(LocationStatus.NOT_OPERATIONAL);
            assertThat(noManager.getStatus()).isEqualTo(LocationStatus.NOT_OPERATIONAL);
        }

        /**
         * "Hôm nay" theo múi giờ của CHI NHÁNH: lúc Việt Nam đã sang ngày mới thì chi nhánh ở múi giờ
         * chậm hơn vẫn đang ở hôm trước — chưa tới ngày bắt đầu làm.
         */
        @Test
        void shouldUseLocationTimezoneForToday() {
            Location honolulu = location(LocationStatus.NOT_OPERATIONAL);
            honolulu.setTimezone("Pacific/Honolulu");          // UTC-10, chậm Việt Nam 17 giờ
            LocalDate localToday = ShiftTimeUtils.todayAt("Pacific/Honolulu");
            when(locationRepository.findByStatus(LocationStatus.NOT_OPERATIONAL)).thenReturn(List.of(honolulu));
            when(userRepository.findFirstByLocationIdAndRoleAndStatusNot(honolulu.getId(), Role.MANAGER, UserStatus.TERMINATED))
                .thenReturn(Optional.of(manager(localToday.plusDays(1))));

            assertThat(service.activateDueLocations()).isZero();
            assertThat(honolulu.getStatus()).isEqualTo(LocationStatus.NOT_OPERATIONAL);
        }
    }

    private static Location location(LocationStatus status) {
        return Location.builder().id(UUID.randomUUID()).tenantId(UUID.randomUUID())
            .name("Khách sạn Test").address("Hà Nội").phone("0900000000")
            .timezone(TIMEZONE).status(status).build();
    }

    private static User manager(LocalDate startWorkDate) {
        return User.builder().id(UUID.randomUUID()).role(Role.MANAGER).status(UserStatus.ACTIVE)
            .email("manager@test.local").fullName("Quản lý Test").startWorkDate(startWorkDate).build();
    }
}
