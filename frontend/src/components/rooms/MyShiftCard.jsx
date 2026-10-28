import { useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { checkInShift, checkOutShift } from '../../api/scheduling';
import ConfirmDialog from '../ConfirmDialog';
import { formatClock, formatDate } from '../../pages/rooms/format';
import { timeRange } from '../../pages/scheduling/scheduleFormat';

/**
 * Khối "Ca hôm nay" của màn Việc của tôi — các ca thuộc NGÀY LÀM HIỆN TẠI (kể cả ca đêm từ hôm qua còn
 * đang làm — BR-SCH-03), kèm nút **Vào ca** / **Kết thúc ca** (M2, chốt 28/10/2026).
 *
 * Tự nhận việc dọn đòi đã vào ca và chưa kết thúc ca (BR-HK-15), mà trước đây chưa có màn nào cho nhân
 * viên chấm vào / ra — nên nút nằm ngay tại đây. Chỉ gọi API có sẵn; khung giờ được vào ca (chấm công M2)
 * do backend kiểm, câu lỗi hiện nguyên văn.
 *
 * @param shifts    ca của chính mình thuộc ngày làm hiện tại
 * @param today     hôm nay "YYYY-MM-DD" — ca của ngày khác là ca đêm từ hôm qua
 * @param onChanged ({ type, text }) — sau mỗi lần vào / kết thúc ca (thành công hay lỗi); trang cha hiện
 *                  thông báo và tải lại, vì được nhận việc hay không thay đổi theo ca
 */
export default function MyShiftCard({ shifts, today, onChanged }) {
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
    // Không hứa "nhận việc được": còn giữ việc dở thì vẫn chưa nhận được — mục chờ nhận tự nói lý do.
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
      {shifts.length === 0 && (
        <span className="my-shift__none">
          Hôm nay bạn không có ca — việc dọn chỉ được giao vào ngày bạn có ca.
        </span>
      )}

      {shifts.map((shift) => (
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

          {!shift.checkInAt && (
            <button
              type="button"
              className="btn btn--primary my-shift__action"
              disabled={submittingId !== null}
              onClick={() => checkIn(shift)}
            >
              {submittingId === shift.id ? 'Đang vào ca…' : 'Vào ca'}
            </button>
          )}
          {shift.checkInAt && !shift.checkOutAt && (
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
      ))}

      {endingShift && (
        <ConfirmDialog
          title="Kết thúc ca?"
          message="Kết thúc ca thì bạn không tự nhận thêm việc dọn được nữa. Việc đang dọn dở vẫn nằm ở «Cần làm»."
          confirmLabel="Kết thúc ca"
          confirmTone="primary"
          onConfirm={checkOut}
          onCancel={() => setEndingShift(null)}
        />
      )}
    </section>
  );
}
