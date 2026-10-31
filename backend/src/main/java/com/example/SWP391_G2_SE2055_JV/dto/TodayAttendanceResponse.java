package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Khối «Hôm nay» trên màn hình đầu của Manager (chấm công M2): ai đang làm, ai quá giờ chưa vào, ca nào đang
 * không có lễ tân nào vào ca, và thao tác nhận / trả phòng của lễ tân lúc chưa vào ca.
 */
@Data
@Builder
public class TodayAttendanceResponse {

    /** Giờ hiện tại của khách sạn. */
    private LocalDateTime                now;
    /** Ca có người thuộc ngày làm hiện tại (kể cả ca đêm từ hôm qua còn đang làm), sớm nhất trước. */
    private List<AttendanceRowResponse>  rows;
    private List<ReceptionGap>           receptionGaps;
    private List<ActionWithoutShift>     actionsWithoutShift;

    /**
     * Ca theo mẫu đã tới giờ bắt đầu, chưa hết, mà chưa lễ tân nào của ca đang trong ca — quản lý cần gọi người.
     */
    @Data
    @Builder
    public static class ReceptionGap {
        private LocalDate    shiftDate;
        private String       templateName;
        private LocalTime    startTime;
        private LocalTime    endTime;
        private long         minutesSinceStart;
        /** Lễ tân được xếp vào ca này mà chưa vào ca — rỗng là ca không xếp lễ tân nào. */
        private List<String> receptionistNames;
    }

    /** Lễ tân đổi trạng thái phòng lúc không trong ca — vẫn cho làm (chốt 01/11/2026), chỉ báo quản lý. */
    @Data
    @Builder
    public static class ActionWithoutShift {
        private String        staffName;
        private String        roomNumber;
        private RoomStatus    fromStatus;
        private RoomStatus    toStatus;
        private LocalDateTime changedAt;
    }
}
