package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Loại task dọn phòng — DM-05.
 *
 * <p>Chỉ còn một loại: việc dọn hằng ngày (STAYOVER) đã bỏ từ V4 (chốt 05/10/2026, thay BR-HK-05).
 * Giữ enum và cột {@code task_type} vì ràng buộc BR-HK-11 ở DB (cột sinh {@code open_task_key})
 * dựa vào nó, và để còn chỗ cho loại việc mới sau này.
 */
public enum HousekeepingTaskType {

    /** Dọn sau khi khách check-out — hệ thống tự sinh, đi đủ 4 bước có Manager kiểm tra. */
    CHECKOUT
}
