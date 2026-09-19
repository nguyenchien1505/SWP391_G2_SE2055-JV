package com.example.SWP391_G2_SE2055_JV.enums;

/** Trạng thái đơn xin nghỉ — BR-SCH-23 (đúng 4 trạng thái). */
public enum LeaveRequestStatus {

    PENDING,

    /** Đã duyệt. Không hủy được nữa — muốn đi làm lại thì Manager xếp ca mới (BR-SCH-16). */
    APPROVED,

    REJECTED,

    /** Người gửi tự hủy khi đơn còn ở PENDING — BR-SCH-16. */
    CANCELLED
}
