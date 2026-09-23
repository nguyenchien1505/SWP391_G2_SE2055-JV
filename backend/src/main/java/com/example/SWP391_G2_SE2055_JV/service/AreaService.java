package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AreaResponse;
import com.example.SWP391_G2_SE2055_JV.dto.CreateAreaRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateAreaRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Area;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AreaRepository;
import com.example.SWP391_G2_SE2055_JV.repository.FixedAssetRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
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
 * <p>Khác Department/Position/RoomType ở chỗ Area tạo ở cấp LOCATION và do Manager CRUD:
 * sảnh, hành lang, kho là đặc thù vật lý của từng khách sạn chứ không phải danh mục dùng
 * chung toàn chuỗi. Vì vậy tên chỉ unique trong phạm vi Location, và Area không có cờ
 * is_active.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AreaService {

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
    }

    @Transactional(readOnly = true)
    public AreaResponse getAreaById(UUID id) {
        return AreaResponse.fromEntity(getOwnedArea(id));
    }

    @Transactional
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
        return AreaResponse.fromEntity(areaRepository.save(area));
    }

    /**
     * BR-ORG-15: chặn cứng nếu còn tài sản cố định đang gắn vào — Manager phải chuyển tài
     * sản sang khu vực khác hoặc thanh lý trước.
     */
    @Transactional
    public void deleteArea(UUID id) {
        Area area = getOwnedArea(id);

        if (fixedAssetRepository.existsByAreaId(area.getId())) {
            throw new BusinessException(
                "Không xóa được Khu vực: vẫn còn tài sản cố định gắn vào. Hãy chuyển tài sản sang "
                + "khu vực khác hoặc thanh lý trước.");
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
    }
}
