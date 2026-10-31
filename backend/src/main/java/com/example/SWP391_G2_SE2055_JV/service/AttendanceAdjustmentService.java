package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AttendanceAdjustmentResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ClientInfo;
import com.example.SWP391_G2_SE2055_JV.dto.CreateAttendanceAdjustmentRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.entity.AttendanceAdjustmentRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.enums.AdjustmentRequestStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import com.example.SWP391_G2_SE2055_JV.repository.AttendanceAdjustmentRequestRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceStatusResolver.AttendanceView;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Đơn điều chỉnh công — chấm công M2 (chốt 01/11/2026). Nhân viên quên bấm, mất mạng hay bấm sai thì tự gửi đơn
 * kèm giờ đúng và lý do; quản lý khách sạn duyệt thì giờ được ghi qua {@link AttendanceService#applyManagerTimes}
 * — cùng đường kiểm tra với Manager nhập tay, lý do của đơn đi vào lịch sử chấm công.
 *
 * <ul>
 *   <li>Chỉ cho ca của chính mình, đã tới giờ vào ca, trong {@value #MAX_AGE_DAYS} ngày gần đây.</li>
 *   <li>Mỗi ca tối đa một đơn đang chờ.</li>
 *   <li>Giờ đề nghị kiểm ngay khi gửi theo cùng luật với Manager nhập — sai thì người gửi biết ngay.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceAdjustmentService {

    /** Đủ để bắt kịp sai sót của tháng trước, không mở lại chuyện từ lâu. */
    static final int MAX_AGE_DAYS = 31;

    private final AttendanceAdjustmentRequestRepository requestRepository;
    private final ShiftRepository                       shiftRepository;
    private final LocationRepository                    locationRepository;
    private final ShiftTemplateRepository               shiftTemplateRepository;
    private final UserRepository                        userRepository;
    private final AttendanceStatusResolver              statusResolver;
    private final AttendanceService                     attendanceService;

    // ── Nhân viên ────────────────────────────────────────────────────────────

    @Transactional
    public AttendanceAdjustmentResponse create(CreateAttendanceAdjustmentRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID requesterId = SecurityUtils.getCurrentUserId();
        // Khóa ca: hai đơn cho cùng một ca gửi cùng lúc thì đơn sau thấy đơn trước đang chờ.
        Shift shift = shiftRepository.findForUpdate(request.getShiftId(), tenantId)
            .filter(s -> requesterId.equals(s.getStaffId()))
            .orElseThrow(() -> new BusinessException("Chỉ gửi được đơn điều chỉnh cho ca của chính mình."));
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isEmpty()) {
            throw new BusinessException("Nhập lý do điều chỉnh.");
        }
        if (request.getCheckInAt() == null && request.getCheckOutAt() == null) {
            throw new BusinessException("Nhập giờ vào ca hoặc giờ ra ca cần điều chỉnh.");
        }

        LocalDateTime now = nowAt(shift);
        if (now.isBefore(AttendanceRules.checkInOpensAt(shift))) {
            throw new BusinessException("Ca chưa tới giờ — tới giờ thì bấm «Vào ca» như bình thường.");
        }
        if (shift.getShiftDate().isBefore(now.toLocalDate().minusDays(MAX_AGE_DAYS))) {
            throw new BusinessException("Chỉ điều chỉnh được ca trong " + MAX_AGE_DAYS + " ngày gần đây.");
        }
        if (requestRepository.existsByShiftIdAndStatus(shift.getId(), AdjustmentRequestStatus.PENDING)) {
            throw new BusinessException("Ca này đã có một đơn điều chỉnh đang chờ duyệt — hủy đơn đó trước nếu muốn sửa.");
        }

        LocalDateTime checkIn = toSeconds(request.getCheckInAt());
        LocalDateTime checkOut = toSeconds(request.getCheckOutAt());
        // Cùng luật với Manager nhập: sai thì người gửi biết ngay, không phải chờ quản lý từ chối.
        LocalDateTime mergedIn = checkIn != null ? checkIn : shift.getCheckInAt();
        LocalDateTime mergedOut = checkOut != null ? checkOut : shift.getCheckOutAt();
        AttendanceRules.assertManagerTimes(shift, mergedIn, mergedOut, now);
        if (Objects.equals(mergedIn, shift.getCheckInAt()) && Objects.equals(mergedOut, shift.getCheckOutAt())) {
            throw new BusinessException("Giờ đề nghị trùng với giờ đang có — không có gì để điều chỉnh.");
        }

        AttendanceAdjustmentRequest saved = requestRepository.save(AttendanceAdjustmentRequest.builder()
            .tenantId(tenantId)
            .locationId(shift.getLocationId())
            .shiftId(shift.getId())
            .requesterId(requesterId)
            .requestedCheckInAt(checkIn)
            .requestedCheckOutAt(checkOut)
            .reason(reason)
            .status(AdjustmentRequestStatus.PENDING)
            .build());
        log.info("Staff {} gửi đơn điều chỉnh công {} cho ca {}", requesterId, saved.getId(), shift.getId());
        return toResponse(saved);
    }

    @Transactional
    public AttendanceAdjustmentResponse cancel(UUID id) {
        AttendanceAdjustmentRequest request = lockRequest(id);
        if (!SecurityUtils.getCurrentUserId().equals(request.getRequesterId())) {
            throw new UnauthorizedException("Chỉ người gửi mới hủy được đơn này.");
        }
        assertPending(request);
        request.setStatus(AdjustmentRequestStatus.CANCELLED);
        request.setDecidedAt(nowAt(request.getLocationId(), request.getTenantId()));
        return toResponse(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public Page<AttendanceAdjustmentResponse> getMine(Pageable pageable) {
        Page<AttendanceAdjustmentRequest> page = requestRepository.findByTenantIdAndRequesterId(
            SecurityUtils.getCurrentTenantId(), SecurityUtils.getCurrentUserId(), pageable);
        return toResponses(page);
    }

    // ── Quản lý khách sạn ────────────────────────────────────────────────────

    /** Đơn của khách sạn mình theo trạng thái — mặc định đang chờ. */
    @Transactional(readOnly = true)
    public Page<AttendanceAdjustmentResponse> getForReview(Collection<AdjustmentRequestStatus> statuses,
                                                           Pageable pageable) {
        Collection<AdjustmentRequestStatus> wanted = statuses == null || statuses.isEmpty()
            ? List.of(AdjustmentRequestStatus.PENDING) : statuses;
        Page<AttendanceAdjustmentRequest> page = requestRepository.findByTenantIdAndLocationIdAndStatusIn(
            SecurityUtils.getCurrentTenantId(), SecurityUtils.getCurrentLocationId(), wanted, pageable);
        return toResponses(page);
    }

    /** Ghi giờ trong đơn vào ca như quản lý nhập; lý do của đơn đi vào lịch sử chấm công. */
    @Transactional
    public AttendanceAdjustmentResponse approve(UUID id, String note, ClientInfo client) {
        AttendanceAdjustmentRequest request = lockRequest(id);
        assertReviewer(request);
        assertPending(request);
        Shift shift = shiftRepository.findForUpdate(request.getShiftId(), request.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", request.getShiftId()));
        if (!request.getRequesterId().equals(shift.getStaffId())) {
            throw new BusinessException("Ca này không còn là ca của người gửi đơn — hãy từ chối đơn.");
        }

        attendanceService.applyManagerTimes(shift, request.getRequestedCheckInAt(), request.getRequestedCheckOutAt(),
            "Duyệt đơn điều chỉnh công: " + request.getReason(), client);
        shiftRepository.save(shift);

        request.setStatus(AdjustmentRequestStatus.APPROVED);
        request.setReviewerId(SecurityUtils.getCurrentUserId());
        request.setDecidedAt(nowAt(shift));
        request.setReviewNote(blankToNull(note));
        log.info("Quản lý {} duyệt đơn điều chỉnh công {}", request.getReviewerId(), id);
        return toResponse(requestRepository.save(request));
    }

    @Transactional
    public AttendanceAdjustmentResponse reject(UUID id, String note) {
        AttendanceAdjustmentRequest request = lockRequest(id);
        assertReviewer(request);
        assertPending(request);
        String reason = blankToNull(note);
        if (reason == null) {
            throw new BusinessException("Nhập lý do từ chối để nhân viên biết.");
        }
        request.setStatus(AdjustmentRequestStatus.REJECTED);
        request.setReviewerId(SecurityUtils.getCurrentUserId());
        request.setDecidedAt(nowAt(request.getLocationId(), request.getTenantId()));
        request.setReviewNote(reason);
        log.info("Quản lý {} từ chối đơn điều chỉnh công {}", request.getReviewerId(), id);
        return toResponse(requestRepository.save(request));
    }

    // ── Kiểm tra ─────────────────────────────────────────────────────────────

    private AttendanceAdjustmentRequest lockRequest(UUID id) {
        return requestRepository.findForUpdate(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("AttendanceAdjustmentRequest", "id", id));
    }

    private static void assertPending(AttendanceAdjustmentRequest request) {
        if (!request.isPending()) {
            throw new BusinessException("Đơn này đã được xử lý.");
        }
    }

    /** Chỉ quản lý của khách sạn có ca mới duyệt / từ chối — BR-PERM-03. */
    private static void assertReviewer(AttendanceAdjustmentRequest request) {
        if (!request.getLocationId().equals(SecurityUtils.getCurrentLocationIdOrNull())) {
            throw new UnauthorizedException("Chỉ quản lý của khách sạn này mới duyệt được đơn điều chỉnh công.");
        }
    }

    // ── Tiện ích ─────────────────────────────────────────────────────────────

    private LocalDateTime nowAt(Shift shift) {
        return nowAt(shift.getLocationId(), shift.getTenantId());
    }

    private LocalDateTime nowAt(UUID locationId, UUID tenantId) {
        String timezone = locationRepository.findByIdAndTenantId(locationId, tenantId)
            .map(Location::getTimezone)
            .orElse(null);
        return ShiftTimeUtils.nowAt(timezone).truncatedTo(ChronoUnit.SECONDS);
    }

    private static LocalDateTime toSeconds(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.SECONDS);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    // ── Dựng response ────────────────────────────────────────────────────────

    private AttendanceAdjustmentResponse toResponse(AttendanceAdjustmentRequest request) {
        return toResponses(List.of(request)).get(0);
    }

    private Page<AttendanceAdjustmentResponse> toResponses(Page<AttendanceAdjustmentRequest> page) {
        List<AttendanceAdjustmentResponse> content = toResponses(page.getContent());
        Map<UUID, AttendanceAdjustmentResponse> byId = content.stream()
            .collect(Collectors.toMap(AttendanceAdjustmentResponse::getId, r -> r));
        return page.map(request -> byId.get(request.getId()));
    }

    /** Ca, tên mẫu, tình trạng chấm công và tên người — mỗi loại một truy vấn cho cả trang. */
    private List<AttendanceAdjustmentResponse> toResponses(List<AttendanceAdjustmentRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<UUID, Shift> shifts = new HashMap<>();
        shiftRepository.findAllById(requests.stream().map(AttendanceAdjustmentRequest::getShiftId).collect(Collectors.toSet()))
            .forEach(shift -> shifts.put(shift.getId(), shift));
        Map<UUID, AttendanceView> views = statusResolver.resolve(shifts.values());
        Set<UUID> templateIds = shifts.values().stream().map(Shift::getSourceTemplateId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, String> templateNames = new HashMap<>();
        if (!templateIds.isEmpty()) {
            shiftTemplateRepository.findAllById(templateIds).forEach(t -> templateNames.put(t.getId(), t.getName()));
        }
        Set<UUID> userIds = requests.stream()
            .flatMap(r -> Stream.of(r.getRequesterId(), r.getReviewerId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        userRepository.findAllById(userIds).forEach(user -> names.put(user.getId(), user.getFullName()));

        return requests.stream()
            .map(request -> {
                Shift shift = shifts.get(request.getShiftId());
                ShiftResponse shiftResponse = shift == null ? null : ShiftResponse.fromEntity(shift,
                    shift.getSourceTemplateId() == null ? null : templateNames.get(shift.getSourceTemplateId()),
                    views.get(shift.getId()));
                return AttendanceAdjustmentResponse.fromEntity(request, shiftResponse,
                    names.get(request.getRequesterId()),
                    request.getReviewerId() == null ? null : names.get(request.getReviewerId()));
            })
            .toList();
    }
}
