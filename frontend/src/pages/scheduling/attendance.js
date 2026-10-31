import { formatClock } from '../rooms/format';

/**
 * Tình trạng chấm công (chấm công M2) — backend suy ra và gửi kèm mỗi ca (`attendanceStatus`), giao diện
 * chỉ đặt tên và tô màu, không tự tính lại khung giờ.
 */
export const ATTENDANCE_LABELS = {
  UPCOMING: 'Sắp tới',
  OPEN: 'Chưa vào ca',
  WORKING: 'Đang làm',
  COMPLETED: 'Đã xong',
  ABSENT: 'Vắng',
  MISSED_CHECK_OUT: 'Quên kết thúc ca',
  ON_LEAVE: 'Nghỉ phép',
};

/** Tông màu `tt-pill--*` của từng tình trạng. */
export const ATTENDANCE_TONES = {
  UPCOMING: 'upcoming',
  OPEN: 'upcoming',
  WORKING: 'in',
  COMPLETED: 'out',
  ABSENT: 'absent',
  MISSED_CHECK_OUT: 'warn',
  ON_LEAVE: 'leave',
};

/** Giờ vào / ra do ai ghi. */
export const ATTENDANCE_METHOD_LABELS = {
  SELF: 'Tự bấm',
  MANAGER: 'Quản lý nhập',
};

/**
 * Các nhãn chấm công của một ca: giờ vào / ra đã có, đi muộn / về sớm (chỉ để xem, không phạt), và tình
 * trạng cần chú ý (Vắng, Quên kết thúc ca, Nghỉ phép, Sắp tới).
 */
export function attendancePills(shift) {
  const pills = [];
  const status = shift.attendanceStatus;
  if (shift.checkInAt) {
    pills.push({ tone: 'in', text: `Đã vào ca ${formatClock(new Date(shift.checkInAt))}` });
    if (shift.lateMinutes > 0) pills.push({ tone: 'warn', text: `Muộn ${shift.lateMinutes} phút` });
  }
  if (shift.checkOutAt) {
    pills.push({ tone: 'out', text: `Đã ra ca ${formatClock(new Date(shift.checkOutAt))}` });
    if (shift.earlyLeaveMinutes > 0) pills.push({ tone: 'warn', text: `Về sớm ${shift.earlyLeaveMinutes} phút` });
  }
  if (['ABSENT', 'MISSED_CHECK_OUT', 'ON_LEAVE', 'UPCOMING'].includes(status)) {
    pills.push({ tone: ATTENDANCE_TONES[status], text: ATTENDANCE_LABELS[status] });
  }
  return pills;
}

/** Trạng thái đơn điều chỉnh công — cùng bộ tông `tt-pill--*`. */
export const ADJUSTMENT_STATUS = {
  PENDING: { label: 'Chờ duyệt', tone: 'warn' },
  APPROVED: { label: 'Đã duyệt', tone: 'in' },
  REJECTED: { label: 'Từ chối', tone: 'absent' },
  CANCELLED: { label: 'Đã hủy', tone: 'leave' },
};

export const adjustmentStatusMeta = (status) => ADJUSTMENT_STATUS[status] ?? { label: status ?? '—', tone: 'leave' };
