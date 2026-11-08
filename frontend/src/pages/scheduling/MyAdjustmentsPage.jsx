import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { cancelAdjustment, createAdjustment, fetchMyAdjustments } from '../../api/attendanceAdjustments';
import { fetchShifts } from '../../api/scheduling';
import AdjustmentDetails from '../../components/scheduling/AdjustmentDetails';
import ConfirmDialog from '../../components/ConfirmDialog';
import { formatDate, todayIso } from '../rooms/format';
import { addDays, timeRange } from './scheduleFormat';
import { ATTENDANCE_LABELS } from './attendance';
import '../rooms/rooms.css';
import './scheduling.css';

/** Khớp backend: chỉ điều chỉnh ca trong 31 ngày gần đây; lý do tối đa 400 ký tự. */
const MAX_AGE_DAYS = 31;
const REASON_MAX = 400;

/** "2026-11-02T06:05:00" → "2026-11-02T06:05" cho ô datetime-local. */
const toInput = (value) => (value ? String(value).slice(0, 16) : '');

/** Gợi ý giờ khi ca chưa có giờ: giờ bắt đầu / kết thúc theo lịch (ca qua đêm kết thúc hôm sau). */
function plannedTimes(shift) {
  const start = `${shift.shiftDate}T${String(shift.startTime).slice(0, 5)}`;
  const endDate = shift.overnight ? addDays(shift.shiftDate, 1) : shift.shiftDate;
  return { start, end: `${endDate}T${String(shift.endTime).slice(0, 5)}` };
}

/**
 * Điều chỉnh công — nhân viên gửi đơn sửa giờ vào / ra ca của mình khi quên bấm, mất mạng hay bấm sai (chấm công
 * M2, chốt 01/11/2026). Quản lý khách sạn duyệt thì giờ được ghi vào ca. Mở từ khối «Ca hôm nay» thì chọn sẵn ca
 * qua `?shift=<id>`.
 */
