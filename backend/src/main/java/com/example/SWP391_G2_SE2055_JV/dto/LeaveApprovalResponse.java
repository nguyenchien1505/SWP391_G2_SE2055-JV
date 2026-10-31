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
     * Ca theo mẫu sẽ mất lễ tân duy nhất khi gỡ người gửi. BR-SCH-29 sửa (chốt 31/10/2026): mỗi ca theo mẫu phải có
     * lễ tân, nên phải chọn một lễ tân thay cho TỪNG ca này thì mới duyệt được. Kết quả duyệt thì rỗng — ca nào cũng
     * đã có người thay ở {@link #replacements}.
     */
    private List<ReceptionGap> receptionGaps;

    /** Kết quả duyệt: lễ tân đã xếp thay vào các ca mất lễ tân. Rỗng ở bản xem trước. */
    private List<Replacement> replacements;

    /** Quỹ phép năm của người gửi trong năm của đơn; null với nghỉ không lương. */
    private LeaveBalanceResponse balance;

    /** @param note vì sao ca này được giữ — chỉ có ở {@code keptShifts} */
    public record ShiftImpact(UUID shiftId, LocalDate shiftDate, LocalTime startTime, LocalTime endTime,
                              String templateName, String note) {}

    /**
     * @param candidates lễ tân xếp được vào ca này: đang làm ở khách sạn, không nghỉ (đơn đã duyệt) ngày đó và qua
     *                   đủ Schedule Policy (BR-SCH-02). Rỗng thì chưa duyệt được — sắp lại lịch hoặc từ chối đơn.
     */
    public record ReceptionGap(ShiftImpact shift, List<Candidate> candidates) {}

    /** @param positionName vị trí của lễ tân; null nếu hồ sơ chưa có */
    public record Candidate(UUID staffId, String fullName, String positionName) {}

    public record Replacement(ShiftImpact shift, UUID staffId, String fullName) {}
}
