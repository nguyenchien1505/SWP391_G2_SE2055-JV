import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { changeRoomStatus } from '../../api/rooms';
import { roomStatusMeta } from '../../pages/rooms/roomLabels';

/** Khớp @Size(max = 500) của ChangeRoomStatusRequest và cột unavailable_reason. */
const REASON_MAX = 500;

/** Giải thích từng lựa chọn khi mở khóa — Manager tự chọn đích (BR-ROOM-03). */
const UNLOCK_HINTS = {
  DIRTY: 'Phòng cần dọn trước khi đón khách. Hệ thống tự tạo task dọn phòng.',
  AVAILABLE: 'Phòng đã sạch, đón khách được ngay. Không tạo task dọn.',
};

/**
 * Thứ tự HIỂN THỊ các lựa chọn: "Chờ dọn" (an toàn hơn) đứng trước. Chỉ sắp xếp — đích nào
 * được hiện vẫn do `room.allowedTargets` của backend quyết định.
 */
const UNLOCK_ORDER = ['DIRTY', 'AVAILABLE'];

/**
 * S-08 Khóa / mở khóa phòng — RM-11, RM-12. Chỉ Manager mở được hộp thoại này (nút hiện theo
 * `room.allowedTargets`); backend vẫn kiểm tra lại mọi điều kiện.
 *
 *   - Phòng chưa khóa → KHÓA: bắt buộc nhập lý do (BR-ROOM-07), không có thời gian dự kiến.
 *   - Phòng đang khóa → MỞ KHÓA: chọn "Chờ dọn" hoặc "Sẵn sàng" trong số đích backend cho phép.
 *
 * Lỗi từ backend hiện ngay trong hộp thoại; thành công thì trả phòng MỚI qua `onChanged`.
 */
export default function LockRoomModal({ room, onClose, onChanged }) {
  const unlocking = room.status === 'UNAVAILABLE';
  const unlockTargets = unlocking
    ? UNLOCK_ORDER.filter((status) => room.allowedTargets?.includes(status))
    : [];

  // Mở khóa: KHÔNG chọn sẵn — Manager phải tự chọn (BR-ROOM-03), tránh lỡ tay đưa phòng chưa
  // dọn ra đón khách. Nút "Mở khóa" chỉ bật khi đã chọn.
  const [target, setTarget] = useState(unlocking ? '' : 'UNAVAILABLE');
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
    // Chặn sớm cho đỡ một lượt gọi API; backend vẫn kiểm tra lại (BR-ROOM-07).
    if (!unlocking && reason.trim() === '') {
      setError('Phải nhập lý do khóa phòng.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      onChanged(await changeRoomStatus(room.id, { targetStatus: target, reason: reason.trim() }));
    } catch (err) {
      setError(readErrorMessage(err, 'Không đổi được trạng thái phòng.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="lock-room-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="lock-room-title">{unlocking ? 'Mở khóa' : 'Khóa'} phòng {room.roomNumber}</h2>

        <div className="modal__body">
          {unlocking ? (
            <UnlockFields room={room} targets={unlockTargets} target={target} onTarget={setTarget} />
          ) : (
            <p className="alert alert--info">
              Phòng chuyển sang <b>Không khả dụng</b>. Mọi task dọn đang mở của phòng sẽ bị hủy.
            </p>
          )}

          <label className="field">
            <span className="field__label">
              {unlocking ? 'Ghi chú' : 'Lý do khóa phòng'} {!unlocking && <span className="req">*</span>}
            </span>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={REASON_MAX}
              rows={3}
              autoFocus={!unlocking}
              placeholder={unlocking ? 'Không bắt buộc — ví dụ: đã thay xong điều hòa' : 'Ví dụ: hỏng điều hòa, chờ thợ sửa'}
            />
            <small className="field__help">
              {unlocking ? 'Ghi vào lịch sử trạng thái.' : 'Bắt buộc.'} {reason.length}/{REASON_MAX}
            </small>
          </label>

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
            className={`btn ${unlocking ? 'btn--primary' : 'btn--danger'}`}
            disabled={submitting || !target}
          >
            {submitting ? 'Đang lưu…' : unlocking ? 'Mở khóa' : 'Khóa phòng'}
          </button>
        </div>
      </form>
    </div>
  );
}

/** Lý do đang khóa + lựa chọn trạng thái đích. Chỉ liệt kê đích backend cho phép. */
function UnlockFields({ room, targets, target, onTarget }) {
  return (
    <>
      <div className="readonly-box">
        <b>Đang khóa vì</b>
        <p>{room.unavailableReason}</p>
      </div>

      <fieldset className="choice-list">
        <legend className="field__label">Chuyển phòng sang</legend>
        {targets.map((status, index) => (
          <label key={status} className={`choice ${target === status ? 'is-selected' : ''}`}>
            <input
              type="radio"
              name="unlock-target"
              value={status}
              checked={target === status}
              onChange={() => onTarget(status)}
              autoFocus={index === 0}
            />
            <span>
              <b>{roomStatusMeta(status).label}</b>
              <small>{UNLOCK_HINTS[status]}</small>
            </span>
          </label>
        ))}
      </fieldset>
    </>
  );
}
