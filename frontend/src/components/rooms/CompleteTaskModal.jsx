import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { completeTask } from '../../api/housekeeping';
import { taskRoomLabel, teamNames } from '../../pages/rooms/roomLabels';

/** Khớp @Size(max = 500) của CompleteTaskRequest và cột housekeeping_tasks.completion_note. */
const NOTE_MAX = 500;

/**
 * Xác nhận «Hoàn thành» (S-17) kèm lời nhắn cho người kiểm tra — không bắt buộc (chốt 02/11/2026). Một bước
 * xác nhận cũng chặn bấm nhầm khi nút to nằm ngay dưới ngón cái. Một người bấm là xong cho cả nhóm.
 *
 * @param meId        người đang đăng nhập — để ghi "báo xong cho cả nhóm (… )" không có tên mình
 * @param onCompleted (task đã sang «Chờ kiểm tra») => trang cha báo kết quả và tải lại
 */
export default function CompleteTaskModal({ task, meId, onClose, onCompleted }) {
  const [note, setNote] = useState('');
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
    setSubmitting(true);
    setError('');
    try {
      onCompleted(await completeTask(task.id, note));
    } catch (err) {
      setError(readErrorMessage(err, 'Không báo hoàn thành được.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="complete-task-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="complete-task-title">Báo xong phòng {room}?</h2>

        <div className="modal__body">
          <div className="alert alert--info">
            Phòng chuyển sang «Chờ kiểm tra» để quản lý hoặc giám sát nghiệm thu
            {teammates ? <> — báo xong cho cả nhóm, gồm cả <strong>{teammates}</strong>.</> : '.'}
          </div>

          <label className="field">
            <span className="field__label">Ghi chú cho người kiểm tra (không bắt buộc)</span>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={NOTE_MAX}
              rows={3}
              placeholder="Ví dụ: thiếu 1 gối, đã báo kho"
            />
            <small className="field__help">{note.length}/{NOTE_MAX}</small>
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
          <button type="submit" className="btn btn--primary" disabled={submitting} autoFocus>
            {submitting ? 'Đang gửi…' : 'Báo xong'}
          </button>
        </div>
      </form>
    </div>
  );
}
