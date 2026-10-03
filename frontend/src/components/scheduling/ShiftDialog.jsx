import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { assignShift, createShift, deleteShift, unassignShift, updateShift } from '../../api/scheduling';
import ConfirmDialog from '../ConfirmDialog';
import { formatDateTime } from '../../pages/rooms/format';
import {
  UNASSIGNED_REASON_LABEL,
  durationHours,
  formatDayMonth,
  formatHours,
  formatLongDate,
  hhmm,
  isOvernight,
  timeRange,
} from '../../pages/scheduling/scheduleFormat';

/**
 * Hộp thoại thêm / sửa một ca — design.md màn 22, BR-SCH-02, BR-SCH-03, BR-SCH-04, BR-SCH-24.
 *
 * Mọi kiểm tra quy định xếp ca chạy ở BACKEND lúc lưu: vi phạm thì backend trả câu nêu rõ vi phạm
 * điều nào, hộp thoại giữ nguyên dữ liệu và hiện khung chặn màu đỏ để Manager sửa rồi lưu lại.
 *
 * Sửa một ca tách thành các thao tác riêng, mỗi thao tác đúng MỘT lệnh gọi API (gỡ người / gán người /
 * đổi ngày giờ / xóa) — gộp vào một nút "Lưu" thì một bước bị chặn giữa chừng sẽ để lại ca đã đổi
 * một nửa.
 *
 * @param people    người xếp ca được (Manager và nhân viên chưa nghỉ việc của khách sạn)
 * @param peopleById tra tên cả người đã nghỉ việc / đã chuyển đi cho ca cũ
 * @param templates mẫu ca đang dùng — chỉ mẫu này được chọn (BR-SCH-22)
 * @param templatesById tra tên cả mẫu đã tắt cho ca cũ
 * @param onSaved   nhận câu thông báo sau khi lưu xong; trang tự đóng hộp thoại và tải lại lịch
 */
export default function ShiftDialog(props) {
  return props.shift ? <EditShift {...props} /> : <CreateShift {...props} />;
}

// ── Thêm ca ─────────────────────────────────────────────────────────────────────

