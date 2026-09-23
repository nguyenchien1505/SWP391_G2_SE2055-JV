import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { updateRoomNote } from '../../api/rooms';

/** Khớp @Size(max = 500) của UpdateRoomOperationalRequest và cột note. */
const NOTE_MAX = 500;

/**
 * S-07 Ghi chú vận hành — RM-04.
 *
 * Phần duy nhất của phòng mà Quản lý chi nhánh được sửa: số phòng, tầng, loại phòng và sức chứa
 * là thông tin cấu trúc, thuộc quyền Giám đốc. Để trống ô ghi chú nghĩa là XÓA ghi chú.
 *
 * Thành công thì trả phòng MỚI qua `onSaved` để màn hình cập nhật ngay, không phải tải lại.
 */
export default function RoomNoteModal({ room, onClose, onSaved }) {
  const [note, setNote] = useState(room.note ?? '');
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
      onSaved(await updateRoomNote(room.id, { note: note.trim() }));
    } catch (err) {
      setError(readErrorMessage(err, 'Không lưu được ghi chú.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="room-note-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="room-note-title">Ghi chú vận hành · phòng {room.roomNumber}</h2>

        <div className="modal__body">
          <label className="field">
            <span className="field__label">Ghi chú</span>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={NOTE_MAX}
              rows={4}
              autoFocus
              placeholder="Ví dụ: điều hòa mới thay 20/09, rèm cần giặt"
            />
            <small className="field__help">
              Để trống nghĩa là xóa ghi chú. {note.length}/{NOTE_MAX}
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
          <button type="submit" className="btn btn--primary" disabled={submitting}>
            {submitting ? 'Đang lưu…' : 'Lưu ghi chú'}
          </button>
        </div>
      </form>
    </div>
  );
}
