import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { fetchOpenTasks, fetchTasks } from '../../api/housekeeping';
import CleaningTaskDetail from '../../components/rooms/CleaningTaskDetail';
import CompleteTaskModal from '../../components/rooms/CompleteTaskModal';
import ReturnTaskModal from '../../components/rooms/ReturnTaskModal';
import TaskStatusBadge from '../../components/rooms/TaskStatusBadge';
import MyShiftCard from '../../components/scheduling/MyShiftCard';
import { useWorkDayShifts } from '../../hooks/useWorkDayShifts';
import { formatDate, todayIso } from './format';
import { compareNatural, taskRoomLabel, teamNames } from './roomLabels';
import './rooms.css';

const DONE_STATUSES = ['PENDING_INSPECTION', 'COMPLETED'];

/**
 * Thứ tự làm việc: tồn đọng trước (BR-HK-04), rồi phòng dọn lại (khách có thể đang chờ — BR-HK-12), rồi theo
 * tầng và số phòng để đi ít nhất.
 */
function compareWorkOrder(a, b) {
  return Number(b.overdue) - Number(a.overdue)
    || Number(Boolean(b.parentTaskId)) - Number(Boolean(a.parentTaskId))
    || compareNatural(a.floor, b.floor)
    || compareNatural(a.roomNumber, b.roomNumber);
}

/**
 * S-16 Việc của tôi + S-17 Hoàn thành + S-22 Trả lại việc — RM-17, RM-18, RM-22, BR-PERM-05, BR-HK-04,
 * BR-HK-16, BR-HK-17.
 *
 * Màn hình được dùng nhiều nhất hệ thống, và dùng khi đang đứng trong phòng: làm **mobile-first** thật sự.
 * Chốt 02/11/2026: thay danh sách "mỗi việc một dòng + nút Hoàn thành" bằng CHI TIẾT phòng đang dọn
 * ({@link CleaningTaskDetail}) — thông tin phòng, lý do dọn lại, ghi chú phòng, tài sản để báo hỏng. Danh sách
 * việc chờ nhận (S-21) chuyển sang sơ đồ phòng: nhận việc ngay trên thẻ phòng.
 *
 * Không có bộ lọc theo người: backend đã ép nhân viên chỉ thấy việc có mình trong nhóm dọn, nên gọi
 * `GET /housekeeping/tasks` là đủ (BR-PERM-05). Một người bấm «Hoàn thành» là xong cho cả nhóm.
 *
 * Các mục, theo thứ tự quan trọng khi đang làm:
 *   - **Ca hôm nay** — khối Vào ca / Kết thúc ca của chấm công (`MyShiftCard`), y như trên sơ đồ phòng: tự nhận
 *     việc đòi đã vào ca (BR-HK-15).
 *   - **Đang dọn** — chi tiết MỘT phòng: phòng đầu theo thứ tự làm việc, hoặc phòng vừa chọn ở «Tiếp theo».
 *     Tự nhận chỉ được một việc một lúc (BR-HK-16), nhưng Quản lý / Giám sát giao tay được nhiều việc
 *     (BR-HK-02) — các việc còn lại nằm ở «Tiếp theo». Mọi việc đang làm đều hiện, KHÔNG lọc theo ngày:
 *     việc tồn đọng từ hôm qua vẫn phải làm (BR-HK-04).
 *   - Chưa có việc: số phòng đang chờ người nhận và lối sang sơ đồ phòng (lọc sẵn «Chờ dọn» — phòng chờ dọn
 *     luôn có đúng một việc chưa phân công), kèm lý do chưa nhận được nếu có — backend nói
 *     (`/housekeeping/tasks/open`).
 *   - **Đã xong hôm nay** — để tự đối chiếu cuối ca; tính theo NGÀY LÀM của ca đang làm (ca đêm từ hôm
 *     qua thì là hôm qua — BR-SCH-03).
 *
 * Không có thông báo đẩy (DM-16): tải lại sau mỗi thao tác và khi người dùng mở lại tab.
 */
