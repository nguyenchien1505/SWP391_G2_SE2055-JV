package com.example.SWP391_G2_SE2055_JV.enums;

/** Kết quả Manager kiểm tra phòng sau khi dọn — BR-ROOM-02, BR-HK-06. */
public enum InspectionResult {

    /** Đạt: phòng chuyển Chờ kiểm tra → Trống/Sẵn sàng. */
    PASS,

    /**
     * Không đạt: BẮT BUỘC nhập lý do (text tự do — BR-HK-08); phòng quay về Chờ dọn
     * và hệ thống tự sinh task mới. Task đang kiểm tra vẫn chuyển COMPLETED.
     */
    FAIL
}
