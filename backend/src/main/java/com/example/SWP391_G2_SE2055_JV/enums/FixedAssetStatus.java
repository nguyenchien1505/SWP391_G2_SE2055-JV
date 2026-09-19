package com.example.SWP391_G2_SE2055_JV.enums;

/** Trạng thái tài sản cố định — BR-ASSET-02, BR-ASSET-14 (đúng 4 giá trị). */
public enum FixedAssetStatus {

    GOOD,
    BROKEN,
    UNDER_REPAIR,

    /**
     * Trạng thái CUỐI: không quay lại được, ẩn khỏi danh sách vận hành nhưng vẫn
     * giữ bản ghi để tra lịch sử. Không cho tạo báo hỏng mới — BR-ASSET-11, BR-ASSET-14.
     */
    DISPOSED;

    /** BR-ASSET-14: 3 trạng thái đầu chuyển tự do cho nhau, DISPOSED là một chiều. */
    public boolean canTransitionTo(FixedAssetStatus target) {
        if (this == DISPOSED) return false;
        return this != target;
    }
}
