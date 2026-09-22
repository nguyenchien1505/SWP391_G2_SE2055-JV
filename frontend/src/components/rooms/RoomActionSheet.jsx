import { useEffect } from 'react';
import RoomStatusBadge from './RoomStatusBadge';

/**
 * Bảng thao tác mở ra khi bấm một thẻ trên sơ đồ phòng (S-06) — Manager khóa / mở khóa ngay tại
 * sơ đồ, không phải vào trang chi tiết. F4 dùng lại cho các thao tác của Lễ tân (S-15).
 *
 * Mỗi thao tác là một nút to (≥ 48px, design.md mục 3) để bấm bằng ngón tay trên máy tính bảng.
 *
 * @param actions [{ key, label, danger }] — lấy từ `roomActionsFor(room)`
 * @param onPick  nhận `key` của thao tác được chọn
 */
export default function RoomActionSheet({ room, actions, onPick, onOpenDetail, onClose }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="room-sheet-title" onClick={onClose}>
      <div className="modal room-modal" onClick={(e) => e.stopPropagation()}>
        <div className="action-sheet__head">
          <h2 id="room-sheet-title">Phòng {room.roomNumber}</h2>
          <RoomStatusBadge status={room.status} />
        </div>
        {room.status === 'UNAVAILABLE' && room.unavailableReason && (
          <p className="muted action-sheet__reason">Lý do: {room.unavailableReason}</p>
        )}

        <div className="action-sheet__list">
          {actions.map((action) => (
            <button
              key={action.key}
              type="button"
              className={`btn ${action.danger ? 'btn--danger' : 'btn--primary'}`}
              onClick={() => onPick(action.key)}
            >
              {action.label}
            </button>
          ))}
          <button type="button" className="btn btn--ghost" onClick={onOpenDetail}>
            Xem chi tiết và lịch sử
          </button>
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
