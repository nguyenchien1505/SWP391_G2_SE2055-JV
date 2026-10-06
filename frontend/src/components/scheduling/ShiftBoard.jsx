import { formatDate } from '../../pages/rooms/format';
import {
  WEEKDAY_SHORT,
  dayClass,
  formatDayMonth,
  formatHours,
  hhmm,
  shiftTone,
  timeRange,
} from '../../pages/scheduling/scheduleFormat';
import { OTHER_ROW, cellKey, groupByCell, lacksReceptionist } from '../../pages/scheduling/scheduleCells';
import { coverageSummary } from '../../pages/scheduling/schedulePeople';

/**
 * Lịch tuần XEM THEO CA: hàng là các mẫu trong bộ mẫu chi nhánh đang dùng (thứ tự theo giờ bắt đầu) +
 * một hàng "Ca giờ khác", cột là 7 ngày Thứ Hai → Chủ Nhật (BR-SCH-13).
 *
 * Mẫu đã tắt, hay mẫu không còn thuộc bộ chi nhánh đang dùng (chi nhánh vừa đổi bộ), vẫn có hàng
 * riêng khi tuần đó còn ca của nó — chỉ xem, sửa, gỡ được; không xếp thêm ca mới theo mẫu đó.
 *
 * Mỗi ô là một KHỐI tóm tắt (chốt 05/10/2026): bao nhiêu người, bao nhiêu chỗ trống, đủ quyền nào, và
 * cờ «Thiếu lễ tân» khi ca theo mẫu đã có người mà chưa có lễ tân. Bấm khối để xem chi tiết từng người
 * và thao tác; ô trống bấm + để giao ca luôn.
 *
 * @param templates     mẫu dùng được để xếp ca mới (đang dùng, thuộc bộ chi nhánh đang dùng)
 * @param templatesById tra mọi mẫu đã biết — kể cả mẫu của các ca cũ
 * @param onOpenCell    ({ date, templateId }) — mở chi tiết ô; templateId null là hàng "Ca giờ khác"
 * @param onAdd         ({ date, templateId }) — giao ca cho ô đang trống
 */
export default function ShiftBoard({ days, today, templates, templatesById, shifts, peopleById, onOpenCell, onAdd }) {
  const usableIds = new Set(templates.map((t) => t.id));
  // Mẫu cũ còn ca trong tuần: hàng riêng, xếp theo giờ bắt đầu sau các mẫu đang dùng.
  const legacyIds = [...new Set(shifts.map((s) => s.sourceTemplateId).filter((id) => id && !usableIds.has(id)))]
    .sort((a, b) => hhmm(templatesById.get(a)?.startTime).localeCompare(hhmm(templatesById.get(b)?.startTime)));
  const rows = [
    ...templates.map((t) => ({ id: t.id, template: t, legacy: false })),
    ...legacyIds.map((id) => ({ id, template: templatesById.get(id) ?? null, legacy: true })),
    { id: OTHER_ROW, template: null, legacy: false },
  ];
  const cells = groupByCell(shifts, peopleById);

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
          {rows.map(({ id, template, legacy }) => {
            const isOther = id === OTHER_ROW;
            const name = isOther ? 'Ca giờ khác' : template?.name ?? 'Mẫu ca khác';
            return (
              <tr key={id} className={`${isOther ? 'sched-row--other' : ''} ${legacy ? 'sched-row--legacy' : ''}`}>
                <th
                  scope="row"
                  className={`sched-grid__person person-cell shift-row-head ${template ? `shift-tone--${shiftTone(template.startTime)}` : ''}`}
                >
                  <b>{name}</b>
                  {isOther ? (
                    <small>Ca tự nhập giờ — không bắt buộc lễ tân</small>
                  ) : (
                    template && (
                      <small>
                        {timeRange(template.startTime, template.endTime)}
                        {template.overnight ? ' (qua đêm)' : ''}
                      </small>
                    )
                  )}
                  {!isOther && !legacy && template && (
                    <span className="person-cell__hours">{formatHours(template.durationHours)}</span>
                  )}
                  {legacy && (
                    <span className="legacy-note">
                      {template && !template.active ? 'Mẫu đã tắt' : 'Không thuộc bộ mẫu đang dùng'} — chỉ còn ca cũ
                    </span>
                  )}
                </th>
                {days.map((day) => {
                  const list = cells.get(cellKey(id, day)) ?? [];
                  const label = `${name} ngày ${formatDate(day)}`;
                  const templateId = isOther ? null : id;
                  return (
                    <td key={day} className={dayClass(day, today)}>
                      {list.length > 0 && (
                        <CellBlock
                          list={list}
                          rowId={id}
                          template={template}
                          peopleById={peopleById}
                          label={label}
                          onClick={() => onOpenCell({ date: day, templateId })}
                        />
                      )}
                      {list.length === 0 && !legacy && (
                        <button
                          type="button"
                          className="cell-add"
                          onClick={() => onAdd({ date: day, templateId })}
                          aria-label={`Giao ca: ${label}`}
                          title={`Giao ca: ${label}`}
                        >
                          +
                        </button>
                      )}
                    </td>
                  );
                })}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

/** Khối tóm tắt một ô: số người, chỗ trống, quyền, vài tên đầu và cờ thiếu lễ tân. */
function CellBlock({ list, rowId, template, peopleById, label, onClick }) {
  const staffed = list.filter((shift) => shift.staffId);
  const open = list.length - staffed.length;
  const people = staffed.map((shift) => peopleById.get(shift.staffId));
  const coverage = coverageSummary(people);
  const missingReception = lacksReceptionist(rowId, list, peopleById);
  const names = people.map((person) => person?.fullName ?? 'Nhân viên khác');
  // Hàng "Ca giờ khác": mỗi ca một giờ riêng — liệt kê các khung giờ có trong ô.
  const times = rowId === OTHER_ROW ? [...new Set(list.map((shift) => timeRange(shift.startTime, shift.endTime)))] : [];
  const tone = shiftTone((template ?? list[0]).startTime);

  const summary = [
    `${staffed.length} người`,
    open > 0 ? `${open} chỗ trống` : null,
    missingReception ? 'thiếu lễ tân' : null,
  ].filter(Boolean).join(', ');

  return (
    <button
      type="button"
      className={`cell-block shift-tone--${tone} ${missingReception ? 'cell-block--warn' : ''}`}
      onClick={onClick}
      aria-label={`${label}: ${summary}. Xem chi tiết`}
      title="Xem chi tiết ca"
    >
      <span className="cell-block__count">
        <b>{staffed.length}</b> người
        {open > 0 && <span className="cell-block__open"> · {open} chỗ trống</span>}
      </span>
      {times.length > 0 && <span className="cell-block__meta">{times.join(', ')}</span>}
      {coverage && <span className="cell-block__meta">{coverage}</span>}
      {names.length > 0 && <span className="cell-block__names">{names.join(', ')}</span>}
      {missingReception && <span className="cell-block__flag">Thiếu lễ tân</span>}
    </button>
  );
}
