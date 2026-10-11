import RoomStatusBadge from './RoomStatusBadge';
import TaskStatusBadge from './TaskStatusBadge';
import { roomStatusMeta, taskStatusMeta } from '../../pages/rooms/roomLabels';

/** Việc còn mở: trạng thái phòng lúc này chính là bước dọn đang tới (Đang dọn / Chờ kiểm tra). */
const OPEN_STATUSES = ['IN_PROGRESS', 'PENDING_INSPECTION'];

/**
 * Trạng thái hiện cạnh phòng trên lịch dọn. Việc còn mở → trạng thái phòng HIỆN TẠI (trùng đúng bước
 * dọn); việc đã xong / đã hủy → kết quả của lần dọn đó, vì trạng thái phòng bây giờ (ví dụ đã có khách
 * mới) không còn nói gì về hôm dọn.
 */
export default function CleaningStatusBadge({ task }) {
  return OPEN_STATUSES.includes(task.status)
    ? <RoomStatusBadge status={task.roomStatus} />
    : <TaskStatusBadge status={task.status} />;
}

/** Tông màu đi cùng {@link CleaningStatusBadge} — cho viền của dòng phòng. */
export function cleaningTone(task) {
  return OPEN_STATUSES.includes(task.status)
    ? roomStatusMeta(task.roomStatus).tone
    : taskStatusMeta(task.status).tone;
}
