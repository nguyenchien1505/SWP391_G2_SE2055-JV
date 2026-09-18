package com.example.SWP391_G2_SE2055_JV.housekeeping.enums;

/**
 * Trạng thái vòng đời task dọn phòng.
 * Lưu vào DB dạng chuỗi (EnumType.STRING) khớp cột cleaning_assignments.status.
 */
public enum CleaningTaskStatus {
    /** Vừa được Manager phân công (BR-57); phòng chuyển sang Đang dọn. */
    ASSIGNED,
    /** Nhân viên đang thực hiện. */
    IN_PROGRESS,
    /** Nhân viên bấm hoàn thành (BR spec 6.2); phòng chuyển sang Chờ kiểm tra. */
    DONE
}
