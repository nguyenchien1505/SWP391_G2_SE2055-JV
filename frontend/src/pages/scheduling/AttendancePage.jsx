import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { fetchAttendance } from '../../api/scheduling';
import AttendanceEditDialog from '../../components/scheduling/AttendanceEditDialog';
import AttendanceNetworkDialog from '../../components/scheduling/AttendanceNetworkDialog';
import { useAuth } from '../../context/AuthContext';
import { formatClock, formatDate, todayIso } from '../rooms/format';
import {
  ATTENDANCE_LABELS,
  ATTENDANCE_METHOD_LABELS,
  ATTENDANCE_TONES,
} from './attendance';
import { addDays, isoWeekNumber, timeRange, weekDays, weekStartOf } from './scheduleFormat';
import '../rooms/rooms.css';
import './scheduling.css';

/** Bộ lọc nhanh: việc cần quản lý xử lý đứng đầu. */
const FILTERS = [
  { key: 'all', label: 'Tất cả', match: () => true },
  {
    key: 'action',
    label: 'Cần xử lý',
    match: (shift) => ['ABSENT', 'MISSED_CHECK_OUT'].includes(shift.attendanceStatus),
  },
  { key: 'late', label: 'Đi muộn / về sớm', match: (shift) => shift.lateMinutes > 0 || shift.earlyLeaveMinutes > 0 },
  { key: 'leave', label: 'Nghỉ phép', match: (shift) => shift.attendanceStatus === 'ON_LEAVE' },
];

/** Nút trên từng dòng — gọi đúng việc quản lý sắp làm. */
function editLabel(status) {
  if (status === 'ABSENT' || status === 'OPEN' || status === 'ON_LEAVE') return 'Nhập bù';
  if (status === 'MISSED_CHECK_OUT') return 'Nhập giờ ra';
  return 'Sửa';
}

const clock = (value) => (value ? formatClock(new Date(value)) : '—');

/**
 * Chấm công — Quản lý chi nhánh xem giờ vào / ra ca của nhân viên khách sạn mình theo tuần, nhập bù cho ca
 * Vắng / Quên kết thúc ca và sửa giờ, luôn kèm lý do (chấm công M2, chốt 31/10/2026).
 *
 * Tình trạng (Vắng, Đi muộn…) do backend suy ra từ giờ ca và giờ của khách sạn; trang chỉ hiện và lọc. Cột
 * «Nguồn» cho biết giờ hiện tại do nhân viên tự bấm hay quản lý nhập — chi tiết IP / thiết bị ở hộp sửa.
 */
