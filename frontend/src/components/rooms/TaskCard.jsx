import TaskStatusBadge from './TaskStatusBadge';
import { formatDate } from '../../pages/rooms/format';
import { taskSourceLabel, taskStatusMeta, teamNames, unassignedReasonLabel } from '../../pages/rooms/roomLabels';

/**
 * Một việc dọn phòng dạng thẻ — S-09 / S-11 (bảng lịch dọn của Quản lý chi nhánh).
 *
 * Số phòng là thứ to nhất trên thẻ: người dùng quét bảng để tìm phòng, không phải tìm mã việc. Loại
 * phòng đi ngay sau số phòng ("301 - Deluxe") để biết phòng to nhỏ khi chia người.
 *
 * @param today      hôm nay theo ISO — để đánh dấu việc TỒN ĐỌNG (BR-HK-04)
 * @param actions    [{ key, label, danger, onClick }] — nút hiện dưới thẻ
 * @param onShowPreviousInspection  mở biên bản lần kiểm tra trước; chỉ có nghĩa với việc dọn lại
 */
export default function TaskCard({ task, today, actions = [], onShowPreviousInspection }) {
  const tone = taskStatusMeta(task.status).tone;
  // BR-HK-04: hết ca chưa xong thì việc TỒN ĐỌNG chứ không tự hủy — phải nhìn ra ngay.
  const overdue = task.status === 'IN_PROGRESS' && task.assignedDate && task.assignedDate < today;
  const team = teamNames(task);

  return (
    <article className={`task-card room-tone--${tone}`}>
      <header className="task-card__head">
        <span className="task-card__room">
          {task.roomNumber ?? '—'}
          {task.roomTypeName && <small> - {task.roomTypeName}</small>}
        </span>
        <TaskStatusBadge status={task.status} />
      </header>

      <p className="task-card__meta">
        Tầng {task.floor ?? '—'} · {taskSourceLabel(task.createdSource)}
      </p>

      {team && (
        <p className="task-card__meta">
          <b>{team}</b>
          {task.assignedDate ? ` · ${formatDate(task.assignedDate)}` : ''}
        </p>
      )}

      {overdue && (
        <p className="task-card__flag">Tồn đọng từ {formatDate(task.assignedDate)}</p>
      )}

      {task.status === 'UNASSIGNED' && unassignedReasonLabel(task.unassignedReason) && (
        <p className="task-card__meta muted">{unassignedReasonLabel(task.unassignedReason)}</p>
      )}

      {/* BR-HK-12: việc dọn lại phải xem được lý do không đạt, nằm ở biên bản của task cha. */}
      {task.parentTaskId && onShowPreviousInspection && (
        <button type="button" className="link-btn" onClick={() => onShowPreviousInspection(task)}>
          Xem lần kiểm tra trước
        </button>
      )}

      {actions.length > 0 && (
        <div className="task-card__actions">
          {actions.map((action) => (
            <button
              key={action.key}
              type="button"
              className={`btn btn--sm ${action.danger ? 'btn--danger' : action.ghost ? 'btn--ghost' : 'btn--primary'}`}
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
