import { compareQueue, isRedo } from '../../pages/rooms/cleaningBoard';
import { formatShortDateTime } from '../../pages/rooms/format';
import { taskSourceLabel } from '../../pages/rooms/roomLabels';

/**
 * S-21 Việc đang chờ nhận — RM-21, BR-HK-02 (sửa), BR-HK-14 → BR-HK-16, BR-HK-18 (M2, chốt 19/10/2026).
 *
 * Mọi việc «Chưa phân công» của khách sạn, việc dọn lại xếp đầu (cùng thứ tự bảng điều phối). Được nhận
 * hay không do BACKEND trả (`canClaim`, `blockedReason`) — màn hình không tự suy luật: chưa nhận được thì
 * hiện nguyên câu lý do và làm mờ nút, danh sách vẫn hiện để biết còn bao nhiêu phòng chờ.
 *
 * Không có thông báo đẩy (DM-16): có nút Tải lại, và trang cha tải lại sau mỗi lần bấm Nhận.
 *
 * @param data       `{ canClaim, blockedReason, tasks }` từ `GET /housekeeping/tasks/open`; null = chưa tải
 * @param claimingId id việc đang gửi lệnh nhận — trong lúc đó mọi nút Nhận khóa lại
 * @param onClaim    (task) — bấm Nhận việc
 * @param onReload   () — bấm Tải lại
 * @param onShowPreviousInspection (task) — xem lý do lần trước không đạt của việc dọn lại
 */
export default function OpenTaskList({ data, loading, claimingId, onClaim, onReload, onShowPreviousInspection }) {
  const tasks = [...(data?.tasks ?? [])].sort(compareQueue);
  const canClaim = Boolean(data?.canClaim);

  return (
    <section className="panel open-tasks" aria-label="Việc đang chờ nhận">
      <div className="panel__head">
        <h2>Việc đang chờ nhận</h2>
        <div className="open-tasks__tools">
          <span className="chip">{tasks.length} phòng</span>
          <button type="button" className="btn btn--ghost btn--sm" onClick={onReload} disabled={loading}>
            Tải lại
          </button>
        </div>
      </div>

      {data && !canClaim && data.blockedReason && (
        <div className="alert alert--info" role="note">
          {data.blockedReason}
        </div>
      )}

      {data && tasks.length === 0 && (
        <div className="state state--empty">
          <p>Không có phòng nào đang chờ nhận.</p>
        </div>
      )}

      <ul className="task-list">
        {tasks.map((task) => (
          <li key={task.id} className="task-row">
            <div className="task-row__info">
              <span className="task-row__room">
                {task.roomNumber ?? '—'}
                {task.roomTypeName && <small> - {task.roomTypeName}</small>}
              </span>
              <span className="task-row__meta">
                Tầng {task.floor ?? '—'}
                {task.createdAt && ` · chờ từ ${formatShortDateTime(task.createdAt)}`}
              </span>
              {/* BR-HK-12: việc dọn lại — người nhận cần biết lần trước không đạt ở chỗ nào. */}
              {isRedo(task) && (
                <>
                  <span className="task-row__meta open-tasks__redo">{taskSourceLabel(task.createdSource)}</span>
                  <button type="button" className="link-btn" onClick={() => onShowPreviousInspection(task)}>
                    Xem lý do phải dọn lại
                  </button>
                </>
              )}
            </div>
            <button
              type="button"
              className="btn btn--primary task-row__action"
              disabled={!canClaim || claimingId !== null}
              onClick={() => onClaim(task)}
            >
              {claimingId === task.id ? 'Đang nhận…' : 'Nhận việc'}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
