package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Loại task dọn phòng — BR-61.
 * Lưu vào DB dạng chuỗi (EnumType.STRING) khớp cột cleaning_assignments.task_type.
 */
public enum CleaningTaskType {
    /** Dọn sau khi khách check-out (luồng Chờ dọn → Đang dọn). */
    POST_CHECKOUT,
    /** Dọn hằng ngày khi phòng vẫn Đang sử dụng; phòng không đổi trạng thái. */
    STAYOVER
}
