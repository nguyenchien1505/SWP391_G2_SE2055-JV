package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AttendanceRowResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.dto.TodayAttendanceResponse;
import com.example.SWP391_G2_SE2055_JV.dto.TodayAttendanceResponse.ActionWithoutShift;
import com.example.SWP391_G2_SE2055_JV.dto.TodayAttendanceResponse.ReceptionGap;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.RoomStatusHistory;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomStatusHistoryRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Khối «Hôm nay» của Manager (chấm công M2, chốt 01/11/2026) — biến dữ liệu chấm công thành việc cần làm ngay:
 * ai quá giờ chưa vào, ca nào đang không có lễ tân trong ca, và lễ tân nào đã nhận / trả phòng lúc chưa vào ca.
 *
 * <p>Thao tác của lễ tân ngoài ca được suy ra lúc đọc — đối chiếu {@code room_status_history} (nguồn Lễ tân,
 * người thao tác, giờ) với giờ vào / ra ca của chính người đó — nên luồng đổi trạng thái phòng không phải sửa gì.
 * Mốc giờ của lịch sử phòng là giờ server; khớp với giờ chấm công khi server chạy theo giờ Việt Nam như hiện nay.
 */
@Service
@RequiredArgsConstructor
public class AttendanceTodayService {

    private final AttendanceService           attendanceService;
    private final LocationRepository          locationRepository;
    private final UserRepository              userRepository;
    private final RoomRepository              roomRepository;
    private final RoomStatusHistoryRepository roomHistoryRepository;

    /**
     * @param locationId Giám đốc / Admin chọn khách sạn; Manager bỏ qua — luôn là khách sạn của mình
     */
    @Transactional(readOnly = true)
    public TodayAttendanceResponse getToday(UUID locationId) {
        Location location = requireLocation(locationId);
        LocalDateTime now = ShiftTimeUtils.nowAt(location.getTimezone());
        LocalDate today = now.toLocalDate();

        // Ca đêm bắt đầu hôm qua có thể vẫn đang làm (BR-SCH-03): lấy cả hôm qua rồi lọc theo ngày làm hiện tại.
        List<AttendanceRowResponse> rows = attendanceService.getAttendance(today.minusDays(1), today, location.getId())
            .stream()
            .filter(row -> ShiftTimeUtils.isInCurrentWorkDay(row.getShift().getShiftDate(), row.getShift().isOvernight(),
                row.getShift().getEndTime(), now))
            .toList();

        Map<UUID, User> staff = usersById(rows.stream().map(row -> row.getShift().getStaffId()).collect(Collectors.toSet()));
        return TodayAttendanceResponse.builder()
            .now(now)
            .rows(rows)
            .receptionGaps(receptionGaps(rows, staff, now))
            .actionsWithoutShift(actionsWithoutShift(location, rows, today, now))
            .build();
    }

    /**
     * Ca theo mẫu (ca tự nhập giờ không thuộc quy tắc «mỗi ca có lễ tân») đã bắt đầu, chưa hết, mà không lễ tân
     * nào của ca đang trong ca.
     */
    static List<ReceptionGap> receptionGaps(List<AttendanceRowResponse> rows, Map<UUID, User> staff, LocalDateTime now) {
        Map<String, List<ShiftResponse>> byShift = new LinkedHashMap<>();
        for (AttendanceRowResponse row : rows) {
            ShiftResponse shift = row.getShift();
            if (shift.getSourceTemplateId() != null) {
                byShift.computeIfAbsent(shift.getShiftDate() + "|" + shift.getSourceTemplateId(), key -> new ArrayList<>())
                    .add(shift);
            }
        }
        List<ReceptionGap> gaps = new ArrayList<>();
        for (List<ShiftResponse> crew : byShift.values()) {
            ShiftResponse first = crew.get(0);
            LocalDateTime start = LocalDateTime.of(first.getShiftDate(), first.getStartTime());
            LocalDateTime end = LocalDateTime.of(first.getShiftDate(), first.getEndTime()).plusDays(first.isOvernight() ? 1 : 0);
            if (now.isBefore(start) || !now.isBefore(end)) {
                continue;
            }
            List<ShiftResponse> receptionists = crew.stream()
                .filter(shift -> isReceptionist(staff.get(shift.getStaffId())))
                .toList();
            boolean covered = receptionists.stream().anyMatch(shift -> shift.getCheckInAt() != null && shift.getCheckOutAt() == null);
            if (covered) {
                continue;
            }
            gaps.add(ReceptionGap.builder()
                .shiftDate(first.getShiftDate())
                .templateName(first.getSourceTemplateName())
                .startTime(first.getStartTime())
                .endTime(first.getEndTime())
                .minutesSinceStart(Duration.between(start, now).toMinutes())
                .receptionistNames(receptionists.stream()
                    .filter(shift -> shift.getCheckInAt() == null)
                    .map(shift -> staff.get(shift.getStaffId()).getFullName())
                    .toList())
                .build());
        }
        return gaps;
    }

    /** Lễ tân đổi trạng thái phòng hôm nay lúc không có ca nào đang vào (tính theo giờ vào / ra thật). */
    private List<ActionWithoutShift> actionsWithoutShift(Location location, List<AttendanceRowResponse> rows,
                                                         LocalDate today, LocalDateTime now) {
        List<RoomStatusHistory> actions = roomHistoryRepository.findBySourceAtLocationBetween(
            location.getTenantId(), location.getId(), ChangeSource.RECEPTION, today.atStartOfDay(), now);
        if (actions.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ShiftResponse>> shiftsOf = rows.stream()
            .map(AttendanceRowResponse::getShift)
            .collect(Collectors.groupingBy(ShiftResponse::getStaffId));
        List<RoomStatusHistory> outside = actions.stream()
            .filter(action -> action.getChangedBy() != null)
            .filter(action -> !inShift(shiftsOf.getOrDefault(action.getChangedBy(), List.of()), action.getChangedAt()))
            .toList();
        if (outside.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> people = usersById(outside.stream().map(RoomStatusHistory::getChangedBy).collect(Collectors.toSet()));
        Map<UUID, String> rooms = new HashMap<>();
        roomRepository.findAllById(outside.stream().map(RoomStatusHistory::getRoomId).collect(Collectors.toSet()))
            .forEach(room -> rooms.put(room.getId(), room.getRoomNumber()));
        return outside.stream()
            // Quản lý / Giám đốc không có ca — chỉ báo thao tác của nhân viên.
            .filter(action -> people.containsKey(action.getChangedBy()) && people.get(action.getChangedBy()).getRole() == Role.STAFF)
            .map(action -> ActionWithoutShift.builder()
                .staffName(people.get(action.getChangedBy()).getFullName())
                .roomNumber(rooms.get(action.getRoomId()))
                .fromStatus(action.getFromStatus())
                .toStatus(action.getToStatus())
                .changedAt(action.getChangedAt())
                .build())
            .toList();
    }

    static boolean inShift(List<ShiftResponse> shifts, LocalDateTime at) {
        return shifts.stream().anyMatch(shift -> shift.getCheckInAt() != null
            && !at.isBefore(shift.getCheckInAt())
            && (shift.getCheckOutAt() == null || !at.isAfter(shift.getCheckOutAt())));
    }

    private static boolean isReceptionist(User user) {
        return user != null && user.getPermissions().contains(StaffPermission.RECEPTION);
    }

    private Map<UUID, User> usersById(Collection<UUID> ids) {
        Set<UUID> nonNull = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (nonNull.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(nonNull).stream().collect(Collectors.toMap(User::getId, Function.identity()));
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