export default function AttendancePage() {
  const today = todayIso();
  const [weekStart, setWeekStart] = useState(() => weekStartOf(today));
  const days = useMemo(() => weekDays(weekStart), [weekStart]);
  const [rows, setRows] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('all');
  const [editing, setEditing] = useState(null);
  const [banner, setBanner] = useState('');
  const [networkOpen, setNetworkOpen] = useState(false);
  const { user } = useAuth();

  // Bấm chuyển tuần nhanh hơn tốc độ mạng: chỉ nhận kết quả của lần gọi mới nhất.
  const latestRequest = useRef(0);
  const load = useCallback(async () => {
    const requestId = ++latestRequest.current;
    setLoading(true);
    setError('');
    try {
      const data = await fetchAttendance({ from: days[0], to: days[6] });
      if (requestId === latestRequest.current) setRows(data);
    } catch (err) {
      if (requestId === latestRequest.current) setError(readErrorMessage(err, 'Không tải được bảng chấm công.'));
    } finally {
      if (requestId === latestRequest.current) setLoading(false);
    }
  }, [days]);

  useEffect(() => {
    load();
  }, [load]);

  const current = FILTERS.find((f) => f.key === filter);
  const visible = (rows ?? []).filter((row) => current.match(row.shift));
  const countOf = (key) => (rows ?? []).filter((row) => FILTERS.find((f) => f.key === key).match(row.shift)).length;

  function handleSaved(shift) {
    const name = editing?.staffName ?? 'nhân viên';
    setEditing(null);
    setBanner(`Đã lưu giờ chấm công ca ${formatDate(shift.shiftDate)} của ${name}.`);
    load();
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Lịch làm việc › Chấm công</p>
          <h1>Chấm công</h1>
          <p className="muted">
            Nhân viên tự vào ca từ 30 phút trước giờ bắt đầu tới hết ca, kết thúc ca tới 4 giờ sau giờ kết thúc.
            Ca hết giờ mà chưa vào là Vắng — nhập bù nếu nhân viên có đi làm.
          </p>
        </div>
        {/* Giám đốc chỉ xem chấm công; mạng chấm công do Manager khai. */}
        {user?.role === 'MANAGER' && (
          <button type="button" className="btn btn--ghost" onClick={() => setNetworkOpen(true)}>
            Mạng chấm công
          </button>
        )}
      </div>

      <div className="sched-toolbar">
        <div className="week-nav" role="group" aria-label="Chọn tuần">
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setWeekStart(addDays(weekStart, -7))}>
            ‹ Tuần trước
          </button>
          <div className="week-nav__label" aria-live="polite">
            Tuần {isoWeekNumber(weekStart)}
            <small>
              {formatDate(days[0])} – {formatDate(days[6])}
            </small>
          </div>
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setWeekStart(addDays(weekStart, 7))}>
            Tuần sau ›
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setWeekStart(weekStartOf(today))}
            disabled={days.includes(today)}
          >
            Tuần này
          </button>
        </div>
        {rows && (
          <div className="sched-summary" role="group" aria-label="Lọc theo tình trạng">
            {FILTERS.map((f) => (
              <button
                key={f.key}
                type="button"
                className={`chip ${filter === f.key ? 'chip--green' : ''}`}
                aria-pressed={filter === f.key}
                onClick={() => setFilter(f.key)}
              >
                {f.label} ({countOf(f.key)})
              </button>
            ))}
          </div>
        )}
      </div>

      {banner && (
        <div className="alert alert--success" role="status">
          {banner}
          <button type="button" className="alert__close" onClick={() => setBanner('')} aria-label="Đóng">
            ×
          </button>
        </div>
      )}
      {error && <div className="alert alert--error" role="alert">{error}</div>}
      {!rows && !error && <p className="state">Đang tải bảng chấm công…</p>}

      {rows && visible.length === 0 && (
        <div className="state state--empty">
          <p>{rows.length === 0 ? 'Tuần này chưa có ca nào có người.' : 'Không có ca nào khớp bộ lọc.'}</p>
        </div>
      )}

      {rows && visible.length > 0 && (
        <div className="table-wrap" aria-busy={loading}>
          <table className="table attendance-table">
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Nhân viên</th>
                <th>Ca</th>
                <th>Vào</th>
                <th>Ra</th>
                <th>Tình trạng</th>
                <th>Nguồn</th>
                <th aria-label="Thao tác" />
              </tr>
            </thead>
            <tbody>
              {visible.map((row) => {
                const { shift } = row;
                const status = shift.attendanceStatus;
                return (
                  <tr key={shift.id}>
                    <td>{formatDate(shift.shiftDate)}</td>
                    <td><b>{row.staffName ?? '—'}</b></td>
                    <td>
                      {shift.sourceTemplateName ? `${shift.sourceTemplateName} · ` : ''}
                      {timeRange(shift.startTime, shift.endTime)}
                    </td>
                    <td>
                      {clock(shift.checkInAt)}
                      {shift.lateMinutes > 0 && <small className="attendance-table__late"> muộn {shift.lateMinutes}′</small>}
                    </td>
                    <td>
                      {clock(shift.checkOutAt)}
                      {shift.earlyLeaveMinutes > 0 && (
                        <small className="attendance-table__late"> sớm {shift.earlyLeaveMinutes}′</small>
                      )}
                    </td>
                    <td>
                      {status && (
                        <span className={`tt-pill tt-pill--${ATTENDANCE_TONES[status]}`}>{ATTENDANCE_LABELS[status]}</span>
                      )}
                    </td>
                    <td>
                      <small>
                        {[row.checkInMethod, row.checkOutMethod]
                          .filter(Boolean)
                          .map((method) => ATTENDANCE_METHOD_LABELS[method])
                          .filter((label, index, all) => all.indexOf(label) === index)
                          .join(' · ') || '—'}
                      </small>
                      {row.outsideNetwork && (
                        <span className="tt-pill tt-pill--warn cell-block__flag" title="Tự vào / ra ca từ mạng ngoài khách sạn">
                          Ngoài mạng khách sạn
                        </span>
                      )}
                    </td>
                    <td className="col-actions">
                      {status !== 'UPCOMING' && (
                        <button type="button" className="btn btn--ghost btn--sm" onClick={() => setEditing(row)}>
                          {editLabel(status)}
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <AttendanceEditDialog row={editing} onClose={() => setEditing(null)} onSaved={handleSaved} />
      )}
      {networkOpen && <AttendanceNetworkDialog onClose={() => setNetworkOpen(false)} onChanged={load} />}
    </div>
  );
}
