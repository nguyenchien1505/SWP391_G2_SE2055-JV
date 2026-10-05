package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftBatchRequest;
import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.exception.ApiError;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.exception.ShiftBatchRejectedException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    /**
     * @param from ngày bắt đầu ca nhỏ nhất (tính cả ngày này), đi cùng {@code to}. Bỏ trống cả
     *             hai thì không lọc theo ngày.
     * @param to   ngày bắt đầu ca lớn nhất (tính cả ngày này).
     */
    @Transactional(readOnly = true)
    public Page<ShiftResponse> getShifts(LocalDate from, LocalDate to, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        if ((from == null) != (to == null)) {
            throw new BusinessException("Lọc theo ngày cần đủ cả from và to.");
        }
        if (from != null && to.isBefore(from)) {
            throw new BusinessException("Ngày kết thúc (to) không được trước ngày bắt đầu (from).");
        }
        boolean byDate = from != null;

        // Staff chỉ thấy ca của chính mình; Manager giới hạn trong Location của mình.
        if (SecurityUtils.hasRole(Role.STAFF)) {
            UUID staffId = SecurityUtils.getCurrentUserId();
            return (byDate
                    ? shiftRepository.findByStaffIdAndShiftDateBetween(staffId, from, to, pageable)
                    : shiftRepository.findByStaffId(staffId, pageable))
                .map(ShiftResponse::fromEntity);
        }
        if (SecurityUtils.hasRole(Role.MANAGER)) {
            UUID locationId = SecurityUtils.getCurrentLocationId();
            return (byDate
                    ? shiftRepository.findByTenantIdAndLocationIdAndShiftDateBetween(
                        tenantId, locationId, from, to, pageable)
                    : shiftRepository.findByTenantIdAndLocationId(tenantId, locationId, pageable))
                .map(ShiftResponse::fromEntity);
        }
        return (byDate
                ? shiftRepository.findByTenantIdAndShiftDateBetween(tenantId, from, to, pageable)
                : shiftRepository.findByTenantId(tenantId, pageable))
            .map(ShiftResponse::fromEntity);
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

        ShiftHours hours = resolveHours(
            request.getSourceTemplateId(), request.getStartTime(), request.getEndTime(), tenantId);
        Shift shift = newShift(tenantId, request.getLocationId(), request.getShiftDate(), hours,
            request.getStaffId());

        if (request.getStaffId() != null) {
            assertStaffBelongsToLocation(request.getStaffId(), tenantId, request.getLocationId());
            policyValidator.validate(tenantId, request.getStaffId(), shift, null);
        }

        Shift saved = shiftRepository.save(shift);
        log.info("Tạo ca {} ngày {} tại Location {} cho staff {}",
            saved.getId(), saved.getShiftDate(), saved.getLocationId(), saved.getStaffId());
        return ShiftResponse.fromEntity(saved);
    }

    /**
     * Giao CÙNG MỘT ca cho nhiều người — mỗi người một bản ghi ca riêng (DM-03), mỗi người qua kiểm
     * tra Schedule Policy riêng (BR-SCH-02). Nhiều người khác nhau làm chung một khung giờ là bình
     * thường; kiểm tra trùng giờ chỉ so các ca của CHÍNH người đó (BR-SCH-05).
     *
     * <p>Tất cả hoặc không: kiểm tra hết mọi người trước, có người vi phạm thì không lưu ca nào và
     * trả lý do của từng người ({@link ShiftBatchRejectedException}). Không lưu một nửa, nên Manager
     * không phải đoán ca nào đã vào lịch.
     *
     * @return các ca vừa tạo: ca của từng người theo thứ tự gửi lên, rồi tới các chỗ trống
     */
    @Transactional
    public List<ShiftResponse> createShifts(CreateShiftBatchRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = request.getLocationId();
        assertManagesLocation(locationId);
        assertLocationInTenant(locationId, tenantId);

        // Gửi trùng một người hai lần thì chỉ tạo một ca — ca thứ hai đằng nào cũng bị chặn vì trùng giờ.
        Set<UUID> staffIds = new LinkedHashSet<>(
            request.getStaffIds() == null ? List.of() : request.getStaffIds());
        if (staffIds.isEmpty() && request.getOpenSlots() == 0) {
            throw new BusinessException("Chọn ít nhất một nhân viên hoặc mở ít nhất một chỗ trống.");
        }

        ShiftHours hours = resolveHours(
            request.getSourceTemplateId(), request.getStartTime(), request.getEndTime(), tenantId);

        List<Shift> toSave = new ArrayList<>();
        List<ApiError.StaffViolation> violations = new ArrayList<>();
        for (UUID staffId : staffIds) {
            User staff = userRepository.findByIdAndTenantId(staffId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", staffId));
            Shift shift = newShift(tenantId, locationId, request.getShiftDate(), hours, staffId);
            try {
                assertSchedulableAt(staff, locationId);
                policyValidator.validate(tenantId, staffId, shift, null);
                toSave.add(shift);
            } catch (BusinessException ex) {
                violations.add(ApiError.StaffViolation.builder()
                    .staffId(staffId)
                    .fullName(staff.getFullName())
                    .message(ex.getMessage())
                    .build());
            }
        }
        if (!violations.isEmpty()) {
            throw new ShiftBatchRejectedException(violations, staffIds.size());
        }

        for (int i = 0; i < request.getOpenSlots(); i++) {
            toSave.add(newShift(tenantId, locationId, request.getShiftDate(), hours, null));
        }

        List<Shift> saved = shiftRepository.saveAll(toSave);
        log.info("Tạo {} ca ngày {} {}–{} tại Location {} ({} người, {} chỗ trống)",
            saved.size(), request.getShiftDate(), hours.start(), hours.end(), locationId,
            staffIds.size(), request.getOpenSlots());
        return saved.stream().map(ShiftResponse::fromEntity).toList();
    }

    @Transactional
    public ShiftResponse updateShift(UUID id, UpdateShiftRequest request) {
        Shift shift = getOwnedShift(id);
        assertManagesLocation(shift.getLocationId());
        // DM-15: mốc chấm công nằm trên chính bản ghi ca — đổi ngày giờ sau khi đã vào ca thì
        // giờ ca không còn khớp với giờ chấm công nữa.
        if (shift.isCheckedIn()) {
            throw new BusinessException("Không sửa được ca đã check-in — giờ ca sẽ lệch với dữ liệu chấm công.");
        }

        if (request.getShiftDate() != null) {
            shift.setShiftDate(request.getShiftDate());
        }

        // Đổi sang mẫu khác thì giờ lấy theo mẫu, không nhận giờ tự nhập cùng lúc (BR-SCH-04).
        if (request.getSourceTemplateId() != null) {
            if (request.getStartTime() != null || request.getEndTime() != null) {
                throw new BusinessException(
                    "Đã chọn mẫu ca thì không gửi kèm giờ: giờ lấy theo mẫu.");
            }
            ShiftTemplate template =
                requireUsableTemplate(request.getSourceTemplateId(), shift.getTenantId());
            shift.setSourceTemplateId(template.getId());
            shift.setStartTime(template.getStartTime());
            shift.setEndTime(template.getEndTime());
        } else {
            if (request.getStartTime() != null) {
                shift.setStartTime(request.getStartTime());
            }
            if (request.getEndTime() != null) {
                shift.setEndTime(request.getEndTime());
            }
            // Sửa giờ tay nghĩa là ca không còn khớp mẫu cũ — bỏ liên kết cho khỏi sai lịch sử.
            if (request.getStartTime() != null || request.getEndTime() != null) {
                shift.setSourceTemplateId(null);
            }
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
        // DM-15: gỡ người thì giờ check-in còn đó nhưng không còn biết là của ai.
        if (shift.isCheckedIn()) {
            throw new BusinessException("Không gỡ người khỏi ca đã check-in — sẽ mất dữ liệu chấm công của người đó.");
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
     * BR-SCH-04: mẫu phải thuộc Tenant này. BR-SCH-22: mẫu đã vô hiệu hóa chỉ còn để tra
     * lịch sử, không xếp ca mới được nữa.
     */
    private ShiftTemplate requireUsableTemplate(UUID templateId, UUID tenantId) {
        ShiftTemplate template = shiftTemplateRepository.findByIdAndTenantId(templateId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("ShiftTemplate", "id", templateId));
        if (!template.isActive()) {
            throw new BusinessException("Mẫu ca này đã bị vô hiệu hóa.");
        }
        return template;
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
        assertSchedulableAt(staff, locationId);
    }

    private static void assertSchedulableAt(User staff, UUID locationId) {
        if (staff.isTerminated()) {
            throw new BusinessException("Không xếp ca cho nhân viên đã nghỉ việc.");
        }
        if (!locationId.equals(staff.getLocationId())) {
            throw new BusinessException(
                "Nhân viên không thuộc Location của ca này.");
        }
    }

    // ── Dựng ca ──────────────────────────────────────────────────────────────

    /** Giờ của một ca và mẫu sinh ra nó ({@code templateId = null} là ca tự do). */
    private record ShiftHours(LocalTime start, LocalTime end, UUID templateId) {}

    /** BR-SCH-04: hai cách tạo ca, chọn đúng một — theo mẫu (giờ lấy từ mẫu) hoặc tự nhập giờ. */
    private ShiftHours resolveHours(UUID templateId, LocalTime start, LocalTime end, UUID tenantId) {
        if (templateId != null) {
            if (start != null || end != null) {
                throw new BusinessException(
                    "Đã chọn mẫu ca thì không gửi kèm giờ: giờ lấy theo mẫu.");
            }
            ShiftTemplate template = requireUsableTemplate(templateId, tenantId);
            return new ShiftHours(template.getStartTime(), template.getEndTime(), template.getId());
        }
        if (start == null || end == null) {
            throw new BusinessException(
                "Ca tự do phải có cả giờ bắt đầu và giờ kết thúc, hoặc chọn một mẫu ca.");
        }
        return new ShiftHours(start, end, null);
    }

    /** Cờ qua đêm và số giờ suy ra từ giờ bắt đầu/kết thúc — BR-SCH-03. */
    private static Shift newShift(UUID tenantId, UUID locationId, LocalDate date, ShiftHours hours,
                                  UUID staffId) {
        return Shift.builder()
            .tenantId(tenantId)
            .locationId(locationId)
            .staffId(staffId)
            .shiftDate(date)
            .startTime(hours.start())
            .endTime(hours.end())
            .overnight(ShiftTimeUtils.isOvernight(hours.start(), hours.end()))
            .durationHours(ShiftTimeUtils.durationHours(hours.start(), hours.end()))
            .sourceTemplateId(hours.templateId())
            .build();
    }
}
