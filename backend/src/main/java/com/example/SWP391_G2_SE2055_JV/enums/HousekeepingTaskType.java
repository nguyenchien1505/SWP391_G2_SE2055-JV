package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Loại task dọn phòng — BR-HK-05, DM-05.
 *
 * <p>Hai loại dùng chung 1 bảng, phân biệt bằng trường này.
 */
public enum HousekeepingTaskType {

    /** Dọn sau khi khách check-out. Đi đủ 4 bước, có bước Manager kiểm tra. */
    CHECKOUT,

    /**
     * Dọn hằng ngày khi phòng vẫn Đang sử dụng. Manager tạo thủ công, phòng giữ
     * nguyên trạng thái, BỎ QUA bước Chờ kiểm tra — BR-HK-05, BR-HK-06.
     */
    STAYOVER
}
