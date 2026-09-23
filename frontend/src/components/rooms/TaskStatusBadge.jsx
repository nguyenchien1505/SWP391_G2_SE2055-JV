import { taskStatusMeta } from '../../pages/rooms/roomLabels';

/**
 * Huy hiệu trạng thái một việc dọn phòng — BR-HK-06 (5 trạng thái).
 *
 * Dùng lại đúng bộ tông màu của trạng thái PHÒNG (`room-tone--*`) chứ không đặt màu mới: việc
 * "Đang làm" luôn đi cùng phòng "Đang dọn", "Chờ kiểm tra" đi cùng phòng "Chờ kiểm tra" — cùng
 * màu giúp người dùng nối hai thứ đó với nhau mà không phải học thêm bảng màu thứ hai.
 */
export default function TaskStatusBadge({ status, large = false }) {
  const meta = taskStatusMeta(status);
  return (
    <span className={`badge badge--room room-tone--${meta.tone} ${large ? 'badge--lg' : ''}`}>
      {meta.label}
    </span>
  );
}
