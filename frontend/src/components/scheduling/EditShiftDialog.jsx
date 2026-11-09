import { useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { assignShift, deleteShift, unassignShift, updateShift } from '../../api/scheduling';
import ConfirmDialog from '../ConfirmDialog';
import { formatDateTime, todayIso } from '../../pages/rooms/format';
import {
  UNASSIGNED_REASON_LABEL,
  formatDayMonth,
  formatHours,
  formatLongDate,
  shiftHasEnded,
  hhmm,
  timeRange,
} from '../../pages/scheduling/scheduleFormat';
import { hasReceptionist, isReceptionist, personOptionLabel } from '../../pages/scheduling/schedulePeople';
import { BlockedAlert, DateHelp, ShiftTimeFields, resolveHours, useCloseOnEscape } from './shiftFields';

/** Vì sao ca bị khóa — nói đúng tình trạng chấm công và việc quản lý nên làm tiếp. */
function lockedMessage(shift, checkedIn) {
  const frozen = 'không đổi giờ, đổi người hay xóa được nữa';
  if (checkedIn) {
    return `Ca đã check-in nên ${frozen} — giờ chấm công nằm ngay trên ca này.`;
  }
  if (shift.attendanceStatus === 'ON_LEAVE') {
    return `Ca đã qua và trùng ngày nghỉ phép đã duyệt — ${frozen}, ca được giữ làm lịch sử nghỉ phép.`;
  }
  return `Ca đã kết thúc mà nhân viên chưa vào ca (Vắng) — ${frozen}. `
    + 'Nhân viên có đi làm thì nhập bù ở màn Chấm công.';
}

/**
 * Sửa MỘT ca đã có — BR-SCH-02, BR-SCH-04, BR-SCH-24, DM-15.
 *
 * Tách thành các thao tác riêng, mỗi thao tác đúng MỘT lệnh gọi API (gỡ người / giao người / đổi ngày
 * giờ / xóa) — gộp vào một nút "Lưu" thì một bước bị chặn giữa chừng sẽ để lại ca đã đổi một nửa.
 * Mọi kiểm tra quy định chạy ở backend lúc lưu; bị chặn thì hộp thoại hiện nguyên văn lý do.
 *
 * Quy tắc mỗi ca theo mẫu có ít nhất 1 lễ tân (chốt 05/10/2026) được nhắc TRƯỚC khi bấm: người này là
 * lễ tân duy nhất của một ca còn người khác thì báo trước là không gỡ / xóa / dời được; ca chưa có lễ
 * tân thì chỉ giao được chỗ trống cho một lễ tân.
 *
 * @param shifts        mọi ca của tuần đang xem — để biết ai đang cùng ca
 * @param people        người giao ca được (nhân viên chưa nghỉ việc của khách sạn)
 * @param peopleById    tra tên cả người đã nghỉ việc / đã chuyển đi cho ca cũ
 * @param templates     mẫu ca đang dùng — chỉ mẫu này được chọn (BR-SCH-22)
 * @param templatesById tra tên cả mẫu đã tắt cho ca cũ
 * @param onSaved       nhận câu thông báo sau khi lưu xong; trang tự đóng hộp thoại và tải lại lịch
 */
export default function EditShiftDialog({
  shift, shifts, people, peopleById, templates, templatesById, onClose, onSaved,
}) {
  const checkedIn = Boolean(shift.checkInAt);
  // Chấm công M2: ca có người đã hết giờ là dữ liệu chấm công (Vắng, Nghỉ phép…) — backend chặn sửa / gỡ / xóa,
  // nên khóa như ca đã check-in. `ended` do backend tính theo giờ khách sạn, không tự suy từ tình trạng.
  const ended = Boolean(shift.staffId) && shift.ended === true;
  const locked = checkedIn || ended;
  // Chỗ trống đã hết giờ: không giao được ai nữa (chốt 09/11/2026) — chỉ dời sang ngày khác hoặc xóa.
  const openEnded = !shift.staffId && shiftHasEnded(shift);
  const assignee = shift.staffId ? peopleById.get(shift.staffId) : null;
  const template = shift.sourceTemplateId ? templatesById.get(shift.sourceTemplateId) : null;

  // Những người KHÁC đang cùng ca theo mẫu này hôm đó (một ca = một mẫu trong một ngày).
  const crewmates = template
    ? shifts
      .filter((s) => s.id !== shift.id && s.staffId && s.shiftDate === shift.shiftDate
        && s.sourceTemplateId === shift.sourceTemplateId)
      .map((s) => peopleById.get(s.staffId))
    : [];
  const onlyReceptionist = Boolean(template) && isReceptionist(assignee) && crewmates.length > 0
    && !hasReceptionist(crewmates);
  const crewLacksReception = Boolean(template) && !hasReceptionist(crewmates);
  const currentStart = hhmm(shift.startTime);
  const currentEnd = hhmm(shift.endTime);

  const [newStaffId, setNewStaffId] = useState('');
  const [date, setDate] = useState(shift.shiftDate);
  // Mẫu đã tắt (BR-SCH-22) hay không còn thuộc bộ mẫu chi nhánh đang dùng thì không chọn lại được.
  const usable = Boolean(template) && templates.some((t) => t.id === template.id);
  const [time, setTime] = useState(() =>
    // Mẫu không chọn lại được thì mở sẵn ô giờ tự do với giờ hiện tại.
    usable
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
  // Giao chỗ trống của ca theo mẫu chưa có lễ tân: người được giao phải là lễ tân.
  const newcomer = newStaffId ? peopleById.get(newStaffId) : null;
  const assignNeedsReceptionist = crewLacksReception && Boolean(newcomer) && !isReceptionist(newcomer);

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
              {template ? template.name : shift.sourceTemplateId ? 'Mẫu ca khác' : 'Ca tự do'}
              {template && !usable ? (template.active ? ' (không thuộc bộ mẫu đang dùng)' : ' (mẫu đã tắt)') : ''}
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

        {onlyReceptionist && !locked && (
          <div className="alert alert--warn" role="status">
            <b className="alert__title">{assignee.fullName} là lễ tân duy nhất của ca này</b>
            Mỗi ca theo mẫu đã có người phải có ít nhất 1 lễ tân. Giao thêm một lễ tân khác cho ca này trước,
            rồi mới gỡ, xóa hoặc dời ca của {assignee.fullName}.
          </div>
        )}

        {locked ? (
          <div className="alert alert--info">
            {lockedMessage(shift, checkedIn)}
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
              ) : openEnded ? (
                <p className="muted">Ca đã qua — không giao người được nữa. Dời sang ngày khác hoặc xóa chỗ trống này.</p>
              ) : (
                <>
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
                      disabled={!newStaffId || assignNeedsReceptionist || Boolean(busy)}
                      onClick={() => run('assign', () => assignShift(shift.id, newStaffId),
                        `Đã giao ${label} cho ${peopleById.get(newStaffId)?.fullName ?? 'nhân viên'}.`)}
                    >
                      {busy === 'assign' ? 'Đang kiểm tra…' : 'Giao ca'}
                    </button>
                  </div>
                  {crewLacksReception && (
                    <p className={`field__help ${assignNeedsReceptionist ? 'is-error' : ''}`}>
                      Ca này chưa có lễ tân — chỗ trống chỉ giao được cho người có quyền Lễ tân.
                    </p>
                  )}
                </>
              )}
            </section>

            <section className="dialog-section" aria-labelledby="shift-time-title">
              <h3 id="shift-time-title">Đổi ngày giờ</h3>
              <label className="field" htmlFor="shift-edit-date">
                <span className="field__label">Ngày bắt đầu ca</span>
                <input id="shift-edit-date" type="date" value={date} min={todayIso()} onChange={(e) => setDate(e.target.value)} />
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

        <div className={`modal__actions ${locked ? '' : 'modal__actions--split'}`}>
          {!locked && (
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
