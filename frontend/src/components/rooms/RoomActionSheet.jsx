import { useEffect } from 'react';
import RoomStatusBadge from './RoomStatusBadge';

/**
 * Bảng thao tác mở ra khi bấm một thẻ trên sơ đồ phòng — Manager khóa / mở khóa và Lễ tân
 * đặt / nhận / trả phòng ngay tại sơ đồ, không phải vào trang chi tiết (S-06, S-15).
 *
 * Mỗi thao tác là một nút to (≥ 48px, design.md mục 3) để bấm bằng ngón tay. Trên điện thoại
 * bảng này trượt lên từ cạnh dưới — vùng ngón cái với tới được (design.md mục 2).
 *
 * Component chỉ hiển thị: nó không biết thao tác nào cần xác nhận, chỉ báo lên trên bằng
 * `onPick`; phần đó là việc của `useRoomAction`.
 *
 * @param actions [{ key, label, danger, … }] — lấy từ `roomActionsFor(room)`
 * @param onPick  nhận NGUYÊN mô tả thao tác được chọn
 */
export default function RoomActionSheet({ room, actions, onPick, onOpenDetail, onClose }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="modal-backdrop action-sheet-backdrop" role="dialog" aria-modal="true"
         aria-labelledby="room-sheet-title" onClick={onClose}>
      <div className="modal room-modal action-sheet" onClick={(e) => e.stopPropagation()}>
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
              onClick={() => onPick(action)}
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
