import { hhmm } from './scheduleFormat';
import { hasReceptionist } from './schedulePeople';

/**
 * Ô của bảng xếp lịch: một HÀNG (mẫu ca, hoặc "Ca giờ khác") trong một NGÀY. Dùng chung cho bảng tuần
 * và hộp thoại chi tiết ô, để hai nơi luôn chia ca giống hệt nhau.
 */

/** Hàng của các ca không theo mẫu nào: ca tự nhập giờ. */
export const OTHER_ROW = 'OTHER';

/**
 * Mỗi mẫu một hàng — kể cả mẫu đã tắt hay không còn thuộc bộ mẫu chi nhánh đang dùng, khi tuần đó còn
 * ca của nó: backend vẫn coi đó là ca theo mẫu (quy tắc lễ tân vẫn áp). Sửa tay giờ của một ca thì
 * backend bỏ liên kết mẫu, nên ca đó chuyển sang hàng "Ca giờ khác" — đúng với giờ thật của nó.
 */
export function rowIdOf(shift) {
  return shift.sourceTemplateId ?? OTHER_ROW;
}

export const cellKey = (rowId, date) => `${rowId}|${date}`;

/**
 * Map khóa ô → các ca trong ô: giờ trước (cho hàng "Ca giờ khác"), rồi người đã giao theo tên, chỗ
 * trống xuống cuối.
 */
export function groupByCell(shifts, peopleById) {
  const cells = new Map();
  for (const shift of shifts) {
    const key = cellKey(rowIdOf(shift), shift.shiftDate);
    if (!cells.has(key)) cells.set(key, []);
    cells.get(key).push(shift);
  }
  const nameOf = (shift) => peopleById.get(shift.staffId)?.fullName ?? '';
  for (const list of cells.values()) {
    list.sort((a, b) =>
      hhmm(a.startTime).localeCompare(hhmm(b.startTime))
      || Number(!a.staffId) - Number(!b.staffId)
      || nameOf(a).localeCompare(nameOf(b), 'vi'));
  }
  return cells;
}

/**
 * Ca theo mẫu đã có người mà chưa có lễ tân — chốt 05/10/2026: mỗi ca theo mẫu phải có ít nhất 1 lễ
 * tân. Hàng "Ca giờ khác" không bắt buộc (thường là ca hỗ trợ); chỗ trống không tính là người.
 */
export function lacksReceptionist(rowId, list, peopleById) {
  if (rowId === OTHER_ROW) return false;
  const staffed = list.filter((shift) => shift.staffId);
  return staffed.length > 0 && !hasReceptionist(staffed.map((shift) => peopleById.get(shift.staffId)));
}
