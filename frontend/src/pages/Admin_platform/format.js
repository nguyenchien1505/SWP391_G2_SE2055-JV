// Hàm định dạng dùng chung cho các trang khu Quản trị nền tảng.

/** Chuỗi ngày "YYYY-MM-DD" được hiểu theo giờ địa phương, tránh lệch ngày do múi giờ UTC. */
function toDate(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
}

export function formatDate(value) {
  return value ? toDate(value).toLocaleDateString('vi-VN') : '—';
}

export function formatDateTime(value) {
  return value ? new Date(value).toLocaleString('vi-VN') : '—';
}

/** Tiền VND là số nguyên (BR-SAAS-15). */
export function formatVnd(value) {
  return value == null ? '—' : `${Number(value).toLocaleString('vi-VN')} ₫`;
}

/** Hai chữ cái đầu để làm hình đại diện cho tên Tenant. */
export function initials(name) {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '??';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

/** Mã rút gọn của Tenant hiển thị cho người xem (6 ký tự đầu của UUID). */
export function shortId(id) {
  return (id ?? '').replace(/-/g, '').slice(0, 6).toUpperCase();
}
