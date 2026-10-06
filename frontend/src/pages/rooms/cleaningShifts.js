import { compareNatural } from './roomLabels';
import { hhmm, timeRange } from '../scheduling/scheduleFormat';

/**
 * Lịch dọn của MỘT nhân viên dạng bảng theo ca — cùng cách chia hàng / cột với bảng xếp lịch của quản
 * lý (chốt 06/10/2026): hàng là ca, cột là ngày, ô là việc dọn của ca đó.
 */

/** Hàng của các ca tự nhập giờ (không theo mẫu). */
export const OTHER_SHIFT_ROW = 'OTHER';
/** Hàng của việc dọn rơi vào ngày không có ca (ví dụ ca đã bị xóa sau khi dọn xong). */
export const NO_SHIFT_ROW = 'NO_SHIFT';

/** Giờ được giao việc "HH:mm" — để biết việc thuộc ca nào khi một ngày có hai ca. */
function assignedClock(task) {
  return task.assignedAt ? String(task.assignedAt).slice(11, 16) : null;
}

/** Giờ {@code clock} có nằm trong khung ca không — ca qua đêm thì vắt qua nửa đêm. */
function within(clock, shift) {
  const start = hhmm(shift.startTime);
  const end = hhmm(shift.endTime);
  return start < end ? clock >= start && clock < end : clock >= start || clock < end;
}

const rowIdOfShift = (shift) => shift.sourceTemplateId ?? OTHER_SHIFT_ROW;

/**
 * Chia ca và việc dọn của một người vào các ô (ca, ngày).
 *
 * Việc dọn chỉ có NGÀY làm, không gắn ca: ngày có một ca thì việc thuộc ca đó; ngày có nhiều ca thì
 * thuộc ca chứa giờ được giao (không khớp ca nào thì ca đầu tiên); ngày không có ca thì vào hàng
 * "Ngoài ca" để không việc nào bị khuất.
 *
 * @param shifts ca của người đó trong tuần (có `sourceTemplateName` do backend trả kèm)
 * @param tasks  việc dọn có người đó trong nhóm, trong tuần
 * @returns {{ rows: { id, name, timeLabel, start }[], cellOf: (rowId, day) => { shifts, tasks } }}
 */
export function buildMyShiftBoard(shifts, tasks) {
  const rows = new Map();
  const cells = new Map();
  const cell = (rowId, day) => {
    const key = `${rowId}|${day}`;
    if (!cells.has(key)) cells.set(key, { shifts: [], tasks: [] });
    return cells.get(key);
  };

  const shiftsByDay = new Map();
  for (const shift of shifts) {
    const rowId = rowIdOfShift(shift);
    if (!rows.has(rowId)) {
      rows.set(rowId, shift.sourceTemplateId
        ? {
          id: rowId,
          name: shift.sourceTemplateName ?? 'Ca theo mẫu',
          timeLabel: `${timeRange(shift.startTime, shift.endTime)}${shift.overnight ? ' (qua đêm)' : ''}`,
          start: hhmm(shift.startTime),
        }
        : { id: rowId, name: 'Ca giờ khác', timeLabel: 'Ca tự nhập giờ', start: null });
    }
    cell(rowId, shift.shiftDate).shifts.push(shift);
    if (!shiftsByDay.has(shift.shiftDate)) shiftsByDay.set(shift.shiftDate, []);
    shiftsByDay.get(shift.shiftDate).push(shift);
  }

  for (const task of tasks) {
    const dayShifts = shiftsByDay.get(task.assignedDate) ?? [];
    if (dayShifts.length === 0) {
      if (!rows.has(NO_SHIFT_ROW)) {
        rows.set(NO_SHIFT_ROW, { id: NO_SHIFT_ROW, name: 'Ngoài ca', timeLabel: 'Việc dọn vào ngày không có ca', start: null });
      }
      cell(NO_SHIFT_ROW, task.assignedDate).tasks.push(task);
      continue;
    }
    const clock = assignedClock(task);
    const target = dayShifts.length === 1
      ? dayShifts[0]
      : dayShifts.find((shift) => clock && within(clock, shift)) ?? dayShifts[0];
    cell(rowIdOfShift(target), task.assignedDate).tasks.push(task);
  }

  for (const value of cells.values()) {
    value.tasks.sort((a, b) => compareNatural(a.roomNumber, b.roomNumber));
    value.shifts.sort((a, b) => hhmm(a.startTime).localeCompare(hhmm(b.startTime)));
  }

  // Ca theo mẫu theo giờ bắt đầu, rồi "Ca giờ khác", cuối cùng "Ngoài ca" — như bảng của quản lý.
  const rank = (row) => (row.id === NO_SHIFT_ROW ? 2 : row.id === OTHER_SHIFT_ROW ? 1 : 0);
  const sortedRows = [...rows.values()].sort((a, b) => rank(a) - rank(b) || (a.start ?? '').localeCompare(b.start ?? ''));

  return {
    rows: sortedRows,
    cellOf: (rowId, day) => cells.get(`${rowId}|${day}`) ?? { shifts: [], tasks: [] },
  };
}
