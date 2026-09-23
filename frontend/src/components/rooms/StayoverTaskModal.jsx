import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { createStayoverTask } from '../../api/housekeeping';
import { fetchRooms } from '../../api/rooms';

/**
 * S-12 Tạo việc dọn hằng ngày — RM-16, BR-HK-05.
 *
 * Chỉ chọn được phòng ĐANG CÓ KHÁCH: dọn hằng ngày là dọn khi khách còn ở. Danh sách lấy bằng
 * `fetchRooms({ status: 'OCCUPIED' })` — backend đã ép Quản lý chi nhánh về khách sạn của mình
 * nên không cần truyền locationId.
 *
 * Việc dọn sau trả phòng KHÔNG tạo tay được ở đâu cả: hệ thống tự sinh khi phòng vào «Chờ dọn»
 * (BR-HK-01), nên màn này chỉ có một loại việc.
 *
 * @param onCreated nhận việc vừa tạo — trang cha hỏi tiếp "phân công luôn?"
 */
export default function StayoverTaskModal({ onClose, onCreated }) {
  const [rooms, setRooms] = useState(null); // null = đang tải
  const [roomId, setRoomId] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  useEffect(() => {
    let cancelled = false;
    fetchRooms({ status: 'OCCUPIED', size: 100 })
      .then((data) => !cancelled && setRooms(data.content ?? []))
      .catch((err) => {
        if (cancelled) return;
        setRooms([]);
        setError(readErrorMessage(err, 'Không tải được danh sách phòng.'));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      onCreated(await createStayoverTask(roomId));
    } catch (err) {
      // Hay gặp nhất: phòng này đã có việc dọn hằng ngày đang mở (BR-HK-11).
      setError(readErrorMessage(err, 'Không tạo được việc dọn.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="stayover-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="stayover-title">Thêm việc dọn hằng ngày</h2>

        <div className="modal__body">
          <p className="alert alert--info">
            Dành cho phòng <b>đang có khách</b>. Phòng giữ nguyên trạng thái «Đang sử dụng» trong
            suốt quá trình dọn.
          </p>

          <label className="field" htmlFor="stayover-room">
            <span className="field__label">
              Phòng <b className="req">*</b>
            </span>
            <select
              id="stayover-room"
              value={roomId}
              onChange={(e) => setRoomId(e.target.value)}
              disabled={rooms === null}
              required
            >
              <option value="">{rooms === null ? 'Đang tải…' : '— Chọn phòng —'}</option>
              {(rooms ?? []).map((room) => (
                <option key={room.id} value={room.id}>
                  Phòng {room.roomNumber} · tầng {room.floor}
                </option>
              ))}
            </select>
          </label>

          {rooms?.length === 0 && (
            <div className="alert alert--info">Hiện không có phòng nào đang có khách.</div>
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
          <button type="submit" className="btn btn--primary" disabled={submitting || !roomId}>
            {submitting ? 'Đang lưu…' : 'Tạo việc dọn'}
          </button>
        </div>
      </form>
    </div>
  );
}
