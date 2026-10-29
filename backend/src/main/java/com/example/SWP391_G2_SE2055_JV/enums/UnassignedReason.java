package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Lý do một ca (hoặc task dọn) bị gỡ NGƯỜI — BR-SCH-24 (ca: đúng 4 giá trị đầu), BR-HK-17 (việc dọn
 * thêm {@link #SELF_RETURNED}).
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
    MANAGER_MANUAL,

    /**
     * Người tự nhận trả lại việc dọn và nhóm hết người — BR-HK-17. <b>Chỉ việc dọn dùng</b>; ca không có giá
     * trị này (CHECK của bảng shifts vẫn 4 giá trị — BR-SCH-24, và {@code ShiftController} luôn truyền cứng
     * {@link #MANAGER_MANUAL}).
     */
    SELF_RETURNED
}
