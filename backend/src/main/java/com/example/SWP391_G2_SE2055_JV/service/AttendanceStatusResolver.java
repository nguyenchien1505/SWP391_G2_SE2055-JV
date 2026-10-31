package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.repository.LeaveRequestRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Tình trạng chấm công cho cả một trang ca — {@link AttendanceRules#status} cộng hai thứ phải tra DB: giờ
 * hiện tại theo múi giờ từng khách sạn, và đơn nghỉ đã duyệt (ngày nghỉ thì không tính vắng). Mỗi loại một
 * truy vấn cho cả trang, không N+1.
 */
@Component
@RequiredArgsConstructor
public class AttendanceStatusResolver {

    private final LocationRepository     locationRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    /** Chấm công của một ca, gửi kèm ca để giao diện không tự tính lại khung giờ. */
    public record AttendanceView(AttendanceStatus status, Long lateMinutes, Long earlyLeaveMinutes,
                                 LocalDateTime checkInOpensAt, LocalDateTime checkOutClosesAt) {}

    /** @return theo id ca; ca chưa có người không có mặt trong kết quả */
    public Map<UUID, AttendanceView> resolve(Collection<Shift> shifts) {
        List<Shift> assigned = shifts.stream().filter(Shift::isAssigned).toList();
        if (assigned.isEmpty()) {
            return Map.of();
        }
        Map<UUID, LocalDateTime> nowByLocation = nowByLocation(assigned);
        List<LeaveRequest> leaves = approvedLeaves(assigned);

        Map<UUID, AttendanceView> views = new HashMap<>();
        for (Shift shift : assigned) {
            LocalDateTime now = nowByLocation.get(shift.getLocationId());
            boolean onLeave = leaves.stream().anyMatch(leave -> covers(leave, shift));
            views.put(shift.getId(), new AttendanceView(
                AttendanceRules.status(shift, onLeave, now),
                AttendanceRules.lateMinutes(shift),
                AttendanceRules.earlyLeaveMinutes(shift),
                AttendanceRules.checkInOpensAt(shift),
                AttendanceRules.checkOutClosesAt(shift)));
        }
        return views;
    }

    /** Khách sạn không tìm thấy thì lùi về giờ Hà Nội như {@link ShiftTimeUtils#nowAt}. */
    private Map<UUID, LocalDateTime> nowByLocation(List<Shift> shifts) {
        Set<UUID> locationIds = shifts.stream().map(Shift::getLocationId).collect(Collectors.toSet());
        Map<UUID, String> timezones = new HashMap<>();
        locationRepository.findAllById(locationIds)
            .forEach(location -> timezones.put(location.getId(), location.getTimezone()));
        return locationIds.stream()
            .collect(Collectors.toMap(id -> id, id -> ShiftTimeUtils.nowAt(timezones.get(id))));
    }

    /** Chỉ cần cho ca chưa vào — ca đã vào thì đi làm thật, không phải nghỉ. */
    private List<LeaveRequest> approvedLeaves(List<Shift> shifts) {
        List<Shift> notCheckedIn = shifts.stream().filter(shift -> !shift.isCheckedIn()).toList();
        if (notCheckedIn.isEmpty()) {
            return List.of();
        }
        Set<UUID> staffIds = notCheckedIn.stream().map(Shift::getStaffId).collect(Collectors.toSet());
        LocalDate from = notCheckedIn.stream().map(Shift::getShiftDate).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = notCheckedIn.stream().map(Shift::getShiftDate).max(Comparator.naturalOrder()).orElseThrow();
        return leaveRequestRepository.findOverlappingForRequesters(staffIds, LeaveRequestStatus.APPROVED, from, to);
    }

    private static boolean covers(LeaveRequest leave, Shift shift) {
        return Objects.equals(leave.getRequesterId(), shift.getStaffId())
            && !shift.getShiftDate().isBefore(leave.getFromDate())
            && !shift.getShiftDate().isAfter(leave.getToDate());
    }

    /** Một ca — cho lệnh vào / ra ca trả về ngay trạng thái mới. */
    public AttendanceView resolve(Shift shift) {
        return resolve(List.of(shift)).get(shift.getId());
    }
}
