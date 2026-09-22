// Thao tác đổi trạng thái phòng trên màn hình — S-04 Chi tiết phòng và S-06 Sơ đồ phòng.
//
// Nút nào được hiện do BACKEND quyết định qua `room.allowedTargets` (F2): FE KHÔNG chép lại ma
// trận BR-ROOM-02 hay bảng phân quyền. File này chỉ gom các trạng thái đích thành THAO TÁC có
// nghĩa với người dùng. F4 thêm các thao tác của Lễ tân (đặt phòng, check-in, check-out) vào đây.

import { roomStatusMeta } from './roomLabels';

/** Manager đưa phòng vào Không khả dụng — BR-ROOM-03, bắt buộc lý do (BR-ROOM-07). */
export const LOCK = 'lock';

/** Manager đưa phòng ra khỏi Không khả dụng, tự chọn Chờ dọn hoặc Sẵn sàng — BR-ROOM-03. */
export const UNLOCK = 'unlock';

/**
 * Các thao tác người đang đăng nhập làm được trên phòng này.
 * @returns [{ key, label, danger }] — rỗng nghĩa là không có nút nào (Giám đốc, Dọn dẹp…)
 */
export function roomActionsFor(room) {
  const targets = room?.allowedTargets ?? [];
  if (room?.status === 'UNAVAILABLE') {
    return targets.length > 0 ? [{ key: UNLOCK, label: 'Mở khóa phòng', danger: false }] : [];
  }
  return targets.includes('UNAVAILABLE') ? [{ key: LOCK, label: 'Khóa phòng', danger: true }] : [];
}

/** Câu báo thành công sau khi đổi trạng thái, nêu rõ hệ quả lên task dọn (BR-HK-09). */
export function statusChangedMessage(before, after) {
  const number = after.roomNumber;
  if (after.status === 'UNAVAILABLE') {
    return `Đã khóa phòng ${number}. Các task dọn đang mở của phòng (nếu có) đã được hủy.`;
  }
  if (before.status === 'UNAVAILABLE' && after.status === 'DIRTY') {
    return `Đã mở khóa phòng ${number} về «Chờ dọn». Hệ thống đã tạo task dọn phòng chờ phân công.`;
  }
  return `Phòng ${number} đã chuyển sang «${roomStatusMeta(after.status).label}».`;
}
