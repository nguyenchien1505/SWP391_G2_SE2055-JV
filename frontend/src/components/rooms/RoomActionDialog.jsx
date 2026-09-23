import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { changeRoomStatus } from '../../api/rooms';
import { REASON } from '../../pages/rooms/roomActions';

/**
 * Hộp thoại xác nhận cho các thao tác của Lễ tân — S-15 (RM-08, RM-10).
 *
 * Một component cho hai kiểu, vì phần khó giống hệt nhau (gọi API, chặn bấm hai lần, hiện câu lỗi
 * của backend ngay tại chỗ người dùng đang nhìn):
 *
 *   - `CONFIRM` — chỉ cần một câu cảnh báo rồi xác nhận. Dùng cho **check-out**: bấm nhầm không
 *     quay lại được, và nó còn kéo theo việc dọn phòng.
 *   - `REASON`  — phải chọn một lý do trước. Dùng cho **hủy giữ phòng**: hủy và no-show là cùng
 *     một bước chuyển, lý do là thứ duy nhất phân biệt hai ca trong lịch sử.
 *
 * Các thao tác nhẹ (đặt trước, check-in) KHÔNG đi qua đây — bấm là chạy luôn, xem `useRoomAction`.
 *
 * @param action  mô tả thao tác lấy từ `roomActionsFor(room)`; phần chữ nằm ở `action.prompt`
 * @param onDone  nhận phòng MỚI từ response để màn hình cập nhật ngay
 */
export default function RoomActionDialog({ room, action, onDone, onClose }) {
  const { prompt, flow } = action;
  const needsReason = flow === REASON;

  // Không chọn sẵn lý do: buộc Lễ tân đọc và chọn, vì lý do này đi thẳng vào lịch sử phòng.
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Esc để đóng, trừ lúc đang gửi — tránh đóng giữa chừng rồi không biết kết quả.
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      onDone(await changeRoomStatus(room.id, {
        targetStatus: action.targetStatus,
        reason: needsReason ? reason : undefined,
      }));
    } catch (err) {
      // Backend đã trả câu tiếng Việt nêu rõ vi phạm gì — hiện nguyên văn, không diễn giải lại.
      setError(readErrorMessage(err, 'Không đổi được trạng thái phòng.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="room-action-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="room-action-title">
          {prompt.title} · phòng {room.roomNumber}
        </h2>

        <div className="modal__body">
          {needsReason ? (
            <fieldset className="choice-list">
              <legend className="field__label">{prompt.question}</legend>
              {prompt.options.map((option, index) => (
                <label key={option.value} className={`choice ${reason === option.value ? 'is-selected' : ''}`}>
                  <input
                    type="radio"
                    name="room-action-reason"
                    value={option.value}
                    checked={reason === option.value}
                    onChange={() => setReason(option.value)}
                    autoFocus={index === 0}
                  />
                  <span>
                    <b>{option.value}</b>
                    <small>{option.hint}</small>
                  </span>
                </label>
              ))}
            </fieldset>
          ) : (
            <p className="alert alert--info">{prompt.message}</p>
          )}

          {error && (
            <div className="alert alert--error" role="alert">
              {error}
            </div>
          )}
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={submitting}>
            Hủy
          </button>
          <button
            type="submit"
            className={`btn ${action.danger ? 'btn--danger' : 'btn--primary'}`}
            disabled={submitting || (needsReason && !reason)}
          >
            {submitting ? 'Đang lưu…' : prompt.confirmLabel}
          </button>
        </div>
      </form>
    </div>
  );
}
