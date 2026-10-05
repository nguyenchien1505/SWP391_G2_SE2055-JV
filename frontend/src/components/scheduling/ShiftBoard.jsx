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
import { coverageSummary } from '../../pages/scheduling/schedulePeople';

/** Hàng của các ca không thuộc mẫu đang dùng: ca tự nhập giờ, ca của mẫu đã tắt. */
const OTHER_ROW = 'OTHER';

/**
 * Lịch tuần XEM THEO CA: hàng là các mẫu ca đang dùng (thứ tự theo giờ bắt đầu) + một hàng "Ca giờ
 * khác", cột là 7 ngày Thứ Hai → Chủ Nhật (BR-SCH-13). Mỗi ô liệt kê những người làm ca đó trong ngày
 * — nhiều người, cùng quyền hay khác quyền — và các chỗ chưa phân công (DM-03).
 *
 * Ca thuộc hàng mẫu khi còn trỏ tới mẫu đó (`sourceTemplateId`). Sửa tay giờ của một ca thì backend
 * bỏ liên kết mẫu, nên ca đó chuyển sang hàng "Ca giờ khác" — đúng với giờ thật của nó.
 *
 * @param onAdd  ({ date, templateId }) — templateId null nghĩa là ca tự nhập giờ
 * @param onEdit (shift)
 */
export default function ShiftBoard({ days, today, templates, shifts, peopleById, onAdd, onEdit }) {
  const activeIds = new Set(templates.map((t) => t.id));
  const rows = [...templates.map((t) => ({ id: t.id, template: t })), { id: OTHER_ROW, template: null }];

  const cells = new Map();
  for (const shift of shifts) {
    const rowId = activeIds.has(shift.sourceTemplateId) ? shift.sourceTemplateId : OTHER_ROW;
    const key = `${rowId}|${shift.shiftDate}`;
    if (!cells.has(key)) cells.set(key, []);
    cells.get(key).push(shift);
  }
  const nameOf = (shift) => peopleById.get(shift.staffId)?.fullName ?? '';
  for (const list of cells.values()) {
    // Giờ trước (cho hàng "Ca giờ khác"), rồi người đã giao theo tên, chỗ trống xuống cuối.
    list.sort((a, b) =>
      hhmm(a.startTime).localeCompare(hhmm(b.startTime))
      || Number(!a.staffId) - Number(!b.staffId)
      || nameOf(a).localeCompare(nameOf(b), 'vi'));
  }

  return (
    <div className="sched-scroll">
      <table className="sched-grid">
        <thead>
          <tr>
            <th scope="col" className="sched-grid__person">Ca làm việc</th>
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
          {rows.map(({ id, template }) => (
            <tr key={id} className={template ? '' : 'sched-row--other'}>
              <th
                scope="row"
                className={`sched-grid__person person-cell shift-row-head ${template ? `shift-tone--${shiftTone(template.startTime)}` : ''}`}
              >
                {template ? (
                  <>
                    <b>{template.name}</b>
                    <small>
                      {timeRange(template.startTime, template.endTime)}
                      {template.overnight ? ' (qua đêm)' : ''}
                    </small>
                    <span className="person-cell__hours">{formatHours(template.durationHours)}</span>
                  </>
                ) : (
                  <>
                    <b>Ca giờ khác</b>
                    <small>Ca tự nhập giờ, ca của mẫu đã tắt</small>
                  </>
                )}
              </th>
              {days.map((day) => {
                const list = cells.get(`${id}|${day}`) ?? [];
                const coverage = coverageSummary(list.filter((s) => s.staffId).map((s) => peopleById.get(s.staffId)));
                const label = template ? template.name : 'ca giờ khác';
                return (
                  <td key={day} className={dayClass(day, today)}>
                    {list.map((shift) => (
                      <PersonChip
                        key={shift.id}
                        shift={shift}
                        person={peopleById.get(shift.staffId)}
                        showTime={!template}
                        onClick={onEdit}
                      />
                    ))}
                    {coverage && <p className="cell-coverage">{coverage}</p>}
                    <button
                      type="button"
                      className="cell-add"
                      onClick={() => onAdd({ date: day, templateId: template ? id : null })}
                      aria-label={`Giao người cho ${label} ngày ${formatDate(day)}`}
                      title={`Giao người cho ${label} ngày ${formatDate(day)}`}
                    >
                      +
                    </button>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Một người trong ô ca, hoặc một chỗ trống chưa giao người (viền đứt nét — design.md). */
function PersonChip({ shift, person, showTime, onClick }) {
  const open = !shift.staffId;
  const name = open ? 'Chỗ trống' : (person?.fullName ?? 'Nhân viên khác');
  const meta = [
    showTime ? timeRange(shift.startTime, shift.endTime) : null,
    showTime && shift.overnight ? 'qua đêm' : null,
    shift.checkInAt ? (shift.checkOutAt ? 'đã ra ca' : 'đã vào ca') : null,
    open && shift.unassignedReason ? UNASSIGNED_REASON_LABEL[shift.unassignedReason] : null,
  ].filter(Boolean);

  return (
    <button
      type="button"
      className={`shift-chip shift-chip--compact shift-tone--${shiftTone(shift.startTime)} ${open ? 'shift-chip--open' : ''}`}
      onClick={() => onClick(shift)}
      title={`${name} · ${timeRange(shift.startTime, shift.endTime)}${shift.overnight ? ' (kết thúc sáng hôm sau)' : ''}`}
    >
      <span className="shift-chip__name">{name}</span>
      {meta.length > 0 && <span className="shift-chip__meta">{meta.join(' · ')}</span>}
    </button>
  );
}

