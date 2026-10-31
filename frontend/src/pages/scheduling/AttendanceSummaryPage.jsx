import { useEffect, useRef, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { fetchLocations } from '../../api/locations';
import { fetchAttendanceSummary } from '../../api/scheduling';
import { useAuth } from '../../context/AuthContext';
import { addMonths, currentMonth, formatMinutes, formatMonth } from './attendanceSummary';
import '../rooms/rooms.css';
import './scheduling.css';

/**
 * Tổng kết chuyên cần theo tháng (chấm công M2, chốt 01/11/2026) — mỗi nhân viên một dòng: số ca, tỉ lệ đúng
 * giờ, đi muộn / về sớm, vắng, quên kết thúc ca, chấm ngoài mạng khách sạn, giờ quản lý nhập. Chỉ để xem —
 * KHÔNG phải bảng công tính lương. Quản lý xem khách sạn mình; Giám đốc chọn khách sạn.
 */
export default function AttendanceSummaryPage() {
  const { user } = useAuth();
  const isDirector = user?.role === 'DIRECTOR';
  const [month, setMonth] = useState(currentMonth);
  const [locations, setLocations] = useState(null);
  const [locationId, setLocationId] = useState('');
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!isDirector) return;
    fetchLocations({ size: 200 })
      .then((page) => {
        const list = page.content ?? [];
        setLocations(list);
        setLocationId((current) => current || list[0]?.id || '');
      })
      .catch((err) => setError(readErrorMessage(err, 'Không tải được danh sách khách sạn.')));
  }, [isDirector]);

  const latest = useRef(0);
  useEffect(() => {
    if (isDirector && !locationId) return;
    const requestId = ++latest.current;
    setLoading(true);
    setError('');
    fetchAttendanceSummary({ month, locationId: isDirector ? locationId : undefined })
      .then((data) => requestId === latest.current && setRows(data))
      .catch((err) => requestId === latest.current && setError(readErrorMessage(err, 'Không tải được tổng kết.')))
      .finally(() => requestId === latest.current && setLoading(false));
  }, [month, locationId, isDirector]);

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Tổng quan › Tổng kết chuyên cần</p>
          <h1>Tổng kết chuyên cần</h1>
          <p className="muted">
            Số liệu chấm công theo tháng để theo dõi — không dùng để tính lương. Ca chưa tới giờ không tính vào tỉ
            lệ; ngày nghỉ phép không tính là phải làm.
          </p>
        </div>
      </div>

      <div className="sched-toolbar">
        <div className="week-nav" role="group" aria-label="Chọn tháng">
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setMonth(addMonths(month, -1))}>
            ‹ Tháng trước
          </button>
          <div className="week-nav__label" aria-live="polite">Tháng {formatMonth(month)}</div>
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setMonth(addMonths(month, 1))}>
            Tháng sau ›
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setMonth(currentMonth())}
            disabled={month === currentMonth()}
          >
            Tháng này
          </button>
        </div>
        {isDirector && locations && (
          <select
            className="board-select"
            value={locationId}
            onChange={(e) => setLocationId(e.target.value)}
            aria-label="Chọn khách sạn"
          >
            {locations.map((location) => (
              <option key={location.id} value={location.id}>{location.name}</option>
            ))}
          </select>
        )}
      </div>

      {error && <div className="alert alert--error" role="alert">{error}</div>}
      {!rows && !error && <p className="state">Đang tải…</p>}
      {rows && rows.length === 0 && (
        <div className="state state--empty"><p>Tháng này chưa có ca nào có người.</p></div>
      )}

      {rows && rows.length > 0 && (
        <div className="table-wrap" aria-busy={loading}>
          <table className="table attendance-table">
            <thead>
              <tr>
                <th>Nhân viên</th>
                <th title="Đã tới giờ / tổng số ca trong tháng">Ca</th>
                <th title="Ca vào đúng giờ trên số ca phải làm">Đúng giờ</th>
                <th>Đi muộn</th>
                <th>Về sớm</th>
                <th>Vắng</th>
                <th>Quên kết thúc ca</th>
                <th>Nghỉ phép</th>
                <th title="Ca có lần tự vào / ra ngoài mạng khách sạn">Ngoài mạng</th>
                <th title="Ca có giờ do quản lý nhập / sửa">Quản lý nhập</th>
                <th>Giờ làm</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.staffId}>
                  <td><b>{row.staffName ?? '—'}</b></td>
                  <td>{row.dueShifts}/{row.totalShifts}</td>
                  <td>{row.onTimePercent == null ? '—' : `${row.onTimePercent}%`}</td>
                  <td className={row.late > 0 ? 'attendance-table__late' : ''}>
                    {row.late > 0 ? `${row.late} lần · ${formatMinutes(row.lateMinutes)}` : '0'}
                  </td>
                  <td className={row.earlyLeave > 0 ? 'attendance-table__late' : ''}>
                    {row.earlyLeave > 0 ? `${row.earlyLeave} lần · ${formatMinutes(row.earlyLeaveMinutes)}` : '0'}
                  </td>
                  <td className={row.absent > 0 ? 'attendance-table__absent' : ''}>{row.absent}</td>
                  <td className={row.missedCheckOut > 0 ? 'attendance-table__late' : ''}>{row.missedCheckOut}</td>
                  <td>{row.onLeave}</td>
                  <td className={row.outsideNetwork > 0 ? 'attendance-table__late' : ''}>{row.outsideNetwork}</td>
                  <td>{row.managerEntered}</td>
                  <td>{formatMinutes(row.workedMinutes)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
