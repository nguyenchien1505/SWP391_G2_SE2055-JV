import { formatDate } from '../../pages/rooms/format';
import {
  UNASSIGNED_REASON_LABEL,
  WEEKDAY_SHORT,
  dayClass,
  formatDayMonth,
  formatHours,
  hhmm,
  shiftTone,
  timeRange,
} from '../../pages/scheduling/scheduleFormat';
import { personSubtitle } from '../../pages/scheduling/schedulePeople';

/** Khóa hàng của ca chưa phân công — không trùng được với id người dùng (UUID). */
const OPEN_ROW = 'OPEN';

/**
 * Lịch tuần XEM THEO NHÂN VIÊN: hàng là người, cột là 7 ngày. Dùng để theo dõi giờ làm của từng người
 * so với quy định (giờ/tuần, số ngày có ca) — điều mà bảng theo ca không cho thấy. Hàng đầu gom các
 * ca chưa phân công (DM-03).
 *
 * @param rows          người xếp ca được + người không còn xếp được nhưng vẫn có ca trong tuần (readOnly)
 * @param onAddForPerson (staffId, date)
 * @param onAddOpen      (date) — mở chỗ trống chưa giao người
 * @param onEdit         (shift)
 */
export default function PersonBoard({
  days, today, rows, shifts, templatesById, weekLoad, maxWeekHours, onAddForPerson, onAddOpen, onEdit,
}) {
  const cells = new Map();
  for (const shift of shifts) {
    const key = `${shift.staffId ?? OPEN_ROW}|${shift.shiftDate}`;
    if (!cells.has(key)) cells.set(key, []);
    cells.get(key).push(shift);
  }
  for (const list of cells.values()) list.sort((a, b) => hhmm(a.startTime).localeCompare(hhmm(b.startTime)));
  const openCount = shifts.filter((s) => !s.staffId).length;

  return (
    <div className="sched-scroll">
      <table className="sched-grid">
        <thead>
          <tr>
            <th scope="col" className="sched-grid__person">Nhân viên</th>
            {days.map((day, index) => (
              <th key={day} scope="col" className={dayClass(day, today)}>
                {WEEKDAY_SHORT[index]}
                <b>{formatDayMonth(day)}</b>
                {day === today && <span className="today-chip">Hôm nay</span>}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          <tr className="sched-row--open">
            <th scope="row" className="sched-grid__person person-cell">
              <b>Ca chưa phân công</b>
              <small>{openCount > 0 ? `${openCount} ca cần giao người` : 'Không còn ca trống'}</small>
            </th>
            {days.map((day) => (
              <td key={day} className={dayClass(day, today)}>
                {(cells.get(`${OPEN_ROW}|${day}`) ?? []).map((shift) => (
                  <ShiftChip key={shift.id} shift={shift} template={templatesById.get(shift.sourceTemplateId)} onClick={onEdit} />
                ))}
                <AddButton label={`Mở chỗ trống chưa phân công ngày ${formatDate(day)}`} onClick={() => onAddOpen(day)} />
              </td>
            ))}
          </tr>

          {rows.map((person) => {
            const load = weekLoad.get(person.id);
            const over = load && Number.isFinite(maxWeekHours) && load.hours > maxWeekHours;
            return (
              <tr key={person.id} className={person.readOnly ? 'sched-row--readonly' : ''}>
                <th scope="row" className="sched-grid__person person-cell">
                  <b>{person.fullName}</b>
                  <small>{personSubtitle(person)}</small>
                  <span className={`person-cell__hours ${over ? 'is-over' : ''}`}>
                    {formatHours(load?.hours ?? 0)}
                    {Number.isFinite(maxWeekHours) ? ` / ${formatHours(maxWeekHours)}` : ''}
                    {load ? ` · ${load.days.size} ngày` : ''}
                  </span>
                </th>
                {days.map((day) => (
                  <td key={day} className={dayClass(day, today)}>
                    {(cells.get(`${person.id}|${day}`) ?? []).map((shift) => (
                      <ShiftChip key={shift.id} shift={shift} template={templatesById.get(shift.sourceTemplateId)} onClick={onEdit} />
                    ))}
                    {!person.readOnly && (
                      <AddButton
                        label={`Thêm ca cho ${person.fullName} ngày ${formatDate(day)}`}
                        onClick={() => onAddForPerson(person.id, day)}
                      />
                    )}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function ShiftChip({ shift, template, onClick }) {
  const open = !shift.staffId;
  const name = template?.name ?? 'Ca tự do';
  const meta = [
    formatHours(shift.durationHours),
    shift.overnight ? 'qua đêm' : null,
    shift.checkInAt ? (shift.checkOutAt ? 'đã ra ca' : 'đã vào ca') : null,
    open && shift.unassignedReason ? UNASSIGNED_REASON_LABEL[shift.unassignedReason] : null,
  ].filter(Boolean);

  return (
    <button
      type="button"
      className={`shift-chip shift-tone--${shiftTone(shift.startTime)} ${open ? 'shift-chip--open' : ''}`}
      onClick={() => onClick(shift)}
      title={`${name} · ${timeRange(shift.startTime, shift.endTime)}${shift.overnight ? ' (kết thúc sáng hôm sau)' : ''}`}
    >
      <span className="shift-chip__name">{name}</span>
      <span className="shift-chip__time">{timeRange(shift.startTime, shift.endTime)}</span>
      <span className="shift-chip__meta">{meta.join(' · ')}</span>
    </button>
  );
}

function AddButton({ label, onClick }) {
  return (
    <button type="button" className="cell-add" onClick={onClick} aria-label={label} title={label}>
      +
    </button>
  );
}
