import { useCallback, useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { completeTask, fetchTasks } from '../../api/housekeeping';
import TaskStatusBadge from '../../components/rooms/TaskStatusBadge';
import { formatDate, todayIso } from './format';
import { compareNatural, taskTypeLabel } from './roomLabels';
import './rooms.css';

const DONE_STATUSES = ['PENDING_INSPECTION', 'COMPLETED'];

/**
 * S-16 Việc của tôi + S-17 Hoàn thành — RM-17, RM-18, BR-PERM-05.
 *
 * Màn hình được dùng nhiều nhất hệ thống, và dùng khi đang đứng trong hành lang: làm
 * **mobile-first** thật sự — không có bảng, mỗi việc là một dòng với số phòng cỡ lớn và đúng
 * MỘT nút to chiếm hết chiều ngang.
 *
 * Không có bộ lọc theo người: backend đã ép nhân viên chỉ thấy việc của chính mình, nên gọi
 * `GET /housekeeping/tasks` là đủ (BR-PERM-05).
 *
 * Hai mục:
 *   - **Cần làm** — mọi việc đang làm, KHÔNG lọc theo ngày: việc tồn đọng từ hôm qua vẫn phải
 *     hiện, nếu không nhân viên sẽ không bao giờ thấy nó nữa (BR-HK-04).
 *   - **Đã xong hôm nay** — để tự đối chiếu cuối ca.
 */
export default function MyTasksPage() {
  const today = todayIso();

  const [todo, setTodo] = useState([]);
  const [done, setDone] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [banner, setBanner] = useState(null);       // { type, text }
  const [submittingId, setSubmittingId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const [inProgress, ofToday] = await Promise.all([
        fetchTasks({ status: 'IN_PROGRESS' }),
        fetchTasks({ assignedDate: today }),
      ]);
      setTodo([...(inProgress.content ?? [])].sort((a, b) => compareNatural(a.roomNumber, b.roomNumber)));
      setDone((ofToday.content ?? []).filter((task) => DONE_STATUSES.includes(task.status)));
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được danh sách việc của bạn.'));
    } finally {
      setLoading(false);
    }
  }, [today]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleComplete(task) {
    setSubmittingId(task.id);
    setBanner(null);
    try {
      const updated = await completeTask(task.id);
      setBanner({
        type: 'success',
        text: updated.status === 'PENDING_INSPECTION'
          // Nói rõ là CHƯA xong hẳn, tránh nhân viên tưởng mình còn sót việc khi thấy nó vẫn ở đó.
          ? `Đã báo xong phòng ${updated.roomNumber} — chờ quản lý kiểm tra.`
          : `Đã hoàn thành phòng ${updated.roomNumber}.`,
      });
      await load();
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không báo hoàn thành được.') });
    } finally {
      setSubmittingId(null);
    }
  }

  return (
    <div className="page my-tasks">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Cá nhân › Việc dọn phòng</p>
          <h1>Việc của tôi</h1>
        </div>
      </div>

      {banner && (
        <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="status">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">
            ×
          </button>
        </div>
      )}

      {loadError && (
        <div className="alert alert--error" role="alert">
          {loadError}
        </div>
      )}
      {loading && <p className="state">Đang tải dữ liệu…</p>}

      <section className="panel">
        <div className="panel__head">
          <h2>Cần làm</h2>
          <span className="chip">{todo.length} phòng</span>
        </div>

        {!loading && todo.length === 0 && (
          <div className="state state--empty">
            <p>Bạn không có việc dọn nào đang làm.</p>
            <p className="muted">Quản lý giao việc thì phòng sẽ hiện ở đây.</p>
          </div>
        )}

        <ul className="task-list">
          {todo.map((task) => (
            <li key={task.id} className="task-row">
              <div className="task-row__info">
                <span className="task-row__room">{task.roomNumber ?? '—'}</span>
                <span className="task-row__meta">
                  Tầng {task.floor ?? '—'} · {taskTypeLabel(task.taskType)}
                </span>
                {/* BR-HK-04: việc của hôm trước chưa xong thì vẫn phải làm, không tự hủy. */}
                {task.assignedDate && task.assignedDate < today && (
                  <span className="task-card__flag">Tồn đọng từ {formatDate(task.assignedDate)}</span>
                )}
              </div>
              <button
                type="button"
                className="btn btn--primary task-row__action"
                disabled={submittingId === task.id}
                onClick={() => handleComplete(task)}
              >
                {submittingId === task.id ? 'Đang gửi…' : 'Hoàn thành'}
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section className="panel">
        <div className="panel__head">
          <h2>Đã xong hôm nay</h2>
          <span className="chip">{done.length} phòng</span>
        </div>

        {!loading && done.length === 0 && (
          <div className="state state--empty">
            <p>Chưa có phòng nào hoàn thành hôm nay.</p>
          </div>
        )}

        <ul className="task-list">
          {done.map((task) => (
            <li key={task.id} className="task-row task-row--done">
              <div className="task-row__info">
                <span className="task-row__room">{task.roomNumber ?? '—'}</span>
                <span className="task-row__meta">{taskTypeLabel(task.taskType)}</span>
              </div>
              <TaskStatusBadge status={task.status} />
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
