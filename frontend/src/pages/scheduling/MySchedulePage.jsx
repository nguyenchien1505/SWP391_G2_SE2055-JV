import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { fetchShifts } from '../../api/scheduling';
import { formatDate, todayIso } from '../rooms/format';
import { buildPersonalShiftBoard } from './personalShifts';
import {
  WEEKDAY_SHORT,
  addDays,
  dayClass,
  formatDayMonth,
  formatHours,
  isoWeekNumber,
  shiftTone,
  timeRange,
  weekDays,
  weekStartOf,
} from './scheduleFormat';
import './scheduling.css';

/**
 * Lịch cá nhân — nhân viên xem ca làm việc của chính mình (BR-PERM-04..06). Cùng cách chia với bảng xếp
 * lịch của quản lý: hàng là ca (Ca sáng, Ca chiều… «Ca giờ khác»), cột là 7 ngày. Backend chỉ trả ca của
 * người đang đăng nhập.
 *
 * Chỉ xem: chấm công (check-in / check-out) để sau (chốt 11/10/2026); xin nghỉ, đổi ca là phần «làm
 * sau» của BR-SCH.
 */
export default function MySchedulePage() {
  const today = todayIso();
  const [weekStart, setWeekStart] = useState(() => weekStartOf(today));
  const days = useMemo(() => weekDays(weekStart), [weekStart]);

  const [week, setWeek] = useState(null); // { from, shifts } — ca của tuần tải gần nhất
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Bấm chuyển tuần nhanh hơn tốc độ mạng: chỉ nhận kết quả của lần gọi mới nhất.
  const latestRequest = useRef(0);
  const load = useCallback(async () => {
    const requestId = ++latestRequest.current;
    setLoading(true);
    setError('');
    try {
      const data = await fetchShifts({ from: days[0], to: days[6] });
      if (requestId === latestRequest.current) setWeek({ from: days[0], shifts: data });
    } catch (err) {
      if (requestId === latestRequest.current) setError(readErrorMessage(err, 'Không tải được lịch tuần này.'));
    } finally {
      if (requestId === latestRequest.current) setLoading(false);
    }
  }, [days]);

  useEffect(() => {
    load();
  }, [load]);

  // Vừa chuyển tuần mà chưa tải xong thì chưa có gì để hiện — không hiện số liệu tuần cũ dưới nhãn tuần mới.
  const shifts = week?.from === days[0] ? week.shifts : null;
  const board = useMemo(() => buildPersonalShiftBoard(shifts ?? []), [shifts]);

  // Điện thoại: bảng rộng hơn màn hình — cuộn ngang sẵn tới cột hôm nay (cột tên ca dính bên trái).
  const scrollRef = useRef(null);
  useEffect(() => {
    const box = scrollRef.current;
    const todayHead = box?.querySelector('thead th.is-today');
    if (!todayHead || box.scrollWidth <= box.clientWidth) return;
    const firstColumn = box.querySelector('thead th')?.offsetWidth ?? 0;
    box.scrollLeft = todayHead.offsetLeft - firstColumn;
  }, [shifts]);
  // Ca qua đêm tính trọn giờ vào tuần chứa ngày bắt đầu ca (BR-SCH-03, BR-SCH-13) — đúng như backend cộng.
  const totalHours = (shifts ?? []).reduce((sum, shift) => sum + Number(shift.durationHours ?? 0), 0);
  const todayShifts = (shifts ?? []).filter((shift) => shift.shiftDate === today);

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Lịch làm việc › Lịch cá nhân</p>
          <h1>Lịch làm việc của tôi</h1>
          <p className="muted">Hàng là ca làm của bạn, cột là ngày. Lịch do quản lý chi nhánh xếp.</p>
        </div>
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
        {shifts && (
          <div className="sched-summary">
            <span className="chip">{shifts.length} ca</span>
            <span className="chip">Tổng {formatHours(totalHours)}</span>
            {days.includes(today) && (
              <span className={`chip ${todayShifts.length > 0 ? 'chip--green' : ''}`}>
                Hôm nay: {todayShifts.length > 0 ? todayShifts.map(shiftLabel).join(', ') : 'không có ca'}
              </span>
            )}
          </div>
        )}
      </div>

      {error && <div className="alert alert--error" role="alert">{error}</div>}
      {loading && !shifts && <p className="state">Đang tải lịch…</p>}

      {shifts && board.rows.length === 0 && (
        <div className="state state--empty">
          <p>Tuần này bạn không có ca nào.</p>
          <p className="muted">Quản lý chi nhánh xếp ca thì lịch của bạn sẽ hiện ở đây.</p>
        </div>
      )}

      {shifts && board.rows.length > 0 && (
        <div className="sched-scroll" ref={scrollRef} aria-busy={loading}>
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
              {board.rows.map((row) => (
                <tr key={row.id} className={row.start ? '' : 'sched-row--other'}>
                  <th
                    scope="row"
                    className={`sched-grid__person person-cell shift-row-head ${row.start ? `shift-tone--${shiftTone(row.start)}` : ''}`}
                  >
                    <b>{row.name}</b>
                    <small>{row.timeLabel}</small>
                  </th>
                  {days.map((day) => (
                    <td key={day} className={dayClass(day, today)}>
                      {board.cellOf(row.id, day).map((shift) => (
                        <ShiftBlock key={shift.id} shift={shift} />
                      ))}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className="alert alert--info">
        Ca qua đêm thuộc về ngày bắt đầu ca. Muốn đổi ca hoặc xin nghỉ, hãy báo quản lý chi nhánh.
      </p>
    </div>
  );
}

/** Một ca trong ô: khung giờ, số giờ, cờ qua đêm. Chỉ để xem nên không bấm được. */
function ShiftBlock({ shift }) {
  return (
    <div className={`cell-block cell-block--static shift-tone--${shiftTone(shift.startTime)}`}>
      <b className="my-shift__time">{timeRange(shift.startTime, shift.endTime)}</b>
      <span className="cell-block__meta">
        {formatHours(shift.durationHours)}
        {shift.overnight ? ' · qua đêm' : ''}
      </span>
    </div>
  );
}

/** "Ca sáng 06:00 – 14:00"; ca tự nhập giờ chỉ còn khung giờ. */
function shiftLabel(shift) {
  const time = timeRange(shift.startTime, shift.endTime);
  return shift.sourceTemplateName ? `${shift.sourceTemplateName} ${time}` : time;
}
