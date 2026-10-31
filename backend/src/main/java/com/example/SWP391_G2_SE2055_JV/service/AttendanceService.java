package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AttendanceEventResponse;
import com.example.SWP391_G2_SE2055_JV.dto.AttendanceRowResponse;
import com.example.SWP391_G2_SE2055_JV.dto.AttendanceVerification;
import com.example.SWP391_G2_SE2055_JV.dto.CheckInOutRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ClientInfo;
import com.example.SWP391_G2_SE2055_JV.dto.ManagerAttendanceRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftAttendanceEvent;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceEventType;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceMethod;
import com.example.SWP391_G2_SE2055_JV.enums.NetworkCheckResult;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.VerificationType;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftAttendanceEventRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceNetworkService.NetworkCheck;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceStatusResolver.AttendanceView;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chấm công — BR Milestone 2 (chốt 31/10/2026), sửa BR-DASH-01 / DM-15.
 *
 * <ul>
 *   <li>Nhân viên tự vào / ra ca của chính mình trong khung giờ của {@link AttendanceRules}, theo giờ của
 *       khách sạn có ca (không theo giờ server).</li>
 *   <li>Manager nhập bù hoặc sửa giờ cho ca ở khách sạn mình, luôn kèm lý do.</li>
 *   <li>Mỗi lần ghi thêm một dòng {@link ShiftAttendanceEvent} (IP, trình duyệt, mã thiết bị, cách ghi);
 *       giờ hiệu lực vẫn nằm trên ca nên các luồng đang đọc ca (nhận việc dọn, đổi ca, nghỉ phép) không đổi.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    /** Bảng chấm công xem tối đa một tháng một lần. */
    private static final int MAX_RANGE_DAYS = 31;

    private final ShiftRepository                shiftRepository;
    private final LocationRepository             locationRepository;
    private final ShiftTemplateRepository        shiftTemplateRepository;
    private final UserRepository                 userRepository;
    private final ShiftAttendanceEventRepository eventRepository;
    private final AttendanceStatusResolver       statusResolver;
    private final AttendanceNetworkService       networkService;

    // ── Nhân viên tự chấm công ───────────────────────────────────────────────

    @Transactional
    public ShiftResponse checkIn(UUID id, CheckInOutRequest request, ClientInfo client) {
        // Khóa ca: bấm đúp hai request cùng lúc thì request sau thấy ca đã vào và bị chặn.
        Shift shift = lockShift(id);
        assertIsOwnShift(shift);
        VerificationType verification = verify(request);
        Location location = locationOf(shift);
        LocalDateTime now = nowAt(location);

        AttendanceRules.assertCanCheckIn(shift, now);
        // Kiểm mạng SAU khung giờ: bấm sớm thì câu báo nói đúng chuyện giờ trước.
        NetworkCheck network = networkService.check(location, client);
        shift.setCheckInAt(now);
        record(shift, AttendanceEventType.CHECK_IN, now, now, AttendanceMethod.SELF, client, verification, network, null);
        log.info("Staff {} vào ca {} lúc {} (mạng: {})", shift.getStaffId(), id, now, network.result());
        return toResponse(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftResponse checkOut(UUID id, CheckInOutRequest request, ClientInfo client) {
        Shift shift = lockShift(id);
        assertIsOwnShift(shift);
        VerificationType verification = verify(request);
        Location location = locationOf(shift);
        LocalDateTime now = nowAt(location);

        AttendanceRules.assertCanCheckOut(shift, now);
        NetworkCheck network = networkService.check(location, client);
        shift.setCheckOutAt(now);
        record(shift, AttendanceEventType.CHECK_OUT, now, now, AttendanceMethod.SELF, client, verification, network, null);
        log.info("Staff {} kết thúc ca {} lúc {} (mạng: {})", shift.getStaffId(), id, now, network.result());
        return toResponse(shiftRepository.save(shift));
    }

    // ── Manager nhập bù / sửa giờ ────────────────────────────────────────────

    /** Trường bỏ trống giữ nguyên giờ đang có; mỗi giờ thực sự đổi ghi một dòng MANAGER kèm lý do. */
    @Transactional
    public ShiftResponse updateAttendance(UUID id, ManagerAttendanceRequest request, ClientInfo client) {
        Shift shift = lockShift(id);
        assertManagesLocation(shift.getLocationId());
        applyManagerTimes(shift, request.getCheckInAt(), request.getCheckOutAt(), request.getNote(), client);
        return toResponse(shiftRepository.save(shift));
    }

    /**
     * Ghi giờ do quản lý quyết — Manager nhập tay, hoặc duyệt đơn điều chỉnh công. Cùng một đường kiểm tra để
     * hai lối không bao giờ nói khác nhau. Không kiểm mạng: người ghi là quản lý, đã có lý do bắt buộc.
     * Nơi gọi đã khóa ca và kiểm quyền.
     *
     * @param requestedIn  giờ vào mới; null = giữ nguyên
     * @param requestedOut giờ ra mới; null = giữ nguyên
     */
    void applyManagerTimes(Shift shift, LocalDateTime requestedIn, LocalDateTime requestedOut, String rawNote,
                           ClientInfo client) {
        if (!shift.isAssigned()) {
            throw new BusinessException("Ca chưa có người nên không có chấm công.");
        }
        String note = rawNote == null ? "" : rawNote.trim();
        if (note.isEmpty()) {
            throw new BusinessException("Nhập lý do nhập / sửa giờ chấm công.");
        }
        if (requestedIn == null && requestedOut == null) {
            throw new BusinessException("Nhập giờ vào ca hoặc giờ ra ca.");
        }

        LocalDateTime checkIn = requestedIn != null ? toSeconds(requestedIn) : shift.getCheckInAt();
        LocalDateTime checkOut = requestedOut != null ? toSeconds(requestedOut) : shift.getCheckOutAt();
        LocalDateTime now = nowAt(locationOf(shift));
        AttendanceRules.assertManagerTimes(shift, checkIn, checkOut, now);

        boolean checkInChanged = !Objects.equals(checkIn, shift.getCheckInAt());
        boolean checkOutChanged = !Objects.equals(checkOut, shift.getCheckOutAt());
        if (!checkInChanged && !checkOutChanged) {
            throw new BusinessException("Giờ nhập trùng với giờ đang có — không có gì để sửa.");
        }
        if (checkInChanged) {
            shift.setCheckInAt(checkIn);
            record(shift, AttendanceEventType.CHECK_IN, checkIn, now, AttendanceMethod.MANAGER, client,
                VerificationType.NONE, NetworkCheck.NOT_CHECKED, note);
        }
        if (checkOutChanged) {
            shift.setCheckOutAt(checkOut);
            record(shift, AttendanceEventType.CHECK_OUT, checkOut, now, AttendanceMethod.MANAGER, client,
                VerificationType.NONE, NetworkCheck.NOT_CHECKED, note);
        }
        log.info("Quản lý {} sửa chấm công ca {}: vào {}, ra {}", SecurityUtils.getCurrentUserId(), shift.getId(),
            checkIn, checkOut);
    }

    // ── Xem ──────────────────────────────────────────────────────────────────

    /**
     * Bảng chấm công một khách sạn trong [from, to] (theo ngày bắt đầu ca): mọi ca có người, sớm nhất trước.
     * Manager luôn xem khách sạn của mình; Giám đốc / Admin chọn khách sạn qua {@code locationId}.
     */
    @Transactional(readOnly = true)
    public List<AttendanceRowResponse> getAttendance(LocalDate from, LocalDate to, UUID locationId) {
        if (to.isBefore(from)) {
            throw new BusinessException("Ngày kết thúc (to) không được trước ngày bắt đầu (from).");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BusinessException("Mỗi lần xem tối đa " + MAX_RANGE_DAYS + " ngày.");
        }
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID scopeLocationId = readableLocation(locationId, tenantId);

        List<Shift> shifts = shiftRepository.findByTenantIdAndLocationIdAndShiftDateBetween(
                tenantId, scopeLocationId, from, to,
                Pageable.unpaged(Sort.by("shiftDate", "startTime")))
            .getContent().stream()
            .filter(Shift::isAssigned)
            .toList();
        if (shifts.isEmpty()) {
            return List.of();
        }

        Map<UUID, AttendanceView> attendance = statusResolver.resolve(shifts);
        Map<UUID, String> templateNames = templateNames(shifts);
        Map<UUID, String> staffNames = userNames(shifts.stream().map(Shift::getStaffId).collect(Collectors.toSet()));
        Map<UUID, List<ShiftAttendanceEvent>> events = eventRepository
            .findByShiftIdInOrderByRecordedAtAsc(shifts.stream().map(Shift::getId).toList()).stream()
            .collect(Collectors.groupingBy(ShiftAttendanceEvent::getShiftId));

        return shifts.stream()
            .sorted(Comparator.comparing(Shift::getShiftDate).thenComparing(Shift::getStartTime))
            .map(shift -> {
                List<ShiftAttendanceEvent> ofShift = events.getOrDefault(shift.getId(), List.of());
                return AttendanceRowResponse.builder()
                    .shift(ShiftResponse.fromEntity(shift, templateName(templateNames, shift),
                        attendance.get(shift.getId())))
                    .staffName(staffNames.get(shift.getStaffId()))
                    .checkInMethod(lastMethod(ofShift, AttendanceEventType.CHECK_IN))
                    .checkOutMethod(lastMethod(ofShift, AttendanceEventType.CHECK_OUT))
                    .outsideNetwork(ofShift.stream().anyMatch(event -> event.getNetworkCheck() == NetworkCheckResult.OUTSIDE))
                    .build();
            })
            .toList();
    }

    /** Lịch sử chấm công của một ca, cũ nhất trước — cùng phạm vi với {@link #getAttendance}. */
    @Transactional(readOnly = true)
    public List<AttendanceEventResponse> getEvents(UUID shiftId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Shift shift = shiftRepository.findByIdAndTenantId(shiftId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", shiftId));
        // Ngoài phạm vi thì 404 như ShiftService.getShiftById — không lộ ca có tồn tại.
        if (SecurityUtils.hasRole(Role.MANAGER) && !SecurityUtils.getCurrentLocationId().equals(shift.getLocationId())) {
            throw new ResourceNotFoundException("Shift", "id", shiftId);
        }
        List<ShiftAttendanceEvent> events = eventRepository.findByTenantIdAndShiftIdOrderByRecordedAtAsc(tenantId, shiftId);
        Map<UUID, String> names = userNames(events.stream().map(ShiftAttendanceEvent::getRecordedBy).collect(Collectors.toSet()));
        return events.stream()
            .map(event -> AttendanceEventResponse.fromEntity(event, names.get(event.getRecordedBy())))
            .toList();
    }

    // ── Kiểm tra ─────────────────────────────────────────────────────────────

    /**
     * Trường xác thực — M2 để trống. Chừa sẵn chỗ cho QR / IP: thêm giá trị vào {@link VerificationType} và
     * phần kiểm tra ở đây; giao diện cũ không gửi gì vẫn là {@link VerificationType#NONE}.
     */
    static VerificationType verify(CheckInOutRequest request) {
        AttendanceVerification verification = request == null ? null : request.getVerification();
        String type = verification == null || verification.getType() == null ? "" : verification.getType().trim();
        if (type.isEmpty() || type.equalsIgnoreCase(VerificationType.NONE.name())) {
            return VerificationType.NONE;
        }
        throw new BusinessException("Cách xác thực «" + type + "» chưa được hỗ trợ.");
    }

    private void assertIsOwnShift(Shift shift) {
        if (!SecurityUtils.getCurrentUserId().equals(shift.getStaffId())) {
            throw new BusinessException("Chỉ người được phân công mới check-in/check-out ca này.");
        }
    }

    /** Manager chỉ thao tác trong Location của mình — BR-PERM-03. */
    private static void assertManagesLocation(UUID locationId) {
        if (SecurityUtils.hasRole(Role.MANAGER) && !locationId.equals(SecurityUtils.getCurrentLocationId())) {
            throw new BusinessException("Không có quyền thao tác trên Location khác.");
        }
    }

    private UUID readableLocation(UUID requested, UUID tenantId) {
        if (SecurityUtils.hasRole(Role.MANAGER)) {
            return SecurityUtils.getCurrentLocationId();
        }
        if (requested == null) {
            throw new BusinessException("Chọn khách sạn cần xem chấm công.");
        }
        return locationRepository.findByIdAndTenantId(requested, tenantId)
            .map(Location::getId)
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", requested));
    }

    // ── Ghi / tải ────────────────────────────────────────────────────────────

    private Shift lockShift(UUID id) {
        return shiftRepository.findForUpdate(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", id));
    }

    private Location locationOf(Shift shift) {
        return locationRepository.findByIdAndTenantId(shift.getLocationId(), shift.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", shift.getLocationId()));
    }

    /** Giờ hiện tại của khách sạn có ca, làm tròn giây cho khớp cột DATETIME. */
    private static LocalDateTime nowAt(Location location) {
        return toSeconds(ShiftTimeUtils.nowAt(location.getTimezone()));
    }

    private static LocalDateTime toSeconds(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.SECONDS);
    }

    private void record(Shift shift, AttendanceEventType type, LocalDateTime occurredAt, LocalDateTime recordedAt,
                        AttendanceMethod method, ClientInfo client, VerificationType verification,
                        NetworkCheck network, String note) {
        eventRepository.save(ShiftAttendanceEvent.builder()
            .tenantId(shift.getTenantId())
            .shiftId(shift.getId())
            .staffId(shift.getStaffId())
            .eventType(type)
            .occurredAt(occurredAt)
            .recordedAt(recordedAt)
            .method(method)
            .recordedBy(SecurityUtils.getCurrentUserId())
            .ipAddress(client == null ? null : client.ipAddress())
            .userAgent(client == null ? null : client.userAgent())
            .deviceId(client == null ? null : client.deviceId())
            .verificationType(verification)
            .networkCheck(network.result())
            .matchedCidr(network.matchedCidr())
            .note(note)
            .build());
    }

    private static AttendanceMethod lastMethod(List<ShiftAttendanceEvent> events, AttendanceEventType type) {
        AttendanceMethod last = null;
        for (ShiftAttendanceEvent event : events) {
            if (event.getEventType() == type) {
                last = event.getMethod();
            }
        }
        return last;
    }

    // ── Dựng response ────────────────────────────────────────────────────────

    private ShiftResponse toResponse(Shift shift) {
        String templateName = shift.getSourceTemplateId() == null ? null
            : shiftTemplateRepository.findById(shift.getSourceTemplateId()).map(ShiftTemplate::getName).orElse(null);
        return ShiftResponse.fromEntity(shift, templateName, statusResolver.resolve(shift));
    }

    private Map<UUID, String> templateNames(Collection<Shift> shifts) {
        Set<UUID> ids = shifts.stream()
            .map(Shift::getSourceTemplateId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return shiftTemplateRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(ShiftTemplate::getId, ShiftTemplate::getName));
    }

    private static String templateName(Map<UUID, String> names, Shift shift) {
        return shift.getSourceTemplateId() == null ? null : names.get(shift.getSourceTemplateId());
    }

    private Map<UUID, String> userNames(Set<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(user -> names.put(user.getId(), user.getFullName()));
        }
        return names;
    }
}
