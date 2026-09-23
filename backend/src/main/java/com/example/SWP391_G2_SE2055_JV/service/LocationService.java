package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateLocationRequest;
import com.example.SWP391_G2_SE2055_JV.dto.LocationResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateLocationContactRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateLocationRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AreaRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.SubscriptionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Location (khách sạn) — BR-ORG-02, BR-ORG-03, BR-ORG-04, BR-ORG-05.
 *
 * <p><b>Trạng thái vận hành không sửa tay.</b> Location mới luôn ở NOT_OPERATIONAL và chỉ
 * chuyển sang OPERATIONAL khi có Manager (BR-ORG-02, DM-13). Việc lật trạng thái nằm ở
 * {@link UserService}: tạo tài khoản Manager cho Location thì bật, cho Manager nghỉ việc
 * thì tắt. Đó chính là "2 bước tách rời" của BR-ORG-02 — tạo Location trước, gán Manager
 * sau — nên ở đây không có endpoint gán Manager riêng.
 *
 * <p><b>Chưa làm:</b> điều chuyển Manager/Staff sang Location khác (BR-TRF-01..07).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final RoomRepository     roomRepository;
    private final UserRepository     userRepository;
    private final AreaRepository     areaRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * BR-ORG-04: "Tổng số phòng" là derived field nên phải đếm khi trả về. Đếm gộp một lần
     * cho cả trang thay vì mỗi Location một query.
     */
    @Transactional(readOnly = true)
    public Page<LocationResponse> getLocations(Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Location> page = locationRepository.findByTenantId(tenantId, pageable);

        if (page.isEmpty()) {
            // Truy vấn đếm dùng IN: danh sách rỗng là câu SQL không hợp lệ.
            return page.map(location -> LocationResponse.fromEntity(location, 0L));
        }

        List<UUID> locationIds = page.getContent().stream().map(Location::getId).toList();
        Map<UUID, Long> roomCounts = roomRepository
            .countActiveRoomsGroupedByLocation(tenantId, locationIds)
            .stream()
            .collect(Collectors.toMap(
                RoomRepository.LocationRoomCount::getLocationId,
                RoomRepository.LocationRoomCount::getTotal));

        return page.map(location -> LocationResponse.fromEntity(
            location, roomCounts.getOrDefault(location.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public LocationResponse getLocationById(UUID id) {
        Location location = getOwnedLocation(id);
        return LocationResponse.fromEntity(
            location, roomRepository.countByLocationIdAndActiveTrue(location.getId()));
    }

    /** BR-ORG-03: chỉ Giám đốc tạo Location. Chặn thô theo URL ở SecurityConfig. */
    @Transactional
    public LocationResponse createLocation(CreateLocationRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        assertLocationQuotaAvailable(tenantId);

        Location location = Location.builder()
            .tenantId(tenantId)
            .name(request.getName().trim())
            .address(request.getAddress().trim())
            .phone(request.getPhone().trim())
            .starRating(request.getStarRating())
            .timezone(normalizeTimezone(request.getTimezone()))
            // BR-ORG-02: chưa có Manager thì chưa được vận hành chính thức.
            .status(LocationStatus.NOT_OPERATIONAL)
            .build();

        Location saved = locationRepository.save(location);
        log.info("Tạo Location {} cho Tenant {}", saved.getName(), tenantId);

        return LocationResponse.fromEntity(saved, 0L);
    }

    /** Sửa toàn bộ thông tin — chỉ Giám đốc (BR-ORG-03, BR-ORG-04). */
    @Transactional
    public LocationResponse updateLocation(UUID id, UpdateLocationRequest request) {
        Location location = getOwnedLocation(id);

        location.setName(request.getName().trim());
        location.setAddress(request.getAddress().trim());
        location.setPhone(request.getPhone().trim());
        location.setStarRating(request.getStarRating());
        location.setTimezone(normalizeTimezone(request.getTimezone()));

        log.info("Cập nhật Location {}", location.getId());
        return LocationResponse.fromEntity(
            locationRepository.save(location),
            roomRepository.countByLocationIdAndActiveTrue(location.getId()));
    }

    /**
     * BR-ORG-03: Manager chỉ cập nhật thông tin vận hành (địa chỉ, số điện thoại) và chỉ
     * trong Location của chính mình. Giám đốc cũng dùng được endpoint này cho sửa nhanh.
     */
    @Transactional
    public LocationResponse updateContactInfo(UUID id, UpdateLocationContactRequest request) {
        Location location = getOwnedLocation(id);

        if (SecurityUtils.hasRole(Role.MANAGER)
                && !SecurityUtils.getCurrentLocationId().equals(location.getId())) {
            throw new BusinessException("Manager chỉ sửa được thông tin Location của mình.");
        }

        if (request.getAddress() != null && !request.getAddress().isBlank()) {
            location.setAddress(request.getAddress().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            location.setPhone(request.getPhone().trim());
        }

        return LocationResponse.fromEntity(
            locationRepository.save(location),
            roomRepository.countByLocationIdAndActiveTrue(location.getId()));
    }

    /**
     * BR-ORG-05: chặn cứng nếu còn Manager/Staff/Phòng gắn với Location.
     *
     * <p>Nhân sự tính CẢ người đã nghỉ việc — hồ sơ họ vẫn trỏ vào Location để tra lịch sử
     * (BR-USER-04). Ngược lại, phòng đã xóa mềm KHÔNG tính.
     *
     * <p>Khu vực không nằm trong danh sách của BR-ORG-05 nhưng khóa ngoại {@code areas.
     * location_id} vẫn chặn, nên kiểm tra luôn ở đây để báo lỗi rõ ràng thay vì để DB ném
     * ra lỗi ràng buộc khó hiểu.
     */
    @Transactional
    public void deleteLocation(UUID id) {
        Location location = getOwnedLocation(id);

        if (userRepository.existsByLocationId(location.getId())) {
            throw new BusinessException(
                "Không xóa được Location: vẫn còn nhân sự trực thuộc (tính cả người đã nghỉ việc).");
        }
        if (roomRepository.existsByLocationIdAndActiveTrue(location.getId())) {
            throw new BusinessException("Không xóa được Location: vẫn còn phòng đang hoạt động.");
        }
        if (areaRepository.existsByLocationId(location.getId())) {
            throw new BusinessException(
                "Không xóa được Location: hãy xóa các khu vực thuộc Location này trước.");
        }

        locationRepository.delete(location);
        log.info("Xóa Location {}", location.getId());
    }

    /**
     * Dành cho Admin Platform xem Location của MỘT Tenant bất kỳ theo id trên URL — khác
     * {@link #getLocations(Pageable)} vốn luôn lấy tenant từ session người đăng nhập nên
     * Admin (tenant_id NULL) không dùng được.
     */
    @Transactional(readOnly = true)
    public Page<LocationResponse> getLocationsForTenant(UUID tenantId, Pageable pageable) {
        Page<Location> page = locationRepository.findByTenantId(tenantId, pageable);
        if (page.isEmpty()) {
            return page.map(location -> LocationResponse.fromEntity(location, 0L));
        }

        List<UUID> locationIds = page.getContent().stream().map(Location::getId).toList();
        Map<UUID, Long> roomCounts = roomRepository
            .countActiveRoomsGroupedByLocation(tenantId, locationIds)
            .stream()
            .collect(Collectors.toMap(
                RoomRepository.LocationRoomCount::getLocationId,
                RoomRepository.LocationRoomCount::getTotal));

        return page.map(location -> LocationResponse.fromEntity(
            location, roomCounts.getOrDefault(location.getId(), 0L)));
    }

    // ── Nội bộ ──────────────────────────────────────────────────────────────

    /**
     * BR-SAAS-02: số Location bị chặn bởi quota của gói dịch vụ; muốn thêm thì nâng cấp gói
     * (BR-SAAS-06). Áp dụng cả khi đang dùng thử: BR-SAAS-08 cho dùng thử "dùng chung luồng
     * custom plan, chỉ khác là miễn phí", tức vẫn theo quota của gói đã chọn.
     *
     * <p>Gói được đọc kèm khóa ghi để các request tạo Location cùng lúc của một Tenant đi
     * lần lượt qua bước "đếm rồi lưu" — xem {@link SubscriptionRepository#findForUpdateByTenantId}.
     */
    private void assertLocationQuotaAvailable(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findForUpdateByTenantId(tenantId)
            .orElseThrow(() -> new BusinessException(
                "Tenant chưa có gói dịch vụ nên chưa tạo được Location."));

        long used = locationRepository.countByTenantId(tenantId);
        if (used >= subscription.getQuotaLocation()) {
            throw new BusinessException(String.format(
                "Đã dùng hết %d/%d Location của gói dịch vụ. Nâng cấp gói để thêm Location.",
                used, subscription.getQuotaLocation()));
        }
    }

    /** Khóa ngoại chỉ đảm bảo Location TỒN TẠI, không đảm bảo thuộc Tenant người gọi. */
    private Location getOwnedLocation(UUID id) {
        return locationRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", id));
    }

    /**
     * Múi giờ sai làm lệch mốc "ca tương lai" của cả Location (BR-SCH-17), và job dọn ca
     * chạy theo giá trị này — chặn ngay từ lúc nhập thay vì để hỏng lúc chạy.
     */
    private static String normalizeTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "Asia/Ho_Chi_Minh";
        }
        String value = timezone.trim();
        try {
            ZoneId.of(value);
        } catch (DateTimeException ex) {
            throw new BusinessException("Múi giờ không hợp lệ: " + value);
        }
        return value;
    }
}
