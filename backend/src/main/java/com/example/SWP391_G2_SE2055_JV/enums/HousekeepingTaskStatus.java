package com.example.SWP391_G2_SE2055_JV.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Trạng thái task dọn phòng — BR-HK-06 (5 trạng thái dùng chung cho cả 2 loại task).
 *
 * <p>CHECKOUT đi đủ 4 bước đầu; STAYOVER bỏ qua {@link #PENDING_INSPECTION}.
 * Kiểm tra KHÔNG đạt vẫn chuyển {@link #COMPLETED} — kết quả FAIL nằm ở
 * InspectionRecord, không có trạng thái FAILED riêng.
 */
public enum HousekeepingTaskStatus {

    /** Chưa phân công. */
    UNASSIGNED,

    /** Đang thực hiện. Hết ca chưa xong thì TỒN ĐỌNG ở đây, không tự hủy — BR-HK-04. */
    IN_PROGRESS,

    /** Chờ Manager kiểm tra. Chỉ áp dụng cho task CHECKOUT. */
    PENDING_INSPECTION,

    COMPLETED,

    /** Đã hủy — luôn kèm lý do (BR-HK-09, BR-HK-10). */
    CANCELLED;

    /**
     * Các trạng thái "đang mở". BR-HK-11: mỗi phòng tối đa 1 task đang mở cho mỗi
     * loại task — ràng buộc này cũng được ép ở DB qua cột sinh {@code open_task_key}.
     */
    public static final Set<HousekeepingTaskStatus> OPEN_STATUSES =
        EnumSet.of(UNASSIGNED, IN_PROGRESS, PENDING_INSPECTION);

    public boolean isOpen() {
        return OPEN_STATUSES.contains(this);
    }
}
