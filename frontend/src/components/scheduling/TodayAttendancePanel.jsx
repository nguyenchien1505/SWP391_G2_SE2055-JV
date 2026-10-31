import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { fetchTodayAttendance } from '../../api/scheduling';
import { formatClock } from '../../pages/rooms/format';
import { roomStatusMeta } from '../../pages/rooms/roomLabels';
import { timeRange } from '../../pages/scheduling/scheduleFormat';
import { ATTENDANCE_LABELS, ATTENDANCE_TONES } from '../../pages/scheduling/attendance';
import '../../pages/scheduling/scheduling.css';

/** Tình trạng cần quản lý chú ý — đứng đầu danh sách. */
const NEEDS_ACTION = ['ABSENT', 'MISSED_CHECK_OUT'];

/** Chưa vào ca dù đã qua giờ bắt đầu — vẫn trong khung vào ca nên backend báo OPEN. */
function isLate(shift, now) {
  return shift.attendanceStatus === 'OPEN' && `${shift.shiftDate}T${shift.startTime}` < now;
}

const clock = (value) => formatClock(new Date(value));

/**
 * Khối «Hôm nay» trên màn hình đầu của Quản lý chi nhánh (chấm công M2, chốt 01/11/2026) — biến dữ liệu chấm
 * công thành việc cần làm ngay:
 *   - ca đang không có lễ tân nào trong ca (quá giờ bắt đầu) — gọi người thay;
 *   - ai quá giờ chưa vào, ai vắng, ai quên kết thúc ca, ai chấm ngoài mạng khách sạn;
 *   - lễ tân nhận / trả phòng lúc chưa vào ca (vẫn cho làm, chỉ báo).
 * Không có thông báo đẩy (DM-16): tải lại khi bấm «Tải lại» hoặc mở lại tab.
 */
export default function TodayAttendancePanel() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await fetchTodayAttendance());
      setError('');
    } catch (err) {
      setError(readErrorMessage(err, 'Không tải được tình hình hôm nay.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const onVisible = () => document.visibilityState === 'visible' && load();
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [load]);

  const rows = data?.rows ?? [];
  const now = data?.now ?? '';
  const working = rows.filter((row) => row.shift.attendanceStatus === 'WORKING');
  const attention = rows.filter((row) => NEEDS_ACTION.includes(row.shift.attendanceStatus) || isLate(row.shift, now)
    || row.outsideNetwork);

  return (
    <section className="panel today-panel" aria-label="Chấm công hôm nay">
      <div className="panel__head">
        <h2>Hôm nay</h2>
        <div className="today-panel__tools">
          {data && <small className="muted">Cập nhật {clock(data.now)}</small>}
          <button type="button" className="btn btn--ghost btn--sm" onClick={load} disabled={loading}>
            {loading ? 'Đang tải…' : 'Tải lại'}
          </button>
          <Link className="btn btn--ghost btn--sm" to="/cham-cong">Bảng chấm công</Link>
        </div>
      </div>

      {error && <div className="alert alert--error" role="alert">{error}</div>}
      {!data && !error && <p className="muted">Đang tải…</p>}

      {data && (
        <>
          <div className="sched-summary">
            <span className="chip chip--green">Đang làm: {working.length}</span>
            <span className="chip">Ca hôm nay: {rows.length}</span>
            <span className={`chip ${attention.length > 0 ? 'chip--warn' : ''}`}>Cần chú ý: {attention.length}</span>
          </div>

          {data.receptionGaps.map((gap) => (
            <div key={`${gap.shiftDate}-${gap.startTime}`} className="alert alert--error" role="alert">
              <b>{gap.templateName ?? 'Ca'} {timeRange(gap.startTime, gap.endTime)} đang không có lễ tân trong ca</b>
              {' '}— đã qua giờ bắt đầu {gap.minutesSinceStart} phút.{' '}
              {gap.receptionistNames.length > 0
                ? `Lễ tân của ca chưa vào: ${gap.receptionistNames.join(', ')}.`
                : 'Ca này không còn lễ tân nào đang trong ca.'}
            </div>
          ))}

          {attention.length > 0 && (
            <ul className="today-panel__list">
              {attention.map((row) => {
                const { shift } = row;
                const status = isLate(shift, now) ? null : shift.attendanceStatus;
                return (
                  <li key={shift.id}>
                    <b>{row.staffName ?? 'Nhân viên'}</b>
                    <span className="muted"> · {timeRange(shift.startTime, shift.endTime)}</span>
                    {isLate(shift, now) && <span className="tt-pill tt-pill--warn">Quá giờ chưa vào ca</span>}
                    {status && NEEDS_ACTION.includes(status) && (
                      <span className={`tt-pill tt-pill--${ATTENDANCE_TONES[status]}`}>{ATTENDANCE_LABELS[status]}</span>
                    )}
                    {row.outsideNetwork && <span className="tt-pill tt-pill--warn">Ngoài mạng khách sạn</span>}
                  </li>
                );
              })}
            </ul>
          )}

          {data.actionsWithoutShift.length > 0 && (
            <>
              <h3 className="today-panel__title">Lễ tân thao tác phòng lúc chưa vào ca</h3>
              <ul className="today-panel__list">
                {data.actionsWithoutShift.map((action) => (
                  <li key={`${action.changedAt}-${action.roomNumber}`}>
                    <b>{action.staffName}</b>
                    <span className="muted">
                      {' '}· {clock(action.changedAt)} · phòng {action.roomNumber ?? '—'}:{' '}
                      {roomStatusMeta(action.fromStatus).label} →{' '}
                      {roomStatusMeta(action.toStatus).label}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}

          {rows.length === 0 && <p className="muted">Hôm nay chưa có ca nào có người.</p>}
          {rows.length > 0 && attention.length === 0 && data.receptionGaps.length === 0
            && data.actionsWithoutShift.length === 0 && <p className="muted">Mọi ca đang ổn.</p>}
        </>
      )}
    </section>
  );
}
