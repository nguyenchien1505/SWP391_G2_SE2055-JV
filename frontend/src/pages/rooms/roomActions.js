// Thao tác đổi trạng thái phòng trên màn hình — S-04 Chi tiết phòng và S-06/S-15 Sơ đồ phòng.
//
// Nút nào được hiện do BACKEND quyết định qua `room.allowedTargets` (F2): FE KHÔNG chép lại ma
// trận BR-ROOM-02 hay bảng phân quyền. File này chỉ làm hai việc trên tập đích đó:
//   1. đặt tên có nghĩa với người dùng (Đặt trước, Check-in, Check-out… — F4),
//   2. nói rõ mỗi thao tác cần bao nhiêu bước xác nhận (`flow`).
//
// Nhờ vậy khi backend mở thêm một bước chuyển cho vai trò nào đó, FE chỉ cần thêm một dòng vào
// bảng ACTIONS bên dưới.

import { ACTION_LABEL, roomStatusMeta, transitionKey } from './roomLabels';

/** Bấm là gọi API luôn — việc nhẹ, làm sai thì đổi lại được ngay (1–2 chạm, design.md mục 2). */
export const INSTANT = 'instant';

/** Cần một hộp thoại xác nhận trước: bấm nhầm không quay lại được. */
export const CONFIRM = 'confirm';

/** Cần chọn lý do rồi mới gửi; lý do đi vào lịch sử trạng thái. */
export const REASON = 'reason';

/** Khóa / mở khóa có màn riêng (`LockRoomModal`): chọn đích + lý do bắt buộc — F2. */
export const LOCK = 'lock';

/**
 * Bảng thao tác của Lễ tân, khóa theo cặp `từ→đến` (RM-08, RM-09, RM-10 — F4).
 * `targetStatus` là giá trị gửi lên `PATCH /rooms/{id}/status`.
 */
const RECEPTION_ACTIONS = {
  'AVAILABLE→RESERVED': {
    key: 'reserve',
    targetStatus: 'RESERVED',
    flow: INSTANT,
  },
  'AVAILABLE→OCCUPIED': {
    key: 'check-in',
    targetStatus: 'OCCUPIED',
    flow: INSTANT,
  },
  'RESERVED→OCCUPIED': {
    key: 'guest-arrived',
    targetStatus: 'OCCUPIED',
    flow: INSTANT,
  },
  'RESERVED→AVAILABLE': {
    key: 'cancel-reservation',
    targetStatus: 'AVAILABLE',
    flow: REASON,
    danger: true,
    // Hủy và no-show là CÙNG một bước chuyển, chỉ khác lý do — nên hỏi lý do thay vì tách 2 nút.
    prompt: {
      title: 'Bỏ giữ phòng',
      question: 'Vì sao bỏ giữ phòng này?',
      options: [
        { value: 'Khách hủy', hint: 'Khách chủ động báo hủy.' },
        { value: 'Khách không đến (no-show)', hint: 'Hết giờ giữ phòng mà khách không tới.' },
      ],
      confirmLabel: 'Bỏ giữ phòng',
    },
  },
  'OCCUPIED→DIRTY': {
    key: 'check-out',
    targetStatus: 'DIRTY',
    flow: CONFIRM,
    danger: true,
    prompt: {
      title: 'Xác nhận trả phòng?',
      message:
        'Phòng sẽ chuyển sang «Chờ dọn», hệ thống tự tạo một việc dọn phòng và hủy việc dọn '
        + 'hằng ngày đang mở (nếu có). Thao tác này không hoàn tác được.',
      confirmLabel: 'Check-out',
    },
  },
};

/**
 * Thứ tự hiện nút: việc hay dùng và nhẹ nhàng lên trước, việc không hoàn tác được xuống cuối —
 * để ngón tay không vô tình chạm trúng "Check-out".
 */
const ACTION_ORDER = [
  'AVAILABLE→RESERVED',
  'AVAILABLE→OCCUPIED',
  'RESERVED→OCCUPIED',
  'RESERVED→AVAILABLE',
  'OCCUPIED→DIRTY',
];

/** Manager đưa phòng vào Không khả dụng — BR-ROOM-03, bắt buộc lý do (BR-ROOM-07). */
const LOCK_ACTION = { key: LOCK, label: 'Khóa phòng', flow: LOCK, danger: true };

/** Manager đưa phòng ra khỏi Không khả dụng, tự chọn Chờ dọn hoặc Sẵn sàng — BR-ROOM-03. */
const UNLOCK_ACTION = { key: 'unlock', label: 'Mở khóa phòng', flow: LOCK, danger: false };

/**
 * Các thao tác người đang đăng nhập làm được trên phòng này.
 *
 * @returns [{ key, label, targetStatus, flow, danger, prompt }] — rỗng nghĩa là không có nút nào
 *          (ví dụ Giám đốc, nhân viên Dọn dẹp, hoặc phòng đang Chờ dọn với Lễ tân)
 */
export function roomActionsFor(room) {
  const from = room?.status;
  const targets = room?.allowedTargets ?? [];
  if (!from || targets.length === 0) {
    return [];
  }

  // Mở khóa gộp CẢ HAI đích (Chờ dọn / Sẵn sàng) vào một nút: hộp thoại mở khóa tự cho chọn đích,
  // nên hiện hai nút riêng sẽ thừa.
  if (from === 'UNAVAILABLE') {
    return [UNLOCK_ACTION];
  }

  const actions = ACTION_ORDER
    .filter((pair) => pair.startsWith(`${from}→`))
    .filter((pair) => targets.includes(RECEPTION_ACTIONS[pair].targetStatus))
    .map((pair) => ({ ...RECEPTION_ACTIONS[pair], label: ACTION_LABEL[pair] }));

  if (targets.includes('UNAVAILABLE')) {
    actions.push(LOCK_ACTION);
  }
  return actions;
}

/**
 * Câu báo thành công sau khi đổi trạng thái. Nêu luôn HỆ QUẢ mà người dùng không nhìn thấy trên
 * màn hình này (task dọn bị hủy / được sinh — BR-HK-01, BR-HK-09, BR-HK-10), vì đó là thứ hay
 * gây bất ngờ nhất.
 */
export function statusChangedMessage(before, after) {
  const number = after.roomNumber;
  const pair = transitionKey(before?.status, after.status);

  switch (pair) {
    case 'AVAILABLE→RESERVED':
      return `Đã giữ phòng ${number} cho khách.`;
    case 'AVAILABLE→OCCUPIED':
    case 'RESERVED→OCCUPIED':
      return `Khách đã nhận phòng ${number}.`;
    case 'RESERVED→AVAILABLE':
      return `Đã bỏ giữ phòng ${number}. Phòng trở lại «Trống / Sẵn sàng».`;
    case 'OCCUPIED→DIRTY':
      return `Đã trả phòng ${number}. Phòng chuyển sang «Chờ dọn» và đã có việc dọn phòng chờ phân công.`;
    default:
      break;
  }

  if (after.status === 'UNAVAILABLE') {
    return `Đã khóa phòng ${number}. Các task dọn đang mở của phòng (nếu có) đã được hủy.`;
  }
  if (before?.status === 'UNAVAILABLE' && after.status === 'DIRTY') {
    return `Đã mở khóa phòng ${number} về «Chờ dọn». Hệ thống đã tạo task dọn phòng chờ phân công.`;
  }
  return `Phòng ${number} đã chuyển sang «${roomStatusMeta(after.status).label}».`;
}
