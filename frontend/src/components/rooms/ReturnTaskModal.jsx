import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { returnTask } from '../../api/housekeeping';
import { taskRoomLabel, teamNames } from '../../pages/rooms/roomLabels';

/** Khớp @Size(max = 500) của ReturnTaskRequest và cột housekeeping_task_returns.reason. */
const REASON_MAX = 500;

/**
 * S-22 Trả lại việc dọn — RM-22, BR-HK-17.
 *
 * Chỉ mở từ dòng «Cần làm» của việc mình TỰ NHẬN (S-16); backend vẫn kiểm lại ai được trả. Lý do bắt
 * buộc, chữ tự do — không có danh sách lý do có sẵn, giống lý do kiểm tra không đạt. Quản lý đọc lại ở
 * «Lịch sử trả lại» và lịch sử phòng.
 *
 * Câu giải thích nói trước điều sẽ xảy ra: dọn một mình thì phòng về «Chờ dọn» và quay lại danh sách chờ
 * nhận; có đồng đội thì chỉ mình rời nhóm, đồng đội dọn tiếp.
 *
 * @param meId       người đang đăng nhập — để biết nhóm còn ai khác
 * @param onReturned (việc sau khi trả) — trang cha hiện thông báo và tải lại
 */
export default function ReturnTaskModal({ task, meId, onClose, onReturned }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const room = taskRoomLabel(task);
  const teammates = teamNames(task, meId);

  // Esc để đóng, trừ lúc đang gửi — tránh đóng giữa chừng rồi không biết kết quả.
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  async function handleSubmit(e) {
    e.preventDefault();
    if (reason.trim() === '') return;   // nút gửi đã mờ; backend vẫn kiểm lại
    setSubmitting(true);
    setError('');
    try {
      onReturned(await returnTask(task.id, reason.trim()));
    } catch (err) {
      setError(readErrorMessage(err, 'Không trả lại được việc này.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="return-task-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="return-task-title">Trả lại việc dọn phòng {room}</h2>

        <div className="modal__body">
          <div className="alert alert--info">
            {teammates
              ? <>Bạn rời nhóm dọn phòng {room}; <strong>{teammates}</strong> vẫn dọn tiếp.</>
              : `Phòng ${room} sẽ quay về «Chờ dọn» và trở lại danh sách chờ nhận.`}
          </div>

          <label className="field">
            <span className="field__label">
              Lý do trả lại <span className="req">*</span>
            </span>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={REASON_MAX}
              rows={3}
              autoFocus
              placeholder="Ví dụ: thiếu hóa chất tẩy rửa, phải xuống hỗ trợ lễ tân"
            />
            <small className="field__help">
              Quản lý sẽ đọc được câu này. {reason.length}/{REASON_MAX}
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
          <button type="submit" className="btn btn--danger" disabled={submitting || reason.trim() === ''}>
            {submitting ? 'Đang gửi…' : 'Trả lại việc'}
          </button>
        </div>
      </form>
    </div>
  );
}
