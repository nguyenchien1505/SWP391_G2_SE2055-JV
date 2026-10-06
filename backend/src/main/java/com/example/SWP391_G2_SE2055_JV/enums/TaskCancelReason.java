package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Lý do một task dọn bị HỦY — BR-HK-09.
 *
 * <p>Khác {@link UnassignedReason}: gỡ người thì task quay về "chưa phân công",
 * còn hủy thì task đóng lại vĩnh viễn. ERD không có cột nào cho việc này dù
 * BR-HK-09 yêu cầu hủy "kèm lý do", nên cột cancel_reason được bổ sung.
 *
 * <p>Hai lý do "khách trả phòng" (BR-HK-10) và "quản lý hủy" chỉ dùng cho việc dọn hằng ngày —
 * bỏ cùng loại việc đó (V4). Việc dọn sau trả phòng chỉ dừng được bằng cách khóa phòng.
 */
public enum TaskCancelReason {

    /** Manager chuyển phòng sang Không khả dụng — BR-HK-09. */
    ROOM_UNAVAILABLE
}
