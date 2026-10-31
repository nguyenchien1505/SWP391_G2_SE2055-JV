package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Luật khung giờ chấm công — BR Milestone 2 (chốt 31/10/2026). Thuần, không đọc đồng hồ: bên gọi truyền
 * {@code now} theo múi giờ của khách sạn có ca, nên test được mọi mốc mà không cần giả đồng hồ.
 *
 * <ul>
 *   <li>Vào ca: từ 30 phút trước giờ bắt đầu tới TRƯỚC giờ kết thúc. Hết ca mà chưa vào là «Vắng».</li>
 *   <li>Kết thúc ca: từ lúc vào ca tới hết ca + 4 giờ (cho tăng ca). Quá mốc đó là «Quên check-out», chỉ
 *       Manager nhập giờ ra.</li>
 *   <li>Ca qua đêm kết thúc vào hôm sau ngày bắt đầu (BR-SCH-03).</li>
 * </ul>
 *
 * <p>Đi muộn / về sớm chỉ tính ra để xem, không chặn và không phạt.
 */
public final class AttendanceRules {

    /** Được vào ca sớm nhất bao lâu trước giờ bắt đầu. */
    public static final Duration CHECK_IN_EARLY = Duration.ofMinutes(30);

    /** Được kết thúc ca muộn nhất bao lâu sau giờ kết thúc. */
    public static final Duration CHECK_OUT_GRACE = Duration.ofHours(4);

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private AttendanceRules() {}

    public static LocalDateTime startOf(Shift shift) {
        return LocalDateTime.of(shift.getShiftDate(), shift.getStartTime());
    }

    /** Ca qua đêm kết thúc vào ngày hôm sau ngày bắt đầu. */
    public static LocalDateTime endOf(Shift shift) {
        LocalDateTime end = LocalDateTime.of(shift.getShiftDate(), shift.getEndTime());
        return shift.isOvernight() ? end.plusDays(1) : end;
    }

    public static LocalDateTime checkInOpensAt(Shift shift) {
        return startOf(shift).minus(CHECK_IN_EARLY);
    }

    public static LocalDateTime checkOutClosesAt(Shift shift) {
        return endOf(shift).plus(CHECK_OUT_GRACE);
    }

    /** Ca đã hết giờ — dữ liệu chấm công của nó (kể cả «Vắng») là lịch sử. */
    public static boolean hasEnded(Shift shift, LocalDateTime now) {
        return !now.isBefore(endOf(shift));
    }

    public static void assertCanCheckIn(Shift shift, LocalDateTime now) {
        if (shift.isCheckedIn()) {
            throw new BusinessException("Ca này đã được check-in.");
        }
        if (now.isBefore(checkInOpensAt(shift))) {
            throw new BusinessException("Chưa tới giờ vào ca — được vào ca từ "
                + checkInOpensAt(shift).format(CLOCK) + ".");
        }
        if (hasEnded(shift, now)) {
            throw new BusinessException("Ca đã kết thúc nên không vào ca được nữa. "
                + "Nếu bạn có đi làm, nhờ quản lý nhập bù giờ vào ca.");
        }
    }

    public static void assertCanCheckOut(Shift shift, LocalDateTime now) {
        if (!shift.isCheckedIn()) {
            throw new BusinessException("Phải check-in trước khi check-out.");
        }
        if (shift.getCheckOutAt() != null) {
            throw new BusinessException("Ca này đã được check-out.");
        }
        if (now.isAfter(checkOutClosesAt(shift))) {
            throw new BusinessException("Đã quá hạn kết thúc ca (" + checkOutClosesAt(shift).format(CLOCK)
                + ") — nhờ quản lý nhập giờ ra ca.");
        }
    }

    /**
     * Giờ Manager nhập bù / sửa: vào ca trong [mở vào ca, hết ca], ra ca sau giờ vào và không quá hạn kết
     * thúc ca, cả hai không ở tương lai.
     *
     * @param checkIn  giờ vào sau khi sửa; {@code null} là ca vẫn chưa có giờ vào
     * @param checkOut giờ ra sau khi sửa; {@code null} là ca chưa có giờ ra
     */
    public static void assertManagerTimes(Shift shift, LocalDateTime checkIn, LocalDateTime checkOut,
                                          LocalDateTime now) {
        if (checkIn == null) {
            throw new BusinessException("Ca chưa có giờ vào — nhập giờ vào ca trước giờ ra.");
        }
        if (checkIn.isBefore(checkInOpensAt(shift)) || checkIn.isAfter(endOf(shift))) {
            throw new BusinessException("Giờ vào ca phải trong khoảng " + checkInOpensAt(shift).format(CLOCK)
                + " – " + endOf(shift).format(CLOCK) + ".");
        }
        if (checkIn.isAfter(now)) {
            throw new BusinessException("Giờ vào ca không được ở tương lai.");
        }
        if (checkOut == null) {
            return;
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BusinessException("Giờ ra ca phải sau giờ vào ca.");
        }
        if (checkOut.isAfter(checkOutClosesAt(shift))) {
            throw new BusinessException("Giờ ra ca muộn nhất là " + checkOutClosesAt(shift).format(CLOCK) + ".");
        }
        if (checkOut.isAfter(now)) {
            throw new BusinessException("Giờ ra ca không được ở tương lai.");
        }
    }

    /**
     * @param onLeave ngày ca nằm trong một đơn nghỉ đã duyệt của người làm ca
     * @return {@code null} với ca chưa có người — chỗ trống không có chấm công
     */
    public static AttendanceStatus status(Shift shift, boolean onLeave, LocalDateTime now) {
        if (!shift.isAssigned()) {
            return null;
        }
        if (shift.isCheckedIn()) {
            if (shift.getCheckOutAt() != null) {
                return AttendanceStatus.COMPLETED;
            }
            return now.isAfter(checkOutClosesAt(shift)) ? AttendanceStatus.MISSED_CHECK_OUT : AttendanceStatus.WORKING;
        }
        if (onLeave) {
            return AttendanceStatus.ON_LEAVE;
        }
        if (now.isBefore(checkInOpensAt(shift))) {
            return AttendanceStatus.UPCOMING;
        }
        return hasEnded(shift, now) ? AttendanceStatus.ABSENT : AttendanceStatus.OPEN;
    }

    /** Số phút vào ca sau giờ bắt đầu; {@code null} khi chưa vào ca. */
    public static Long lateMinutes(Shift shift) {
        if (shift.getCheckInAt() == null) {
            return null;
        }
        return Math.max(0, Duration.between(startOf(shift), shift.getCheckInAt()).toMinutes());
    }

    /** Số phút kết thúc ca trước giờ kết thúc; {@code null} khi chưa kết thúc ca. */
    public static Long earlyLeaveMinutes(Shift shift) {
        if (shift.getCheckOutAt() == null) {
            return null;
        }
        return Math.max(0, Duration.between(shift.getCheckOutAt(), endOf(shift)).toMinutes());
    }
}
