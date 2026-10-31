import { useEffect, useState } from 'react';
import { fetchMyAttendanceSummary } from '../../api/scheduling';
import { currentMonth, formatMinutes, formatMonth } from '../../pages/scheduling/attendanceSummary';

/**
 * Chuyên cần tháng này của chính nhân viên (chấm công M2) — biết số liệu của mình đang hiện cho quản lý thì tự
 * điều chỉnh. Lỗi thì ẩn khối, không chặn xem lịch.
 *
 * @param version đổi giá trị là tải lại (sau mỗi lần vào / kết thúc ca)
 */
export default function MyAttendanceSummary({ version = 0 }) {
  const month = currentMonth();
  const [summary, setSummary] = useState(null);

  useEffect(() => {
    let cancelled = false;
    fetchMyAttendanceSummary(month)
      .then((data) => !cancelled && setSummary(data))
      .catch(() => !cancelled && setSummary(null));
    return () => {
      cancelled = true;
    };
  }, [month, version]);

  if (!summary || summary.totalShifts === 0) return null;

  return (
    <section className="sched-summary my-attendance" aria-label={`Chuyên cần tháng ${formatMonth(month)}`}>
      <span className="chip">Tháng {formatMonth(month)}: {summary.dueShifts}/{summary.totalShifts} ca đã tới giờ</span>
      {summary.onTimePercent != null && (
        <span className={`chip ${summary.onTimePercent >= 90 ? 'chip--green' : 'chip--warn'}`}>
          Đúng giờ {summary.onTimePercent}%
        </span>
      )}
      {summary.late > 0 && <span className="chip chip--warn">Muộn {summary.late} lần · {formatMinutes(summary.lateMinutes)}</span>}
      {summary.absent > 0 && <span className="chip chip--danger">Vắng {summary.absent}</span>}
      {summary.missedCheckOut > 0 && <span className="chip chip--warn">Quên kết thúc ca {summary.missedCheckOut}</span>}
      {summary.outsideNetwork > 0 && <span className="chip chip--warn">Ngoài mạng {summary.outsideNetwork}</span>}
      <span className="chip">Giờ làm {formatMinutes(summary.workedMinutes)}</span>
    </section>
  );
}
