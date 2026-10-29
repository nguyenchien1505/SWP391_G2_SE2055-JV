import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { fetchShiftTemplates, fetchShifts, fetchSwapRequests } from '../../api/scheduling';
import SwapRequestDialog from '../../components/scheduling/SwapRequestDialog';
import TimeRangeText from '../../components/scheduling/TimeRangeText';
import WeekTimetable, { TIMETABLE_QUERY } from '../../components/scheduling/WeekTimetable';
import { useAuth } from '../../context/AuthContext';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { formatClock, formatDate, todayIso } from '../rooms/format';
import { buildPersonalShiftBoard, compareShiftStart } from './personalShifts';
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
import { PENDING_SWAP_STATUSES, isSwappable } from './swapRequests';
import './scheduling.css';

/**
 * Lịch cá nhân — nhân viên xem ca làm việc của chính mình (BR-PERM-04..06). Cùng cách chia với bảng xếp
 * lịch của quản lý: hàng là MỌI ca của chi nhánh (Ca sáng, Ca chiều, Ca đêm… — chốt 12/10/2026), cột là 7
 * ngày; ô nào là ca của mình mới hiện khối. Backend chỉ trả ca của người đang đăng nhập.
 *
 * Điện thoại (≤ 640px) không dùng bảng mà dùng thời khóa biểu dạng danh sách theo ngày
 * ({@link WeekTimetable}, kiểu "Weekly timetable" — chốt 13/10/2026): mỗi mục một ca, thẻ bên phải ghi
 * khách sạn, số giờ và chấm công nếu đã có — bảng 7 cột trên màn hẹp phải kéo ngang mới thấy hết tuần.
 *
 * Ca chưa bắt đầu, chưa check-in có nút «Đổi ca» (BR-SCH-09, BR-SCH-25 — Milestone 2); ca đang nằm
 * trong yêu cầu đổi ca chờ xử lý thì thay bằng nhãn dẫn tới trang Đổi ca (BR-SCH-19). Chấm công
 * (check-in / check-out) để sau (chốt 11/10/2026); xin nghỉ gửi ở màn «Đơn xin nghỉ».
 */
