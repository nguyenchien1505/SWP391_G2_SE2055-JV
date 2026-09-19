package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Lý do một ca (hoặc task dọn) bị gỡ NGƯỜI — BR-SCH-24 (đúng 4 giá trị).
 *
 * <p>Gỡ người ≠ hủy: ca/task quay về trạng thái "chưa phân công" chứ không đóng lại.
 * Phạm vi tự động gỡ là "ca tương lai" theo BR-SCH-17.
 */
public enum UnassignedReason {

    /** Job điều chuyển chạy tại effective date — BR-TRF-05. */
    TRANSFER,

    /** Staff nghỉ việc — BR-USER-04. */
    TERMINATION,

    /** Duyệt đơn nghỉ trùng ca đã xếp — BR-SCH-07. */
    LEAVE_APPROVED,

    /** Manager gỡ thủ công. */
    MANAGER_MANUAL
}
