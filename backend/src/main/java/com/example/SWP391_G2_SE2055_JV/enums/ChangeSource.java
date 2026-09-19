package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Nguồn thực hiện việc đổi trạng thái phòng — cột "Người/nguồn thực hiện" của ma
 * trận BR-ROOM-02, lưu ở RoomStatusHistory (BR-ROOM-09).
 */
public enum ChangeSource {

    /** Lễ tân: đặt trước, check-in, check-out. */
    RECEPTION,

    /** Nhân viên dọn: bấm hoàn thành dọn. */
    HOUSEKEEPING,

    /** Manager: kiểm tra phòng, vào/ra trạng thái Không khả dụng. */
    MANAGER,

    /** Hệ thống: tự động khi assign task dọn, hoặc khi task bị gỡ người giữa chừng. */
    SYSTEM
}
