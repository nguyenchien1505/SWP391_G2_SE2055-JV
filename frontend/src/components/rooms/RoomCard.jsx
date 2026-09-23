import { roomStatusMeta } from '../../pages/rooms/roomLabels';
import RoomStatusBadge from './RoomStatusBadge';

/**
 * Một phòng dạng thẻ màu — dùng cho lưới phòng trên điện thoại (S-02) và sơ đồ phòng (S-06).
 * Cả thẻ là một nút bấm (vùng bấm ≥ 48px, design.md mục 3) để thao tác bằng ngón tay dễ.
 *
 * @param showFloor sơ đồ phòng đã nhóm theo tầng nên không cần in lại tầng trên từng thẻ
 */
export default function RoomCard({ room, onSelect, showFloor = false }) {
  const tone = roomStatusMeta(room.status).tone;
  return (
    <button
      type="button"
      className={`room-card room-tone--${tone}`}
      onClick={() => onSelect(room)}
      aria-label={`Phòng ${room.roomNumber}, ${roomStatusMeta(room.status).label}`}
    >
      <span className="room-card__number">{room.roomNumber}</span>
      <span className="room-card__meta">
        {showFloor && `Tầng ${room.floor} · `}
        {room.roomTypeName ?? 'Chưa rõ loại'} · {room.capacity} người
      </span>
      <RoomStatusBadge status={room.status} />
    </button>
  );
}
