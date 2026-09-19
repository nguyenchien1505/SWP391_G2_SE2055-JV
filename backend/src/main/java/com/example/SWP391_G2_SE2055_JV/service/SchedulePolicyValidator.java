package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.SchedulePolicy;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Kiểm tra một ca dự kiến có vi phạm Schedule Policy của Tenant hay không — BR-SCH-02.
 *
 * <p><b>Vi phạm BẤT KỲ điều nào là chặn cứng: không cho lưu, KHÔNG có cơ chế override.</b>
 * Mọi đường ghi ca đều phải đi qua đây: xếp ca theo template lẫn ca tự do (BR-SCH-04),
 * và bước re-check cho CẢ HAI người sau khi đồng nghiệp đồng ý đổi ca (BR-SCH-10).
 *
 * <p>Các quy ước đếm dễ hiểu sai, đã cài đúng theo tài liệu:
 * <ul>
 *   <li>BR-SCH-03 — ca qua đêm tính TRỌN số giờ vào ngày BẮT ĐẦU ca, không chia cắt.</li>
 *   <li>BR-SCH-13 — "tuần" là tuần lịch Thứ Hai đến Chủ Nhật, xác định theo ngày bắt đầu ca.</li>
 *   <li>BR-SCH-14 — "số ca liên tiếp" đếm theo SỐ NGÀY LIÊN TIẾP CÓ CA, không phải số bản ghi ca.</li>
 *   <li>BR-SCH-15 — số ngày riêng biệt có ca trong tuần ≤ (7 − số ngày nghỉ tối thiểu).</li>
 *   <li>BR-SCH-05 — trùng ca chỉ kiểm trong phạm vi Location hiện tại, không check chéo Location khác.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SchedulePolicyValidator {

    private final SchedulePolicyService policyService;
    private final ShiftRepository       shiftRepository;

    /**
     * @param excludeShiftId ca đang được sửa — loại khỏi phép tính để không tự đụng chính nó.
     *                       Truyền {@code null} khi tạo ca mới.
     */
    public void validate(UUID tenantId, UUID staffId, Shift candidate, UUID excludeShiftId) {
        if (staffId == null) {
            // Ca chưa phân công thì không ràng buộc ai — DM-03.
            return;
        }

        // BR-SCH-20: "không chặn xếp ca vì chưa cấu hình" — Tenant chưa có policy thì
        // dùng luôn bản mặc định thay vì báo lỗi.
        SchedulePolicy policy = policyService.getOrCreate(tenantId);

        LocalDate date = candidate.getShiftDate();
        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        // Cửa sổ đủ rộng để phủ cả tuần lịch lẫn chuỗi ngày liên tiếp có thể vắt sang tuần khác.
        int span = policy.getMaxConsecutiveShifts() + 2;
        LocalDate from = min(weekStart, date.minusDays(span));
        LocalDate to = max(weekEnd, date.plusDays(span));

        List<Shift> existing = new ArrayList<>(
            shiftRepository.findByStaffIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(staffId, from, to));
        if (excludeShiftId != null) {
            existing.removeIf(s -> excludeShiftId.equals(s.getId()));
        }

        checkOverlap(existing, candidate);
        checkMaxHoursPerDay(existing, candidate, policy);
        checkMaxHoursPerWeek(existing, candidate, policy, weekStart, weekEnd);
        checkConsecutiveDays(existing, candidate, policy);
        checkMinRestBetweenShifts(existing, candidate, policy);
        checkMinDaysOffPerWeek(existing, candidate, policy, weekStart, weekEnd);
    }

    /** BR-SCH-05: trùng ca chỉ xét trong CÙNG Location. */
    private void checkOverlap(List<Shift> existing, Shift candidate) {
        LocalDateTime candStart = startOf(candidate);
        LocalDateTime candEnd = endOf(candidate);

        for (Shift s : existing) {
            if (!s.getLocationId().equals(candidate.getLocationId())) {
                continue;
            }
            if (candStart.isBefore(endOf(s)) && startOf(s).isBefore(candEnd)) {
                throw new BusinessException(String.format(
                    "Trùng ca: nhân viên đã có ca %s %s–%s tại Location này (BR-SCH-05).",
                    s.getShiftDate(), s.getStartTime(), s.getEndTime()));
            }
        }
    }

    /** BR-SCH-03: ca qua đêm dồn trọn giờ vào ngày bắt đầu, nên chỉ cần nhóm theo shiftDate. */
    private void checkMaxHoursPerDay(List<Shift> existing, Shift candidate, SchedulePolicy policy) {
        BigDecimal total = candidate.getDurationHours();
        for (Shift s : existing) {
            if (s.getShiftDate().equals(candidate.getShiftDate())) {
                total = total.add(s.getDurationHours());
            }
        }
        if (total.compareTo(policy.getMaxHoursPerDay()) > 0) {
            throw new BusinessException(String.format(
                "Vượt giờ làm tối đa/ngày: tổng %s giờ ngày %s, giới hạn %s giờ (BR-SCH-02).",
                total.toPlainString(), candidate.getShiftDate(), policy.getMaxHoursPerDay().toPlainString()));
        }
    }

    /** BR-SCH-13: tuần lịch T2–CN, xác định theo NGÀY BẮT ĐẦU ca. */
    private void checkMaxHoursPerWeek(List<Shift> existing, Shift candidate, SchedulePolicy policy,
                                      LocalDate weekStart, LocalDate weekEnd) {
        BigDecimal total = candidate.getDurationHours();
        for (Shift s : existing) {
            if (withinInclusive(s.getShiftDate(), weekStart, weekEnd)) {
                total = total.add(s.getDurationHours());
            }
        }
        if (total.compareTo(policy.getMaxHoursPerWeek()) > 0) {
            throw new BusinessException(String.format(
                "Vượt giờ làm tối đa/tuần: tổng %s giờ trong tuần %s–%s, giới hạn %s giờ (BR-SCH-02).",
                total.toPlainString(), weekStart, weekEnd, policy.getMaxHoursPerWeek().toPlainString()));
        }
    }

    /** BR-SCH-14: đếm SỐ NGÀY liên tiếp có ca, không đếm số bản ghi ca. */
    private void checkConsecutiveDays(List<Shift> existing, Shift candidate, SchedulePolicy policy) {
        Set<LocalDate> workDays = new HashSet<>();
        for (Shift s : existing) {
            workDays.add(s.getShiftDate());
        }
        workDays.add(candidate.getShiftDate());

        // Đo chiều dài chuỗi ngày liên tiếp CHỨA ngày của ca đang xét.
        LocalDate cursor = candidate.getShiftDate();
        int run = 1;
        for (LocalDate d = cursor.minusDays(1); workDays.contains(d); d = d.minusDays(1)) {
            run++;
        }
        for (LocalDate d = cursor.plusDays(1); workDays.contains(d); d = d.plusDays(1)) {
            run++;
        }

        if (run > policy.getMaxConsecutiveShifts()) {
            throw new BusinessException(String.format(
                "Vượt số ngày làm liên tiếp tối đa: chuỗi %d ngày liên tiếp có ca, giới hạn %d (BR-SCH-02, BR-SCH-14).",
                run, policy.getMaxConsecutiveShifts()));
        }
    }

    private void checkMinRestBetweenShifts(List<Shift> existing, Shift candidate, SchedulePolicy policy) {
        List<Shift> all = new ArrayList<>(existing);
        all.add(candidate);
        all.sort(Comparator.comparing(SchedulePolicyValidator::startOf));

        long minRestMinutes = policy.getMinRestHoursBetweenShifts()
            .multiply(BigDecimal.valueOf(60))
            .longValue();

        for (int i = 1; i < all.size(); i++) {
            Shift prev = all.get(i - 1);
            Shift next = all.get(i);
            // Chỉ soi cặp có liên quan tới ca đang xét; các cặp cũ đã hợp lệ từ trước.
            if (prev != candidate && next != candidate) {
                continue;
            }
            long restMinutes = Duration.between(endOf(prev), startOf(next)).toMinutes();
            if (restMinutes < minRestMinutes) {
                throw new BusinessException(String.format(
                    "Không đủ thời gian nghỉ giữa 2 ca: chỉ %.1f giờ, tối thiểu %s giờ (BR-SCH-02).",
                    restMinutes / 60.0, policy.getMinRestHoursBetweenShifts().toPlainString()));
            }
        }
    }

    /** BR-SCH-15: số ngày riêng biệt có ca trong tuần ≤ (7 − số ngày nghỉ tối thiểu). */
    private void checkMinDaysOffPerWeek(List<Shift> existing, Shift candidate, SchedulePolicy policy,
                                        LocalDate weekStart, LocalDate weekEnd) {
        Set<LocalDate> workDays = new HashSet<>();
        for (Shift s : existing) {
            if (withinInclusive(s.getShiftDate(), weekStart, weekEnd)) {
                workDays.add(s.getShiftDate());
            }
        }
        workDays.add(candidate.getShiftDate());

        int maxWorkDays = 7 - policy.getMinDaysOffPerWeek();
        if (workDays.size() > maxWorkDays) {
            throw new BusinessException(String.format(
                "Không đủ ngày nghỉ trong tuần %s–%s: đã có ca %d ngày, tối đa %d ngày "
                + "(tối thiểu %d ngày nghỉ — BR-SCH-02, BR-SCH-15).",
                weekStart, weekEnd, workDays.size(), maxWorkDays, policy.getMinDaysOffPerWeek()));
        }
    }

    // ── Mốc thời gian tuyệt đối của một ca ───────────────────────────────────
    // Ca qua đêm kết thúc vào NGÀY HÔM SAU; riêng số GIỜ vẫn tính trọn vào ngày
    // bắt đầu khi cộng dồn theo ngày/tuần (BR-SCH-03).

    private static LocalDateTime startOf(Shift shift) {
        return shift.getShiftDate().atTime(shift.getStartTime());
    }

    private static LocalDateTime endOf(Shift shift) {
        LocalDateTime end = shift.getShiftDate().atTime(shift.getEndTime());
        return shift.isOvernight() ? end.plusDays(1) : end;
    }

    private static boolean withinInclusive(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
