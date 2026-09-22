// Định dạng dùng chung cho các màn hình phòng — design.md mục 6:
// ngày dd/MM/yyyy, giờ HH:mm 24 giờ.
//
// Không dùng toLocaleString('vi-VN') vì nó đặt GIỜ TRƯỚC NGÀY ("13:05:12 22/9/2026") và bỏ
// số 0 ở đầu — sai quy ước hiển thị của dự án.

const pad = (n) => String(n).padStart(2, '0');

/** "2026-09-22T13:05:00" (LocalDateTime từ backend, không có múi giờ) → "22/09/2026 13:05". */
export function formatDateTime(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Giờ:phút hiện tại — dùng cho dòng "Cập nhật lúc HH:mm" của sơ đồ phòng. */
export function formatClock(date = new Date()) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
