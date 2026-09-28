import { roomStatusMeta } from '../../pages/rooms/roomLabels';
import RoomStatusBadge from './RoomStatusBadge';

/**
 * Một phòng dạng thẻ màu — lưới phòng trên điện thoại (S-02) và sơ đồ phòng (S-06 / S-15).
 * Cả thẻ là MỘT nút bấm (vùng bấm ≥ 48px, design.md mục 3), không lồng nút con bên trong.
 *
 * Thẻ chỉ hiện dữ liệu hệ thống thật sự có: số phòng, tầng, loại, sức chứa, trạng thái, và một
 * dòng gợi ý (lý do khóa phòng, người đang dọn…). Không có tên khách hay mã đặt phòng — hệ thống
 * không lưu thông tin khách (BR-ROOM-09).
 *
 * @param showFloor sơ đồ phòng đã nhóm theo tầng nên không cần in lại tầng trên từng thẻ
 * @param hint      { text, attention } — dòng gợi ý dưới thẻ; `attention` = việc cần xử lý
 * @param selected  đang mở ở bảng chi tiết bên phải
 */
export default function RoomCard({ room, onSelect, showFloor = false, hint = null, selected = false }) {
  const meta = roomStatusMeta(room.status);
  return (
    <button
      type="button"
      className={`room-card room-tone--${meta.tone} ${selected ? 'is-selected' : ''}`}
      onClick={() => onSelect(room)}
      aria-pressed={selected}
      aria-label={`Phòng ${room.roomNumber}, ${meta.label}${hint ? `, ${hint.text}` : ''}`}
    >
      <span className="room-card__top">
        <span className="room-card__number">{room.roomNumber}</span>
        <span className="room-card__icon material-symbols-outlined" aria-hidden="true">
          {meta.icon}
        </span>
      </span>
      <span className="room-card__meta">
        {showFloor && `Tầng ${room.floor} · `}
        {room.roomTypeName ?? 'Chưa rõ loại'} · {room.capacity} người
      </span>
      <RoomStatusBadge status={room.status} />
      {hint && (
        <span className={`room-card__hint ${hint.attention ? 'is-attention' : ''}`} title={hint.text}>
          {hint.text}
        </span>
      )}
    </button>
  );
}
