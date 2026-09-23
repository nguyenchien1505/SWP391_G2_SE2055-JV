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
 * Nguồn của một bước chuyển trạng thái — cột "Người/nguồn thực hiện" của BR-ROOM-02, hiện trên
 * S-05 Lịch sử trạng thái. SYSTEM không có người thực hiện nên màn hình ghi "Hệ thống".
 */
export const CHANGE_SOURCE = {
  RECEPTION: 'Lễ tân',
  HOUSEKEEPING: 'Nhân viên dọn',
  MANAGER: 'Quản lý',
  SYSTEM: 'Hệ thống',
};

export function changeSourceLabel(source) {
  return CHANGE_SOURCE[source] ?? source ?? '—';
}

/**
 * Nhãn của một THAO TÁC, khóa theo CẶP `từ→đến` chứ không theo trạng thái đích.
 *
 * Lý do phải là cặp: cùng đích "Đang sử dụng" nhưng từ "Trống" là khách vãng lai đến quầy
 * (Check-in), còn từ "Đã đặt" là khách đặt trước đã tới (Khách đã đến) — hai việc khác nhau với
 * người dùng dù backend chỉ thấy một bước chuyển. Cùng lý do với "Hủy đặt / Không đến": hủy và
 * no-show là một bước chuyển, phân biệt bằng lý do ghi vào lịch sử.
 *
 * Danh sách nút thật sự hiện ra vẫn do `room.allowedTargets` của backend quyết định — bảng này
 * chỉ đặt tên cho chúng.
 */
export const ACTION_LABEL = {
  'AVAILABLE→RESERVED': 'Đặt trước',
  'AVAILABLE→OCCUPIED': 'Check-in',
  'RESERVED→OCCUPIED': 'Khách đã đến',
  'RESERVED→AVAILABLE': 'Hủy đặt / Không đến',
  'OCCUPIED→DIRTY': 'Check-out',
};

/** Khóa tra cứu cho {@link ACTION_LABEL} và bảng thao tác trong `roomActions.js`. */
export function transitionKey(from, to) {
  return `${from}→${to}`;
}

/**
 * So sánh tầng / số phòng: tầng là TEXT (G, M, B1 — BR-ROOM-05) nên so kiểu "tự nhiên"
 * để "2" đứng trước "10", thay vì so chuỗi thuần.
 */
export function compareNatural(a, b) {
  return String(a ?? '').localeCompare(String(b ?? ''), 'vi', { numeric: true });
}
