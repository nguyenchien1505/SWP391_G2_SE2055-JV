package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftTemplateRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftTemplateResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateShiftTemplateRequest;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Mẫu ca — BR-SCH-04, BR-SCH-22.
 *
 * <p>Danh mục cấp TENANT do Giám đốc định nghĩa, dùng chung cho mọi Location; Manager chỉ
 * đọc để chọn khi xếp ca.
 *
 * <p><b>Không có xóa.</b> BR-SCH-22 quy định chỉ vô hiệu hóa, vì ca đã xếp vẫn trỏ vào mẫu
 * qua {@code shifts.source_template_id} để tra lịch sử — xóa cứng sẽ vỡ khóa ngoại đó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftTemplateService {

    private final ShiftTemplateRepository shiftTemplateRepository;

    /**
     * @param includeInactive true để Giám đốc quản trị danh mục (thấy cả mẫu đã tắt);
     *                        false (mặc định) cho màn hình xếp ca — BR-SCH-22.
     */
    @Transactional(readOnly = true)
    public Page<ShiftTemplateResponse> getTemplates(boolean includeInactive, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Page<ShiftTemplate> page = includeInactive
            ? shiftTemplateRepository.findByTenantId(tenantId, pageable)
            : shiftTemplateRepository.findByTenantIdAndActiveTrue(tenantId, pageable);

        return page.map(ShiftTemplateResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ShiftTemplateResponse getTemplateById(UUID id) {
        return ShiftTemplateResponse.fromEntity(getOwnedTemplate(id));
    }

    @Transactional
    public ShiftTemplateResponse createTemplate(CreateShiftTemplateRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        String name = request.getName().trim();

        if (shiftTemplateRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new BusinessException("Mẫu ca đã tồn tại trong khách sạn của bạn: " + name);
        }

        ShiftTemplate template = shiftTemplateRepository.save(ShiftTemplate.builder()
            .tenantId(tenantId)
            .name(name)
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .description(trimToNull(request.getDescription()))
            .build());

        log.info("Tạo mẫu ca {} ({}–{}) cho Tenant {}",
            name, template.getStartTime(), template.getEndTime(), tenantId);
        return ShiftTemplateResponse.fromEntity(template);
    }

    /**
     * Sửa mẫu KHÔNG lan sang ca đã xếp: mỗi ca đã lưu giờ của riêng nó và đã qua kiểm tra
     * Schedule Policy tại thời điểm xếp (BR-SCH-02). Đổi giờ hàng loạt sẽ tạo ra ca vi phạm
     * policy mà không ai kiểm lại được.
     */
    @Transactional
    public ShiftTemplateResponse updateTemplate(UUID id, UpdateShiftTemplateRequest request) {
        ShiftTemplate template = getOwnedTemplate(id);
        String name = request.getName().trim();

        if (shiftTemplateRepository.existsByTenantIdAndNameAndIdNot(
                template.getTenantId(), name, template.getId())) {
            throw new BusinessException("Mẫu ca đã tồn tại trong khách sạn của bạn: " + name);
        }

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
