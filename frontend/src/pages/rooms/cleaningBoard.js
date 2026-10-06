import { compareNatural } from './roomLabels';

/**
 * Tiện ích của bảng lịch dọn theo nhân viên — dùng chung cho CleaningDayBoard và AssignToStaffModal.
 */

/** Việc dọn lại sau khi kiểm tra không đạt (BR-HK-12). */
export const isRedo = (task) => task.createdSource === 'INSPECTION_FAILED';

/**
 * Thứ tự hàng chờ: dọn lại (phòng đã chờ hai lần) trước, rồi theo số phòng tự nhiên.
 */
export function compareQueue(a, b) {
  const rank = (task) => (isRedo(task) ? 0 : 1);
  return rank(a) - rank(b) || compareNatural(a.roomNumber, b.roomNumber);
}

/**
 * Giao được việc cho ngày `date` không: chỉ HÔM NAY — giao là phòng sang «Đang dọn» ngay (Q5), nên
 * không giao trước cho ngày mai, và ngày đã qua thì chỉ xem. Backend kiểm lại đúng điều này.
 */
export function canAssignOn(date, today) {
  return date === today;
}

/** Chữ viết tắt cho avatar — cùng cách các trang danh sách nhân sự đang làm. */
export function initials(name) {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '??';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}
