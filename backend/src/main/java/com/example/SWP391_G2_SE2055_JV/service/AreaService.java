package com.example.SWP391_G2_SE2055_JV.service;

<<<<<<< HEAD
import com.example.SWP391_G2_SE2055_JV.dto.AreaResponse;
import com.example.SWP391_G2_SE2055_JV.dto.CreateAreaRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateAreaRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Area;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
=======
import com.example.SWP391_G2_SE2055_JV.dto.organization.AreaRequest;
import com.example.SWP391_G2_SE2055_JV.dto.organization.AreaResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Area;
>>>>>>> Nguyen
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AreaRepository;
import com.example.SWP391_G2_SE2055_JV.repository.FixedAssetRepository;
<<<<<<< HEAD
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
=======
>>>>>>> Nguyen
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Khu vực — BR-ORG-12, BR-ORG-13, BR-ORG-15.
 *
<<<<<<< HEAD
 * <p>Khác Department/Position/RoomType ở chỗ Area tạo ở cấp LOCATION và do Manager CRUD:
 * sảnh, hành lang, kho là đặc thù vật lý của từng khách sạn chứ không phải danh mục dùng
 * chung toàn chuỗi. Vì vậy tên chỉ unique trong phạm vi Location, và Area không có cờ
 * is_active.
=======
 * <p>Khác 3 danh mục cấp Tenant (Department/Position/RoomType): Khu vực tạo ở cấp
 * LOCATION, do Manager CRUD trong Location của mình. Giám đốc chỉ xem (toàn Tenant),
 * không tạo/sửa/xóa — BR-ORG-12 chỉ giao quyền này cho Manager.
 *
 * <p>Mọi đường ghi lấy {@code locationId} từ session, giống {@code FixedAssetService}
 * — client không truyền được.
