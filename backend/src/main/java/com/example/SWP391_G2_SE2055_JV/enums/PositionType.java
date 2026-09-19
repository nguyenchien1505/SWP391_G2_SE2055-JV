package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Loại Position — BR-ORG-08.
 *
 * <p>Tên Position do Giám đốc đặt tùy ý và có thể có nhiều Position cùng Loại;
 * quyền nghiệp vụ đặc thù gán theo LOẠI này, không theo tên.
 */
public enum PositionType {

    /** Lễ tân — cập nhật trạng thái phòng khi khách check-in/check-out (BR-PERM-04). */
    RECEPTION,

    /** Dọn dẹp — nhận và hoàn thành task dọn phòng (BR-PERM-05). */
    HOUSEKEEPING,

    /** Khác — chỉ có quyền chung, không có quyền nghiệp vụ đặc thù (BR-ORG-09, BR-PERM-06). */
    OTHER
}
