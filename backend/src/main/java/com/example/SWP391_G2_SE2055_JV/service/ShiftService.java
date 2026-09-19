package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Xếp ca — BR-SCH-02..05, BR-SCH-24, DM-03, DM-15.
 *
 * <p>Ca là một SLOT: gỡ người là {@code staffId = null} kèm lý do, không xóa bản ghi.
 * Mọi đường ghi thay đổi người hoặc giờ ca đều chạy lại {@link SchedulePolicyValidator},
 * vi phạm là chặn cứng không override (BR-SCH-02).
 *
 * <p>Mọi truy vấn đều lọc theo tenantId lấy từ session — không dùng {@code findAll()} trần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository          shiftRepository;
    private final UserRepository           userRepository;
    private final LocationRepository       locationRepository;
    private final ShiftTemplateRepository  shiftTemplateRepository;
    private final SchedulePolicyValidator  policyValidator;

    @Transactional(readOnly = true)
    public Page<ShiftResponse> getShifts(Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        // Staff chỉ thấy ca của chính mình; Manager giới hạn trong Location của mình.
        if (SecurityUtils.hasRole(Role.STAFF)) {
            return shiftRepository.findByStaffId(SecurityUtils.getCurrentUserId(), pageable)
                .map(ShiftResponse::fromEntity);
        }
        if (SecurityUtils.hasRole(Role.MANAGER)) {
            return shiftRepository
                .findByTenantIdAndLocationId(tenantId, SecurityUtils.getCurrentLocationId(), pageable)
                .map(ShiftResponse::fromEntity);
        }
        return shiftRepository.findByTenantId(tenantId, pageable).map(ShiftResponse::fromEntity);
    }

    /**
     * Cùng phạm vi với {@link #getShifts}: Staff chỉ xem ca của mình, Manager chỉ xem ca
     * trong Location của mình. Ngoài phạm vi thì trả 404 thay vì 403 để không lộ việc ca
     * đó có tồn tại.
     */
    @Transactional(readOnly = true)
    public ShiftResponse getShiftById(UUID id) {
        Shift shift = getOwnedShift(id);

        boolean outOfScope =
            (SecurityUtils.hasRole(Role.STAFF)
                && !SecurityUtils.getCurrentUserId().equals(shift.getStaffId()))
            || (SecurityUtils.hasRole(Role.MANAGER)
                && !SecurityUtils.getCurrentLocationId().equals(shift.getLocationId()));
        if (outOfScope) {
            throw new ResourceNotFoundException("Shift", "id", id);
        }
        return ShiftResponse.fromEntity(shift);
    }

    @Transactional
    public ShiftResponse createShift(CreateShiftRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        assertManagesLocation(request.getLocationId());
        assertLocationInTenant(request.getLocationId(), tenantId);
        assertTemplateUsable(request.getSourceTemplateId(), tenantId);

        Shift shift = Shift.builder()
            .tenantId(tenantId)
            .locationId(request.getLocationId())
            .staffId(request.getStaffId())
            .shiftDate(request.getShiftDate())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .overnight(ShiftTimeUtils.isOvernight(request.getStartTime(), request.getEndTime()))
            .durationHours(ShiftTimeUtils.durationHours(request.getStartTime(), request.getEndTime()))
            .sourceTemplateId(request.getSourceTemplateId())
            .build();

        if (request.getStaffId() != null) {
            assertStaffBelongsToLocation(request.getStaffId(), tenantId, request.getLocationId());
            policyValidator.validate(tenantId, request.getStaffId(), shift, null);
        }

        Shift saved = shiftRepository.save(shift);
        log.info("Tạo ca {} ngày {} tại Location {} cho staff {}",
            saved.getId(), saved.getShiftDate(), saved.getLocationId(), saved.getStaffId());
        return ShiftResponse.fromEntity(saved);
    }

    @Transactional
    public ShiftResponse updateShift(UUID id, UpdateShiftRequest request) {
        Shift shift = getOwnedShift(id);
        assertManagesLocation(shift.getLocationId());

        if (request.getShiftDate() != null) {
            shift.setShiftDate(request.getShiftDate());
        }
        if (request.getStartTime() != null) {
            shift.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            shift.setEndTime(request.getEndTime());
        }
        if (request.getSourceTemplateId() != null) {
            assertTemplateUsable(request.getSourceTemplateId(), shift.getTenantId());
            shift.setSourceTemplateId(request.getSourceTemplateId());
        }

        // Giờ ca đổi thì độ dài và cờ qua đêm phải tính lại trước khi validate (BR-SCH-03).
        shift.setOvernight(ShiftTimeUtils.isOvernight(shift.getStartTime(), shift.getEndTime()));
        shift.setDurationHours(ShiftTimeUtils.durationHours(shift.getStartTime(), shift.getEndTime()));

        policyValidator.validate(shift.getTenantId(), shift.getStaffId(), shift, shift.getId());

        log.info("Cập nhật ca {}", id);
        return ShiftResponse.fromEntity(shiftRepository.save(shift));
    }

    /** Gán người cho ca — chạy lại toàn bộ kiểm tra Policy cho người được gán. */
    @Transactional
    public ShiftResponse assignStaff(UUID id, UUID staffId) {
        Shift shift = getOwnedShift(id);
        assertManagesLocation(shift.getLocationId());

        if (shift.isAssigned()) {
            throw new BusinessException(
                "Ca đã có người phụ trách. Gỡ người hiện tại trước khi gán người mới.");
        }
        assertStaffBelongsToLocation(staffId, shift.getTenantId(), shift.getLocationId());
        policyValidator.validate(shift.getTenantId(), staffId, shift, shift.getId());

        shift.setStaffId(staffId);
        shift.setUnassignedReason(null);
        shift.setUnassignedAt(null);

        log.info("Gán ca {} cho staff {}", id, staffId);
        return ShiftResponse.fromEntity(shiftRepository.save(shift));
    }

    /**
     * Gỡ người khỏi ca — BR-SCH-24. Ca quay về trạng thái chưa phân công và LUÔN
     * ghi lại lý do; bản ghi ca không bị xóa (DM-03).
     */
    @Transactional
    public ShiftResponse unassignStaff(UUID id, UnassignedReason reason) {
        Shift shift = getOwnedShift(id);
        assertManagesLocation(shift.getLocationId());

        if (!shift.isAssigned()) {
            throw new BusinessException("Ca này vốn đã ở trạng thái chưa phân công.");
        }

        shift.setStaffId(null);
        shift.setUnassignedReason(reason);
        shift.setUnassignedAt(LocalDateTime.now());

        log.info("Gỡ người khỏi ca {} — lý do {}", id, reason);
        return ShiftResponse.fromEntity(shiftRepository.save(shift));
    }

    @Transactional
    public void deleteShift(UUID id) {
        Shift shift = getOwnedShift(id);
        assertManagesLocation(shift.getLocationId());
        // DM-15: dữ liệu chấm công nằm ngay trên bản ghi ca — xóa ca đã check-in là mất luôn.
        if (shift.isCheckedIn()) {
            throw new BusinessException("Không xóa được ca đã check-in — sẽ mất dữ liệu chấm công.");
        }
        // Ca không có cột xóa mềm: BR-ROOM-09/DM-17 chỉ yêu cầu lưu vết cho phòng,
        // còn một slot ca chưa ai làm thì không còn giá trị lịch sử nào.
        shiftRepository.delete(shift);
        log.info("Xóa ca {}", id);
    }

    /** BR-DASH-01: chỉ ghi nhận timestamp, không tự tính đi muộn/về sớm. */
    @Transactional
    public ShiftResponse checkIn(UUID id) {
        Shift shift = getOwnedShift(id);
        assertIsOwnShift(shift);

        if (shift.isCheckedIn()) {
            throw new BusinessException("Ca này đã được check-in.");
        }
        shift.setCheckInAt(LocalDateTime.now());
        return ShiftResponse.fromEntity(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftResponse checkOut(UUID id) {
        Shift shift = getOwnedShift(id);
        assertIsOwnShift(shift);

        if (!shift.isCheckedIn()) {
            throw new BusinessException("Phải check-in trước khi check-out.");
        }
        if (shift.getCheckOutAt() != null) {
            throw new BusinessException("Ca này đã được check-out.");
        }
        shift.setCheckOutAt(LocalDateTime.now());
        return ShiftResponse.fromEntity(shiftRepository.save(shift));
    }

    // ── Kiểm tra quyền sở hữu ────────────────────────────────────────────────

    /** Chốt chặn cách ly Tenant: không bao giờ tải ca bằng findById trần. */
    private Shift getOwnedShift(UUID id) {
        return shiftRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", id));
    }

    /** Manager chỉ thao tác trong Location của mình — BR-PERM-03. */
    private void assertManagesLocation(UUID locationId) {
        if (SecurityUtils.hasRole(Role.MANAGER)
                && !locationId.equals(SecurityUtils.getCurrentLocationId())) {
            throw new BusinessException("Không có quyền thao tác trên Location khác.");
        }
    }

    /** Khóa ngoại chỉ đảm bảo Location TỒN TẠI, không đảm bảo nó thuộc Tenant này. */
    private void assertLocationInTenant(UUID locationId, UUID tenantId) {
        locationRepository.findByIdAndTenantId(locationId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", locationId));
    }

    /**
     * BR-SCH-04: template phải thuộc Tenant này. BR-SCH-22: template đã vô hiệu hóa chỉ
     * còn để tra lịch sử, không dùng xếp ca mới. {@code null} = ca tự do, bỏ qua.
     */
    private void assertTemplateUsable(UUID templateId, UUID tenantId) {
        if (templateId == null) {
            return;
        }
        ShiftTemplate template = shiftTemplateRepository.findByIdAndTenantId(templateId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("ShiftTemplate", "id", templateId));
        if (!template.isActive()) {
            throw new BusinessException("Mẫu ca này đã bị vô hiệu hóa (BR-SCH-22).");
        }
    }

    private void assertIsOwnShift(Shift shift) {
        if (!SecurityUtils.getCurrentUserId().equals(shift.getStaffId())) {
            throw new BusinessException("Chỉ người được phân công mới check-in/check-out ca này.");
        }
    }

    /** BR-SCH-05: chỉ xếp ca cho nhân viên thuộc đúng Location đó. */
    private void assertStaffBelongsToLocation(UUID staffId, UUID tenantId, UUID locationId) {
        User staff = userRepository.findByIdAndTenantId(staffId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", staffId));

        if (staff.isTerminated()) {
            throw new BusinessException("Không xếp ca cho nhân viên đã nghỉ việc (BR-USER-04).");
        }
        if (!locationId.equals(staff.getLocationId())) {
            throw new BusinessException(
                "Nhân viên không thuộc Location của ca này (BR-SCH-05).");
        }
    }
}