>>>>>>> Nguyen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AreaService {

<<<<<<< HEAD
    private final AreaRepository       areaRepository;
    private final LocationRepository   locationRepository;
    private final FixedAssetRepository fixedAssetRepository;

    /**
     * @param locationId Manager bỏ trống để lấy Location của mình; Giám đốc bắt buộc chỉ rõ
     *                   vì đứng ở phạm vi toàn Tenant.
     */
    @Transactional(readOnly = true)
    public Page<AreaResponse> getAreas(UUID locationId, Pageable pageable) {
        Location location = resolveLocation(locationId);
        return areaRepository.findByLocationId(location.getId(), pageable)
            .map(AreaResponse::fromEntity);
=======
    private final AreaRepository        areaRepository;
    private final FixedAssetRepository  fixedAssetRepository;

    @Transactional(readOnly = true)
    public Page<AreaResponse> getAreas(Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Page<Area> page = isTenantWide()
            ? areaRepository.findByTenantId(tenantId, pageable)
            : areaRepository.findByTenantIdAndLocationId(tenantId, SecurityUtils.getCurrentLocationId(), pageable);

        return page.map(AreaResponse::fromEntity);
>>>>>>> Nguyen
    }

    @Transactional(readOnly = true)
    public AreaResponse getAreaById(UUID id) {
        return AreaResponse.fromEntity(getOwnedArea(id));
    }

    @Transactional
<<<<<<< HEAD
    public AreaResponse createArea(CreateAreaRequest request) {
        Location location = resolveLocation(request.getLocationId());
        String name = request.getName().trim();

        // BR-ORG-13: Area unique trong phạm vi LOCATION, khác 3 danh mục cấp Tenant.
        if (areaRepository.existsByLocationIdAndName(location.getId(), name)) {
            throw new BusinessException("Khu vực đã tồn tại trong khách sạn này: " + name);
        }

        Area area = areaRepository.save(Area.builder()
            .tenantId(location.getTenantId())
            .locationId(location.getId())
            .name(name)
            .build());

        log.info("Tạo Khu vực {} tại Location {}", name, location.getId());
        return AreaResponse.fromEntity(area);
    }

    @Transactional
    public AreaResponse updateArea(UUID id, UpdateAreaRequest request) {
        Area area = getOwnedArea(id);
        String name = request.getName().trim();

        if (areaRepository.existsByLocationIdAndNameAndIdNot(area.getLocationId(), name, area.getId())) {
            throw new BusinessException("Khu vực đã tồn tại trong khách sạn này: " + name);
        }

        area.setName(name);
=======
    public AreaResponse createArea(AreaRequest request) {
        UUID tenantId   = SecurityUtils.getCurrentTenantId();
        UUID locationId = SecurityUtils.getCurrentLocationId();
        String name     = request.getName().trim();

        assertNameAvailable(locationId, name, null);

        Area area = Area.builder()
            .tenantId(tenantId)
            .locationId(locationId)
            .name(name)
            .build();

        return AreaResponse.fromEntity(areaRepository.save(area));
    }

    @Transactional
    public AreaResponse updateArea(UUID id, AreaRequest request) {
        Area area = getOwnedArea(id);
        String name = request.getName().trim();

        assertNameAvailable(area.getLocationId(), name, id);
        area.setName(name);

>>>>>>> Nguyen
        return AreaResponse.fromEntity(areaRepository.save(area));
    }

    /**
<<<<<<< HEAD
     * BR-ORG-15: chặn cứng nếu còn tài sản cố định đang gắn vào — Manager phải chuyển tài
     * sản sang khu vực khác hoặc thanh lý trước.
=======
     * BR-ORG-15: chặn cứng nếu còn tài sản cố định gắn vào. Manager phải tự chuyển tài
     * sản sang khu vực/phòng khác hoặc thanh lý trước.
>>>>>>> Nguyen
     */
    @Transactional
    public void deleteArea(UUID id) {
        Area area = getOwnedArea(id);

<<<<<<< HEAD
        if (fixedAssetRepository.existsByAreaId(area.getId())) {
            throw new BusinessException(
                "Không xóa được Khu vực: vẫn còn tài sản cố định gắn vào. Hãy chuyển tài sản sang "
                + "khu vực khác hoặc thanh lý trước (BR-ORG-15).");
        }

        areaRepository.delete(area);
        log.info("Xóa Khu vực {}", area.getId());
    }

    // ── Nội bộ ──────────────────────────────────────────────────────────────

    /**
     * Manager luôn làm việc trong Location của mình, kể cả khi client gửi kèm locationId
     * khác — chốt này ngăn thao tác chéo khách sạn (BR-PERM-03).
     */
    private Location resolveLocation(UUID requestedLocationId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        UUID targetId;
        if (SecurityUtils.hasRole(Role.MANAGER)) {
            targetId = SecurityUtils.getCurrentLocationId();
            if (requestedLocationId != null && !requestedLocationId.equals(targetId)) {
                throw new BusinessException("Manager chỉ quản lý khu vực trong Location của mình.");
            }
        } else {
            if (requestedLocationId == null) {
                throw new BusinessException("locationId là bắt buộc: khu vực thuộc về một Location cụ thể.");
            }
            targetId = requestedLocationId;
        }

        return locationRepository.findByIdAndTenantId(targetId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", targetId));
    }

    private Area getOwnedArea(UUID id) {
        Area area = areaRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Area", "id", id));

        if (SecurityUtils.hasRole(Role.MANAGER)
                && !SecurityUtils.getCurrentLocationId().equals(area.getLocationId())) {
            throw new ResourceNotFoundException("Area", "id", id);
        }
        return area;
=======
        if (fixedAssetRepository.existsByAreaId(id)) {
            throw new BusinessException(
                "Không xóa được khu vực đang có tài sản cố định gắn vào. "
                    + "Hãy chuyển tài sản sang khu vực khác hoặc thanh lý trước (BR-ORG-15).");
        }

        areaRepository.delete(area);
        log.info("Đã xóa khu vực {} ({})", area.getName(), id);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Giám đốc đứng trên nhiều Location nên không có {@code locationId} để lọc. */
    private boolean isTenantWide() {
        return SecurityUtils.hasRole(Role.DIRECTOR);
    }

    /** Ngoài phạm vi thì trả 404 chứ không 403, để không lộ việc bản ghi có tồn tại. */
    private Area getOwnedArea(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return (isTenantWide()
            ? areaRepository.findByIdAndTenantId(id, tenantId)
            : areaRepository.findByIdAndTenantIdAndLocationId(id, tenantId, SecurityUtils.getCurrentLocationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khu vực"));
    }

    /** BR-ORG-13: tên unique trong phạm vi LOCATION, không phải toàn Tenant. */
    private void assertNameAvailable(UUID locationId, String name, UUID idToExclude) {
        boolean duplicated = idToExclude == null
            ? areaRepository.existsByLocationIdAndName(locationId, name)
            : areaRepository.existsByLocationIdAndNameAndIdNot(locationId, name, idToExclude);

        if (duplicated) {
            throw new BusinessException("Tên khu vực đã tồn tại trong khách sạn này: " + name);
        }
>>>>>>> Nguyen
    }
}
