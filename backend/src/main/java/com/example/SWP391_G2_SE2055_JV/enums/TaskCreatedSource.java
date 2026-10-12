package com.example.SWP391_G2_SE2055_JV.enums;

/** Nguồn sinh task dọn — BR-HK-12. Bỏ "Manager tạo cho phòng stayover" cùng việc dọn hằng ngày (V4). */
public enum TaskCreatedSource {

    /** Tự sinh khi khách check-out, hoặc khi phòng mới được tạo — BR-HK-01, BR-ROOM-10. */
    CHECKOUT_AUTO,

    /** Tự sinh do kiểm tra không đạt; parentTaskId trỏ về task gốc — BR-HK-12. */
    INSPECTION_FAILED
}
