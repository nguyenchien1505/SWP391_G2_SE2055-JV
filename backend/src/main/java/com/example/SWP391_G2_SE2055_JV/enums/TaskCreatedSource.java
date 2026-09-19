package com.example.SWP391_G2_SE2055_JV.enums;

/** Nguồn sinh task dọn — BR-HK-12 (đúng 3 giá trị). */
public enum TaskCreatedSource {

    /** Tự sinh khi khách check-out, hoặc khi phòng mới được tạo — BR-HK-01, BR-ROOM-10. */
    CHECKOUT_AUTO,

    /** Manager tạo tay cho phòng stayover — BR-HK-05. */
    MANAGER_STAYOVER,

    /** Tự sinh do kiểm tra không đạt; parentTaskId trỏ về task gốc — BR-HK-12. */
    INSPECTION_FAILED
}
