package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AttendanceNetworkRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AttendanceNetworkSettingsResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ClientInfo;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.LocationAttendanceNetwork;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceIpMode;
import com.example.SWP391_G2_SE2055_JV.enums.NetworkCheckResult;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationAttendanceNetworkRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.utils.IpRange;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Xác thực chấm công theo IP mạng khách sạn — chấm công M2 (chốt 01/11/2026).
 *
 * <ul>
 *   <li>Manager khai IP / dải IP mạng của khách sạn mình và chọn chế độ {@link AttendanceIpMode}. Giám đốc
 *       xem được, không sửa.</li>
 *   <li>{@link #check} chạy khi nhân viên TỰ vào / ra ca; Manager nhập bù không bị kiểm.</li>
 *   <li>Bật kiểm (WARN / ENFORCE) phải có ít nhất một IP, và không xóa được IP cuối cùng khi đang bật — tránh
 *       chặn nhầm cả khách sạn.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceNetworkService {

    /** Đủ cho vài đường mạng + IPv4 / IPv6 của mỗi đường. */
    static final int MAX_NETWORKS_PER_LOCATION = 20;

    private final LocationRepository                  locationRepository;
    private final LocationAttendanceNetworkRepository networkRepository;

    /** Kết quả kiểm mạng của một lần vào / ra ca. */
    public record NetworkCheck(NetworkCheckResult result, String matchedCidr) {
        static final NetworkCheck NOT_CHECKED = new NetworkCheck(NetworkCheckResult.NOT_CHECKED, null);
    }

    // ── Kiểm khi vào / ra ca ─────────────────────────────────────────────────

    /**
     * @throws BusinessException chế độ {@link AttendanceIpMode#ENFORCE} mà IP ngoài danh sách
     */
    public NetworkCheck check(Location location, ClientInfo client) {
        if (location.getAttendanceIpMode() == AttendanceIpMode.OFF) {
            return NetworkCheck.NOT_CHECKED;
        }
        String ip = client == null ? null : client.ipAddress();
        Optional<String> matched = matchingCidr(location, ip);
        if (matched.isPresent()) {
            return new NetworkCheck(NetworkCheckResult.MATCHED, matched.get());
        }
        if (location.getAttendanceIpMode() == AttendanceIpMode.ENFORCE) {
            throw new BusinessException("Bạn đang không dùng mạng của khách sạn" + (ip == null ? "" : " (IP " + ip + ")")
                + ". Hãy kết nối wifi của khách sạn rồi thử lại, hoặc báo quản lý.");
        }
        return new NetworkCheck(NetworkCheckResult.OUTSIDE, null);
    }

    private Optional<String> matchingCidr(Location location, String ip) {
        if (ip == null) {
            return Optional.empty();
        }
        return networkRepository.findByTenantIdAndLocationIdOrderByCreatedAtAsc(location.getTenantId(), location.getId())
            .stream()
            .map(LocationAttendanceNetwork::getCidr)
            .filter(cidr -> IpRange.parse(cidr).map(range -> range.contains(ip)).orElse(false))
            .findFirst();
    }

    // ── Cài đặt ──────────────────────────────────────────────────────────────

    /**
     * @param locationId Giám đốc / Admin chọn khách sạn; Manager bỏ qua — luôn là khách sạn của mình
     */
    @Transactional(readOnly = true)
    public AttendanceNetworkSettingsResponse getSettings(UUID locationId, ClientInfo client) {
        return toResponse(requireLocation(locationId), client);
    }

    @Transactional
    public AttendanceNetworkSettingsResponse setMode(UUID locationId, AttendanceIpMode mode, ClientInfo client) {
        Location location = requireLocation(locationId);
        if (mode != AttendanceIpMode.OFF && networkRepository.countByLocationId(location.getId()) == 0) {
            throw new BusinessException("Thêm ít nhất một IP mạng của khách sạn trước khi bật kiểm IP.");
        }
        location.setAttendanceIpMode(mode);
        locationRepository.save(location);
        log.info("Khách sạn {}: chế độ kiểm IP chấm công = {}", location.getId(), mode);
        return toResponse(location, client);
    }

    @Transactional
    public AttendanceNetworkSettingsResponse addNetwork(UUID locationId, AttendanceNetworkRequest request,
                                                        ClientInfo client) {
        Location location = requireLocation(locationId);
        IpRange range = IpRange.parse(request.getCidr())
            .orElseThrow(() -> new BusinessException("«" + request.getCidr().trim() + "» không phải IP hoặc dải IP hợp lệ. "
                + "Ví dụ: 113.160.92.202, 113.160.92.0/24, 2001:ee0:4f1::/64."));
        String cidr = range.canonical();
        if (networkRepository.existsByLocationIdAndCidr(location.getId(), cidr)) {
            throw new BusinessException("IP / dải IP " + cidr + " đã có trong danh sách.");
        }
        if (networkRepository.countByLocationId(location.getId()) >= MAX_NETWORKS_PER_LOCATION) {
            throw new BusinessException("Mỗi khách sạn khai tối đa " + MAX_NETWORKS_PER_LOCATION + " IP / dải IP.");
        }
        String label = request.getLabel() == null || request.getLabel().isBlank() ? null : request.getLabel().trim();
        networkRepository.save(LocationAttendanceNetwork.builder()
            .tenantId(location.getTenantId())
            .locationId(location.getId())
            .cidr(cidr)
            .label(label)
            .build());
        log.info("Khách sạn {}: thêm mạng chấm công {}", location.getId(), cidr);
        return toResponse(location, client);
    }

    @Transactional
    public AttendanceNetworkSettingsResponse removeNetwork(UUID locationId, UUID networkId, ClientInfo client) {
        Location location = requireLocation(locationId);
        LocationAttendanceNetwork network = networkRepository.findByIdAndTenantId(networkId, location.getTenantId())
            .filter(n -> n.getLocationId().equals(location.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("LocationAttendanceNetwork", "id", networkId));
        if (location.getAttendanceIpMode() != AttendanceIpMode.OFF
                && networkRepository.countByLocationId(location.getId()) <= 1) {
            throw new BusinessException("Đây là IP cuối cùng — tắt kiểm IP trước khi xóa, "
                + "nếu không sẽ không ai vào ca được.");
        }
        networkRepository.delete(network);
        log.info("Khách sạn {}: xóa mạng chấm công {}", location.getId(), network.getCidr());
        return toResponse(location, client);
    }

    // ── Phạm vi / dựng response ──────────────────────────────────────────────

    /** Manager: khách sạn của mình. Giám đốc / Admin: khách sạn chọn, trong Tenant. */
    private Location requireLocation(UUID requested) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = SecurityUtils.hasRole(Role.MANAGER) ? SecurityUtils.getCurrentLocationId() : requested;
        if (locationId == null) {
            throw new BusinessException("Chọn khách sạn.");
        }
        return locationRepository.findByIdAndTenantId(locationId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", locationId));
    }

    private AttendanceNetworkSettingsResponse toResponse(Location location, ClientInfo client) {
        List<LocationAttendanceNetwork> networks =
            networkRepository.findByTenantIdAndLocationIdOrderByCreatedAtAsc(location.getTenantId(), location.getId());
        String ip = client == null ? null : client.ipAddress();
        boolean allowed = ip != null && networks.stream()
            .anyMatch(n -> IpRange.parse(n.getCidr()).map(range -> range.contains(ip)).orElse(false));
        return AttendanceNetworkSettingsResponse.builder()
            .locationId(location.getId())
            .mode(location.getAttendanceIpMode())
            .networks(networks.stream()
                .map(n -> AttendanceNetworkSettingsResponse.Network.builder()
                    .id(n.getId()).cidr(n.getCidr()).label(n.getLabel()).createdAt(n.getCreatedAt()).build())
                .toList())
            .currentIp(ip)
            .currentIpAllowed(allowed)
            .suggestedCidr(ip == null ? null : IpRange.networkOf(ip).orElse(null))
            .build();
    }
}
