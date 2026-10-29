package com.example.SWP391_G2_SE2055_JV.enums;

/** Loại nghỉ — BR-SCH-06 bản sửa M2 (chốt 19/10/2026): thay "một loại nghỉ, không tính ngày phép" của BR1. */
public enum LeaveType {

    /**
     * Nghỉ phép năm — có lương (BR-PAY-10), trừ quỹ phép năm mọi ngày trong khoảng nghỉ (BR-SCH-27, BR-SCH-31).
     * Phải gửi trước tối thiểu số ngày báo trước (BR-SCH-07 bản sửa).
     */
    ANNUAL,

    /** Nghỉ không lương — không trừ quỹ phép, gửi được cả cho hôm nay (BR-SCH-07 bản sửa). */
    UNPAID
}
