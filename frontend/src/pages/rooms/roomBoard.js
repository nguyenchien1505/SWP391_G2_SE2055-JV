// Xử lý dữ liệu cho S-06 / S-15 Sơ đồ phòng — tách khỏi RoomBoardPage để trang chỉ còn lo phần
// hiển thị. Mọi thứ ở đây đều SUY RA từ dữ liệu thật (phòng + việc dọn đang mở), không có dữ
// liệu khách: hệ thống không lưu thông tin khách lưu trú (BR-ROOM-09, DM-06).

import { formatDate, formatShortDateTime, todayIso } from './format';
import { compareNatural } from './roomLabels';

/**
 * Gom phòng theo tầng, tầng và số phòng đều sắp kiểu "tự nhiên" (2 trước 10; tầng là chữ nên
 * G, M, B1 vẫn sắp được — BR-ROOM-05).
 * @returns [{ floor: '1', rooms: [...] }, …]
 */
export function groupByFloor(rooms) {
  const byFloor = new Map();
  for (const room of rooms) {
    if (!byFloor.has(room.floor)) byFloor.set(room.floor, []);
    byFloor.get(room.floor).push(room);
  }
  return [...byFloor.entries()]
    .sort(([a], [b]) => compareNatural(a, b))
    .map(([floor, list]) => ({
      floor,
      rooms: [...list].sort((a, b) => compareNatural(a.roomNumber, b.roomNumber)),
    }));
}

/** Đếm theo trạng thái trên chính dữ liệu đã tải — sơ đồ đã có đủ phòng nên không gọi thêm API. */
export function countByStatus(rooms) {
  const counts = {};
  for (const room of rooms) counts[room.status] = (counts[room.status] ?? 0) + 1;
  return counts;
}

/** Tên các loại phòng đang có trong khách sạn — ô lọc lấy từ dữ liệu, không gọi API danh mục
 *  (nhân viên gọi `/organization/room-types` sẽ nhận 403). */
export function roomTypeNamesOf(rooms) {
  const names = new Set(rooms.map((room) => room.roomTypeName).filter(Boolean));
  return [...names].sort((a, b) => a.localeCompare(b, 'vi'));
}

/**
 * Phòng có khớp bộ lọc không. Tìm kiếm chỉ theo SỐ PHÒNG — không có tên khách hay mã đặt phòng
 * để tìm (BR-OUT-01: đặt phòng nằm ngoài phạm vi).
 */
export function matchesBoardFilter(room, { status, floor, roomType, search }) {
  if (status && room.status !== status) return false;
  if (floor && room.floor !== floor) return false;
  if (roomType && room.roomTypeName !== roomType) return false;
  const query = search.trim().toLowerCase();
  return !query || String(room.roomNumber).toLowerCase().includes(query);
}

/**
 * Việc dọn ĐANG MỞ theo phòng: { [roomId]: task[] }, việc dọn sau trả phòng đứng trước.
 * Mỗi phòng tối đa 1 việc mở cho mỗi loại (BR-HK-11), nên mỗi mảng dài nhất 2 phần tử.
 */
export function indexOpenTasks(tasks) {
  const byRoom = {};
  for (const task of tasks) {
    (byRoom[task.roomId] ??= []).push(task);
  }
  for (const list of Object.values(byRoom)) {
    list.sort((a, b) => (a.taskType === 'CHECKOUT' ? -1 : 0) - (b.taskType === 'CHECKOUT' ? -1 : 0));
  }
  return byRoom;
}

/** BR-HK-04: hết ca chưa xong thì việc TỒN ĐỌNG chứ không tự hủy — phải nhìn ra ngay. */
export function isOverdue(task) {
  return task?.status === 'IN_PROGRESS' && Boolean(task.assignedDate) && task.assignedDate < todayIso();
}

/**
 * Một dòng ngắn dưới thẻ phòng, lấy từ việc dọn đang mở của phòng đó. Chỉ Quản lý chi nhánh có
 * dữ liệu việc dọn (nhân viên gọi API việc dọn chỉ thấy việc của chính mình), nên với vai trò
 * khác `tasks` rỗng và chỉ còn lý do khóa phòng.
 *
 * @returns {{ text: string, attention: boolean } | null} `attention` = cần Quản lý xử lý
 */
export function roomCardHint(room, tasks = [], staffNames = {}) {
  if (room.status === 'UNAVAILABLE') {
    return room.unavailableReason ? { text: room.unavailableReason, attention: false } : null;
  }

  const checkout = tasks.find((task) => task.taskType === 'CHECKOUT');
  const stayover = tasks.find((task) => task.taskType === 'STAYOVER');
  const nameOf = (task) => staffNames[task.assignedStaffId] ?? 'Nhân viên đã nghỉ';
  const join = (...parts) => parts.filter(Boolean).join(' · ');

  switch (room.status) {
    case 'DIRTY':
      if (checkout?.status !== 'UNASSIGNED') return null;
      return {
        text: checkout.createdSource === 'INSPECTION_FAILED' ? 'Dọn lại · chưa phân công' : 'Chưa phân công',
        attention: true,
      };
    case 'CLEANING':
      if (checkout?.status !== 'IN_PROGRESS') return null;
      return isOverdue(checkout)
        ? { text: join(nameOf(checkout), `tồn đọng từ ${formatDate(checkout.assignedDate)}`), attention: true }
        : { text: join(nameOf(checkout), checkout.assignedAt && `từ ${formatShortDateTime(checkout.assignedAt)}`), attention: false };
    case 'INSPECTION':
      if (checkout?.status !== 'PENDING_INSPECTION') return null;
      return {
        text: join(checkout.completedAt && `Xong ${formatShortDateTime(checkout.completedAt)}`, nameOf(checkout)),
        attention: true,
      };
    case 'OCCUPIED':
      if (!stayover) return null;
      return stayover.status === 'UNASSIGNED'
        ? { text: 'Dọn hằng ngày: chưa phân công', attention: true }
        : { text: `Dọn hằng ngày: ${nameOf(stayover)}`, attention: isOverdue(stayover) };
    default:
      return null;
  }
}

/**
 * Vì sao không có nút đổi trạng thái nào — design.md mục 1: bị chặn thì phải nói rõ vì sao, không
 * để người dùng bấm thử rồi nhận lỗi. Câu này CHỈ giải thích; nút nào hiện vẫn do backend quyết
 * định qua `room.allowedTargets`.
 */
export function whyNoRoomActions(room, { isDirector, isManager, isReception }) {
  if (isDirector) {
    return 'Giám đốc theo dõi sơ đồ; thao tác trên phòng do Quản lý chi nhánh và Lễ tân thực hiện.';
  }
  if (isManager) {
    return room.status === 'OCCUPIED'
      ? 'Phòng đang có khách nên chưa khóa được. Nhận / trả phòng do Lễ tân thực hiện.'
      : 'Không có thao tác đổi trạng thái nào cho phòng này.';
  }
  if (isReception) {
    if (['DIRTY', 'CLEANING', 'INSPECTION'].includes(room.status)) {
      return 'Phòng đang trong quy trình dọn. Nhận khách được khi phòng trở lại «Trống / Sẵn sàng».';
    }
    if (room.status === 'UNAVAILABLE') {
      return 'Phòng đang bị khóa. Quản lý chi nhánh mở khóa thì mới nhận khách được.';
    }
    return 'Không có thao tác nào cho phòng này.';
  }
  return 'Bạn chỉ xem được trạng thái phòng. Đổi trạng thái do Lễ tân và Quản lý chi nhánh thực hiện.';
}
