package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftTemplateRequest;
import com.example.SWP391_G2_SE2055_JV.dto.LocationTemplateSetResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftTemplateResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateShiftTemplateRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mẫu ca — BR-SCH-04, BR-SCH-22.
 *
 * <p>Giám đốc quản lý hai loại BỘ mẫu (chốt 06/10/2026, V6): bộ mẫu CHUNG của chuỗi và bộ mẫu
 * RIÊNG của từng chi nhánh. Mỗi chi nhánh dùng ĐÚNG MỘT bộ — mặc định bộ chung; Giám đốc bật bộ riêng
 * cho chi nhánh nào thì Manager ở đó chỉ thấy bộ riêng ({@code Location.ownShiftTemplates}). Bật là
 * thao tác chủ động: thêm mẫu riêng không tự đổi bộ, nên Giám đốc soạn sẵn bộ riêng (tự thêm từng mẫu,
 * hoặc sao chép bộ chung làm điểm xuất phát) rồi mới bật.
 *
 * <p><b>Không có xóa.</b> BR-SCH-22 quy định chỉ vô hiệu hóa, vì ca đã xếp vẫn trỏ vào mẫu
 * qua {@code shifts.source_template_id} để tra lịch sử — xóa cứng sẽ vỡ khóa ngoại đó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftTemplateService {

    private final ShiftTemplateRepository shiftTemplateRepository;
    private final LocationRepository      locationRepository;

    /**
     * @param includeInactive true để Giám đốc quản trị danh mục (thấy cả mẫu đã tắt);
     *                        false (mặc định) cho màn hình xếp ca — BR-SCH-22.
     * @param locationId      chỉ lấy BỘ mẫu chi nhánh này đang dùng (chung hoặc riêng); null = mọi
     *                        mẫu của Tenant. Manager luôn chỉ thấy bộ của chi nhánh mình.
     */
    @Transactional(readOnly = true)
    public Page<ShiftTemplateResponse> getTemplates(boolean includeInactive, UUID locationId, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID usableAt = SecurityUtils.hasRole(Role.MANAGER) ? SecurityUtils.getCurrentLocationId() : locationId;

        Page<ShiftTemplate> page;
        if (usableAt != null) {
            Location location = requireLocation(usableAt, tenantId);
            page = shiftTemplateRepository.findInSet(tenantId, setOf(location), includeInactive, pageable);
        } else {
            page = includeInactive
                ? shiftTemplateRepository.findByTenantId(tenantId, pageable)
                : shiftTemplateRepository.findByTenantIdAndActiveTrue(tenantId, pageable);
        }
        return page.map(ShiftTemplateResponse::fromEntity);
    }

    /**
     * Mẫu riêng của chi nhánh khác: với Manager coi như không tồn tại (404, không lộ). Mẫu chung và
     * mẫu riêng của chính chi nhánh thì đọc được cả khi chi nhánh đang dùng bộ kia — ca cũ xếp từ bộ
     * trước khi đổi vẫn phải tra được tên mẫu.
     */
    @Transactional(readOnly = true)
    public ShiftTemplateResponse getTemplateById(UUID id) {
        ShiftTemplate template = getOwnedTemplate(id);
        if (SecurityUtils.hasRole(Role.MANAGER) && template.getLocationId() != null
                && !template.getLocationId().equals(SecurityUtils.getCurrentLocationId())) {
            throw new ResourceNotFoundException("ShiftTemplate", "id", id);
        }
        return ShiftTemplateResponse.fromEntity(template);
    }

    /**
     * {@code request.locationId} null = thêm vào bộ mẫu chung; có giá trị = thêm vào bộ mẫu riêng của
     * chi nhánh đó. Thêm vào bộ riêng KHÔNG tự bật bộ riêng cho chi nhánh.
     */
    @Transactional
    public ShiftTemplateResponse createTemplate(CreateShiftTemplateRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = request.getLocationId();
        String name = request.getName().trim();

        if (locationId != null) {
            requireLocation(locationId, tenantId);
        }
        assertNameFree(tenantId, locationId, name, null);

        ShiftTemplate template = shiftTemplateRepository.save(ShiftTemplate.builder()
            .tenantId(tenantId)
            .locationId(locationId)
            .name(name)
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .description(trimToNull(request.getDescription()))
            .build());

        log.info("Tạo mẫu ca {} ({}–{}) cho Tenant {}, bộ {}",
            name, template.getStartTime(), template.getEndTime(), tenantId,
            locationId == null ? "chung" : locationId);
        return ShiftTemplateResponse.fromEntity(template);
    }

    /**
     * Sửa mẫu KHÔNG lan sang ca đã xếp: mỗi ca đã lưu giờ của riêng nó và đã qua kiểm tra
     * Schedule Policy tại thời điểm xếp (BR-SCH-02). Đổi giờ hàng loạt sẽ tạo ra ca vi phạm
     * policy mà không ai kiểm lại được.
     *
     * <p>Không chuyển được mẫu sang bộ khác: ca đã xếp đang trỏ vào mẫu, đổi bộ sẽ để lại những ca
     * trỏ vào một mẫu mà chi nhánh của chúng không dùng.
     */
    @Transactional
    public ShiftTemplateResponse updateTemplate(UUID id, UpdateShiftTemplateRequest request) {
        ShiftTemplate template = getOwnedTemplate(id);
        String name = request.getName().trim();

        assertNameFree(template.getTenantId(), template.getLocationId(), name, template.getId());

        template.setName(name);
        template.setStartTime(request.getStartTime());
        template.setEndTime(request.getEndTime());
        template.setDescription(trimToNull(request.getDescription()));

        log.info("Cập nhật mẫu ca {}", template.getId());
        return ShiftTemplateResponse.fromEntity(shiftTemplateRepository.save(template));
    }

    /** BR-SCH-22: vô hiệu hóa thay cho xóa. Mẫu đã tắt không xếp ca mới được nữa. */
    @Transactional
    public ShiftTemplateResponse setActive(UUID id, boolean active) {
        ShiftTemplate template = getOwnedTemplate(id);
        template.setActive(active);

        log.info("{} mẫu ca {}", active ? "Bật" : "Tắt", template.getId());
        return ShiftTemplateResponse.fromEntity(shiftTemplateRepository.save(template));
    }

    /**
     * Chi nhánh dùng bộ mẫu chung hay bộ mẫu riêng của nó (chốt 06/10/2026). Ca đã xếp giữ nguyên mẫu
     * của nó; chỉ ca xếp từ giờ mới chọn trong bộ đang dùng.
     *
     * <p>Bộ riêng phải có ít nhất một mẫu đang dùng mới bật được — không để Manager mở màn xếp lịch ra
     * mà không còn mẫu nào để chọn.
     */
    @Transactional
    public LocationTemplateSetResponse setOwnTemplates(UUID locationId, boolean own) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Location location = requireLocation(locationId, tenantId);
        long ownActive = shiftTemplateRepository.countByTenantIdAndLocationIdAndActiveTrue(tenantId, locationId);

        if (own && ownActive == 0) {
            throw new BusinessException("«" + location.getName() + "» chưa có mẫu ca riêng nào đang dùng. "
                + "Thêm mẫu riêng hoặc sao chép từ mẫu chung trước khi chuyển sang bộ riêng.");
        }
        location.setOwnShiftTemplates(own);
        locationRepository.save(location);

        log.info("Chi nhánh {} chuyển sang bộ mẫu {}", locationId, own ? "riêng" : "chung");
        return LocationTemplateSetResponse.of(location, ownActive);
    }

    /**
     * Sao chép các mẫu CHUNG đang dùng sang bộ riêng của chi nhánh — điểm xuất phát để Giám đốc sửa
     * tiếp, khỏi nhập lại từ đầu. Mẫu trùng tên với mẫu đã có trong bộ riêng thì bỏ qua (giữ bản của
     * chi nhánh). Không tự bật bộ riêng.
     *
     * @return các mẫu vừa tạo trong bộ riêng
     */
    @Transactional
    public List<ShiftTemplateResponse> copyCommonTemplates(UUID locationId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        requireLocation(locationId, tenantId);

        List<ShiftTemplate> created = new ArrayList<>();
        for (ShiftTemplate common
                : shiftTemplateRepository.findByTenantIdAndLocationIdIsNullAndActiveTrueOrderByStartTimeAsc(tenantId)) {
            // So tên bằng truy vấn để khớp đúng collation của cột (không phân biệt hoa thường, dấu).
            if (shiftTemplateRepository.existsNameInSet(tenantId, locationId, common.getName(), null)) {
                continue;
            }
            created.add(shiftTemplateRepository.save(ShiftTemplate.builder()
                .tenantId(tenantId)
                .locationId(locationId)
                .name(common.getName())
                .startTime(common.getStartTime())
                .endTime(common.getEndTime())
                .description(common.getDescription())
                .build()));
        }

        log.info("Sao chép {} mẫu chung sang bộ riêng của chi nhánh {}", created.size(), locationId);
        return created.stream().map(ShiftTemplateResponse::fromEntity).toList();
    }

    /** Bộ mẫu chi nhánh đang dùng, theo quy ước của {@link ShiftTemplateRepository#findInSet}. */
    private static UUID setOf(Location location) {
        return location.isOwnShiftTemplates() ? location.getId() : null;
    }

    /**
     * Manager chọn mẫu theo tên, nên trong MỘT bộ mẫu không được có hai tên giống nhau. Hai bộ khác
     * nhau trùng tên thì không sao — một chi nhánh chỉ dùng một bộ (khớp unique của V5).
     */
    private void assertNameFree(UUID tenantId, UUID locationId, String name, UUID excludeId) {
        if (shiftTemplateRepository.existsNameInSet(tenantId, locationId, name, excludeId)) {
            throw new BusinessException(locationId == null
                ? "Bộ mẫu chung đã có mẫu ca tên: " + name
                : "Bộ mẫu riêng của chi nhánh này đã có mẫu ca tên: " + name);
        }
    }

    /** Khóa ngoại chỉ đảm bảo chi nhánh TỒN TẠI, không đảm bảo nó thuộc Tenant này. */
    private Location requireLocation(UUID locationId, UUID tenantId) {
        return locationRepository.findByIdAndTenantId(locationId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", locationId));
    }

    private ShiftTemplate getOwnedTemplate(UUID id) {
        return shiftTemplateRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("ShiftTemplate", "id", id));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
