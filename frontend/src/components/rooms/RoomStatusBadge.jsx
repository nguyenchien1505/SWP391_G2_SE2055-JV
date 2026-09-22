import { roomStatusMeta } from '../../pages/rooms/roomLabels';

// design.md mục 3: huy hiệu trạng thái luôn có CHỮ kèm màu — người khó phân biệt màu vẫn
// đọc được. Tách riêng khỏi StatusBadge.jsx (trạng thái Location) để không đụng file dùng chung.
export default function RoomStatusBadge({ status, large = false }) {
  const meta = roomStatusMeta(status);
  return (
    <span className={`badge badge--room room-tone--${meta.tone} ${large ? 'badge--lg' : ''}`}>
      {meta.label}
    </span>
  );
}
