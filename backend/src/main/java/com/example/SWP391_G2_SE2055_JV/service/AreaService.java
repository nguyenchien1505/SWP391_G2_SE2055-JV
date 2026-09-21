package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.organization.AreaRequest;
import com.example.SWP391_G2_SE2055_JV.dto.organization.AreaResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Area;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AreaRepository;
import com.example.SWP391_G2_SE2055_JV.repository.FixedAssetRepository;
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
 * <p>Khác 3 danh mục cấp Tenant (Department/Position/RoomType): Khu vực tạo ở cấp
 * LOCATION, do Manager CRUD trong Location của mình. Giám đốc chỉ xem (toàn Tenant),
 * không tạo/sửa/xóa — BR-ORG-12 chỉ giao quyền này cho Manager.
 *
 * <p>Mọi đường ghi lấy {@code locationId} từ session, giống {@code FixedAssetService}
 * — client không truyền được.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AreaService {

    private final AreaRepository        areaRepository;
    private final FixedAssetRepository  fixedAssetRepository;

    @Transactional(readOnly = true)
    public Page<AreaResponse> getAreas(Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Page<Area> page = isTenantWide()
            ? areaRepository.findByTenantId(tenantId, pageable)
            : areaRepository.findByTenantIdAndLocationId(tenantId, SecurityUtils.getCurrentLocationId(), pageable);

        return page.map(AreaResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public AreaResponse getAreaById(UUID id) {
        return AreaResponse.fromEntity(getOwnedArea(id));
    }

    @Transactional
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

        return AreaResponse.fromEntity(areaRepository.save(area));
    }

    /**
     * BR-ORG-15: chặn cứng nếu còn tài sản cố định gắn vào. Manager phải tự chuyển tài
     * sản sang khu vực/phòng khác hoặc thanh lý trước.
     */
    @Transactional
    public void deleteArea(UUID id) {
        Area area = getOwnedArea(id);

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
    }
}
