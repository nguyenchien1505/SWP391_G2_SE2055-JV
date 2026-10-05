import { compareNatural } from './roomLabels';

/**
 * Tiện ích của bảng lịch dọn theo nhân viên — dùng chung cho CleaningDayBoard và AssignToStaffModal.
 */

/** Việc dọn lại sau khi kiểm tra không đạt (BR-HK-12). */
export const isRedo = (task) => task.createdSource === 'INSPECTION_FAILED';

/**
 * Thứ tự hàng chờ: dọn lại (phòng đã chờ hai lần) → dọn sau trả phòng (phòng trống chờ khách mới)
 * → dọn hằng ngày; cùng nhóm thì theo số phòng tự nhiên.
 */
export function compareQueue(a, b) {
  const rank = (task) => (isRedo(task) ? 0 : task.taskType === 'CHECKOUT' ? 1 : 2);
  return rank(a) - rank(b) || compareNatural(a.roomNumber, b.roomNumber);
}

/**
 * Giao được việc này cho ngày `date` không. Ngày đã qua thì không; việc dọn sau trả phòng chỉ giao
 * cho hôm nay, vì giao là phòng sang «Đang dọn» ngay (Q5). Backend kiểm lại đúng hai điều này.
 */
export function canAssignOn(task, date, today) {
  return date >= today && (task.taskType !== 'CHECKOUT' || date === today);
}

/** Chữ viết tắt cho avatar — cùng cách các trang danh sách nhân sự đang làm. */
export function initials(name) {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '??';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}
