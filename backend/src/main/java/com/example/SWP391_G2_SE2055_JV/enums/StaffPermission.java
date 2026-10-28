package com.example.SWP391_G2_SE2055_JV.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Quyền nghiệp vụ đặc thù của nhân viên — Manager tick cho TỪNG người khi tạo/sửa Staff. Một người
 * có thể có nhiều quyền (ví dụ Lễ tân + Dọn dẹp) hoặc không có quyền nào (chỉ quyền chung —
 * BR-PERM-06).
 *
 * <p>Thay cho cách cũ "quyền đi theo Loại Position" (BR-ORG-08 bản 20/09/2026). Loại Position vẫn
 * còn, chỉ dùng làm gợi ý tick sẵn — xem {@link #defaultFor}.
 *
 * <p>Authority trong session vẫn là {@code POSITION_<tên quyền>} để các rule phân quyền đã có
 * ({@code SecurityConfig}, {@code @PreAuthorize}) giữ nguyên.
 *
 * <p>Thêm quyền mới: thêm hằng ở đây, thêm giá trị vào CHECK {@code ck_user_permissions_value}
 * bằng một migration mới, rồi gắn quyền đó vào các rule cần chặn.
 */
public enum StaffPermission {

    /** Lễ tân — đặt/hủy đặt phòng, check-in/check-out (BR-PERM-04). */
    RECEPTION,

    /** Dọn dẹp — nhận task dọn phòng, bấm hoàn thành (BR-PERM-05). */
    HOUSEKEEPING,

    /**
     * Giám sát vận hành — làm thay Manager phần dọn phòng và báo hỏng: kiểm tra phòng sau dọn, gán
     * task dọn, xem và đóng báo hỏng; chỉ trong ngày có ca (BR-PERM-07, chốt 19/10/2026). Tick kèm
     * được Lễ tân / Dọn dẹp (BR-ORG-09 bản sửa M2).
     */
    SUPERVISION;

    /** Authority tương ứng trong session. */
    public String authority() {
        return "POSITION_" + name();
    }

    /** Quyền tick sẵn khi chọn một Position theo Loại của nó; Loại "Khác" không kèm quyền nào. */
    public static Set<StaffPermission> defaultFor(PositionType type) {
        if (type == PositionType.RECEPTION) {
            return EnumSet.of(RECEPTION);
        }
        if (type == PositionType.HOUSEKEEPING) {
            return EnumSet.of(HOUSEKEEPING);
        }
        if (type == PositionType.SUPERVISION) {
            return EnumSet.of(SUPERVISION);
        }
        return EnumSet.noneOf(StaffPermission.class);
    }
}
