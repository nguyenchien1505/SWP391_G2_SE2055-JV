package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.LeaveBalanceResponse;
import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequest;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveType;
import com.example.SWP391_G2_SE2055_JV.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Quỹ phép năm — BR-SCH-27, BR-SCH-28, BR-SCH-31 (chốt 19/10/2026).
 *
 * <ul>
 *   <li>Tính theo NĂM DƯƠNG LỊCH; phép còn dư không chuyển sang năm sau.</li>
 *   <li>Tích lũy theo THÁNG LÀM ĐỦ trong năm, tính từ Ngày bắt đầu làm việc (vào làm từ năm trước thì tính từ
 *       01/01): quỹ 12 ngày thì mỗi tháng đủ được 1 ngày, lẻ thì làm tròn xuống.</li>
 *   <li>Còn lại = đã tích lũy TỚI NGÀY XÉT − đơn phép năm đã duyệt − đơn phép năm đang chờ duyệt (BR-SCH-28).
 *       Quỹ chỉ thật sự bị trừ khi đơn được duyệt.</li>
 * </ul>
 *
 * <p>Số ngày phép một năm và số ngày phải báo trước do Giám đốc cấu hình trong Schedule Policy — phần việc
 * của Lâm (BR M2 mục 191; Schedule Policy chỉ Lâm sửa). Chừng nào Policy chưa có hai trường đó, lớp này trả
 * mặc định của BR: 12 ngày và 3 ngày. Mọi nơi khác đọc qua {@link #daysPerYear} / {@link #noticeDays}, nên
 * khi có trường chỉ cần sửa hai hàm này.
 */
@Component
@RequiredArgsConstructor
public class LeaveQuota {

    /** BR-SCH-27: mặc định 12 ngày phép một năm. */
    static final int DEFAULT_DAYS_PER_YEAR = 12;

    /** BR-SCH-07 bản sửa: phép năm mặc định phải gửi trước ít nhất 3 ngày. */
    static final int DEFAULT_NOTICE_DAYS = 3;

    private static final Set<LeaveRequestStatus> APPROVED = EnumSet.of(LeaveRequestStatus.APPROVED);
    private static final Set<LeaveRequestStatus> PENDING  = EnumSet.of(LeaveRequestStatus.PENDING);

    private final LeaveRequestRepository leaveRequestRepository;

    /** Số ngày phép một năm của Tenant — BR-SCH-27. */
    public int daysPerYear(UUID tenantId) {
        return DEFAULT_DAYS_PER_YEAR;
    }

    /** Phép năm phải gửi trước ít nhất bao nhiêu ngày — BR-SCH-07 bản sửa. */
    public int noticeDays(UUID tenantId) {
        return DEFAULT_NOTICE_DAYS;
    }

    /** Quỹ phép năm {@code year} của {@code person}, tích lũy tới ngày {@code asOf} (thường là hôm nay). */
    public LeaveBalanceResponse balanceOf(User person, int year, LocalDate asOf) {
        int daysPerYear = daysPerYear(person.getTenantId());
        int notice = noticeDays(person.getTenantId());
        LocalDate start = person.getStartWorkDate();
        int months = start == null ? 0 : fullMonthsWorked(start, year, asOf);
        int entitled = daysPerYear * months / 12;
        int used = annualDaysOf(person.getId(), year, APPROVED);
        int pending = annualDaysOf(person.getId(), year, PENDING);

        return LeaveBalanceResponse.builder()
            .year(year)
            .daysPerYear(daysPerYear)
            .monthsWorked(months)
            .entitledDays(entitled)
            .usedDays(used)
            .pendingDays(pending)
            .remainingDays(Math.max(0, entitled - used - pending))
            .noticeDays(notice)
            .earliestAnnualStart(asOf.plusDays(notice))
            .startWorkDate(start)
            .build();
    }

    /**
     * Số tháng làm ĐỦ trong năm {@code year}, từ ngày bắt đầu làm việc (hoặc 01/01 nếu vào làm từ năm trước)
     * tới hết ngày {@code asOf}; tối đa 12. Một tháng đủ là từ một ngày tới hết ngày liền trước của cùng ngày
     * tháng sau — vào làm 15/01 thì hết ngày 14/02 mới đủ tháng đầu.
     */
    static int fullMonthsWorked(LocalDate startWorkDate, int year, LocalDate asOf) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate from = startWorkDate.isAfter(yearStart) ? startWorkDate : yearStart;
        LocalDate until = asOf.isAfter(yearEnd) ? yearEnd : asOf;
        if (until.isBefore(from)) {
            return 0;
        }
        return (int) Math.min(12, Period.between(from, until.plusDays(1)).toTotalMonths());
    }

    /** Tổng ngày của các đơn phép năm ở {@code statuses} trong năm {@code year}. */
    private int annualDaysOf(UUID personId, int year, Set<LeaveRequestStatus> statuses) {
        return leaveRequestRepository
            .findByRequesterIdAndLeaveTypeAndStatusInAndFromDateBetween(personId, LeaveType.ANNUAL, statuses,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
            .stream()
            .mapToInt(LeaveRequest::days)
            .sum();
    }
}
