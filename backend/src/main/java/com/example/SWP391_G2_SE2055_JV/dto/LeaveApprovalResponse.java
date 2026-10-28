package com.example.SWP391_G2_SE2055_JV.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Hệ quả của việc duyệt một đơn nghỉ lên lịch làm việc — dùng cho cả xem trước (đơn còn chờ duyệt: "sẽ
 * gỡ") lẫn kết quả duyệt ("đã gỡ").
 */
@Data
@Builder
public class LeaveApprovalResponse {

    private LeaveRequestResponse request;

    /** Ca từ ngày mai trở đi trong khoảng nghỉ, chưa check-in — gỡ người với lý do LEAVE_APPROVED (BR-SCH-07). */
    private List<ShiftImpact> releasedShifts;

    /**
     * Ca trong khoảng nghỉ KHÔNG tự gỡ: ca của chính hôm nay (BR-SCH-17 — Manager gỡ tay nếu cần) hoặc ca đã
     * check-in (gỡ là mất dữ liệu chấm công).
     */
    private List<ShiftImpact> keptShifts;

    /**
     * BR-SCH-29: các ca theo mẫu sẽ mất lễ tân duy nhất, dạng "Ca chiều ngày 28/10/2026". Chỉ là cảnh báo —
     * vẫn duyệt được.
     */
    private List<String> receptionWarnings;

    /** Quỹ phép năm của người gửi trong năm của đơn; null với nghỉ không lương. */
    private LeaveBalanceResponse balance;

    /** @param note vì sao ca này được giữ — chỉ có ở {@code keptShifts} */
    public record ShiftImpact(UUID shiftId, LocalDate shiftDate, LocalTime startTime, LocalTime endTime,
                              String templateName, String note) {}
}
