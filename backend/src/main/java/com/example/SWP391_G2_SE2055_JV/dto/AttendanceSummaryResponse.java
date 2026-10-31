package com.example.SWP391_G2_SE2055_JV.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Chuyên cần của một nhân viên trong một tháng (chấm công M2, chốt 01/11/2026) — để quản lý và chính nhân viên
 * thấy được, KHÔNG dùng để tính lương. Ca chưa tới giờ không tính vào tỉ lệ.
 */
@Data
@Builder
public class AttendanceSummaryResponse {

    private UUID   staffId;
    private String staffName;

    /** Mọi ca có người trong tháng, kể cả ca chưa tới. */
    private int    totalShifts;
    /** Ca đã tới giờ vào ca (trừ ca chưa tới) — mẫu số của tỉ lệ. */
    private int    dueShifts;
    private int    completed;
    private int    absent;
    private int    missedCheckOut;
    private int    onLeave;
    private int    late;
    private long   lateMinutes;
    private int    earlyLeave;
    private long   earlyLeaveMinutes;
    /** Ca có lần tự vào / ra từ ngoài mạng khách sạn. */
    private int    outsideNetwork;
    /** Ca có giờ do quản lý nhập / sửa. */
    private int    managerEntered;
    /** Tổng phút giữa giờ vào và giờ ra của các ca đã xong — chỉ để xem. */
    private long   workedMinutes;
    /**
     * Phần trăm ca vào đúng giờ trên số ca phải làm (đã tới giờ, không nghỉ phép); null khi chưa có ca nào phải
     * làm.
     */
    private Integer onTimePercent;
}
