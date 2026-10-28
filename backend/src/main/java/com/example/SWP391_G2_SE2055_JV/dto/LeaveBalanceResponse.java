package com.example.SWP391_G2_SE2055_JV.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Quỹ phép năm của một người trong một năm dương lịch — BR-SCH-27, BR-SCH-28 (xem {@code LeaveQuota}).
 * {@code remainingDays = entitledDays - usedDays - pendingDays}, không âm.
 */
@Data
@Builder
public class LeaveBalanceResponse {

    private int       year;
    /** Quỹ cả năm — Giám đốc cấu hình, mặc định 12 ngày. */
    private int       daysPerYear;
    /** Số tháng làm ĐỦ trong năm tính tới ngày xét. */
    private int       monthsWorked;
    /** Số ngày phép đã tích lũy tới ngày xét. */
    private int       entitledDays;
    /** Ngày của các đơn phép năm đã duyệt — quỹ chỉ bị trừ khi duyệt. */
    private int       usedDays;
    /** Ngày của các đơn phép năm đang chờ duyệt — vẫn tính khi xét đơn mới (BR-SCH-28). */
    private int       pendingDays;
    private int       remainingDays;
    /** Phép năm phải gửi trước ít nhất bao nhiêu ngày (BR-SCH-07 bản sửa). */
    private int       noticeDays;
    /** Ngày sớm nhất được xin phép năm nếu gửi hôm nay. */
    private LocalDate earliestAnnualStart;
    /** Mốc tích lũy quỹ; null = hồ sơ chưa có nên chưa tính được quỹ, chỉ xin được nghỉ không lương. */
    private LocalDate startWorkDate;
}
