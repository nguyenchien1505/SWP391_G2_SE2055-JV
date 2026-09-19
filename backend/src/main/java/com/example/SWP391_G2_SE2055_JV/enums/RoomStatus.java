package com.example.SWP391_G2_SE2055_JV.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Trạng thái phòng — BR-ROOM-01 (đúng 7 trạng thái) kèm ma trận chuyển trạng thái
 * BR-ROOM-02.
 *
 * <p>Ma trận là nguồn duy nhất quyết định một bước chuyển có hợp lệ hay không;
 * service layer gọi {@link #canTransitionTo(RoomStatus)} trước khi ghi.
 *
 * <p><b>Lưu ý về RESERVED:</b> BR-ROOM-02 chỉ định nghĩa đường ĐI VÀO trạng thái này
 * (AVAILABLE → RESERVED, do Lễ tân) mà không định nghĩa đường đi ra, nên theo đúng
 * văn bản thì phòng đã đặt sẽ kẹt vĩnh viễn. Hai bước chuyển RESERVED → OCCUPIED
 * (khách đến nhận phòng) và RESERVED → AVAILABLE (khách hủy) được bổ sung ở đây để
 * luồng chạy được; <b>cần chốt lại với BA rồi cập nhật BR-ROOM-02.</b>
 */
public enum RoomStatus {

    /** Đã đặt — cờ thủ công do Lễ tân bật khi khách đặt qua SĐT/email. */
    RESERVED,

    /** Trống / Sẵn sàng. */
    AVAILABLE,

    /** Đang sử dụng (có khách). */
    OCCUPIED,

    /** Chờ dọn. Vào trạng thái này là sinh task dọn — BR-HK-01. */
    DIRTY,

    /** Đang dọn. */
    CLEANING,

    /** Chờ Manager kiểm tra. */
    INSPECTION,

    /** Không khả dụng — gộp Bảo trì + Khóa phòng, bắt buộc kèm lý do (BR-ROOM-07). */
    UNAVAILABLE;

    private static final Map<RoomStatus, Set<RoomStatus>> ALLOWED = Map.of(
        // BR-ROOM-02: Lễ tân đặt trước / check-in; Manager khóa phòng.
        AVAILABLE,   EnumSet.of(RESERVED, OCCUPIED, UNAVAILABLE),
        // Bổ sung ngoài BR-ROOM-02 — xem javadoc ở đầu enum.
        RESERVED,    EnumSet.of(OCCUPIED, AVAILABLE, UNAVAILABLE),
        // BR-ROOM-03: phòng có khách KHÔNG được chuyển sang UNAVAILABLE.
        OCCUPIED,    EnumSet.of(DIRTY),
        // Hệ thống tự chuyển khi Manager assign task dọn.
        DIRTY,       EnumSet.of(CLEANING, UNAVAILABLE),
        // Nhân viên dọn bấm hoàn thành; hoặc task bị gỡ người giữa chừng (BR-HK-07).
        CLEANING,    EnumSet.of(INSPECTION, DIRTY, UNAVAILABLE),
        // Manager kiểm tra đạt / không đạt.
        INSPECTION,  EnumSet.of(AVAILABLE, DIRTY, UNAVAILABLE),
        // BR-ROOM-03: ra khỏi Không khả dụng thì Manager tự chọn trạng thái đích.
        UNAVAILABLE, EnumSet.of(DIRTY, AVAILABLE)
    );

    public boolean canTransitionTo(RoomStatus target) {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(RoomStatus.class)).contains(target);
    }

    public Set<RoomStatus> allowedTargets() {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(RoomStatus.class));
    }

    /** BR-ROOM-08: chỉ xóa được phòng đang ở Trống/Sẵn sàng hoặc Không khả dụng. */
    public boolean isDeletable() {
        return this == AVAILABLE || this == UNAVAILABLE;
    }
}
