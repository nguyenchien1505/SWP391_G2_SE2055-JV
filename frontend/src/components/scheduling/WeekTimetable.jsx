import { formatDate } from '../../pages/rooms/format';
import { WEEKDAY_LONG, formatDayMonth, hhmm } from '../../pages/scheduling/scheduleFormat';

/** Khổ màn hình dùng thời khóa biểu dạng danh sách thay cho bảng tuần — điện thoại. */
export const TIMETABLE_QUERY = '(max-width: 640px)';

/** "Tháng 10, 2026" — tuần vắt qua hai tháng / hai năm thì ghi cả hai. */
function monthLabel(days) {
  const [y1, m1] = days[0].split('-').map(Number);
  const [y2, m2] = days[6].split('-').map(Number);
  if (y1 !== y2) return `Tháng ${m1}/${y1} – ${m2}/${y2}`;
  return m1 === m2 ? `Tháng ${m1}, ${y1}` : `Tháng ${m1} – ${m2}, ${y1}`;
}

/**
 * Thời khóa biểu tuần trên điện thoại, kiểu "Weekly timetable" (chốt 13/10/2026). Đầu trang: tuần đang
 * xem và tháng kèm nút ◀ ▶ đổi tuần — dính trên cùng khi cuộn. Dưới là danh sách GOM THEO NGÀY đủ 7
 * ngày: mỗi mục một ca, có vạch và chip màu ca, giờ vào / ra xếp dọc, thẻ chi tiết bên phải do trang tự
 * vẽ. Không có dải nút chọn ngày (bỏ theo yêu cầu 13/10/2026) — cả tuần đã nằm trong danh sách.
 *
 * Dùng chung cho «Lịch cá nhân» và «Lịch dọn của tôi»; màn rộng hơn dùng bảng tuần (hàng = mọi ca).
 *
 * @param entriesOf (day) => [{ key, tone, label, start, end, content }] — các ca của ngày, đã xếp theo
 *                  giờ. `tone`: morning / afternoon / night / other. `start` null = không có giờ ca
 *                  (ví dụ việc dọn ngoài ca)
 * @param legend    [{ tone, label, time }] — mọi ca của chi nhánh, để luôn thấy đủ các ca và nghĩa của màu
 * @param emptyText chữ cho ngày không có lịch
 */
export default function WeekTimetable({
  days,
  today,
  entriesOf,
  legend = [],
  emptyText,
  onPrevWeek,
  onNextWeek,
  onThisWeek,
}) {
  const thisWeek = days.includes(today);

  return (
    <div className="tt">
      <div className="tt-head">
        <p className="tt-week">
          {thisWeek ? 'Tuần này' : 'Tuần'}: {formatDate(days[0])} – {formatDate(days[6])}
          {!thisWeek && (
            <button type="button" className="link-btn tt-week__back" onClick={onThisWeek}>
              Về tuần này
            </button>
          )}
        </p>
        <div className="tt-month">
          <button type="button" className="tt-month__nav" onClick={onPrevWeek} aria-label="Tuần trước">
            ◀
          </button>
          <b aria-live="polite">{monthLabel(days)}</b>
          <button type="button" className="tt-month__nav" onClick={onNextWeek} aria-label="Tuần sau">
            ▶
          </button>
        </div>
      </div>

      {legend.length > 0 && (
        <p className="tt-legend">
          {legend.map((item) => (
            <span key={item.label} className={`legend shift-tone--${item.tone}`}>
              {item.label} {item.time}
            </span>
          ))}
        </p>
      )}

      <ol className="tt-list">
        {days.map((day, index) => {
          const entries = entriesOf(day);
          return (
            <li key={day} className={`tt-group ${day === today ? 'is-today' : ''} ${entries.length > 0 ? '' : 'is-empty'}`}>
              <div className="tt-group__date">
                <b>{formatDayMonth(day)}</b>
                <span>{WEEKDAY_LONG[index]}</span>
                {day === today && <span className="today-chip">Hôm nay</span>}
              </div>
              {entries.length === 0 ? (
                <p className="tt-group__empty">{emptyText}</p>
              ) : (
                <ul className="tt-entries">
                  {entries.map((entry) => (
                    <li key={entry.key} className={`tt-entry shift-tone--${entry.tone}`}>
                      <div className="tt-entry__slot">
                        <span className="tt-chip">{entry.label}</span>
                        {entry.start ? (
                          <span className="tt-time" aria-label={`${hhmm(entry.start)} đến ${hhmm(entry.end)}`}>
                            <span aria-hidden="true">{hhmm(entry.start)}</span>
                            <span className="tt-time__sep" aria-hidden="true" />
                            <span aria-hidden="true">{hhmm(entry.end)}</span>
                          </span>
                        ) : (
                          <span className="tt-time tt-time--none">Không có ca</span>
                        )}
                      </div>
                      <div className="tt-entry__card">{entry.content}</div>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ol>
    </div>
  );
}
