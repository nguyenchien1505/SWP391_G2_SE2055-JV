import { hhmm, timeRange } from './scheduleFormat';

/**
 * Hàng của lịch MỘT người theo ca — dùng chung cho «Lịch cá nhân» và «Lịch dọn của tôi», cùng cách chia
 * với bảng xếp lịch của quản lý: mỗi mẫu ca một hàng (xếp theo giờ bắt đầu), ca tự nhập giờ gom vào hàng
 * «Ca giờ khác». Tên mẫu lấy từ `sourceTemplateName` backend trả kèm ca, vì nhân viên không đọc được
 * danh mục mẫu ca.
 */

/** Hàng của các ca tự nhập giờ (không theo mẫu). */
export const OTHER_SHIFT_ROW = 'OTHER';

export const rowIdOfShift = (shift) => shift.sourceTemplateId ?? OTHER_SHIFT_ROW;

/** Hàng chứa một ca: ca theo mẫu đứng tên mẫu kèm khung giờ, ca tự nhập giờ vào «Ca giờ khác». */
export function shiftRowOf(shift) {
  if (!shift.sourceTemplateId) {
    return { id: OTHER_SHIFT_ROW, name: 'Ca giờ khác', timeLabel: 'Ca tự nhập giờ', start: null };
  }
  return {
    id: shift.sourceTemplateId,
    name: shift.sourceTemplateName ?? 'Ca theo mẫu',
    timeLabel: `${timeRange(shift.startTime, shift.endTime)}${shift.overnight ? ' (qua đêm)' : ''}`,
    start: hhmm(shift.startTime),
  };
}

/** Xếp các ca trong cùng một ô theo giờ bắt đầu. */
export const compareShiftStart = (a, b) => hhmm(a.startTime).localeCompare(hhmm(b.startTime));

/**
 * Ca theo mẫu theo giờ bắt đầu, rồi «Ca giờ khác». `rank` cho bên dùng đưa thêm hàng phụ xuống cuối
 * (ví dụ hàng «Ngoài ca» của lịch dọn).
 */
export function sortShiftRows(rows, rank = (row) => (row.id === OTHER_SHIFT_ROW ? 1 : 0)) {
  return [...rows].sort((a, b) => rank(a) - rank(b) || (a.start ?? '').localeCompare(b.start ?? ''));
}

/**
 * Chia ca của một người vào các ô (hàng ca, ngày).
 *
 * @param shifts ca của người đó trong tuần
 * @returns {{ rows: { id, name, timeLabel, start }[], cellOf: (rowId, day) => object[] }}
 */
export function buildPersonalShiftBoard(shifts) {
  const rows = new Map();
  const cells = new Map();
  for (const shift of shifts) {
    const row = shiftRowOf(shift);
    if (!rows.has(row.id)) rows.set(row.id, row);
    const key = `${row.id}|${shift.shiftDate}`;
    if (!cells.has(key)) cells.set(key, []);
    cells.get(key).push(shift);
  }
  for (const list of cells.values()) list.sort(compareShiftStart);

  return {
    rows: sortShiftRows(rows.values()),
    cellOf: (rowId, day) => cells.get(`${rowId}|${day}`) ?? [],
  };
}