export default function MySchedulePage() {
  const { user } = useAuth();
  const today = todayIso();
  const phone = useMediaQuery(TIMETABLE_QUERY);
  const [weekStart, setWeekStart] = useState(() => weekStartOf(today));
  const days = useMemo(() => weekDays(weekStart), [weekStart]);

  const [week, setWeek] = useState(null); // { from, shifts } — ca của tuần tải gần nhất
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  // Mẫu ca đang dùng của chi nhánh: mỗi mẫu một hàng, kể cả ca tuần đó mình không làm. Không đổi theo
  // tuần nên tải một lần; lỗi thì như cũ — chỉ hàng của các ca mình có.
  const [templates, setTemplates] = useState(null);

  useEffect(() => {
    let cancelled = false;
    fetchShiftTemplates()
      .then((list) => !cancelled && setTemplates(list))
      .catch(() => !cancelled && setTemplates([]));
    return () => {
      cancelled = true;
    };
  }, []);

  const [swapping, setSwapping] = useState(null); // ca đang mở hộp thoại «Đổi ca»
  const [notice, setNotice] = useState('');
  // Ca đang nằm trong yêu cầu đổi ca chờ xử lý — mình gửi hay gửi cho mình đều không đổi thêm được
  // (BR-SCH-19). Tải lại sau mỗi lần gửi. Lỗi thì như chưa có: backend vẫn chặn và báo lý do khi gửi.
  const [heldShiftIds, setHeldShiftIds] = useState(() => new Set());
  const [swapsVersion, setSwapsVersion] = useState(0);

  useEffect(() => {
    let cancelled = false;
    Promise.all(['SENT', 'RECEIVED'].map((box) => fetchSwapRequests({ box, statuses: PENDING_SWAP_STATUSES })))
      .then((pages) => {
        if (cancelled) return;
        const ids = pages.flatMap((page) => (page.content ?? [])
          .flatMap((request) => [request.requester.shift?.id, request.peer.shift?.id]));
        setHeldShiftIds(new Set(ids.filter(Boolean)));
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [swapsVersion]);

  function handleSwapSent(text) {
    setSwapping(null);
    setNotice(text);
    setSwapsVersion((v) => v + 1);
  }

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
  // Chờ cả mẫu ca: vẽ bảng trước rồi mới thêm hàng thì bảng nhảy từ 1 lên 3 hàng.
  const ready = shifts !== null && templates !== null;
  const board = useMemo(() => buildPersonalShiftBoard(shifts ?? [], templates ?? []), [shifts, templates]);

  // Bảng rộng hơn khung (máy tính bảng hẹp) — cuộn ngang sẵn tới cột hôm nay (cột tên ca dính bên trái).
  const scrollRef = useRef(null);
  useEffect(() => {
    const box = scrollRef.current;
    const todayHead = box?.querySelector('thead th.is-today');
    if (!todayHead || box.scrollWidth <= box.clientWidth) return;
    const firstColumn = box.querySelector('thead th')?.offsetWidth ?? 0;
    box.scrollLeft = todayHead.offsetLeft - firstColumn;
  }, [shifts, templates, phone]);
  // Ca qua đêm tính trọn giờ vào tuần chứa ngày bắt đầu ca (BR-SCH-03, BR-SCH-13) — đúng như backend cộng.
  const totalHours = (shifts ?? []).reduce((sum, shift) => sum + Number(shift.durationHours ?? 0), 0);
  const todayShifts = (shifts ?? []).filter((shift) => shift.shiftDate === today);

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Lịch làm việc › Lịch cá nhân</p>
          <h1>Lịch làm việc của tôi</h1>
          <p className="muted">
            {phone
              ? 'Mỗi mục là một ca của bạn, xếp theo ngày. Lịch do quản lý chi nhánh xếp.'
              : 'Hàng là các ca của khách sạn, cột là ngày — ô có khối là ca bạn làm. Lịch do quản lý chi nhánh xếp.'}
          </p>
        </div>
      </div>

      <div className="sched-toolbar">
        {/* Điện thoại: đổi tuần ngay trên thời khóa biểu (◀ tháng ▶). */}
        {!phone && (
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
        )}
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

      {notice && (
        <div className="alert alert--success" role="status">
          {notice}{' '}
          <Link className="inline-link" to="/doi-ca">Xem yêu cầu đổi ca</Link>
          <button type="button" className="alert__close" onClick={() => setNotice('')} aria-label="Đóng">
            ×
          </button>
        </div>
      )}
      {error && <div className="alert alert--error" role="alert">{error}</div>}
      {!ready && !error && <p className="state">Đang tải lịch…</p>}

      {ready && phone && (
        <div aria-busy={loading}>
          <WeekTimetable
            days={days}
            today={today}
            legend={(templates ?? []).map((template) => ({
              tone: shiftTone(template.startTime),
              label: template.name,
              time: timeRange(template.startTime, template.endTime),
            }))}
            emptyText="Không có ca"
            entriesOf={(day) => shifts
              .filter((shift) => shift.shiftDate === day)
              .sort(compareShiftStart)
              .map((shift) => ({
                key: shift.id,
                tone: shiftTone(shift.startTime),
                label: shift.sourceTemplateName ?? 'Ca giờ khác',
                start: shift.startTime,
                end: shift.endTime,
                content: (
                  <ShiftCard
                    shift={shift}
                    today={today}
                    locationName={user?.locationName}
                    held={heldShiftIds.has(shift.id)}
                    onSwap={setSwapping}
                  />
                ),
              }))}
            onPrevWeek={() => setWeekStart(addDays(weekStart, -7))}
            onNextWeek={() => setWeekStart(addDays(weekStart, 7))}
            onThisWeek={() => setWeekStart(weekStartOf(today))}
          />
        </div>
      )}

      {ready && !phone && board.rows.length === 0 && (
        <div className="state state--empty">
          <p>Tuần này bạn không có ca nào.</p>
          <p className="muted">Quản lý chi nhánh xếp ca thì lịch của bạn sẽ hiện ở đây.</p>
        </div>
      )}

      {/* Vẫn đủ các hàng ca, chỉ chưa có khối nào — nói rõ để khỏi tưởng bảng lỗi. */}
      {ready && !phone && board.rows.length > 0 && shifts.length === 0 && (
        <p className="muted">Tuần này bạn không có ca nào. Quản lý chi nhánh xếp ca thì ca sẽ hiện ở đây.</p>
      )}

      {ready && !phone && board.rows.length > 0 && (
        <div className="sched-scroll" ref={scrollRef} aria-busy={loading}>
          <table className="sched-grid sched-grid--personal">
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
                        <ShiftBlock
                          key={shift.id}
                          shift={shift}
                          held={heldShiftIds.has(shift.id)}
                          onSwap={setSwapping}
                        />
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
        Ca qua đêm thuộc về ngày bắt đầu ca. Muốn đổi ca thì bấm «Đổi ca» trên ca chưa bắt đầu — đồng nghiệp
        đồng ý rồi quản lý duyệt (<Link className="inline-link" to="/doi-ca">xem yêu cầu đổi ca</Link>). Muốn
        xin nghỉ, gửi đơn ở mục <Link className="inline-link" to="/don-nghi">Đơn xin nghỉ</Link>.
      </p>

      {swapping && (
        <SwapRequestDialog shift={swapping} onClose={() => setSwapping(null)} onSent={handleSwapSent} />
      )}
    </div>
  );
}

/**
 * Thẻ chi tiết một ca trên thời khóa biểu điện thoại: khách sạn, số giờ, chấm công nếu đã có, và nút
 * «Đổi ca» khi ca còn đổi được.
 */
function ShiftCard({ shift, today, locationName, held, onSwap }) {
  const pills = attendancePills(shift, today);
  return (
    <>
      <div className="tt-box">
        <span className="tt-box__label">Khách sạn</span>
        <b className="tt-box__value">{locationName || '—'}</b>
      </div>
      <p className="tt-line">
        {formatHours(shift.durationHours)}
        {shift.overnight ? ' · qua đêm, tính vào ngày bắt đầu' : ''}
      </p>
      {pills.length > 0 && (
        <div className="tt-pills">
          {pills.map((pill) => (
            <span key={pill.text} className={`tt-pill tt-pill--${pill.tone}`}>
              {pill.text}
            </span>
          ))}
        </div>
      )}
      {held ? (
        <Link className="tt-swap-held" to="/doi-ca">Đang chờ đổi ca — xem yêu cầu</Link>
      ) : (
        isSwappable(shift) && (
          <button type="button" className="tt-action" onClick={() => onSwap(shift)}>
            Đổi ca
          </button>
        )
      )}
    </>
  );
}

/**
 * Chấm công của ca — chỉ đọc giờ check-in / check-out đã có, không tự suy ra đi muộn / vắng mặt
 * (BR-DASH-01). Ca chưa tới thì ghi «Sắp tới».
 */
function attendancePills(shift, today) {
  const pills = [];
  if (shift.checkInAt) pills.push({ tone: 'in', text: `Đã vào ca ${formatClock(new Date(shift.checkInAt))}` });
  if (shift.checkOutAt) pills.push({ tone: 'out', text: `Đã ra ca ${formatClock(new Date(shift.checkOutAt))}` });
  if (pills.length === 0 && shift.shiftDate > today) pills.push({ tone: 'upcoming', text: 'Sắp tới' });
  return pills;
}

/**
 * Một ca trong ô: khung giờ, số giờ, cờ qua đêm. Khối chỉ để xem; riêng nút «Đổi ca» (ca còn đổi
 * được) hoặc nhãn «Chờ đổi ca» (ca đang trong yêu cầu chờ xử lý) bấm được.
 */
function ShiftBlock({ shift, held, onSwap }) {
  return (
    <div className={`cell-block cell-block--static shift-tone--${shiftTone(shift.startTime)}`}>
      <b className="my-shift__time">
        <TimeRangeText start={shift.startTime} end={shift.endTime} />
      </b>
      <span className="cell-block__meta">
        {formatHours(shift.durationHours)}
        {shift.overnight ? ' · qua đêm' : ''}
      </span>
      {held ? (
        <Link className="cell-block__swap is-held" to="/doi-ca">Chờ đổi ca</Link>
      ) : (
        isSwappable(shift) && (
          <button type="button" className="cell-block__swap" onClick={() => onSwap(shift)}>
            Đổi ca
          </button>
        )
      )}
    </div>
  );
}

/** "Ca sáng 06:00 – 14:00"; ca tự nhập giờ chỉ còn khung giờ. */
function shiftLabel(shift) {
  const time = timeRange(shift.startTime, shift.endTime);
  return shift.sourceTemplateName ? `${shift.sourceTemplateName} ${time}` : time;
}
