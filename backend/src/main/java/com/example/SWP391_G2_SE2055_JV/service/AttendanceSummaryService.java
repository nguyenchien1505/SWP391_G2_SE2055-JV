package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AttendanceSummaryResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftAttendanceEvent;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceMethod;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceStatus;
import com.example.SWP391_G2_SE2055_JV.enums.NetworkCheckResult;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftAttendanceEventRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceStatusResolver.AttendanceView;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tổng kết chuyên cần theo tháng (chấm công M2, chốt 01/11/2026). Không thêm bảng nào: tính từ ca, tình trạng
 * chấm công suy ra ({@link AttendanceStatusResolver}) và các lần ghi chấm công. Chỉ để xem — không phải bảng công
 * tính lương, không chốt kỳ.
 */
@Service
@RequiredArgsConstructor
public class AttendanceSummaryService {

    private final ShiftRepository                shiftRepository;
    private final LocationRepository             locationRepository;
    private final UserRepository                 userRepository;
    private final ShiftAttendanceEventRepository eventRepository;
    private final AttendanceStatusResolver       statusResolver;

    /**
     * Mọi nhân viên có ca ở khách sạn trong tháng, tên A→Z.
     *
     * @param locationId Giám đốc / Admin chọn khách sạn; Manager bỏ qua — luôn là khách sạn của mình
     */
    @Transactional(readOnly = true)
    public List<AttendanceSummaryResponse> getLocationSummary(String month, UUID locationId) {
        YearMonth period = parseMonth(month);
        Location location = requireLocation(locationId);
        List<Shift> shifts = assigned(shiftRepository.findByTenantIdAndLocationIdAndShiftDateBetween(
            location.getTenantId(), location.getId(), period.atDay(1), period.atEndOfMonth(), Pageable.unpaged())
            .getContent());
        Map<UUID, List<Shift>> byStaff = shifts.stream().collect(Collectors.groupingBy(Shift::getStaffId));
        Map<UUID, String> names = new HashMap<>();
        if (!byStaff.isEmpty()) {
            userRepository.findAllById(byStaff.keySet()).forEach(user -> names.put(user.getId(), user.getFullName()));
        }
        Map<UUID, AttendanceView> views = statusResolver.resolve(shifts);
        Map<UUID, List<ShiftAttendanceEvent>> events = eventsOf(shifts);
        return byStaff.entrySet().stream()
            .map(entry -> summarize(entry.getKey(), names.get(entry.getKey()), entry.getValue(), views, events))
            .sorted(Comparator.comparing(summary -> summary.getStaffName() == null ? "" : summary.getStaffName()))
            .toList();
    }

    /** Chuyên cần tháng của chính nhân viên đang đăng nhập — ca ở mọi khách sạn mình từng làm trong tháng. */
    @Transactional(readOnly = true)
    public AttendanceSummaryResponse getMySummary(String month) {
        YearMonth period = parseMonth(month);
        UUID staffId = SecurityUtils.getCurrentUserId();
        List<Shift> shifts = assigned(shiftRepository.findByStaffIdAndShiftDateBetween(
            staffId, period.atDay(1), period.atEndOfMonth(), Pageable.unpaged()).getContent());
        String name = userRepository.findById(staffId).map(User::getFullName).orElse(null);
        return summarize(staffId, name, shifts, statusResolver.resolve(shifts), eventsOf(shifts));
    }

    static AttendanceSummaryResponse summarize(UUID staffId, String staffName, List<Shift> shifts,
                                               Map<UUID, AttendanceView> views,
                                               Map<UUID, List<ShiftAttendanceEvent>> events) {
        int due = 0, completed = 0, absent = 0, missed = 0, leave = 0, late = 0, early = 0, outside = 0,
            managerEntered = 0, onTime = 0, mustWork = 0;
        long lateMinutes = 0, earlyMinutes = 0, worked = 0;
        for (Shift shift : shifts) {
            AttendanceView view = views.get(shift.getId());
            AttendanceStatus status = view == null ? null : view.status();
            List<ShiftAttendanceEvent> ofShift = events.getOrDefault(shift.getId(), List.of());
            if (ofShift.stream().anyMatch(event -> event.getNetworkCheck() == NetworkCheckResult.OUTSIDE)) {
                outside++;
            }
            if (ofShift.stream().anyMatch(event -> event.getMethod() == AttendanceMethod.MANAGER)) {
                managerEntered++;
            }
            if (status == null || status == AttendanceStatus.UPCOMING) {
                continue;
            }
            due++;
            switch (status) {
                case COMPLETED -> completed++;
                case ABSENT -> absent++;
                case MISSED_CHECK_OUT -> missed++;
                case ON_LEAVE -> leave++;
                default -> { /* OPEN / WORKING: ca đang diễn ra */ }
            }
            if (status != AttendanceStatus.ON_LEAVE) {
                mustWork++;
            }
            if (view.lateMinutes() != null) {
                if (view.lateMinutes() > 0) {
                    late++;
                    lateMinutes += view.lateMinutes();
                } else {
                    onTime++;
                }
            }
            if (view.earlyLeaveMinutes() != null && view.earlyLeaveMinutes() > 0) {
                early++;
                earlyMinutes += view.earlyLeaveMinutes();
            }
            if (shift.getCheckInAt() != null && shift.getCheckOutAt() != null) {
                worked += Duration.between(shift.getCheckInAt(), shift.getCheckOutAt()).toMinutes();
            }
        }
        return AttendanceSummaryResponse.builder()
            .staffId(staffId)
            .staffName(staffName)
            .totalShifts(shifts.size())
            .dueShifts(due)
            .completed(completed)
            .absent(absent)
            .missedCheckOut(missed)
            .onLeave(leave)
            .late(late)
            .lateMinutes(lateMinutes)
            .earlyLeave(early)
            .earlyLeaveMinutes(earlyMinutes)
            .outsideNetwork(outside)
            .managerEntered(managerEntered)
            .workedMinutes(worked)
            .onTimePercent(mustWork == 0 ? null : Math.round(onTime * 100f / mustWork))
            .build();
    }

    private Map<UUID, List<ShiftAttendanceEvent>> eventsOf(List<Shift> shifts) {
        if (shifts.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = shifts.stream().map(Shift::getId).collect(Collectors.toSet());
        return eventRepository.findByShiftIdInOrderByRecordedAtAsc(ids).stream()
            .collect(Collectors.groupingBy(ShiftAttendanceEvent::getShiftId));
    }

    private static List<Shift> assigned(List<Shift> shifts) {
        return shifts.stream().filter(Shift::isAssigned).toList();
    }

    /** "2026-11". Bỏ trống là tháng này theo giờ Hà Nội. */
    static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(ShiftTimeUtils.HANOI_ZONE);
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException("Tháng phải có dạng YYYY-MM, ví dụ 2026-11.");
        }
    }

    private Location requireLocation(UUID requested) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = SecurityUtils.hasRole(Role.MANAGER) ? SecurityUtils.getCurrentLocationId() : requested;
        if (locationId == null) {
            throw new BusinessException("Chọn khách sạn.");
        }
        return locationRepository.findByIdAndTenantId(locationId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", locationId));
    }
}
