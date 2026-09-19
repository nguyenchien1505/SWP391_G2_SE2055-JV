package com.example.SWP391_G2_SE2055_JV.enums;

/** Trạng thái tài khoản — BR-USER-04. */
public enum UserStatus {

    ACTIVE,

    INACTIVE,

    /**
     * Đã nghỉ việc (xóa mềm). Không đăng nhập được, dữ liệu lịch sử giữ nguyên,
     * email vẫn chiếm chỗ unique toàn hệ thống — BR-USER-04, BR-USER-06.
     */
    TERMINATED
}
