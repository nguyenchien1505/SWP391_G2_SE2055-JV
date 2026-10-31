import { useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { checkInShift, checkOutShift } from '../../api/scheduling';
import ConfirmDialog from '../ConfirmDialog';
import { formatClock, formatDate, formatShortDateTime } from '../../pages/rooms/format';
import { timeRange } from '../../pages/scheduling/scheduleFormat';
import { attendancePills } from '../../pages/scheduling/attendance';
import './MyShiftCard.css';

/**
 * Khối "Ca hôm nay" kèm nút **Vào ca** / **Kết thúc ca** — chấm công M2 (chốt 31/10/2026). Dùng ở Lịch cá
 * nhân (mọi nhân viên, kể cả Lễ tân) và ở Việc dọn của tôi (tự nhận việc dọn đòi đã vào ca — BR-HK-15).
 *
 * Nút bật theo `attendanceStatus` backend gửi kèm ca, không tự tính khung giờ:
 *   - `UPCOMING` — chưa tới giờ, ghi «Vào ca được từ HH:mm» (30 phút trước giờ bắt đầu);
 *   - `OPEN` — nút Vào ca; `WORKING` — nút Kết thúc ca (tới hết ca + 4 giờ);
 *   - `ABSENT` / `MISSED_CHECK_OUT` — không bấm được nữa, nhờ quản lý nhập bù.
 * Câu lỗi của backend (bấm đúng lúc vừa hết khung) hiện nguyên văn.
 *
 * @param shifts      ca của chính mình thuộc ngày làm hiện tại (kể cả ca đêm từ hôm qua còn đang làm)
 * @param today       hôm nay "YYYY-MM-DD" — ca của ngày khác là ca đêm từ hôm qua
 * @param onChanged   ({ type, text }) — sau mỗi lần vào / kết thúc ca (thành công hay lỗi); trang cha hiện
 *                    thông báo và tải lại
 * @param emptyText   câu khi hôm nay không có ca
 * @param checkOutNote câu nhắc trong hộp xác nhận Kết thúc ca
 */
export default function MyShiftCard({
  shifts,
  today,
  onChanged,
  emptyText = 'Hôm nay bạn không có ca.',
  checkOutNote = 'Kết thúc ca thì không vào lại ca này được nữa.',
}) {
  const [submittingId, setSubmittingId] = useState(null);
  const [endingShift, setEndingShift] = useState(null);   // ca đang chờ xác nhận "Kết thúc ca"

  async function run(shift, action, successText, fallbackError) {
    setSubmittingId(shift.id);
    try {
      await action(shift.id);
      onChanged({ type: 'success', text: successText });
    } catch (err) {
      onChanged({ type: 'error', text: readErrorMessage(err, fallbackError) });
    } finally {
      setSubmittingId(null);
    }
  }

  function checkIn(shift) {
    run(shift, checkInShift, 'Đã vào ca.', 'Không vào ca được.');
  }

  function checkOut() {
    const shift = endingShift;
    setEndingShift(null);
    run(shift, checkOutShift, 'Đã kết thúc ca.', 'Không kết thúc ca được.');
  }

  return (
    <section className="my-shift" aria-label="Ca hôm nay">
      <span className="my-shift__label">Ca hôm nay</span>
      {shifts.length === 0 && <span className="my-shift__none">{emptyText}</span>}

      {shifts.map((shift) => {
        const status = shift.attendanceStatus;
        const pills = attendancePills(shift).filter((pill) => pill.tone !== 'in' && pill.tone !== 'out');
        return (
          <div key={shift.id} className="my-shift__item">
            <span className="my-shift__time">
              {shift.shiftDate !== today && `Ca đêm từ ${formatDate(shift.shiftDate)} · `}
              {timeRange(shift.startTime, shift.endTime)}
              {shift.checkInAt && !shift.checkOutAt && (
                <small> · đã vào ca {formatClock(new Date(shift.checkInAt))}</small>
              )}
              {shift.checkOutAt && (
                <small>
                  {' '}· vào {formatClock(new Date(shift.checkInAt))} · kết thúc {formatClock(new Date(shift.checkOutAt))}
                </small>
              )}
            </span>

            {pills.map((pill) => (
              <span key={pill.text} className={`tt-pill tt-pill--${pill.tone}`}>{pill.text}</span>
            ))}

            {status === 'UPCOMING' && shift.checkInOpensAt && (
              <span className="my-shift__hint">Vào ca được từ {formatShortDateTime(shift.checkInOpensAt)}</span>
            )}
            {status === 'ABSENT' && (
              <span className="my-shift__hint">
                Nếu bạn có đi làm,{' '}
                <Link className="inline-link" to={`/dieu-chinh-cong?shift=${shift.id}`}>gửi đơn điều chỉnh công</Link>.
              </span>
            )}
            {status === 'MISSED_CHECK_OUT' && (
              <span className="my-shift__hint">
                Đã quá hạn kết thúc ca —{' '}
                <Link className="inline-link" to={`/dieu-chinh-cong?shift=${shift.id}`}>gửi đơn điều chỉnh giờ ra</Link>.
              </span>
            )}

            {status === 'OPEN' && (
              <button
                type="button"
                className="btn btn--primary my-shift__action"
                disabled={submittingId !== null}
                onClick={() => checkIn(shift)}
              >
                {submittingId === shift.id ? 'Đang vào ca…' : 'Vào ca'}
              </button>
            )}
            {status === 'WORKING' && (
              <button
                type="button"
                className="btn btn--ghost my-shift__action"
                disabled={submittingId !== null}
                onClick={() => setEndingShift(shift)}
              >
                {submittingId === shift.id ? 'Đang kết thúc…' : 'Kết thúc ca'}
              </button>
            )}
          </div>
        );
      })}

      {endingShift && (
        <ConfirmDialog
          title="Kết thúc ca?"
          message={checkOutNote}
          confirmLabel="Kết thúc ca"
          confirmTone="primary"
          onConfirm={checkOut}
          onCancel={() => setEndingShift(null)}
        />
      )}
    </section>
  );
}
