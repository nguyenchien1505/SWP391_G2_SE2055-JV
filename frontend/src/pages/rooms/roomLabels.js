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

// ── Lịch dọn phòng (F5) — BR-HK-01..12 ─────────────────────────────────────
// Cùng nguyên tắc với trạng thái phòng: backend chỉ trả mã enum, chữ hiển thị nằm ở đây.

/** Thứ tự các cột trên bảng lịch dọn: đi theo vòng đời một việc dọn. */
export const TASK_STATUS_ORDER = ['UNASSIGNED', 'IN_PROGRESS', 'PENDING_INSPECTION'];

export const TASK_STATUS = {
  UNASSIGNED: { label: 'Chưa phân công', tone: 'dirty' },
  IN_PROGRESS: { label: 'Đang làm', tone: 'cleaning' },
  // Chỉ việc dọn sau check-out mới đi qua bước này (BR-HK-06).
  PENDING_INSPECTION: { label: 'Chờ kiểm tra', tone: 'inspection' },
  COMPLETED: { label: 'Đã xong', tone: 'available' },
  CANCELLED: { label: 'Đã hủy', tone: 'unavailable' },
};

export function taskStatusMeta(status) {
  return TASK_STATUS[status] ?? { label: status ?? '—', tone: 'unavailable' };
}

/** Gọi theo việc chứ không theo mã: người dùng không cần biết "CHECKOUT" là gì. */
export const TASK_TYPE = {
  CHECKOUT: 'Dọn sau trả phòng',
  STAYOVER: 'Dọn hằng ngày',
};

export const taskTypeLabel = (type) => TASK_TYPE[type] ?? type ?? '—';

/** BR-HK-12: vì sao việc này tồn tại — Quản lý cần biết để xếp thứ tự ưu tiên. */
export const TASK_CREATED_SOURCE = {
  CHECKOUT_AUTO: 'Tự sinh khi khách trả phòng',
  MANAGER_STAYOVER: 'Quản lý tạo cho khách đang ở',
  INSPECTION_FAILED: 'Dọn lại — kiểm tra không đạt',
};

export const taskSourceLabel = (source) => TASK_CREATED_SOURCE[source] ?? source ?? '—';

/** BR-HK-07 / BR-SCH-24: vì sao việc này quay lại hàng chờ. */
export const UNASSIGNED_REASON = {
  TRANSFER: 'Người làm được điều chuyển',
  TERMINATION: 'Người làm đã nghỉ việc',
  LEAVE_APPROVED: 'Người làm được duyệt nghỉ',
  MANAGER_MANUAL: 'Quản lý gỡ người',
};

export const unassignedReasonLabel = (reason) => UNASSIGNED_REASON[reason] ?? reason ?? null;

/** BR-HK-09, BR-HK-10: vì sao việc này bị hủy. */
export const TASK_CANCEL_REASON = {
  ROOM_UNAVAILABLE: 'Phòng chuyển sang Không khả dụng',
  GUEST_CHECKED_OUT: 'Khách đã trả phòng',
  MANAGER_MANUAL: 'Quản lý hủy',
};

export const taskCancelReasonLabel = (reason) => TASK_CANCEL_REASON[reason] ?? reason ?? null;

/**
 * So sánh tầng / số phòng: tầng là TEXT (G, M, B1 — BR-ROOM-05) nên so kiểu "tự nhiên"
 * để "2" đứng trước "10", thay vì so chuỗi thuần.
 */
export function compareNatural(a, b) {
  return String(a ?? '').localeCompare(String(b ?? ''), 'vi', { numeric: true });
}
