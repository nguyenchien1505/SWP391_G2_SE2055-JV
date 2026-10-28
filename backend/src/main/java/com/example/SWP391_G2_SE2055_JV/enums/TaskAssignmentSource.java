package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Việc dọn được BẮT ĐẦU bằng cách nào — BR-HK-12 (sửa M2, chốt 19/10/2026): Manager gán, Giám sát gán,
 * hay nhân viên tự nhận (BR-HK-02 bản sửa).
 *
 * <p>Lưu trên việc dọn, không theo từng người trong nhóm (chốt 28/10/2026): thêm người vào nhóm sau đó
 * không đổi giá trị này. Người bắt đầu là {@code assignedBy} — với {@link #SELF_CLAIM} đó chính là người
 * tự nhận, và chỉ người ấy được trả lại việc (BR-HK-17).
 */
public enum TaskAssignmentSource {

    /** Quản lý chi nhánh gán tay (Admin nền tảng cũng tính vào đây) — BR-HK-02. */
    MANAGER,

    /** Giám sát vận hành gán tay — BR-PERM-07. */
    SUPERVISION,

    /** Nhân viên Dọn dẹp tự nhận từ danh sách việc chờ nhận — BR-HK-02 (sửa), BR-HK-14. */
    SELF_CLAIM
}
