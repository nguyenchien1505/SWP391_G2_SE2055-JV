package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Lý do một task dọn bị HỦY — BR-HK-09, BR-HK-10.
 *
 * <p>Khác {@link UnassignedReason}: gỡ người thì task quay về "chưa phân công",
 * còn hủy thì task đóng lại vĩnh viễn. ERD không có cột nào cho việc này dù
 * BR-HK-09 yêu cầu hủy "kèm lý do", nên cột cancel_reason được bổ sung.
 */
public enum TaskCancelReason {

    /** Manager chuyển phòng sang Không khả dụng — BR-HK-09. */
    ROOM_UNAVAILABLE,

    /** Khách check-out trong lúc task STAYOVER chưa xong — BR-HK-10. */
    GUEST_CHECKED_OUT,

    /** Manager hủy thủ công. */
    MANAGER_MANUAL
}
