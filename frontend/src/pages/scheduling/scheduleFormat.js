// Ngày, tuần và giờ cho các màn Lịch làm việc — design.md mục 6: ngày dd/MM/yyyy, giờ HH:mm 24 giờ.
//
// Ngày luôn đi dưới dạng chuỗi "YYYY-MM-DD" (khớp LocalDate của backend) và chỉ đổi sang Date theo
// giờ ĐỊA PHƯƠNG để cộng ngày — không dùng new Date("2026-10-05") vì chuỗi đó bị hiểu là nửa đêm UTC,
// lệch sang hôm trước ở múi giờ âm.

const pad = (n) => String(n).padStart(2, '0');

export const WEEKDAY_SHORT = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
export const WEEKDAY_LONG = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật'];

export function toIsoDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function parseIsoDate(iso) {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function addDays(iso, days) {
  const date = parseIsoDate(iso);
  date.setDate(date.getDate() + days);
  return toIsoDate(date);
}

/** 0 = Thứ Hai … 6 = Chủ Nhật — tuần lịch Thứ Hai đến Chủ Nhật (BR-SCH-13). */
export function weekdayIndex(iso) {
  return (parseIsoDate(iso).getDay() + 6) % 7;
}

/** Thứ Hai của tuần chứa ngày này. */
export function weekStartOf(iso) {
  return addDays(iso, -weekdayIndex(iso));
}

/** Bảy ngày Thứ Hai → Chủ Nhật bắt đầu từ `weekStart`. */
export function weekDays(weekStart) {
  return Array.from({ length: 7 }, (_, i) => addDays(weekStart, i));
}

/** Số tuần ISO 8601 (tuần chứa Thứ Năm đầu tiên của năm là tuần 1). */
export function isoWeekNumber(iso) {
  const [year, month, day] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  const thursday = new Date(date);
  thursday.setUTCDate(date.getUTCDate() + 3 - ((date.getUTCDay() + 6) % 7));
  const yearStart = new Date(Date.UTC(thursday.getUTCFullYear(), 0, 1));
  return Math.ceil(((thursday - yearStart) / 86400000 + 1) / 7);
}

/** "05/10" */
export function formatDayMonth(iso) {
  const [, month, day] = iso.split('-');
  return `${day}/${month}`;
}

/** "Thứ Hai, 05/10/2026" */
export function formatLongDate(iso) {
  const [year, month, day] = iso.split('-');
  return `${WEEKDAY_LONG[weekdayIndex(iso)]}, ${day}/${month}/${year}`;
}

// ── Giờ ca ──────────────────────────────────────────────────────────────────────

/** LocalTime của backend ("06:00:00" hoặc "06:00") → "06:00". */
export function hhmm(time) {
  return time ? String(time).slice(0, 5) : '';
}

function minutesOf(time) {
  const [hours, minutes] = hhmm(time).split(':').map(Number);
  return hours * 60 + minutes;
}

/**
 * Giờ kết thúc không lớn hơn giờ bắt đầu nghĩa là ca kết thúc vào hôm sau — cùng quy ước với
 * ShiftTimeUtils ở backend, nên 08:00–08:00 là ca 24 giờ chứ không phải ca 0 giờ.
 */
export function isOvernight(start, end) {
  return minutesOf(end) <= minutesOf(start);
}

export function durationHours(start, end) {
  const diff = minutesOf(end) - minutesOf(start);
  return (diff <= 0 ? diff + 24 * 60 : diff) / 60;
}

/** 8 → "8 giờ", 7.5 → "7,5 giờ" (BigDecimal của backend đến dưới dạng số hoặc chuỗi). */
export function formatHours(hours) {
  const value = Number(hours);
  if (!Number.isFinite(value)) return '—';
  return `${String(Math.round(value * 100) / 100).replace('.', ',')} giờ`;
}

/** "22:00 – 06:00" */
export function timeRange(start, end) {
  return `${hhmm(start)} – ${hhmm(end)}`;
}

/**
 * Nhóm màu theo GIỜ BẮT ĐẦU — dùng được cho cả ca theo mẫu lẫn ca tự do, khớp chú thích màu của
 * bản thiết kế (sáng xanh lá, chiều cam, đêm tím).
 */
export function shiftTone(start) {
  const hour = Math.floor(minutesOf(start) / 60);
  if (hour >= 4 && hour < 11) return 'morning';
  if (hour >= 11 && hour < 17) return 'afternoon';
  return 'night';
}

/** BR-SCH-24 — vì sao ca đang trống người. */
export const UNASSIGNED_REASON_LABEL = {
  TRANSFER: 'Gỡ do điều chuyển',
  TERMINATION: 'Gỡ do nghỉ việc',
  LEAVE_APPROVED: 'Gỡ do duyệt nghỉ',
  MANAGER_MANUAL: 'Quản lý gỡ',
};
