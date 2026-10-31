import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { fetchAttendanceEvents, updateAttendance } from '../../api/scheduling';
import { formatDate, formatDateTime } from '../../pages/rooms/format';
import { timeRange } from '../../pages/scheduling/scheduleFormat';
import { ATTENDANCE_METHOD_LABELS } from '../../pages/scheduling/attendance';

/** Khớp @Size(max = 500) của ManagerAttendanceRequest và cột shift_attendance_events.note. */
const NOTE_MAX = 500;

/** "2026-11-02T06:05:00" (LocalDateTime) → "2026-11-02T06:05" cho ô datetime-local. */
const toInput = (value) => (value ? String(value).slice(0, 16) : '');

/**
 * Quản lý nhập bù / sửa giờ vào – ra ca (chấm công M2). Lý do bắt buộc; giờ để trống là giữ nguyên giờ
 * đang có. Khung giờ hợp lệ (30 phút trước giờ bắt đầu … hết ca + 4 giờ, không ở tương lai) do backend kiểm,
 * câu lỗi hiện nguyên văn. Bên dưới là lịch sử mọi lần ghi của ca: ai ghi, cách ghi, IP, thiết bị, lý do.
 *
 * @param row     dòng bảng chấm công `{ shift, staffName }`
 * @param onSaved (ca sau khi lưu) — trang cha báo và tải lại
 */
export default function AttendanceEditDialog({ row, onClose, onSaved }) {
  const { shift, staffName } = row;
  const [checkInAt, setCheckInAt] = useState(toInput(shift.checkInAt));
  const [checkOutAt, setCheckOutAt] = useState(toInput(shift.checkOutAt));
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [events, setEvents] = useState(null);
  const [eventsError, setEventsError] = useState('');

  useEffect(() => {
    let cancelled = false;
    fetchAttendanceEvents(shift.id)
      .then((list) => !cancelled && setEvents(list))
      .catch((err) => !cancelled && setEventsError(readErrorMessage(err, 'Không tải được lịch sử chấm công.')));
    return () => {
      cancelled = true;
    };
  }, [shift.id]);

  // Esc để đóng, trừ lúc đang gửi — tránh đóng giữa chừng rồi không biết kết quả.
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  const changed = checkInAt !== toInput(shift.checkInAt) || checkOutAt !== toInput(shift.checkOutAt);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!changed || note.trim() === '') return;   // nút lưu đã mờ; backend vẫn kiểm lại
    setSubmitting(true);
    setError('');
    try {
      onSaved(await updateAttendance(shift.id, {
        checkInAt: checkInAt !== toInput(shift.checkInAt) ? checkInAt : null,
        checkOutAt: checkOutAt !== toInput(shift.checkOutAt) ? checkOutAt : null,
        note: note.trim(),
      }));
    } catch (err) {
      setError(readErrorMessage(err, 'Không lưu được giờ chấm công.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="attendance-edit-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="attendance-edit-title">Chấm công — {staffName ?? 'Nhân viên'}</h2>

        <div className="modal__body">
          <p className="muted">
            Ca {formatDate(shift.shiftDate)} · {timeRange(shift.startTime, shift.endTime)}
            {shift.overnight ? ' (qua đêm)' : ''}
          </p>

          <div className="attendance-edit__times">
            <label className="field">
              <span className="field__label">Giờ vào ca</span>
              <input type="datetime-local" value={checkInAt} onChange={(e) => setCheckInAt(e.target.value)} />
            </label>
            <label className="field">
              <span className="field__label">Giờ ra ca</span>
              <input type="datetime-local" value={checkOutAt} onChange={(e) => setCheckOutAt(e.target.value)} />
            </label>
          </div>
          <small className="field__help">
            Vào ca từ 30 phút trước giờ bắt đầu tới hết ca; ra ca muộn nhất 4 giờ sau giờ kết thúc. Đã có giờ thì
            không xóa trắng được, chỉ sửa.
          </small>

          <label className="field">
            <span className="field__label">
              Lý do <span className="req">*</span>
            </span>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={NOTE_MAX}
              rows={2}
              placeholder="Ví dụ: điện thoại hết pin, đã xác nhận qua camera lễ tân"
            />
            <small className="field__help">Lưu kèm tên bạn trong lịch sử chấm công. {note.length}/{NOTE_MAX}</small>
          </label>

          {error && (
            <div className="alert alert--error" role="alert">
              {error}
            </div>
          )}

          <h3 className="attendance-edit__history-title">Lịch sử chấm công</h3>
          {eventsError && <p className="muted">{eventsError}</p>}
          {!events && !eventsError && <p className="muted">Đang tải…</p>}
          {events && events.length === 0 && (
            <p className="muted">Chưa có lần ghi nào{shift.checkInAt ? ' (giờ hiện có được ghi trước khi có lịch sử)' : ''}.</p>
          )}
          {events && events.length > 0 && (
            <ul className="attendance-events">
              {events.map((event) => (
                <li key={event.id}>
                  <b>
                    {event.eventType === 'CHECK_IN' ? 'Vào ca' : 'Ra ca'} {formatDateTime(event.occurredAt)}
                  </b>
                  {' · '}{ATTENDANCE_METHOD_LABELS[event.method] ?? event.method}
                  {event.recordedByName ? ` (${event.recordedByName})` : ''}
                  <small>
                    Ghi lúc {formatDateTime(event.recordedAt)}
                    {event.ipAddress ? ` · IP ${event.ipAddress}` : ''}
                    {event.deviceId ? ` · thiết bị ${event.deviceId.slice(0, 8)}` : ''}
                    {event.networkCheck === 'MATCHED' ? ` · mạng khách sạn (${event.matchedCidr})` : ''}
                    {event.networkCheck === 'OUTSIDE' ? ' · NGOÀI mạng khách sạn' : ''}
                  </small>
                  {event.userAgent && <small className="attendance-events__agent">{event.userAgent}</small>}
                  {event.note && <small>Lý do: {event.note}</small>}
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={submitting}>
            Đóng
          </button>
          <button type="submit" className="btn btn--primary" disabled={submitting || !changed || note.trim() === ''}>
            {submitting ? 'Đang lưu…' : 'Lưu giờ chấm công'}
          </button>
        </div>
      </form>
    </div>
  );
}
