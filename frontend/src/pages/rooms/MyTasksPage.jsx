import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { claimTask, completeTask, fetchOpenTasks, fetchTasks } from '../../api/housekeeping';
import { fetchShifts } from '../../api/scheduling';
import MyShiftCard from '../../components/rooms/MyShiftCard';
import OpenTaskList from '../../components/rooms/OpenTaskList';
import PreviousInspectionModal from '../../components/rooms/PreviousInspectionModal';
import TaskStatusBadge from '../../components/rooms/TaskStatusBadge';
import { formatDate, todayIso } from './format';
import { compareNatural, taskRoomLabel, teamNames } from './roomLabels';
import { isInCurrentWorkDay, yesterdayIso } from './workDay';
import './rooms.css';

const DONE_STATUSES = ['PENDING_INSPECTION', 'COMPLETED'];

/**
 * S-16 Việc của tôi + S-17 Hoàn thành + S-21 Việc đang chờ nhận — RM-17, RM-18, RM-21, BR-PERM-05,
 * BR-HK-14 → BR-HK-16.
 *
 * Màn hình được dùng nhiều nhất hệ thống, và dùng khi đang đứng trong hành lang: làm
 * **mobile-first** thật sự — không có bảng, mỗi việc là một dòng với số phòng cỡ lớn và đúng
 * MỘT nút to chiếm hết chiều ngang.
 *
 * Không có bộ lọc theo người: backend đã ép nhân viên chỉ thấy việc có mình trong nhóm dọn, nên gọi
 * `GET /housekeeping/tasks` là đủ (BR-PERM-05). Một phòng có thể nhiều người dọn — mỗi việc ghi rõ
 * dọn cùng ai, và một người bấm «Hoàn thành» là xong cho cả nhóm.
 *
 * Các mục, theo thứ tự quan trọng khi đang làm:
 *   - **Ca hôm nay** — kèm nút Vào ca / Kết thúc ca: tự nhận việc đòi đã vào ca (BR-HK-15).
 *   - **Cần làm** — mọi việc đang làm, KHÔNG lọc theo ngày: việc tồn đọng từ hôm qua vẫn phải
 *     hiện, nếu không nhân viên sẽ không bao giờ thấy nó nữa (BR-HK-04).
 *   - **Việc đang chờ nhận** — tự nhận việc chưa phân công (M2); được nhận hay không do backend nói.
 *   - **Đã xong hôm nay** — để tự đối chiếu cuối ca; tính theo NGÀY LÀM của ca đang làm (ca đêm từ hôm
 *     qua thì là hôm qua — BR-SCH-03).
 *
 * Không có thông báo đẩy (DM-16): tải lại sau mỗi thao tác và khi người dùng mở lại tab.
 */
