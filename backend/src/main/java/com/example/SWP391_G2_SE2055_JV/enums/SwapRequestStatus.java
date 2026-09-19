package com.example.SWP391_G2_SE2055_JV.enums;

import java.util.EnumSet;
import java.util.Set;

/** Trạng thái yêu cầu đổi ca — BR-SCH-23 (đúng 7 trạng thái). */
public enum SwapRequestStatus {

    /** Chờ đồng nghiệp phản hồi. Quá expiresAt thì tự hủy — BR-SCH-11. */
    PENDING_PEER,

    /** Đồng nghiệp đã đồng ý, chờ Manager duyệt. */
    PENDING_MANAGER,

    /** Manager đã duyệt: hoán đổi trực tiếp người được gán của 2 ca — BR-SCH-18. */
    APPROVED,

    PEER_REJECTED,

    MANAGER_REJECTED,

    /** Tự hủy do quá hạn phản hồi — BR-SCH-11. */
    EXPIRED,

    /** Người gửi tự hủy. */
    CANCELLED;

    /**
     * BR-SCH-19: mỗi ca chỉ được là đối tượng của tối đa 1 yêu cầu đang chờ xử lý.
     * Ràng buộc này cũng được ép ở DB qua 2 cột sinh pending_*_shift_id.
     */
    public static final Set<SwapRequestStatus> PENDING_STATUSES =
        EnumSet.of(PENDING_PEER, PENDING_MANAGER);

    public boolean isPending() {
        return PENDING_STATUSES.contains(this);
    }
}
