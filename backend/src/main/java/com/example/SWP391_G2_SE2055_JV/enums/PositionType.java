package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Loại Position — BR-ORG-08 (bản sửa, xem docs/THAY_DOI_BR.docx).
 *
 * <p>Tên Position do Giám đốc đặt tùy ý và có thể có nhiều Position cùng Loại. Loại KHÔNG cấp
 * quyền: quyền nghiệp vụ là {@link StaffPermission}, Manager tick cho từng nhân viên. Loại chỉ
 * quyết định ô nào được tick sẵn khi chọn Position này — xem {@link StaffPermission#defaultFor}.
 */
public enum PositionType {

    /** Lễ tân — tick sẵn quyền Lễ tân. */
    RECEPTION,

    /** Dọn dẹp — tick sẵn quyền Dọn dẹp. */
    HOUSEKEEPING,

    /** Khác — không tick sẵn quyền nào; người giữ chỉ có quyền chung nếu Manager không tick thêm. */
    OTHER
}