export default function MyTasksPage() {
  const { user } = useAuth();
  const today = todayIso();

  const [todo, setTodo] = useState([]);
  const [done, setDone] = useState([]);
  const [openTasks, setOpenTasks] = useState(null);     // { canClaim, blockedReason, workDay, tasks }
  const [openError, setOpenError] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [banner, setBanner] = useState(null);       // { type, text }
  const [submittingId, setSubmittingId] = useState(null);
  const [claimingId, setClaimingId] = useState(null);
  const [previousOf, setPreviousOf] = useState(null);   // việc dọn lại đang xem lý do không đạt
  const [shifts, setShifts] = useState(null);           // ca thuộc ngày làm hiện tại; null = chưa tải / lỗi

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const [inProgress, open] = await Promise.all([
        fetchTasks({ status: 'IN_PROGRESS' }),
        // Danh sách chờ nhận lỗi thì chỉ mục đó báo lỗi — không chặn việc đang làm của mình.
        fetchOpenTasks().catch((err) => {
          setOpenError(readErrorMessage(err, 'Không tải được danh sách việc chờ nhận.'));
          return null;
        }),
      ]);
      if (open) setOpenError('');
      const ofWorkDay = await fetchTasks({ assignedDate: open?.workDay ?? today });
      setTodo([...(inProgress.content ?? [])].sort((a, b) => compareNatural(a.roomNumber, b.roomNumber)));
      setOpenTasks(open);
      setDone((ofWorkDay.content ?? []).filter((task) => DONE_STATUSES.includes(task.status)));
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được danh sách việc của bạn.'));
    } finally {
      setLoading(false);
    }
  }, [today]);

  // Ca hôm nay: backend chỉ trả ca của chính nhân viên. Lỗi thì ẩn thẻ — không chặn việc dọn. Lấy cả
  // hôm qua để hiện ca qua đêm bắt đầu từ hôm qua mà người đó còn đang làm (BR-SCH-03).
  const loadShifts = useCallback(async () => {
    try {
      const list = await fetchShifts({ from: yesterdayIso(), to: today });
      setShifts(list.filter((shift) => isInCurrentWorkDay(shift)));
    } catch {
      setShifts(null);
    }
  }, [today]);

  useEffect(() => {
    load();
    loadShifts();
  }, [load, loadShifts]);

  // Người khác có thể vừa nhận phòng, Quản lý vừa giao việc — mở lại tab là tải lại.
  useEffect(() => {
    const onVisible = () => document.visibilityState === 'visible' && load();
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [load]);

  async function handleComplete(task) {
    setSubmittingId(task.id);
    setBanner(null);
    try {
      const updated = await completeTask(task.id);
      // Nói rõ là CHƯA xong hẳn, tránh nhân viên tưởng mình còn sót việc khi thấy nó vẫn ở đó.
      setBanner({
        type: 'success',
        text: `Đã báo xong phòng ${taskRoomLabel(updated)}`
          + ((updated.assignees?.length ?? 0) > 1 ? ' cho cả nhóm' : '')
          + ' — chờ quản lý kiểm tra.',
      });
      await load();
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không báo hoàn thành được.') });
    } finally {
      setSubmittingId(null);
    }
  }

  /** Tải lại cả khi lỗi: phòng người khác vừa nhận sẽ biến khỏi danh sách (BR-HK-18). */
  async function handleClaim(task) {
    setClaimingId(task.id);
    setBanner(null);
    try {
      const claimed = await claimTask(task.id);
      setBanner({
        type: 'success',
        text: `Bạn đã nhận phòng ${taskRoomLabel(claimed)} — phòng chuyển sang «Đang dọn».`,
      });
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không nhận được việc này.') });
    } finally {
      setClaimingId(null);
      await load();
    }
  }

  /** Vào / kết thúc ca xong thì được nhận việc hay không cũng đổi theo — tải lại cả hai. */
  function handleShiftChanged(message) {
    setBanner(message);
    loadShifts();
    load();
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

      {shifts && <MyShiftCard shifts={shifts} today={today} onChanged={handleShiftChanged} />}

      <section className="panel">
        <div className="panel__head">
          <h2>Cần làm</h2>
          <span className="chip">{todo.length} phòng</span>
        </div>

        {!loading && todo.length === 0 && (
          <div className="state state--empty">
            <p>Bạn không có việc dọn nào đang làm.</p>
            <p className="muted">Quản lý giao việc, hoặc bạn tự nhận ở mục «Việc đang chờ nhận».</p>
          </div>
        )}

        <ul className="task-list">
          {todo.map((task) => (
            <li key={task.id} className="task-row">
              <div className="task-row__info">
                <span className="task-row__room">
                  {task.roomNumber ?? '—'}
                  {task.roomTypeName && <small> - {task.roomTypeName}</small>}
                </span>
                <span className="task-row__meta">Tầng {task.floor ?? '—'}</span>
                {teamNames(task, user?.id) && (
                  <span className="task-row__meta">Dọn cùng: {teamNames(task, user?.id)}</span>
                )}
                {/* BR-HK-04: việc của hôm trước chưa xong thì vẫn phải làm, không tự hủy. Cờ do backend
                    tính — việc của ca đêm từ hôm qua chưa tồn đọng khi ca chưa hết. */}
                {task.overdue && (
                  <span className="task-card__flag">Tồn đọng từ {formatDate(task.assignedDate)}</span>
                )}
                {/* BR-HK-12: người dọn lại cần biết lần trước không đạt ở chỗ nào. */}
                {task.parentTaskId && (
                  <button type="button" className="link-btn" onClick={() => setPreviousOf(task)}>
                    Xem lý do phải dọn lại
                  </button>
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

      {openError ? (
        <div className="alert alert--error" role="alert">
          {openError}
        </div>
      ) : (
        <OpenTaskList
          data={openTasks}
          loading={loading}
          claimingId={claimingId}
          onClaim={handleClaim}
          onReload={load}
          onShowPreviousInspection={setPreviousOf}
        />
      )}

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
                <span className="task-row__room">
                  {task.roomNumber ?? '—'}
                  {task.roomTypeName && <small> - {task.roomTypeName}</small>}
                </span>
                {teamNames(task, user?.id) && (
                  <span className="task-row__meta">Dọn cùng: {teamNames(task, user?.id)}</span>
                )}
              </div>
              <TaskStatusBadge status={task.status} />
            </li>
          ))}
        </ul>
      </section>

      {previousOf && (
        <PreviousInspectionModal task={previousOf} onClose={() => setPreviousOf(null)} />
      )}
    </div>
  );
}
