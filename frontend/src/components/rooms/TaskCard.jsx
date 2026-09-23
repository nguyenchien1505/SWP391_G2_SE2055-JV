import TaskStatusBadge from './TaskStatusBadge';
import { formatDate } from '../../pages/rooms/format';
import {
  taskSourceLabel,
  taskStatusMeta,
  taskTypeLabel,
  unassignedReasonLabel,
} from '../../pages/rooms/roomLabels';

/**
 * Một việc dọn phòng dạng thẻ — S-09 / S-11 (bảng lịch dọn của Quản lý chi nhánh).
 *
 * Số phòng là thứ to nhất trên thẻ: người dùng quét bảng để tìm phòng, không phải tìm mã việc.
 *
 * @param staffName  tên người làm, tra sẵn từ danh bạ (DTO chỉ có `assignedStaffId`)
 * @param today      hôm nay theo ISO — để đánh dấu việc TỒN ĐỌNG (BR-HK-04)
 * @param actions    [{ key, label, danger, onClick }] — nút hiện dưới thẻ
 */
export default function TaskCard({ task, staffName, today, actions = [] }) {
  const tone = taskStatusMeta(task.status).tone;
  // BR-HK-04: hết ca chưa xong thì việc TỒN ĐỌNG chứ không tự hủy — phải nhìn ra ngay.
  const overdue = task.status === 'IN_PROGRESS' && task.assignedDate && task.assignedDate < today;

  return (
    <article className={`task-card room-tone--${tone}`}>
      <header className="task-card__head">
        <span className="task-card__room">{task.roomNumber ?? '—'}</span>
        <TaskStatusBadge status={task.status} />
      </header>

      <p className="task-card__meta">
        Tầng {task.floor ?? '—'} · {taskTypeLabel(task.taskType)}
      </p>
      <p className="task-card__meta">{taskSourceLabel(task.createdSource)}</p>

      {task.assignedStaffId && (
        <p className="task-card__meta">
          <b>{staffName ?? 'Nhân viên đã nghỉ'}</b>
          {task.assignedDate ? ` · ${formatDate(task.assignedDate)}` : ''}
        </p>
      )}

      {overdue && (
        <p className="task-card__flag">Tồn đọng từ {formatDate(task.assignedDate)}</p>
      )}

      {task.status === 'UNASSIGNED' && unassignedReasonLabel(task.unassignedReason) && (
        <p className="task-card__meta muted">{unassignedReasonLabel(task.unassignedReason)}</p>
      )}

      {actions.length > 0 && (
        <div className="task-card__actions">
          {actions.map((action) => (
            <button
              key={action.key}
              type="button"
              className={`btn btn--sm ${action.danger ? 'btn--danger' : 'btn--primary'}`}
              onClick={action.onClick}
            >
              {action.label}
            </button>
          ))}
        </div>
      )}
    </article>
  );
}
