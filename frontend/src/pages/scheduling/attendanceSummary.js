/** Tháng hiện tại "YYYY-MM" — khớp tham số `month` của backend. */
export function currentMonth(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

/** "2026-11" → "11/2026". */
export function formatMonth(month) {
  const [year, value] = String(month).split('-');
  return value && year ? `${value}/${year}` : month;
}

/** Cộng / trừ tháng: ("2026-01", -1) → "2025-12". */
export function addMonths(month, delta) {
  const [year, value] = month.split('-').map(Number);
  return currentMonth(new Date(year, value - 1 + delta, 1));
}

/** 455 phút → "7 giờ 35 phút". */
export function formatMinutes(minutes) {
  const total = Number(minutes ?? 0);
  const hours = Math.floor(total / 60);
  const rest = total % 60;
  if (hours === 0) return `${rest} phút`;
  return rest === 0 ? `${hours} giờ` : `${hours} giờ ${rest} phút`;
}
