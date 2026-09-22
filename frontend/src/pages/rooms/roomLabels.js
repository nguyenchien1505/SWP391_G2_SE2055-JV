// Nhãn tiếng Việt + tông màu của trạng thái phòng — BR-ROOM-01 (đúng 7 trạng thái).
//
// Backend chỉ trả mã enum (AVAILABLE, DIRTY…); nhãn hiển thị nằm ở đây, giống cách
// TenantStatusBadge.jsx đang làm cho trạng thái Tenant. Màu theo design.md mục 3 — lớp CSS
// "room-tone--<tone>" khai báo trong rooms.css.

/**
 * Thứ tự hiển thị trên thẻ số liệu / chip lọc: đi theo vòng đời vận hành của một phòng
 * (sẵn sàng → có khách → dọn → kiểm tra), không theo thứ tự khai báo trong enum Java.
 */
export const ROOM_STATUS_ORDER = [
  'AVAILABLE',
  'RESERVED',
  'OCCUPIED',
  'DIRTY',
  'CLEANING',
  'INSPECTION',
  'UNAVAILABLE',
];

export const ROOM_STATUS = {
  AVAILABLE: { label: 'Trống / Sẵn sàng', tone: 'available' },
  // "Đã đặt" là cờ giữ phòng TRONG NGÀY, không có dữ liệu khách (BR-ROOM-01).
  RESERVED: { label: 'Đã đặt', tone: 'reserved' },
  OCCUPIED: { label: 'Đang sử dụng', tone: 'occupied' },
  DIRTY: { label: 'Chờ dọn', tone: 'dirty' },
  CLEANING: { label: 'Đang dọn', tone: 'cleaning' },
  INSPECTION: { label: 'Chờ kiểm tra', tone: 'inspection' },
  // Gộp Bảo trì + Khóa phòng, luôn kèm lý do (BR-ROOM-07).
  UNAVAILABLE: { label: 'Không khả dụng', tone: 'unavailable' },
};

/** Mã lạ (backend thêm trạng thái mà FE chưa biết) vẫn hiện được, không làm vỡ màn hình. */
export function roomStatusMeta(status) {
  return ROOM_STATUS[status] ?? { label: status ?? '—', tone: 'unavailable' };
}

/**
 * So sánh tầng / số phòng: tầng là TEXT (G, M, B1 — BR-ROOM-05) nên so kiểu "tự nhiên"
 * để "2" đứng trước "10", thay vì so chuỗi thuần.
 */
export function compareNatural(a, b) {
  return String(a ?? '').localeCompare(String(b ?? ''), 'vi', { numeric: true });
}