export default function MyTasksPage() {
  const { user } = useAuth();
  const today = todayIso();
  // Nhận phòng trên sơ đồ xong thì chuyển về đây kèm câu báo (react-router state).
  const { state: navigationState, pathname } = useLocation();
  const navigate = useNavigate();

  const [todo, setTodo] = useState([]);
  const [done, setDone] = useState([]);
  const [focusId, setFocusId] = useState(null);         // việc đang xem chi tiết; null = việc đầu tiên
  const [openTasks, setOpenTasks] = useState(null);     // { canClaim, blockedReason, workDay, tasks }
  const [openError, setOpenError] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [banner, setBanner] = useState(navigationState?.banner ?? null);   // { type, text }
  const [completing, setCompleting] = useState(null);   // việc đang mở hộp «Báo xong» (S-17)
  const [returning, setReturning] = useState(null);     // việc đang mở hộp thoại trả lại (S-22)
  // Chấm công M2: ca thuộc ngày làm hiện tại — cùng nguồn với khối «Ca hôm nay» trên sơ đồ phòng.
  const { shifts: workDayShifts, reload: reloadWorkDay, today: workDayToday } = useWorkDayShifts();

  // Đọc câu báo một lần rồi bỏ khỏi lịch sử duyệt, để F5 không hiện lại thông báo cũ.
  useEffect(() => {
    if (navigationState?.banner) navigate(pathname, { replace: true });
  }, [navigationState, pathname, navigate]);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const [inProgress, open] = await Promise.all([
        fetchTasks({ status: 'IN_PROGRESS' }),
        // Việc chờ nhận lỗi thì chỉ khối "chưa có việc" thiếu số đếm — không chặn việc đang làm của mình.
        fetchOpenTasks().catch((err) => {
          setOpenError(readErrorMessage(err, 'Không tải được số phòng đang chờ nhận.'));
          return null;
        }),
      ]);
      if (open) setOpenError('');
      const ofWorkDay = await fetchTasks({ assignedDate: open?.workDay ?? today });
      setTodo([...(inProgress.content ?? [])].sort(compareWorkOrder));
      setOpenTasks(open);
      setDone((ofWorkDay.content ?? []).filter((task) => DONE_STATUSES.includes(task.status)));
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được danh sách việc của bạn.'));
    } finally {
      setLoading(false);
    }
  }, [today]);

  useEffect(() => {
    load();
  }, [load]);

  // Người khác có thể vừa nhận phòng, Quản lý vừa giao việc — mở lại tab là tải lại.
  useEffect(() => {
    const onVisible = () => document.visibilityState === 'visible' && load();
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [load]);

  // Việc đang xem vừa xong / bị gỡ thì quay về việc đầu tiên còn lại.
  const focus = useMemo(() => todo.find((task) => task.id === focusId) ?? todo[0] ?? null, [todo, focusId]);
  const others = todo.filter((task) => task !== focus);

  function showTask(task) {
    setFocusId(task.id);
    setBanner(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  /** Báo xong: nói rõ là CHƯA xong hẳn — còn chờ kiểm tra — để nhân viên khỏi tưởng còn sót việc. */
  async function handleCompleted(updated) {
    setCompleting(null);
    setBanner({
      type: 'success',
      text: `Đã báo xong phòng ${taskRoomLabel(updated)}`
        + ((updated.assignees?.length ?? 0) > 1 ? ' cho cả nhóm' : '')
        + ' — chờ quản lý kiểm tra.',
    });
    await load();
  }

  /** Trả xong: nói rõ phòng đi đâu — về hàng chờ cho người khác nhận, hay đồng đội dọn tiếp. */
  async function handleReturned(updated) {
    setReturning(null);
    const staying = teamNames(updated);   // mình đã rời nhóm, còn lại là đồng đội
    setBanner({
      type: 'success',
      text: `Đã trả lại phòng ${taskRoomLabel(updated)}` + (staying ? ` — ${staying} vẫn dọn tiếp.` : '.'),
    });
    await load();
  }

  /** Vào / kết thúc ca xong thì được nhận việc hay không cũng đổi theo — tải lại khối ca và việc chờ nhận. */
  function handleShiftChanged(message) {
    setBanner(message);
    reloadWorkDay();
    load();
  }

  const waitingCount = openTasks?.tasks?.length ?? 0;

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
      {loading && todo.length === 0 && <p className="state">Đang tải dữ liệu…</p>}

      {workDayShifts && (
        <MyShiftCard shifts={workDayShifts} today={workDayToday} onChanged={handleShiftChanged} />
      )}

      {focus && (
        <CleaningTaskDetail
          key={focus.id}
          task={focus}
          meId={user?.id}
          onComplete={setCompleting}
          onReturn={setReturning}
        />
      )}

      {others.length > 0 && (
        <section className="panel">
          <div className="panel__head">
            <h2>Tiếp theo</h2>
            <span className="chip">{others.length} phòng</span>
          </div>
          <ul className="task-list">
            {others.map((task) => (
              <li key={task.id}>
                <button type="button" className="task-row task-row--pick" onClick={() => showTask(task)}>
                  <span className="task-row__info">
                    <span className="task-row__room">
                      {task.roomNumber ?? '—'}
                      {task.roomTypeName && <small> - {task.roomTypeName}</small>}
                    </span>
                    <span className="task-row__meta">Tầng {task.floor ?? '—'}</span>
                    {task.overdue && (
                      <span className="task-card__flag">Tồn đọng từ {formatDate(task.assignedDate)}</span>
                    )}
                    {task.parentTaskId && <span className="task-card__flag">Dọn lại sau kiểm tra</span>}
                  </span>
                  <span className="task-row__go">Xem phòng ›</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {!loading && !loadError && todo.length === 0 && (
        <section className="panel clean-empty">
          <p className="clean-empty__title">Bạn chưa có phòng nào đang dọn</p>
          {openTasks && (
            <p className="muted">
              {waitingCount > 0
                ? `Có ${waitingCount} phòng đang chờ người nhận.`
                : 'Hiện không có phòng nào chờ người nhận.'}
            </p>
          )}
          {openError && <p className="muted">{openError}</p>}
          {/* Chưa vào ca, đang là lễ tân duy nhất của ca… — backend nói, màn hình chỉ hiện ra (BR-HK-15). */}
          {openTasks && !openTasks.canClaim && openTasks.blockedReason && waitingCount > 0 && (
            <p className="board-panel__why">
              <span className="material-symbols-outlined" aria-hidden="true">info</span>
              {openTasks.blockedReason}
            </p>
          )}
          <Link className="btn btn--primary clean-empty__go" to="/so-do-phong?status=DIRTY">
            Chọn phòng trên sơ đồ
          </Link>
          <p className="muted">Quản lý hoặc giám sát cũng có thể giao phòng cho bạn.</p>
        </section>
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

      {completing && (
        <CompleteTaskModal
          task={completing}
          meId={user?.id}
          onClose={() => setCompleting(null)}
          onCompleted={handleCompleted}
        />
      )}

      {returning && (
        <ReturnTaskModal
          task={returning}
          meId={user?.id}
          onClose={() => setReturning(null)}
          onReturned={handleReturned}
        />
      )}
    </div>
  );
}
