// Đổi ca giữa hai nhân viên — nhãn trạng thái, cách ghi một ca và mốc thời gian (Milestone 2).
import { formatShortDateTime } from '../rooms/format';
import { WEEKDAY_SHORT, formatDayMonth, hhmm, timeRange, weekdayIndex } from './scheduleFormat';

/** BR-SCH-23 — huy hiệu luôn có chữ kèm màu (design.md mục 3). */
export const SWAP_STATUS = {
  PENDING_PEER: { label: 'Chờ đồng nghiệp', badge: 'badge--orange' },
  PENDING_MANAGER: { label: 'Chờ quản lý duyệt', badge: 'badge--blue' },
  APPROVED: { label: 'Đã duyệt', badge: 'badge--green' },
  PEER_REJECTED: { label: 'Đồng nghiệp từ chối', badge: 'badge--red' },
  MANAGER_REJECTED: { label: 'Quản lý từ chối', badge: 'badge--red' },
  EXPIRED: { label: 'Tự hủy', badge: 'badge--grey' },
  CANCELLED: { label: 'Đã hủy', badge: 'badge--grey' },
};

export const PENDING_SWAP_STATUSES = ['PENDING_PEER', 'PENDING_MANAGER'];
export const CLOSED_SWAP_STATUSES = ['APPROVED', 'PEER_REJECTED', 'MANAGER_REJECTED', 'EXPIRED', 'CANCELLED'];

/** "T3 03/11 · Ca chiều 14:00 – 22:00"; ca tự nhập giờ chỉ còn khung giờ. */
export function swapShiftLabel(shift) {
  if (!shift) return 'Ca không còn';
  const day = `${WEEKDAY_SHORT[weekdayIndex(shift.shiftDate)]} ${formatDayMonth(shift.shiftDate)}`;
  const time = timeRange(shift.startTime, shift.endTime);
  const name = shift.sourceTemplateName ? `${shift.sourceTemplateName} ${time}` : time;
  return `${day} · ${name}${shift.overnight ? ' (qua đêm)' : ''}`;
}

/** Giờ bắt đầu ca theo giờ máy — người dùng ở Việt Nam; backend vẫn kiểm lại theo giờ Hà Nội. */
function shiftStart(shift) {
  const [year, month, day] = shift.shiftDate.split('-').map(Number);
  const [hours, minutes] = hhmm(shift.startTime).split(':').map(Number);
  return new Date(year, month - 1, day, hours, minutes);
}

/** BR-SCH-25: chỉ đổi được ca chưa check-in và chưa tới giờ bắt đầu. */
export function isSwappable(shift, now = new Date()) {
  return Boolean(shift) && !shift.checkInAt && shiftStart(shift) > now;
}

/** Bước gần nhất của một yêu cầu — dòng ghi chú dưới thẻ. */
export function swapProgress(request) {
  const at = formatShortDateTime(request.decidedAt);
  const manager = request.approverName ?? 'Quản lý';
  switch (request.status) {
    case 'PENDING_PEER':
      return `Hạn trả lời ${formatShortDateTime(request.expiresAt)}`;
    case 'PENDING_MANAGER':
      return `Đồng nghiệp đồng ý lúc ${formatShortDateTime(request.peerRespondedAt)}`;
    case 'APPROVED':
      return `${manager} duyệt lúc ${at}`;
    case 'MANAGER_REJECTED':
      return `${manager} từ chối lúc ${at}`;
    case 'PEER_REJECTED':
      return `Đồng nghiệp từ chối lúc ${at}`;
    case 'EXPIRED':
      // BR-SCH-11, BR-SCH-25 và ca bị quản lý sửa / gỡ người (chốt với nhóm).
      return `Tự hủy lúc ${at} — quá hạn trả lời, tới giờ vào ca hoặc ca đã thay đổi`;
    case 'CANCELLED':
      return `Người gửi hủy lúc ${at}`;
    default:
      return '';
  }
}
