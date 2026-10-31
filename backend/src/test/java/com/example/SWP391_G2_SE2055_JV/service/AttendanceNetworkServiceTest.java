package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AttendanceNetworkRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AttendanceNetworkSettingsResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ClientInfo;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.LocationAttendanceNetwork;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceIpMode;
import com.example.SWP391_G2_SE2055_JV.enums.NetworkCheckResult;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationAttendanceNetworkRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceNetworkService.NetworkCheck;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Xác thực chấm công theo IP (chấm công M2): ba chế độ kiểm, và các chốt chặn tránh khóa nhầm cả khách sạn.
 * Phần so IP đã test riêng ở {@code IpRangeTest}.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceNetworkServiceTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final ClientInfo AT_HOTEL = new ClientInfo("113.160.92.15", "UA", "dev");
    private static final ClientInfo AT_HOME  = new ClientInfo("27.72.1.1", "UA", "dev");

    @Mock LocationRepository                  locationRepository;
    @Mock LocationAttendanceNetworkRepository networkRepository;

    @InjectMocks AttendanceNetworkService service;

    private Location location;

    @BeforeEach
    void setUp() {
        location = Location.builder().id(LOCATION_ID).tenantId(TENANT_ID).build();
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    @Nested
    class Check {

        @Test
        void shouldSkipWhenOff() {
            assertThat(service.check(location, AT_HOME).result()).isEqualTo(NetworkCheckResult.NOT_CHECKED);
            verifyNoInteractions(networkRepository);
        }

        @Test
        void shouldMatchHotelNetwork() {
            location.setAttendanceIpMode(AttendanceIpMode.ENFORCE);
            givenNetworks("113.160.92.0/24");

            NetworkCheck check = service.check(location, AT_HOTEL);

            assertThat(check.result()).isEqualTo(NetworkCheckResult.MATCHED);
            assertThat(check.matchedCidr()).isEqualTo("113.160.92.0/24");
        }

        @Test
        void shouldOnlyFlagOutsideInWarnMode() {
            location.setAttendanceIpMode(AttendanceIpMode.WARN);
            givenNetworks("113.160.92.0/24");

            assertThat(service.check(location, AT_HOME).result()).isEqualTo(NetworkCheckResult.OUTSIDE);
        }

        @Test
        void shouldBlockOutsideInEnforceMode() {
            location.setAttendanceIpMode(AttendanceIpMode.ENFORCE);
            givenNetworks("113.160.92.0/24");

            assertThatThrownBy(() -> service.check(location, AT_HOME))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("wifi của khách sạn")
                .hasMessageContaining("27.72.1.1");
        }
    }

    @Nested
    class Settings {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(location));
            lenient().when(networkRepository.findByTenantIdAndLocationIdOrderByCreatedAtAsc(TENANT_ID, LOCATION_ID))
                .thenReturn(List.of());
        }

        @Test
        void shouldStoreCanonicalCidr() {
            AttendanceNetworkRequest request = new AttendanceNetworkRequest();
            request.setCidr(" 113.160.92.77/24 ");
            request.setLabel("Wifi tầng 1");

            service.addNetwork(null, request, AT_HOTEL);

            ArgumentCaptor<LocationAttendanceNetwork> saved = ArgumentCaptor.forClass(LocationAttendanceNetwork.class);
            verify(networkRepository).save(saved.capture());
            assertThat(saved.getValue().getCidr()).isEqualTo("113.160.92.0/24");
            assertThat(saved.getValue().getLocationId()).isEqualTo(LOCATION_ID);
            assertThat(saved.getValue().getLabel()).isEqualTo("Wifi tầng 1");
        }

        @Test
        void shouldRejectInvalidCidr() {
            AttendanceNetworkRequest request = new AttendanceNetworkRequest();
            request.setCidr("wifi.khachsan.vn");

            assertThatThrownBy(() -> service.addNetwork(null, request, AT_HOTEL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không phải IP");
            verify(networkRepository, never()).save(any());
        }

        @Test
        void shouldRejectDuplicate() {
            when(networkRepository.existsByLocationIdAndCidr(LOCATION_ID, "113.160.92.15")).thenReturn(true);
            AttendanceNetworkRequest request = new AttendanceNetworkRequest();
            request.setCidr("113.160.92.15");

            assertThatThrownBy(() -> service.addNetwork(null, request, AT_HOTEL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã có");
        }

        /** Bật kiểm mà chưa có IP nào là khóa cả khách sạn. */
        @Test
        void shouldRequireNetworkBeforeEnabling() {
            when(networkRepository.countByLocationId(LOCATION_ID)).thenReturn(0L);

            assertThatThrownBy(() -> service.setMode(null, AttendanceIpMode.ENFORCE, AT_HOTEL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất một IP");
            assertThat(location.getAttendanceIpMode()).isEqualTo(AttendanceIpMode.OFF);
        }

        @Test
        void shouldBlockRemovingLastNetworkWhileEnabled() {
            location.setAttendanceIpMode(AttendanceIpMode.WARN);
            UUID networkId = UUID.randomUUID();
            when(networkRepository.findByIdAndTenantId(networkId, TENANT_ID)).thenReturn(Optional.of(
                LocationAttendanceNetwork.builder().id(networkId).locationId(LOCATION_ID).cidr("1.2.3.4").build()));
            when(networkRepository.countByLocationId(LOCATION_ID)).thenReturn(1L);

            assertThatThrownBy(() -> service.removeNetwork(null, networkId, AT_HOTEL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("IP cuối cùng");
            verify(networkRepository, never()).delete(any());
        }

        @Test
        void shouldTellWhetherCurrentIpIsAllowed() {
            when(networkRepository.findByTenantIdAndLocationIdOrderByCreatedAtAsc(TENANT_ID, LOCATION_ID))
                .thenReturn(List.of(LocationAttendanceNetwork.builder().id(UUID.randomUUID()).cidr("113.160.92.0/24").build()));

            AttendanceNetworkSettingsResponse atHotel = service.getSettings(null, AT_HOTEL);
            AttendanceNetworkSettingsResponse atHome = service.getSettings(null, AT_HOME);

            assertThat(atHotel.getCurrentIp()).isEqualTo("113.160.92.15");
            assertThat(atHotel.isCurrentIpAllowed()).isTrue();
            assertThat(atHome.isCurrentIpAllowed()).isFalse();
        }

        @Test
        void shouldSuggestIpv6Range() {
            AttendanceNetworkSettingsResponse settings =
                service.getSettings(null, new ClientInfo("2001:ee0:4f1:12:a1b2:c3d4:e5f6:7788", "UA", "dev"));

            assertThat(settings.getSuggestedCidr()).isEqualTo("2001:ee0:4f1:12::/64");
            assertThat(service.getSettings(null, AT_HOTEL).getSuggestedCidr()).isEqualTo("113.160.92.15");
        }

        /** Manager luôn thao tác trên khách sạn của mình, bỏ qua locationId gửi lên. */
        @Test
        void shouldIgnoreRequestedLocationForManager() {
            service.getSettings(UUID.randomUUID(), AT_HOTEL);

            verify(locationRepository).findByIdAndTenantId(LOCATION_ID, TENANT_ID);
        }
    }

    private void givenNetworks(String... cidrs) {
        when(networkRepository.findByTenantIdAndLocationIdOrderByCreatedAtAsc(TENANT_ID, LOCATION_ID))
            .thenReturn(Arrays.stream(cidrs)
                .<LocationAttendanceNetwork>map(cidr -> LocationAttendanceNetwork.builder().cidr(cidr).build())
                .toList());
    }
}
