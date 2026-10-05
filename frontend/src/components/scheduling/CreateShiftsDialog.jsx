import { useEffect, useMemo, useRef, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { createShifts } from '../../api/scheduling';
import { durationHours, formatHours, formatLongDate, hhmm, timeRange } from '../../pages/scheduling/scheduleFormat';
import { PERMISSION_FILTERS, matchesPermissionFilter, personSubtitle } from '../../pages/scheduling/schedulePeople';
import { BlockedAlert, DateHelp, ShiftTimeFields, resolveHours, useCloseOnEscape } from './shiftFields';

/** Khớp giới hạn của CreateShiftBatchRequest ở backend. */
const MAX_OPEN_SLOTS = 20;

/**
 * Giao CÙNG MỘT ca cho một hoặc nhiều người — BR-SCH-02, BR-SCH-04, DM-03.
 *
 * Một ca có thể có nhiều người cùng quyền và khác quyền (lễ tân + dọn dẹp cùng ca sáng). Mỗi người
 * được tick là MỘT bản ghi ca riêng và được kiểm tra quy định riêng; trùng giờ chỉ xét các ca của
 * chính người đó. Có người vi phạm thì backend không lưu ai và trả lý do từng người — hộp thoại đánh
 * dấu đúng các dòng đó để Manager bỏ chọn rồi lưu lại.
 *
 * Danh sách lọc theo quyền nghiệp vụ; nhân viên đa quyền hiện ở mọi quyền họ có. Mỗi dòng kèm số giờ
 * đã xếp trong tuần (từ lịch đang xem), nên ngày chỉ chọn được trong tuần đó.
 *
 * @param days        7 ngày của tuần đang xem
 * @param shifts      mọi ca của tuần đang xem
 * @param people      người giao ca được (đã sắp xếp)
 * @param weekLoad    Map staffId → { hours, days } của tuần đang xem
 * @param templates   mẫu ca đang dùng
 * @param initial     { date, templateId?, mode?: 'template' | 'free', staffIds?, openSlots? }
 * @param onSaved     nhận câu thông báo sau khi lưu xong
 */
export default function CreateShiftsDialog({
  locationId, days, shifts, people, weekLoad, maxWeekHours, templates, initial, onClose, onSaved,
}) {
  const [date, setDate] = useState(initial.date);
  const [time, setTime] = useState(() => initialTime(templates, initial));
  const [filter, setFilter] = useState('ALL');
  const [selected, setSelected] = useState(() => new Set(initial.staffIds ?? []));
  const [openSlots, setOpenSlots] = useState(String(initial.openSlots ?? 0));
  const [error, setError] = useState(null); // { message, violations: Map<staffId, lý do> }
  const [submitting, setSubmitting] = useState(false);

  useCloseOnEscape(onClose, submitting);

  // Khung báo lỗi nằm cuối hộp thoại dài — bị chặn thì cuộn tới để Manager thấy ngay lý do.
  const alertRef = useRef(null);
  useEffect(() => {
    if (error) alertRef.current?.scrollIntoView({ block: 'end', behavior: 'smooth' });
  }, [error]);

  const hours = resolveHours(time, templates);
  const shiftHours = hours ? durationHours(hours.start, hours.end) : 0;
  const template = time.mode === 'template' ? templates.find((t) => t.id === time.templateId) : null;

  // Ca của từng người trong ngày đang chọn: ai đã có trong chính ca này, ai đã có ca khác hôm đó.
  const dayShiftsByPerson = useMemo(() => {
    const map = new Map();
    for (const shift of shifts) {
      if (!shift.staffId || shift.shiftDate !== date) continue;
      if (!map.has(shift.staffId)) map.set(shift.staffId, []);
      map.get(shift.staffId).push(shift);
    }
    return map;
  }, [shifts, date]);

  const isThisShift = (shift) => Boolean(hours)
    && hhmm(shift.startTime) === hours.start && hhmm(shift.endTime) === hours.end;
  const alreadyIn = (personId) => (dayShiftsByPerson.get(personId) ?? []).some(isThisShift);

  const visible = people.filter((person) => matchesPermissionFilter(person, filter));
  const pickable = visible.filter((person) => !alreadyIn(person.id));
  const allPicked = pickable.length > 0 && pickable.every((person) => selected.has(person.id));
  // Gửi theo thứ tự danh sách; người đã có trong ca này bị loại vì đằng nào cũng trùng giờ.
  const staffIds = people.filter((p) => selected.has(p.id) && !alreadyIn(p.id)).map((p) => p.id);
  const hiddenSelected = staffIds.filter((id) => !visible.some((p) => p.id === id)).length;
  const slots = clampSlots(openSlots);
  const ready = Boolean(date && hours) && (staffIds.length > 0 || slots > 0);
  const nameOf = (id) => people.find((p) => p.id === id)?.fullName ?? 'Nhân viên';

  function toggle(personId) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(personId)) next.delete(personId);
      else next.add(personId);
      return next;
    });
  }

  function toggleAllVisible() {
    setSelected((prev) => {
      const next = new Set(prev);
      for (const person of pickable) {
        if (allPicked) next.delete(person.id);
        else next.add(person.id);
      }
      return next;
    });
  }

  function dropViolators() {
    setSelected((prev) => new Set([...prev].filter((id) => !error?.violations.has(id))));
    setError(null);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await createShifts({
        locationId,
        shiftDate: date,
        ...(time.mode === 'template'
          ? { sourceTemplateId: time.templateId }
          : { startTime: hours.start, endTime: hours.end }),
        staffIds,
        openSlots: slots,
      });
      onSaved(successText());
    } catch (err) {
      const violations = err?.response?.data?.violations ?? [];
      setError({
        message: readErrorMessage(err, 'Không lưu được ca.'),
        violations: new Map(violations.map((v) => [v.staffId, v.message])),
      });
      setSubmitting(false);
    }
  }

  function successText() {
    const what = template ? template.name : `ca ${timeRange(hours.start, hours.end)}`;
    const parts = [];
    if (staffIds.length > 0) parts.push(`giao cho ${listNames(staffIds.map(nameOf))}`);
    if (slots > 0) parts.push(`mở ${slots} chỗ trống chưa giao người`);
    return `Đã xếp ${what} ${formatLongDate(date)}: ${parts.join(', ')}.`;
  }

  const submitLabel = [
    staffIds.length > 0 ? `Giao ca cho ${staffIds.length} người` : null,
    slots > 0 ? `${staffIds.length > 0 ? '+ ' : 'Mở '}${slots} chỗ trống` : null,
  ].filter(Boolean).join(' ') || 'Giao ca';

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="assign-shift-title">
      <form className="modal modal--form modal--assign form" onSubmit={handleSubmit} noValidate>
        <button type="button" className="modal__close" onClick={onClose} aria-label="Đóng" disabled={submitting}>
          ✕
        </button>
        <header className="form__head">
          <span className="form__icon" aria-hidden="true">🗓</span>
          <div>
            <h2 id="assign-shift-title">Giao ca</h2>
            <p className="muted">
              Tick một hoặc nhiều người. Mỗi người là một ca riêng và được kiểm tra quy định riêng; có
              người vi phạm thì chưa lưu ai.
            </p>
          </div>
        </header>

        <label className="field" htmlFor="assign-date">
          <span className="field__label">
            Ngày bắt đầu ca <span className="req">*</span>
          </span>
          <input
            id="assign-date"
            type="date"
            value={date}
            min={days[0]}
            max={days[6]}
            onChange={(e) => setDate(e.target.value)}
            required
          />
          <DateHelp date={date} />
        </label>

        <ShiftTimeFields idPrefix="assign" templates={templates} value={time} onChange={setTime} date={date} />

        <section className="dialog-section" aria-labelledby="assign-people-title">
          <h3 id="assign-people-title">Người làm</h3>

          <div className="segmented segmented--wrap" role="radiogroup" aria-label="Lọc theo quyền">
            {PERMISSION_FILTERS.map((option) => (
              <label key={option.value} className={filter === option.value ? 'is-selected' : ''}>
                <input
                  type="radio"
                  name="assign-filter"
                  value={option.value}
                  checked={filter === option.value}
                  onChange={() => setFilter(option.value)}
                />
                {option.label} ({people.filter((p) => matchesPermissionFilter(p, option.value)).length})
              </label>
            ))}
          </div>

          <div className="pick-toolbar">
            <span className="muted">
              Đã chọn <b>{staffIds.length}</b> người
              {hiddenSelected > 0 ? ` (${hiddenSelected} người không hiện trong bộ lọc này)` : ''}
            </span>
            <button
              type="button"
              className="link-button"
              onClick={toggleAllVisible}
              disabled={pickable.length === 0 || submitting}
            >
              {allPicked ? 'Bỏ chọn những người đang hiện' : `Chọn tất cả (${pickable.length})`}
            </button>
          </div>

          {visible.length === 0 ? (
            <p className="state">Không có ai thuộc nhóm quyền này.</p>
          ) : (
            <ul className="pick-list">
              {visible.map((person) => {
                const inShift = alreadyIn(person.id);
                const checked = selected.has(person.id) && !inShift;
                const load = weekLoad.get(person.id)?.hours ?? 0;
                const projected = checked ? load + shiftHours : load;
                const over = Number.isFinite(maxWeekHours) && projected > maxWeekHours;
                const others = (dayShiftsByPerson.get(person.id) ?? []).filter((s) => !isThisShift(s));
                const violation = error?.violations.get(person.id);
                return (
                  <li key={person.id}>
                    <label
                      className={[
                        'pick-row',
                        checked ? 'is-selected' : '',
                        inShift ? 'is-disabled' : '',
                        violation ? 'has-error' : '',
                      ].join(' ')}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={inShift || submitting}
                        onChange={() => toggle(person.id)}
                      />
                      <span className="pick-row__body">
                        <b>{person.fullName}</b>
                        <small>{personSubtitle(person)}</small>
                        <small className={over ? 'is-over' : ''}>
                          Tuần này: {formatHours(load)}
                          {checked && shiftHours ? ` → ${formatHours(projected)}` : ''}
                          {Number.isFinite(maxWeekHours) ? ` / tối đa ${formatHours(maxWeekHours)}` : ''}
                        </small>
                        {inShift && <small>Đã có trong ca này</small>}
                        {!inShift && others.length > 0 && (
                          <small>Ngày này đã có ca {others.map((s) => timeRange(s.startTime, s.endTime)).join(', ')}</small>
                        )}
                        {violation && <small className="pick-row__error">{violation}</small>}
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>
          )}

          <label className="field open-slots" htmlFor="assign-open-slots">
            <span className="field__label">Chỗ trống chưa giao người</span>
            <input
              id="assign-open-slots"
              type="number"
              min={0}
              max={MAX_OPEN_SLOTS}
              step={1}
              value={openSlots}
              onChange={(e) => setOpenSlots(e.target.value)}
            />
            <span className="field__help">Mở thêm ca chưa có người để giao sau, ví dụ khi ca còn thiếu người.</span>
          </label>
        </section>

        <div ref={alertRef}>
          <BlockedAlert message={error?.message} title="Không lưu được">
            {error?.violations.size > 0 && (
              <>
                <ul className="violation-list">
                  {[...error.violations].map(([id, message]) => (
                    <li key={id}>
                      <b>{nameOf(id)}</b>: {message}
                    </li>
                  ))}
                </ul>
                <button type="button" className="btn btn--ghost btn--sm" onClick={dropViolators}>
                  Bỏ chọn {error.violations.size} người vi phạm
                </button>
              </>
            )}
          </BlockedAlert>
        </div>

        <div className="form__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={submitting}>
            Hủy bỏ
          </button>
          <button type="submit" className="btn btn--primary" disabled={!ready || submitting}>
            {submitting ? 'Đang kiểm tra quy định…' : submitLabel}
          </button>
        </div>
      </form>
    </div>
  );
}

function initialTime(templates, initial) {
  const fallbackTemplate = templates[0]?.id ?? '';
  if (initial.mode === 'free' || templates.length === 0) {
    return { mode: 'free', templateId: fallbackTemplate, start: '', end: '' };
  }
  const templateId = templates.some((t) => t.id === initial.templateId) ? initial.templateId : fallbackTemplate;
  return { mode: 'template', templateId, start: '', end: '' };
}

function clampSlots(value) {
  const number = Math.floor(Number(value));
  return Number.isFinite(number) ? Math.min(MAX_OPEN_SLOTS, Math.max(0, number)) : 0;
}

/** "Lan, Hùng và Hoa"; quá 3 người thì "Lan, Hùng, Hoa và 2 người khác". */
function listNames(names) {
  if (names.length <= 1) return names.join('');
  if (names.length <= 3) return `${names.slice(0, -1).join(', ')} và ${names.at(-1)}`;
  return `${names.slice(0, 3).join(', ')} và ${names.length - 3} người khác`;
}
