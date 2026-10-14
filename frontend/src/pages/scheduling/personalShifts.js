import { hhmm, timeRange } from './scheduleFormat';

/**
 * Hàng của lịch MỘT người theo ca — dùng chung cho «Lịch cá nhân» và «Lịch dọn của tôi», cùng cách chia
 * với bảng xếp lịch của quản lý: mỗi mẫu ca một hàng (xếp theo giờ bắt đầu), ca tự nhập giờ gom vào hàng
 * «Ca giờ khác».
 *
 * Mọi mẫu ca đang dùng của chi nhánh LUÔN có hàng, kể cả ca tuần đó người này không làm (chốt 12/10/2026)
 * — ô nào có ca / việc mới hiện khối. Mẫu cũ (đã tắt, hay không còn thuộc bộ chi nhánh đang dùng) và «Ca
 * giờ khác» chỉ có hàng khi tuần đó có ca của nó; tên mẫu cũ lấy từ `sourceTemplateName` backend trả kèm ca.
 */

/** Hàng của các ca tự nhập giờ (không theo mẫu). */
export const OTHER_SHIFT_ROW = 'OTHER';

export const rowIdOfShift = (shift) => shift.sourceTemplateId ?? OTHER_SHIFT_ROW;

/** "22:00 – 06:00 (qua đêm)" — nhãn giờ dưới tên hàng ca. */
const timeLabelOf = (start, end, overnight) => `${timeRange(start, end)}${overnight ? ' (qua đêm)' : ''}`;

/** Hàng của một mẫu ca đang dùng ở chi nhánh. */
export function templateRowOf(template) {
  return {
    id: template.id,
    name: template.name,
    timeLabel: timeLabelOf(template.startTime, template.endTime, template.overnight),
    start: hhmm(template.startTime),
  };
}

/** Hàng chứa một ca: ca theo mẫu đứng tên mẫu kèm khung giờ, ca tự nhập giờ vào «Ca giờ khác». */
export function shiftRowOf(shift) {
  if (!shift.sourceTemplateId) {
    return { id: OTHER_SHIFT_ROW, name: 'Ca giờ khác', timeLabel: 'Ca tự nhập giờ', start: null };
  }
  return {
    id: shift.sourceTemplateId,
    name: shift.sourceTemplateName ?? 'Ca theo mẫu',
    timeLabel: timeLabelOf(shift.startTime, shift.endTime, shift.overnight),
    start: hhmm(shift.startTime),
  };
}

/**
 * Hàng khởi đầu của bảng (id → hàng): MỌI mẫu ca đang dùng của chi nhánh, để ca nào cũng có hàng dù tuần
 * đó không có lịch. Bên dùng thêm tiếp hàng của mẫu cũ / «Ca giờ khác» khi gặp ca của nó.
 */
export function templateRows(templates = []) {
  return new Map(templates.map((template) => [template.id, templateRowOf(template)]));
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
 * @param shifts    ca của người đó trong tuần
 * @param templates mẫu ca đang dùng của chi nhánh — mỗi mẫu luôn có hàng
 * @returns {{ rows: { id, name, timeLabel, start }[], cellOf: (rowId, day) => object[] }}
 */
export function buildPersonalShiftBoard(shifts, templates = []) {
  const rows = templateRows(templates);
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
