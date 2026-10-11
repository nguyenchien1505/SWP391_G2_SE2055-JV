import { LOCK, canDeleteRoom, roomActionsFor } from '../../pages/rooms/roomActions';

/**
 * Nút thao tác ngay trên một dòng phòng của S-02 — Giám đốc / Quản lý chi nhánh sửa, xóa, sửa ghi
 * chú mà không phải mở trang chi tiết (S-04). Ẩn / hiện theo vai trò chỉ để dễ dùng; backend mới
 * là nơi chặn thật (BR-ROOM-04).
 *
 *   - Giám đốc: "Sửa" (form S-03, đã gồm ô ghi chú — giống S-04, không thêm nút ghi chú riêng) và
 *     "Xóa" (chỉ khi phòng còn xóa được — BR-ROOM-08).
 *   - Quản lý chi nhánh: "Sửa ghi chú" (S-07) — phần duy nhất của phòng họ được sửa — và "Khóa
 *     phòng" / "Mở khóa" (S-08). Nút khóa vẽ từ `room.allowedTargets` do backend tính, giống S-04
 *     và S-06: phòng đang có khách thì không có nút (BR-ROOM-03).
 *
 * Cả dòng phòng bấm được để vào trang chi tiết, nên mọi nút ở đây chặn sự kiện lan lên dòng.
 */
export default function RoomRowActions({ room, isDirector, isManager, onEdit, onDelete, onEditNote, onLock }) {
  // Khóa và mở khóa dùng chung hộp thoại S-08; roomActionsFor trả đúng MỘT trong hai (hoặc không có).
  const lockAction = roomActionsFor(room).find((action) => action.flow === LOCK);

  const handle = (handler) => (event) => {
    event.stopPropagation();
    handler(room);
  };

  return (
    <div className="room-row-actions">
      {isDirector && (
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={handle(onEdit)}
          aria-label={`Sửa phòng ${room.roomNumber}`}
          title="Sửa thông tin phòng"
        >
          <span className="material-symbols-outlined" aria-hidden="true">edit</span>
          <span className="room-row-actions__text">Sửa</span>
        </button>
      )}
      {isDirector && canDeleteRoom(room) && (
        <button
          type="button"
          className="btn btn--ghost btn--sm is-danger"
          onClick={handle(onDelete)}
          aria-label={`Xóa phòng ${room.roomNumber}`}
          title="Xóa phòng"
        >
          <span className="material-symbols-outlined" aria-hidden="true">delete</span>
          <span className="room-row-actions__text">Xóa</span>
        </button>
      )}
      {isManager && (
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={handle(onEditNote)}
          aria-label={`Sửa ghi chú phòng ${room.roomNumber}`}
          title="Sửa ghi chú vận hành"
        >
          <span className="material-symbols-outlined" aria-hidden="true">edit_note</span>
          <span className="room-row-actions__text">Sửa ghi chú</span>
        </button>
      )}
      {lockAction && (
        <button
          type="button"
          className={`btn btn--ghost btn--sm ${lockAction.danger ? 'is-danger' : ''}`}
          onClick={handle(onLock)}
          aria-label={`${lockAction.label} ${room.roomNumber}`}
          title={lockAction.label}
        >
          <span className="material-symbols-outlined" aria-hidden="true">
            {lockAction.danger ? 'lock' : 'lock_open'}
          </span>
          <span className="room-row-actions__text">{lockAction.danger ? 'Khóa phòng' : 'Mở khóa'}</span>
        </button>
      )}
    </div>
  );
}
