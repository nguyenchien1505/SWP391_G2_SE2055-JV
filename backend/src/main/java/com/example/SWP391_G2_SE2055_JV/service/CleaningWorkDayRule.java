package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingShiftRepository;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Ngày làm hiện tại" của người dọn phòng — BR-SCH-03, BR-HK-03, BR-HK-04 (M2, chốt 28/10/2026).
 *
 * <p>Ca qua đêm thuộc ngày BẮT ĐẦU ca, nên lúc 02:00 ngày D+1 người làm ca 22:00–06:00 ngày D vẫn đang
 * trong "ngày làm D". Lớp này gom các luật của việc dọn suy ra từ đó:
 * <ul>
 *   <li>giao việc với ngày làm nào thì hợp lệ — {@link #assertIsCurrentWorkDay};</li>
 *   <li>việc nào đang «Tồn đọng» — {@link #overdueTaskIds}.</li>
 * </ul>
 * Điều kiện "ca thuộc ngày làm hiện tại" dùng chung với Giám sát trực ca qua
 * {@link ShiftTimeUtils#isInCurrentWorkDay}. Phần tính thuần nhận {@code now} làm tham số để test được
 * với một mốc giờ cố định — cùng cách {@link SupervisorDutyRule}.
 */
@Component
@RequiredArgsConstructor
public class CleaningWorkDayRule {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HousekeepingShiftRepository shiftRepository;

    /**
     * Giao việc dọn cho {@code staff} với ngày làm {@code date} có hợp lệ không — BR-HK-03, Q5 (nới cho ca
     * qua đêm, chốt 28/10/2026):
     * <ul>
     *   <li>hôm nay → phải có ca hôm nay tại khách sạn này (không cần khớp khung giờ);</li>
     *   <li>hôm qua → phải đang làm dở ca qua đêm bắt đầu hôm qua;</li>
     *   <li>ngày mai trở đi → chặn: giao là phòng sang «Đang dọn» NGAY, giao trước sẽ khiến phòng hiện
     *       sai trạng thái;</li>
     *   <li>trước hôm qua → chặn.</li>
     * </ul>
     *
     * @param now giờ hiện tại theo múi giờ của khách sạn chứa phòng (BR-SCH-17)
     */
    public void assertIsCurrentWorkDay(User staff, UUID locationId, LocalDate date, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (date.isAfter(today)) {
            throw new BusinessException("Việc dọn phòng chỉ gán được cho hôm nay, "
                + "vì phòng chuyển sang «Đang dọn» ngay khi gán người.");
        }
        if (date.isBefore(today.minusDays(1))) {
            throw new BusinessException("Không gán task cho ngày đã qua.");
        }

        List<Shift> shifts = shiftRepository.findOfStaffBetween(staff.getId(), locationId, date, date);
        if (date.equals(today) && shifts.isEmpty()) {
            throw new BusinessException(String.format(
                "%s không có ca làm việc ngày %s — chỉ gán task cho người có ca trong ngày.",
                staff.getFullName(), date.format(DAY)));
        }
        if (date.isBefore(today) && !hasShiftInCurrentWorkDay(shifts, now)) {
            throw new BusinessException(String.format(
                "Ngày %s đã qua — việc của ngày đó chỉ giao được cho người đang làm ca đêm bắt đầu từ hôm đó, "
                    + "mà %s không còn ca đêm nào đang làm.",
                date.format(DAY), staff.getFullName()));
        }
    }

    /**
     * Những việc đang «Tồn đọng» trong {@code tasks} — BR-HK-04: hết ca chưa xong thì để yên ở «Đang
     * thực hiện» và gắn nhãn tồn đọng. Mọi khách sạn chạy giờ Hà Nội (BR-ORG-04 bản sửa) nên một trang
     * việc của nhiều khách sạn dùng chung một mốc "bây giờ".
     */
    public Set<UUID> overdueTaskIds(Collection<HousekeepingTask> tasks) {
        return overdueTaskIds(tasks, LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE));
    }

    /**
     * Bản nhận {@code now} — để test. Chỉ hỏi DB khi trang có việc đang làm của ngày hôm qua, và hỏi một
     * lần cho cả trang.
     */
    Set<UUID> overdueTaskIds(Collection<HousekeepingTask> tasks, LocalDateTime now) {
        LocalDate yesterday = now.toLocalDate().minusDays(1);
        Set<UUID> nightCleaners = tasks.stream()
            .filter(task -> task.getStatus() == HousekeepingTaskStatus.IN_PROGRESS
                && yesterday.equals(task.getAssignedDate()))
            .flatMap(task -> task.getAssigneeIds().stream())
            .collect(Collectors.toSet());
        Set<UUID> stillOnNightShift = nightCleaners.isEmpty()
            ? Set.of()
            : stillOnNightShift(nightCleaners, yesterday, now);

        return tasks.stream()
            .filter(task -> isOverdue(task, now, stillOnNightShift))
            .map(HousekeepingTask::getId)
            .collect(Collectors.toSet());
    }

    /**
     * Việc có «Tồn đọng» không: đang làm, ngày làm đã qua, và KHÔNG phải trường hợp còn người trong nhóm
     * đang làm dở ca đêm bắt đầu từ ngày làm đó (BR-SCH-03 — ngày làm của họ chưa hết).
     *
     * @param stillOnNightShift người đang làm dở ca qua đêm bắt đầu từ hôm qua
     */
    static boolean isOverdue(HousekeepingTask task, LocalDateTime now, Set<UUID> stillOnNightShift) {
        LocalDate today = now.toLocalDate();
        LocalDate workDay = task.getAssignedDate();
        if (task.getStatus() != HousekeepingTaskStatus.IN_PROGRESS || workDay == null || !workDay.isBefore(today)) {
            return false;
        }
        boolean nightShiftGoingOn = workDay.equals(today.minusDays(1))
            && task.getAssigneeIds().stream().anyMatch(stillOnNightShift::contains);
        return !nightShiftGoingOn;
    }

    /** Trong số những người này, ai còn đang làm dở ca qua đêm bắt đầu ngày {@code date}. */
    private Set<UUID> stillOnNightShift(Collection<UUID> staffIds, LocalDate date, LocalDateTime now) {
        return shiftRepository.findOvernightOf(staffIds, date).stream()
            .filter(shift -> ShiftTimeUtils.isInCurrentWorkDay(
                shift.getShiftDate(), shift.isOvernight(), shift.getEndTime(), now))
            .map(Shift::getStaffId)
            .collect(Collectors.toSet());
    }

    private static boolean hasShiftInCurrentWorkDay(Collection<Shift> shifts, LocalDateTime now) {
        return shifts.stream().anyMatch(shift -> ShiftTimeUtils.isInCurrentWorkDay(
            shift.getShiftDate(), shift.isOvernight(), shift.getEndTime(), now));
    }
}