export default function MyAdjustmentsPage() {
  const [searchParams] = useSearchParams();
  const today = todayIso();
  const [shifts, setShifts] = useState(null);
  const [requests, setRequests] = useState(null);
  const [error, setError] = useState('');
  const [banner, setBanner] = useState(null);

  const [shiftId, setShiftId] = useState(searchParams.get('shift') ?? '');
  const [checkInAt, setCheckInAt] = useState('');
  const [checkOutAt, setCheckOutAt] = useState('');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [cancelling, setCancelling] = useState(null);

  const load = useCallback(async () => {
    try {
      const [list, mine] = await Promise.all([
        fetchShifts({ from: addDays(today, -MAX_AGE_DAYS), to: today }),
        fetchMyAdjustments(),
      ]);
      // Ca đã tới giờ vào ca mới điều chỉnh được; mới nhất trước.
      setShifts(list.filter((shift) => shift.attendanceStatus && shift.attendanceStatus !== 'UPCOMING')
        .sort((a, b) => `${b.shiftDate}${b.startTime}`.localeCompare(`${a.shiftDate}${a.startTime}`)));
      setRequests(mine.content ?? []);
      setError('');
    } catch (err) {
      setError(readErrorMessage(err, 'Không tải được ca và đơn điều chỉnh của bạn.'));
    }
  }, [today]);

  useEffect(() => {
    load();
  }, [load]);

  const pendingShiftIds = useMemo(
    () => new Set((requests ?? []).filter((r) => r.status === 'PENDING').map((r) => r.shift?.id)),
    [requests],
  );
  const selected = (shifts ?? []).find((shift) => shift.id === shiftId) ?? null;

  // Chọn ca thì điền sẵn giờ đang có, hoặc giờ theo lịch nếu ca chưa có giờ.
  useEffect(() => {
    if (!selected) return;
    const planned = plannedTimes(selected);
    setCheckInAt(toInput(selected.checkInAt) || planned.start);
    setCheckOutAt(toInput(selected.checkOutAt) || (selected.checkInAt ? '' : planned.end));
  }, [selected]);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!selected || reason.trim() === '') return;
    setSubmitting(true);
    setBanner(null);
    try {
      await createAdjustment({
        shiftId: selected.id,
        checkInAt: checkInAt !== toInput(selected.checkInAt) ? checkInAt : null,
        checkOutAt: checkOutAt !== toInput(selected.checkOutAt) ? checkOutAt : null,
        reason: reason.trim(),
      });
      setBanner({ type: 'success', text: 'Đã gửi đơn — chờ quản lý duyệt.' });
      setShiftId('');
      setReason('');
      await load();
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không gửi được đơn.') });
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancel() {
    const request = cancelling;
    setCancelling(null);
    try {
      await cancelAdjustment(request.id);
      setBanner({ type: 'success', text: 'Đã hủy đơn.' });
      await load();
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không hủy được đơn.') });
    }
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Đơn từ › Điều chỉnh công</p>
          <h1>Điều chỉnh công</h1>
          <p className="muted">
            Quên bấm vào / kết thúc ca, mất mạng hay bấm sai giờ thì gửi đơn ở đây kèm giờ đúng và lý do. Quản lý
            duyệt thì giờ được ghi vào ca.
          </p>
        </div>
      </div>

      {banner && (
        <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="status">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">×</button>
        </div>
      )}
      {error && <div className="alert alert--error" role="alert">{error}</div>}

      <section className="panel">
        <div className="panel__head"><h2>Gửi đơn mới</h2></div>
        {!shifts && !error && <p className="muted">Đang tải…</p>}
        {shifts && shifts.length === 0 && (
          <p className="muted">Chưa có ca nào trong {MAX_AGE_DAYS} ngày gần đây để điều chỉnh.</p>
        )}
        {shifts && shifts.length > 0 && (
          <form className="adjustment-form" onSubmit={handleSubmit} noValidate>
            <label className="field">
              <span className="field__label">Ca <span className="req">*</span></span>
              <select value={shiftId} onChange={(e) => setShiftId(e.target.value)}>
                <option value="">— Chọn ca —</option>
                {shifts.map((shift) => (
                  <option key={shift.id} value={shift.id} disabled={pendingShiftIds.has(shift.id)}>
                    {formatDate(shift.shiftDate)} · {timeRange(shift.startTime, shift.endTime)}
                    {' · '}{ATTENDANCE_LABELS[shift.attendanceStatus] ?? ''}
                    {pendingShiftIds.has(shift.id) ? ' (đang có đơn chờ)' : ''}
                  </option>
                ))}
              </select>
            </label>

            {selected && (
              <>
                <div className="attendance-edit__times">
                  <label className="field">
                    <span className="field__label">Giờ vào ca đúng</span>
                    <input type="datetime-local" value={checkInAt} onChange={(e) => setCheckInAt(e.target.value)} />
                  </label>
                  <label className="field">
                    <span className="field__label">Giờ ra ca đúng</span>
                    <input type="datetime-local" value={checkOutAt} onChange={(e) => setCheckOutAt(e.target.value)} />
                  </label>
                </div>
                <small className="field__help">
                  Giờ vào từ 30 phút trước giờ bắt đầu tới hết ca; giờ ra muộn nhất 4 giờ sau giờ kết thúc. Để trống
                  giờ ra nếu bạn vẫn đang trong ca.
                </small>
                <label className="field">
                  <span className="field__label">Lý do <span className="req">*</span></span>
                  <textarea
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    maxLength={REASON_MAX}
                    rows={2}
                    placeholder="Ví dụ: điện thoại hết pin lúc vào ca, đã báo lễ tân trực"
                  />
                  <small className="field__help">{reason.length}/{REASON_MAX}</small>
                </label>
                <button type="submit" className="btn btn--primary" disabled={submitting || reason.trim() === ''}>
                  {submitting ? 'Đang gửi…' : 'Gửi đơn'}
                </button>
              </>
            )}
          </form>
        )}
      </section>

      <section className="panel">
        <div className="panel__head"><h2>Đơn của tôi</h2></div>
        {requests && requests.length === 0 && <p className="muted">Bạn chưa gửi đơn nào.</p>}
        {requests && requests.length > 0 && (
          <ul className="adjustment-list">
            {requests.map((request) => (
              <li key={request.id}>
                <AdjustmentDetails request={request} />
                {request.status === 'PENDING' && (
                  <button type="button" className="btn btn--ghost btn--sm" onClick={() => setCancelling(request)}>
                    Hủy đơn
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {cancelling && (
        <ConfirmDialog
          title="Hủy đơn điều chỉnh?"
          message="Đơn sẽ không còn chờ quản lý duyệt nữa."
          confirmLabel="Hủy đơn"
          onConfirm={handleCancel}
          onCancel={() => setCancelling(null)}
        />
      )}
    </div>
  );
}
