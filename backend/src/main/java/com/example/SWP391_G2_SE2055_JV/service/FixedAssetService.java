package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.asset.FixedAssetRequest;
import com.example.SWP391_G2_SE2055_JV.dto.asset.FixedAssetResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Area;
import com.example.SWP391_G2_SE2055_JV.entity.AssetCategory;
import com.example.SWP391_G2_SE2055_JV.entity.FixedAsset;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AreaRepository;
import com.example.SWP391_G2_SE2055_JV.repository.AssetCategoryRepository;
import com.example.SWP391_G2_SE2055_JV.repository.FixedAssetRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Tài sản cố định — BR-ASSET-01..03, BR-ASSET-12..14.
 *
 * <p>Phạm vi nhìn khác nhau theo vai trò: Giám đốc xem toàn Tenant, Manager và Staff
 * chỉ trong Location của mình. Quyền GHI thuộc về Manager (BR-ASSET-09: "Manager tạo
 * và quản lý từng tài sản cá thể trong Location của mình"), nên mọi đường ghi lấy
 * {@code locationId} từ session — client không truyền được.
 *
 * <p>Điểm dễ bỏ sót nhất là BR-ASSET-13: khóa ngoại {@code fk_fixed_assets_room} chỉ
 * đảm bảo phòng TỒN TẠI, không đảm bảo phòng thuộc đúng Location. Thiếu kiểm tra ở
 * {@link #validatePlacement} thì Manager gắn được tài sản sang khách sạn khác.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FixedAssetService {

    /** BR-ASSET-12 — tiền tố mặc định; Manager vẫn sửa mã được. */
    private static final String ASSET_CODE_PREFIX = "TS-";
    private static final int    MAX_CODE_ATTEMPTS = 50;

    /** BR-ASSET-14 — ẩn khỏi danh sách vận hành, bản ghi vẫn giữ để tra lịch sử. */
    private static final List<FixedAssetStatus> HIDDEN_IN_OPERATIONS = List.of(FixedAssetStatus.DISPOSED);

    private final FixedAssetRepository    fixedAssetRepository;
    private final AssetCategoryRepository categoryRepository;
    private final RoomRepository          roomRepository;
    private final AreaRepository          areaRepository;
    /** Chỉ dùng để tự đóng báo hỏng NEW còn treo khi thanh lý — xem {@link #updateStatus}. */
    private final DamageReportService     damageReportService;

    /**
     * @param includeDisposed bật để xem cả tài sản đã thanh lý; mặc định ẩn theo
     *                        BR-ASSET-14
     */
    @Transactional(readOnly = true)
    public Page<FixedAssetResponse> getFixedAssets(boolean includeDisposed, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Page<FixedAsset> page;
        if (isTenantWide()) {
            page = includeDisposed
                ? fixedAssetRepository.findByTenantId(tenantId, pageable)
                : fixedAssetRepository.findByTenantIdAndStatusNotIn(tenantId, HIDDEN_IN_OPERATIONS, pageable);
        } else {
            UUID locationId = SecurityUtils.getCurrentLocationId();
            page = includeDisposed
                ? fixedAssetRepository.findByTenantIdAndLocationId(tenantId, locationId, pageable)
                : fixedAssetRepository.findByTenantIdAndLocationIdAndStatusNotIn(
                    tenantId, locationId, HIDDEN_IN_OPERATIONS, pageable);
        }
        return page.map(this::enrichResponse);
    }

    @Transactional(readOnly = true)
    public FixedAssetResponse getFixedAssetById(UUID id) {
        return enrichResponse(getOwnedAsset(id));
    }

    @Transactional
    public FixedAssetResponse createFixedAsset(FixedAssetRequest request) {
        UUID tenantId   = SecurityUtils.getCurrentTenantId();
        UUID locationId = SecurityUtils.getCurrentLocationId();

        validatePlacement(request, tenantId, locationId);
        requireFixedCategory(request.getCategoryId(), tenantId, true);

        String assetCode = StringUtils.isBlank(request.getAssetCode())
            ? generateAssetCode(locationId)
            : request.getAssetCode().trim();
        assertCodeAvailable(locationId, assetCode, null);

        FixedAsset asset = FixedAsset.builder()
            .tenantId(tenantId)
            .locationId(locationId)
            .categoryId(request.getCategoryId())
            .assetCode(assetCode)
            .name(request.getName())
            .roomId(request.getRoomId())
            .areaId(request.getAreaId())
            .status(FixedAssetStatus.GOOD)
            .note(request.getNote())
            .build();

        return enrichResponse(fixedAssetRepository.save(asset));
    }

    /**
     * Sửa thông tin và vị trí. Đổi vị trí giữa các Phòng/Khu vực TRONG CÙNG Location là
     * hợp lệ và không lưu lịch sử di chuyển — BR-ASSET-13.
     */
    @Transactional
    public FixedAssetResponse updateFixedAsset(UUID id, FixedAssetRequest request) {
        FixedAsset asset = getOwnedAsset(id);
        assertNotDisposed(asset);

        UUID tenantId = asset.getTenantId();

        validatePlacement(request, tenantId, asset.getLocationId());
        // Chỉ bắt danh mục phải còn hiệu lực khi Manager ĐỔI sang danh mục khác. Nếu
        // giữ nguyên danh mục cũ mà Giám đốc vừa ẩn nó đi (BR-ORG-14), việc sửa tên hay
        // đổi vị trí tài sản vẫn phải làm được.
        boolean categoryChanged = !asset.getCategoryId().equals(request.getCategoryId());
        requireFixedCategory(request.getCategoryId(), tenantId, categoryChanged);

        if (StringUtils.isNotBlank(request.getAssetCode())) {
            String newCode = request.getAssetCode().trim();
            assertCodeAvailable(asset.getLocationId(), newCode, id);
            asset.setAssetCode(newCode);
        }

        asset.setCategoryId(request.getCategoryId());
        asset.setName(request.getName());
        asset.setRoomId(request.getRoomId());
        asset.setAreaId(request.getAreaId());
        asset.setNote(request.getNote());

        return enrichResponse(fixedAssetRepository.save(asset));
    }

    /**
     * BR-ASSET-02 + BR-ASSET-14: ba trạng thái Tốt/Hỏng/Đang sửa chuyển tự do qua lại,
     * không cần ma trận. {@code DISPOSED} là trạng thái cuối — vào được, không ra được.
     *
     * <p>Đổi trạng thái tài sản KHÔNG kéo theo trạng thái phòng (BR-ASSET-06). Riêng
     * chiều DISPOSED có một hiệu ứng phụ có chủ đích: tự đóng mọi báo hỏng
     * {@code NEW} còn treo trên tài sản này, để không lưu báo cáo "đang chờ xử lý" cho
     * một tài sản không còn tồn tại về mặt vận hành.
     */
    @Transactional
    public FixedAssetResponse updateStatus(UUID id, FixedAssetStatus target) {
        FixedAsset asset = getOwnedAsset(id);
        assertNotDisposed(asset);

        if (asset.getStatus() != target) {
            log.info("Tài sản {} đổi trạng thái {} -> {}", asset.getAssetCode(), asset.getStatus(), target);
            asset.setStatus(target);
            fixedAssetRepository.save(asset);

            if (target == FixedAssetStatus.DISPOSED) {
                damageReportService.autoResolveForDisposedAsset(asset.getId(), SecurityUtils.getCurrentUserId());
            }
        }
        return enrichResponse(asset);
    }

    @Transactional
    public void deleteFixedAsset(UUID id) {
        FixedAsset asset = getOwnedAsset(id);
        log.info("Xóa vĩnh viễn tài sản {}", asset.getAssetCode());
        fixedAssetRepository.delete(asset);
    }

    private FixedAssetResponse enrichResponse(FixedAsset asset) {
        FixedAssetResponse response = FixedAssetResponse.fromEntity(asset);
        if (asset.getRoomId() != null) {
            roomRepository.findById(asset.getRoomId())
                .ifPresent(room -> response.setRoomName("Phòng " + room.getRoomNumber()));
        } else if (asset.getAreaId() != null) {
            areaRepository.findById(asset.getAreaId())
                .ifPresent(area -> response.setAreaName(area.getName()));
        }
        return response;
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Giám đốc đứng trên nhiều Location nên không có {@code locationId} để lọc. */
    private boolean isTenantWide() {
        return SecurityUtils.hasRole(Role.DIRECTOR);
    }

    /** Ngoài phạm vi thì trả 404 chứ không 403, để không lộ việc bản ghi có tồn tại. */
    private FixedAsset getOwnedAsset(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return (isTenantWide()
            ? fixedAssetRepository.findByIdAndTenantId(id, tenantId)
            : fixedAssetRepository.findByIdAndTenantIdAndLocationId(
                id, tenantId, SecurityUtils.getCurrentLocationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài sản cố định"));
    }

    private void assertNotDisposed(FixedAsset asset) {
        if (asset.isDisposed()) {
            throw new BusinessException(
                "Tài sản đã thanh lý là trạng thái cuối, không sửa và không quay lại được (BR-ASSET-14).");
        }
    }

    /**
     * BR-ASSET-03 + BR-ASSET-13. Hai việc tách bạch: XOR là hình thức, còn kiểm tra
     * Phòng/Khu vực có thuộc đúng Location hay không mới là chốt chặn cách ly dữ liệu.
     */
    private void validatePlacement(FixedAssetRequest request, UUID tenantId, UUID locationId) {
        if ((request.getRoomId() == null) == (request.getAreaId() == null)) {
            throw new BusinessException(
                "Tài sản cố định phải gắn với đúng MỘT Phòng HOẶC MỘT Khu vực — "
                    + "không được cả hai, không được bỏ trống (BR-ASSET-03).");
        }

        if (request.getRoomId() != null) {
            Room room = roomRepository.findByIdAndTenantId(request.getRoomId(), tenantId)
                .orElseThrow(() -> new BusinessException(
                    "Không tìm thấy phòng với id: " + request.getRoomId()));
            if (!locationId.equals(room.getLocationId())) {
                throw new BusinessException(
                    "Phòng này thuộc một Location khác. Không gắn tài sản xuyên Location (BR-ASSET-13).");
            }
            if (!room.isActive()) {
                throw new BusinessException("Phòng này đã bị xóa, không gắn tài sản vào được.");
            }
        } else {
            Area area = areaRepository.findByIdAndTenantId(request.getAreaId(), tenantId)
                .orElseThrow(() -> new BusinessException(
                    "Không tìm thấy khu vực với id: " + request.getAreaId()));
            if (!locationId.equals(area.getLocationId())) {
                throw new BusinessException(
                    "Khu vực này thuộc một Location khác. Không gắn tài sản xuyên Location (BR-ASSET-13).");
            }
        }
    }

    /**
     * Danh mục phải thuộc Tenant hiện tại và phải là loại FIXED — hai loại tài sản dùng
     * CHUNG một bảng danh mục nên rất dễ chọn nhầm (BR-ASSET-08).
     *
     * @param requireActive chỉ bắt buộc khi tạo mới hoặc khi đổi sang danh mục khác
     */
    private void requireFixedCategory(UUID categoryId, UUID tenantId, boolean requireActive) {
        AssetCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
            .orElseThrow(() -> new BusinessException("Không tìm thấy danh mục tài sản với id: " + categoryId));

        if (!category.isFixed()) {
            throw new BusinessException(
                "Danh mục \"" + category.getName() + "\" là loại tiêu hao, không dùng cho tài sản cố định.");
        }
        if (requireActive && !category.isActive()) {
            throw new BusinessException(
                "Danh mục \"" + category.getName() + "\" đang bị ẩn, không chọn được cho tài sản mới.");
        }
    }

    /** BR-ASSET-12 — unique trong phạm vi LOCATION, không phải toàn Tenant. */
    private void assertCodeAvailable(UUID locationId, String assetCode, UUID idToExclude) {
        boolean duplicated = idToExclude == null
            ? fixedAssetRepository.existsByLocationIdAndAssetCode(locationId, assetCode)
            : fixedAssetRepository.existsByLocationIdAndAssetCodeAndIdNot(locationId, assetCode, idToExclude);

        if (duplicated) {
            throw new BusinessException("Mã tài sản đã tồn tại trong khách sạn này: " + assetCode);
        }
    }

    /**
     * Sinh mã kế tiếp từ mã LỚN NHẤT đang dùng, không phải từ số lượng bản ghi: vì
     * BR-ASSET-12 cho Manager tự đặt mã, đếm bản ghi sẽ ra số đã bị chiếm và đâm vào
     * ràng buộc unique. Mã được đệm 0 cho đủ 5 chữ số nên thứ tự chuỗi trùng thứ tự số.
     *
     * <p>Vẫn dò tiếp vài bước vì mã Manager tự đặt có thể không theo dạng số. Hai
     * request tạo đồng thời vẫn có thể cùng chọn một số — trường hợp đó ràng buộc
     * unique ở DB chặn lại và trả 409, người dùng thử lại là xong.
     */
    private String generateAssetCode(UUID locationId) {
        int next = fixedAssetRepository
            .findTopByLocationIdAndAssetCodeStartingWithOrderByAssetCodeDesc(locationId, ASSET_CODE_PREFIX)
            .map(FixedAsset::getAssetCode)
            .map(FixedAssetService::parseSequence)
            .orElse(0) + 1;

        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String candidate = String.format("%s%05d", ASSET_CODE_PREFIX, next + i);
            if (!fixedAssetRepository.existsByLocationIdAndAssetCode(locationId, candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("Không sinh được mã tài sản tự động, vui lòng nhập mã thủ công.");
    }

    private static int parseSequence(String assetCode) {
        try {
            return Integer.parseInt(assetCode.substring(ASSET_CODE_PREFIX.length()));
        } catch (NumberFormatException | IndexOutOfBoundsException ex) {
            return 0;
        }
    }
}