function CreateShift({ locationId, people, peopleById, templates, initialStaffId, initialDate, onClose, onSaved }) {
  const [staffId, setStaffId] = useState(initialStaffId ?? '');
  const [date, setDate] = useState(initialDate);
  const [time, setTime] = useState(() => initialTime(templates));
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useCloseOnEscape(onClose, submitting);

  const hours = resolveHours(time, templates);
  const ready = Boolean(date && hours);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      await createShift({
        locationId,
        staffId: staffId || null,
        shiftDate: date,
        ...(time.mode === 'template'
          ? { sourceTemplateId: time.templateId }
          : { startTime: time.start, endTime: time.end }),
      });
      const who = staffId ? `cho ${peopleById.get(staffId)?.fullName ?? 'nhân viên'}` : '(chưa phân công)';
      onSaved(`Đã thêm ca ${timeRange(hours.start, hours.end)} ${formatLongDate(date)} ${who}.`);
    } catch (err) {
      setError(readErrorMessage(err, 'Không lưu được ca.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="shift-dialog-title">
      <form className="modal modal--form form" onSubmit={handleSubmit} noValidate>
        <button type="button" className="modal__close" onClick={onClose} aria-label="Đóng" disabled={submitting}>
          ✕
        </button>
        <header className="form__head">
          <span className="form__icon" aria-hidden="true">🗓</span>
          <div>
            <h2 id="shift-dialog-title">Thêm ca làm việc</h2>
            <p className="muted">Chọn mẫu ca có sẵn hoặc nhập giờ tự do.</p>
          </div>
        </header>

        <label className="field" htmlFor="shift-staff">
          <span className="field__label">Nhân viên</span>
          <select id="shift-staff" value={staffId} onChange={(e) => setStaffId(e.target.value)}>
            <option value="">— Chưa phân công (mở ca, gán người sau) —</option>
            {people.map((person) => (
              <option key={person.id} value={person.id}>
                {personOptionLabel(person)}
              </option>
            ))}
          </select>
        </label>

        <label className="field" htmlFor="shift-date">
          <span className="field__label">
            Ngày bắt đầu ca <span className="req">*</span>
          </span>
          <input id="shift-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
          <DateHelp date={date} />
        </label>

        <ShiftTimeFields idPrefix="new" templates={templates} value={time} onChange={setTime} date={date} />

        <BlockedAlert message={error} />

        <div className="form__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={submitting}>
            Hủy bỏ
          </button>
          <button type="submit" className="btn btn--primary" disabled={!ready || submitting}>
            {submitting ? 'Đang kiểm tra quy định…' : 'Lưu ca'}
          </button>
        </div>
      </form>
    </div>
  );
}

// ── Sửa ca ─────────────────────────────────────────────────────────────────────

function EditShift({ shift, people, peopleById, templates, templatesById, onClose, onSaved }) {
  const checkedIn = Boolean(shift.checkInAt);
  const assignee = shift.staffId ? peopleById.get(shift.staffId) : null;
  const template = shift.sourceTemplateId ? templatesById.get(shift.sourceTemplateId) : null;
  const currentStart = hhmm(shift.startTime);
  const currentEnd = hhmm(shift.endTime);

  const [newStaffId, setNewStaffId] = useState('');
  const [date, setDate] = useState(shift.shiftDate);
  const [time, setTime] = useState(() =>
    // Mẫu đã tắt thì không chọn lại được (BR-SCH-22): mở sẵn ô giờ tự do với giờ hiện tại.
    template?.active
      ? { mode: 'template', templateId: template.id, start: currentStart, end: currentEnd }
      : { mode: 'free', templateId: templates[0]?.id ?? '', start: currentStart, end: currentEnd },
  );
  const [busy, setBusy] = useState('');            // '' | 'assign' | 'unassign' | 'update' | 'delete'
  const [error, setError] = useState('');
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  useCloseOnEscape(onClose, Boolean(busy) || confirmingDelete);

  const label = `ca ${timeRange(shift.startTime, shift.endTime)} ${formatLongDate(shift.shiftDate)}`;

  /** Chỉ gửi phần thật sự đổi: đổi riêng ngày thì ca vẫn giữ liên kết với mẫu của nó. */
  function changedFields() {
    const payload = {};
    if (date !== shift.shiftDate) payload.shiftDate = date;
    if (time.mode === 'template') {
      if (time.templateId && time.templateId !== shift.sourceTemplateId) payload.sourceTemplateId = time.templateId;
    } else if (time.start !== currentStart || time.end !== currentEnd) {
      payload.startTime = time.start;
      payload.endTime = time.end;
    }
    return payload;
  }
  const changes = changedFields();
  const hours = resolveHours(time, templates);
  const canUpdate = Boolean(date && hours) && Object.keys(changes).length > 0;

  async function run(action, call, successText) {
    setBusy(action);
    setError('');
    try {
      await call();
      onSaved(successText);
    } catch (err) {
      setError(readErrorMessage(err, 'Không lưu được thay đổi.'));
      setBusy('');
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="shift-dialog-title">
      <div className="modal modal--form form">
        <button type="button" className="modal__close" onClick={onClose} aria-label="Đóng" disabled={Boolean(busy)}>
          ✕
        </button>
        <header className="form__head">
          <span className="form__icon" aria-hidden="true">🗓</span>
          <div>
            <h2 id="shift-dialog-title">{formatLongDate(shift.shiftDate)}</h2>
            <p className="muted">
              {template ? template.name : 'Ca tự do'}
              {template && !template.active ? ' (mẫu đã tắt)' : ''}
            </p>
          </div>
        </header>

        <div className="readonly-box">
          <b>
            {timeRange(shift.startTime, shift.endTime)}
            {shift.overnight ? ' (qua đêm)' : ''} · {formatHours(shift.durationHours)}
          </b>
          <p>
            Người làm: <strong>{assignee?.fullName ?? (shift.staffId ? 'Nhân viên khác' : 'Chưa phân công')}</strong>
            {!shift.staffId && shift.unassignedReason && (
              <> — {UNASSIGNED_REASON_LABEL[shift.unassignedReason] ?? shift.unassignedReason}
                {shift.unassignedAt ? ` lúc ${formatDateTime(shift.unassignedAt)}` : ''}</>
            )}
          </p>
          {shift.overnight && (
            <small>Ca kết thúc sáng hôm sau nhưng toàn bộ giờ tính vào ngày {formatDayMonth(shift.shiftDate)}.</small>
          )}
          {checkedIn && (
            <small>
              Check-in {formatDateTime(shift.checkInAt)}
              {shift.checkOutAt ? ` · Check-out ${formatDateTime(shift.checkOutAt)}` : ' · chưa check-out'}
            </small>
          )}
        </div>

        {checkedIn ? (
          <div className="alert alert--info">
            Ca đã check-in nên không đổi giờ, đổi người hay xóa được nữa — giờ chấm công nằm ngay trên ca này.
          </div>
        ) : (
          <>
            <section className="dialog-section" aria-labelledby="shift-person-title">
              <h3 id="shift-person-title">Người làm</h3>
              {shift.staffId ? (
                <div className="dialog-inline">
                  <p className="muted dialog-inline__note">
                    Gỡ người thì ca quay về «Chưa phân công» để giao cho người khác.
                  </p>
                  <button
                    type="button"
                    className="btn btn--ghost"
                    disabled={Boolean(busy)}
                    onClick={() => run('unassign', () => unassignShift(shift.id),
                      `Đã gỡ ${assignee?.fullName ?? 'nhân viên'} khỏi ${label}; ca chuyển sang «Chưa phân công».`)}
                  >
                    {busy === 'unassign' ? 'Đang gỡ…' : 'Gỡ người khỏi ca'}
                  </button>
                </div>
              ) : (
                <div className="dialog-inline">
                  <label className="field" htmlFor="shift-assignee">
                    <span className="field__label">Giao cho</span>
                    <select id="shift-assignee" value={newStaffId} onChange={(e) => setNewStaffId(e.target.value)}>
                      <option value="">— Chọn nhân viên —</option>
                      {people.map((person) => (
                        <option key={person.id} value={person.id}>
                          {personOptionLabel(person)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <button
                    type="button"
                    className="btn btn--primary"
                    disabled={!newStaffId || Boolean(busy)}
                    onClick={() => run('assign', () => assignShift(shift.id, newStaffId),
                      `Đã giao ${label} cho ${peopleById.get(newStaffId)?.fullName ?? 'nhân viên'}.`)}
                  >
                    {busy === 'assign' ? 'Đang kiểm tra…' : 'Giao ca'}
                  </button>
                </div>
              )}
            </section>

            <section className="dialog-section" aria-labelledby="shift-time-title">
              <h3 id="shift-time-title">Đổi ngày giờ</h3>
              <label className="field" htmlFor="shift-edit-date">
                <span className="field__label">Ngày bắt đầu ca</span>
                <input id="shift-edit-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
                <DateHelp date={date} />
              </label>
              <ShiftTimeFields idPrefix="edit" templates={templates} value={time} onChange={setTime} date={date} />
              <div className="form__actions">
                <button
                  type="button"
                  className="btn btn--primary"
                  disabled={!canUpdate || Boolean(busy)}
                  onClick={() => run('update', () => updateShift(shift.id, changes),
                    `Đã đổi ${label} thành ${timeRange(hours.start, hours.end)} ${formatLongDate(date)}.`)}
                >
                  {busy === 'update' ? 'Đang kiểm tra quy định…' : 'Lưu ngày giờ'}
                </button>
              </div>
            </section>
          </>
        )}

        <BlockedAlert message={error} />

        <div className={`modal__actions ${checkedIn ? '' : 'modal__actions--split'}`}>
          {!checkedIn && (
            <button
              type="button"
              className="btn btn--danger-ghost"
              disabled={Boolean(busy)}
              onClick={() => setConfirmingDelete(true)}
            >
              Xóa ca
            </button>
          )}
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={Boolean(busy)}>
            Đóng
          </button>
        </div>
      </div>

      {confirmingDelete && (
        <ConfirmDialog
          title="Xóa ca này?"
          message={
            <>
              Xóa hẳn {label}
              {assignee ? <> của <b>{assignee.fullName}</b></> : ''}. Muốn giữ ca để giao người khác thì
              dùng «Gỡ người khỏi ca» thay vì xóa.
            </>
          }
          confirmLabel="Xóa ca"
          onCancel={() => setConfirmingDelete(false)}
          onConfirm={() => {
            setConfirmingDelete(false);
            run('delete', () => deleteShift(shift.id), `Đã xóa ${label}.`);
          }}
        />
      )}
    </div>
  );
}

// ── Phần chọn giờ dùng chung: theo mẫu hoặc tự do (BR-SCH-04) ───────────────────

function ShiftTimeFields({ idPrefix, templates, value, onChange, date }) {
  const set = (patch) => onChange({ ...value, ...patch });
  const hours = resolveHours(value, templates);

  return (
    <>
      <div className="segmented" role="radiogroup" aria-label="Cách tạo ca">
        {[
          ['template', 'Theo mẫu ca'],
          ['free', 'Giờ tự do'],
        ].map(([mode, label]) => (
          <label key={mode} className={value.mode === mode ? 'is-selected' : ''}>
            <input
              type="radio"
              name={`${idPrefix}-mode`}
              value={mode}
              checked={value.mode === mode}
              onChange={() => set({ mode })}
            />
            {label}
          </label>
        ))}
      </div>

      {value.mode === 'template' &&
        (templates.length === 0 ? (
          <div className="alert alert--info">
            Chưa có mẫu ca nào đang dùng. Giám đốc tạo mẫu ở mục «Quy định & Mẫu ca»; trong lúc chờ có thể
            nhập giờ tự do.
          </div>
        ) : (
          <fieldset className="choice-group choice-fieldset">
            <legend className="field__label">Mẫu ca</legend>
            {templates.map((template) => (
              <label key={template.id} className={`choice ${value.templateId === template.id ? 'choice--active' : ''}`}>
                <input
                  type="radio"
                  name={`${idPrefix}-template`}
                  checked={value.templateId === template.id}
                  onChange={() => set({ templateId: template.id })}
                />
                <span>
                  <b>{template.name}</b>
                  <small>
                    {timeRange(template.startTime, template.endTime)}
                    {template.overnight ? ' (qua đêm)' : ''} · {formatHours(template.durationHours)}
                  </small>
                </span>
              </label>
            ))}
          </fieldset>
        ))}

      {value.mode === 'free' && (
        <div className="field-row">
          <label className="field" htmlFor={`${idPrefix}-start`}>
            <span className="field__label">Giờ bắt đầu <span className="req">*</span></span>
            <input id={`${idPrefix}-start`} type="time" value={value.start} onChange={(e) => set({ start: e.target.value })} />
          </label>
          <label className="field" htmlFor={`${idPrefix}-end`}>
            <span className="field__label">Giờ kết thúc <span className="req">*</span></span>
            <input id={`${idPrefix}-end`} type="time" value={value.end} onChange={(e) => set({ end: e.target.value })} />
          </label>
        </div>
      )}

      {hours && <ShiftPreview start={hours.start} end={hours.end} date={date} />}
    </>
  );
}

/** BR-SCH-03 — nói rõ ca qua đêm thuộc về ngày nào trước khi Manager bấm lưu. */
function ShiftPreview({ start, end, date }) {
  const overnight = isOvernight(start, end);
  const total = durationHours(start, end);
  return (
    <p className={`shift-preview ${total >= 24 ? 'shift-preview--warn' : ''}`}>
      Ca <b>{timeRange(start, end)}</b> · <b>{formatHours(total)}</b>
      {overnight && date && <> — qua đêm, toàn bộ giờ tính vào ngày {formatDayMonth(date)}</>}
      {total >= 24 && <> — giờ bắt đầu trùng giờ kết thúc nên thành ca 24 giờ</>}.
    </p>
  );
}

/**
 * Ô date hiển thị theo ngôn ngữ của HỆ ĐIỀU HÀNH (máy tiếng Anh hiện 10/05/2026 = tháng/ngày), nên
 * nhắc lại ngày đang chọn theo quy ước dd/MM/yyyy của dự án cho khỏi nhầm.
 */
function DateHelp({ date }) {
  return <span className="field__help">{date ? formatLongDate(date) : 'Chọn ngày bắt đầu ca.'}</span>;
}

/** design.md mục 5 — thông báo chặn: nền đỏ nhạt, nêu rõ vi phạm điều nào, không cho lưu. */
function BlockedAlert({ message }) {
  if (!message) return null;
  return (
    <div className="alert alert--error" role="alert">
      <b className="alert__title">Không lưu được — ca chưa được thay đổi</b>
      {message}
    </div>
  );
}

// ── Tiện ích ────────────────────────────────────────────────────────────────────

function initialTime(templates) {
  return templates.length > 0
    ? { mode: 'template', templateId: templates[0].id, start: '', end: '' }
    : { mode: 'free', templateId: '', start: '', end: '' };
}

/** Giờ bắt đầu / kết thúc đang chọn, lấy từ mẫu hoặc ô nhập; null khi chưa đủ. */
function resolveHours(time, templates) {
  if (time.mode === 'template') {
    const template = templates.find((t) => t.id === time.templateId);
    return template ? { start: hhmm(template.startTime), end: hhmm(template.endTime) } : null;
  }
  return time.start && time.end ? { start: time.start, end: time.end } : null;
}

function personOptionLabel(person) {
  const role = person.role === 'MANAGER' ? 'Quản lý' : person.positionName;
  const status = person.status === 'INACTIVE' ? ' · đang tạm khóa' : '';
  return `${person.fullName}${role ? ` — ${role}` : ''}${status}`;
}

function useCloseOnEscape(onClose, blocked) {
  useEffect(() => {
    const onKey = (event) => event.key === 'Escape' && !blocked && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose, blocked]);
}
