import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { fetchTasks } from '../../api/housekeeping';
import AssignTaskModal from '../../components/rooms/AssignTaskModal';
import AssignToStaffModal from '../../components/rooms/AssignToStaffModal';
import CleaningDayBoard from '../../components/rooms/CleaningDayBoard';
import InspectTaskModal from '../../components/rooms/InspectTaskModal';
import PreviousInspectionModal from '../../components/rooms/PreviousInspectionModal';
import ReleaseTaskModal from '../../components/rooms/ReleaseTaskModal';
import TaskCard from '../../components/rooms/TaskCard';
import { todayIso } from './format';
import { TASK_STATUS_ORDER, compareNatural, taskRoomLabel, taskStatusMeta, teamNames } from './roomLabels';
import './rooms.css';

/** Ba cột của bảng lịch dọn — đúng ba trạng thái "đang mở" của một việc (BR-HK-11). */
const COLUMNS = TASK_STATUS_ORDER;

/** Tab thứ tư: việc đã đóng trong ngày, để đối chiếu cuối ca. */
const CLOSED = 'CLOSED';
const CLOSED_STATUSES = ['COMPLETED', 'CANCELLED'];

/** Hai cách xem: theo trạng thái (Kanban) và theo nhân viên (lập lịch dọn theo ngày). */
const VIEWS = [
  { key: 'status', label: 'Theo trạng thái' },
  { key: 'staff', label: 'Theo nhân viên' },
];
const VIEW_KEY = 'hk.view';

/** Nhớ cách xem lần trước — chỉ là tiện ích, không đọc được (chế độ riêng tư) thì về mặc định. */
function readSavedView() {
  try {
    const saved = window.localStorage.getItem(VIEW_KEY);
    return VIEWS.some((view) => view.key === saved) ? saved : 'status';
  } catch {
    return 'status';
  }
}

/**
 * S-09 Hàng chờ phân công + S-11 Danh sách việc dọn — RM-13, RM-14, RM-15, RM-16.
 *
 * Chỉ Quản lý chi nhánh vào được màn này (route bọc {@code RequireBranchManager}). Giám đốc
 * đã bị gỡ khỏi đây: mọi thao tác trên lịch dọn đều ✖ với họ, và danh sách của họ trộn phòng
 * của mọi khách sạn trong chuỗi nên đọc dễ nhầm.
 *
 * Chỉ còn việc dọn sau khi khách trả phòng, hệ thống tự sinh — đã bỏ dọn hằng ngày (chốt 05/10/2026),
 * nên màn này không có nút tạo việc. Một phòng giao được cho NHIỀU người cùng dọn.
 *
 * Hai cách xem, chung một bộ hộp thoại và thao tác:
 *   - **Theo trạng thái** — bảng ba cột: Quản lý nhìn ra ngay "còn bao nhiêu phòng chưa có ai dọn".
 *     Dưới 860px ba cột đổi thành ba tab (CSS + state `mobileTab`).
 *   - **Theo nhân viên** — {@link CleaningDayBoard}: mỗi người có ca trong ngày là một hàng, để lập
 *     lịch dọn và chia việc cho đều (BR-HK-02, BR-HK-03).
 *
 * Mỗi cột của bảng ba cột gọi API riêng với `status` tương ứng — đơn giản hơn tải hết rồi lọc ở
 * trình duyệt, và mỗi cột tự phân trang được khi dữ liệu lớn.
 */
export default function HousekeepingPage() {
  const { user } = useAuth();
  const canManage = user?.role === 'MANAGER' || user?.role === 'PLATFORM_ADMIN';
  const today = todayIso();

  const [view, setView] = useState(readSavedView);
  const [version, setVersion] = useState(0);    // tăng sau mỗi thao tác để bảng theo nhân viên tải lại
  const [columns, setColumns] = useState({});   // { [status]: task[] }
  const [closedTasks, setClosedTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [banner, setBanner] = useState(null);   // { type: 'success' | 'error' | 'info', text }

  const [mobileTab, setMobileTab] = useState(COLUMNS[0]);
  const [assigning, setAssigning] = useState(null);      // việc đang mở hộp thoại phân công / thêm người
  const [assigningPerson, setAssigningPerson] = useState(null); // { person, date, tasks } — giao nhiều việc
  const [releasing, setReleasing] = useState(null);      // việc đang mở hộp thoại gỡ người
  const [inspecting, setInspecting] = useState(null);    // việc đang mở hộp thoại kiểm tra (S-13)
  const [previousOf, setPreviousOf] = useState(null);    // việc dọn lại đang xem biên bản của task cha

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      // Bốn truy vấn song song: ba cột đang mở + tab đã đóng trong ngày.
      const [unassigned, inProgress, pending, closed] = await Promise.all([
        fetchTasks({ status: 'UNASSIGNED' }),
        fetchTasks({ status: 'IN_PROGRESS' }),
        fetchTasks({ status: 'PENDING_INSPECTION' }),
        fetchTasks({ assignedDate: today }),
      ]);
      setColumns({
        UNASSIGNED: unassigned.content ?? [],
        IN_PROGRESS: inProgress.content ?? [],
        PENDING_INSPECTION: pending.content ?? [],
      });
      setClosedTasks((closed.content ?? []).filter((task) => CLOSED_STATUSES.includes(task.status)));
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được lịch dọn phòng.'));
    } finally {
      setLoading(false);
    }
  }, [today]);

  // Bảng ba cột chỉ tải khi đang hiện; bảng theo nhân viên tự tải theo ngày nó đang xem.
  useEffect(() => {
    if (view === 'status') load();
  }, [view, load]);

  /** Sau mỗi thao tác: tải lại đúng bảng đang hiện. */
  const refresh = useCallback(() => {
    if (view === 'status') load();
    setVersion((current) => current + 1);
  }, [view, load]);

  function chooseView(next) {
    setView(next);
    try {
      window.localStorage.setItem(VIEW_KEY, next);
    } catch {
      // Không ghi được (chế độ riêng tư): chỉ là không nhớ lựa chọn cho lần sau.
    }
  }

  /** Giao việc xong: đóng hộp thoại và tải lại — việc vừa đổi trạng thái nên nhảy sang chỗ khác. */
  function finishAssign(updated) {
    const wasAdding = assigning?.status === 'IN_PROGRESS';
    setAssigning(null);
    setBanner({
      type: 'success',
      text: wasAdding
        ? `Nhóm dọn phòng ${taskRoomLabel(updated)} giờ gồm: ${teamNames(updated)}.`
        : `Đã giao việc dọn phòng ${taskRoomLabel(updated)} cho ${teamNames(updated)}. Phòng chuyển sang «Đang dọn».`,
    });
    refresh();
  }

  /** Giao nhiều việc cho một người: báo cả phần giao được lẫn phần không giao được. */
  function finishBulkAssign({ assigned, failed }) {
    const name = assigningPerson.person.fullName;
    setAssigningPerson(null);
    const rooms = (tasks) => tasks.map(taskRoomLabel).join(', ');
    if (failed.length === 0) {
      setBanner({ type: 'success', text: `Đã giao ${assigned.length} việc cho ${name}: phòng ${rooms(assigned)}.` });
    } else {
      setBanner({
        type: 'error',
        text: (assigned.length > 0 ? `Đã giao phòng ${rooms(assigned)} cho ${name}. ` : '')
          + `Không giao được: ${failed.map(({ task, message }) => `phòng ${taskRoomLabel(task)} — ${message}`).join('; ')}`,
      });
    }
    refresh();
  }

  function finishRelease({ task, removedName, emptied }) {
    setReleasing(null);
    setBanner({
      type: 'success',
      text: `Đã gỡ ${removedName} khỏi việc dọn phòng ${taskRoomLabel(task)}.`
        + (emptied ? ' Việc quay lại hàng chờ, phòng quay về «Chờ dọn».' : ' Những người còn lại tiếp tục dọn.'),
    });
    refresh();
  }

  /**
   * Kiểm tra xong: hai kết quả dẫn tới hai tình huống khác hẳn nhau nên câu thông báo phải nói
   * rõ điều gì vừa xảy ra với PHÒNG, chứ không chỉ "đã lưu".
   */
  function finishInspection(record) {
    const room = taskRoomLabel(inspecting);
    setInspecting(null);
    setBanner({
      type: 'success',
      text: record.result === 'PASS'
        ? `Phòng ${room} đã Sẵn sàng.`
        : `Đã tạo việc dọn lại cho phòng ${room}. Phòng quay về «Chờ dọn».`,
    });
    refresh();
  }

  const sortedColumns = useMemo(
    () => Object.fromEntries(Object.entries(columns).map(([status, tasks]) => [
      status,
      [...tasks].sort((a, b) => compareNatural(a.roomNumber, b.roomNumber)),
    ])),
    [columns],
  );

  /** Nút trên mỗi thẻ — chỉ Quản lý chi nhánh có; Giám đốc mở trang này chỉ để xem. */
  function actionsFor(task) {
    if (!canManage) return [];
    const actions = [];

    if (task.status === 'UNASSIGNED') {
      actions.push({ key: 'assign', label: 'Phân công', onClick: () => setAssigning(task) });
    }
    if (task.status === 'IN_PROGRESS') {
      // Thêm người chỉ cho việc của hôm nay: người mới phải có ca đúng ngày của nhóm, mà ngày đã
      // qua thì không giao được nữa — việc tồn đọng thì gỡ ra rồi giao lại.
      if (task.assignedDate === today) {
        actions.push({ key: 'add', label: 'Thêm người', ghost: true, onClick: () => setAssigning(task) });
      }
      actions.push({ key: 'unassign', label: 'Gỡ người', danger: true, onClick: () => setReleasing(task) });
    }
    if (task.status === 'PENDING_INSPECTION') {
      actions.push({ key: 'inspect', label: 'Kiểm tra', onClick: () => setInspecting(task) });
    }
    return actions;
  }

  const visibleTabs = [...COLUMNS, CLOSED];
  const tasksOf = (tab) => (tab === CLOSED ? closedTasks : sortedColumns[tab] ?? []);
  const labelOf = (tab) => (tab === CLOSED ? 'Đã đóng hôm nay' : taskStatusMeta(tab).label);

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Vận hành › Công việc dọn phòng</p>
          <h1>Công việc dọn phòng</h1>
        </div>
      </div>

      {banner && (
        <div className={`alert alert--${banner.type}`} role="status">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">
            ×
          </button>
        </div>
      )}

      <div className="view-toggle" role="group" aria-label="Cách xem lịch dọn">
        {VIEWS.map((option) => (
          <button
            key={option.key}
            type="button"
            className={`view-toggle__btn ${view === option.key ? 'is-active' : ''}`}
            aria-pressed={view === option.key}
            onClick={() => chooseView(option.key)}
          >
            {option.label}
          </button>
        ))}
      </div>

      {view === 'staff' && (
        <CleaningDayBoard
          today={today}
          canManage={canManage}
          version={version}
          actionsFor={actionsFor}
          onAssignToPerson={setAssigningPerson}
          onShowPreviousInspection={setPreviousOf}
        />
      )}

      {view === 'status' && (
        <>
          {loadError && (
            <div className="alert alert--error" role="alert">
              {loadError}
            </div>
          )}
          {loading && <p className="state">Đang tải dữ liệu…</p>}

          {/* Điện thoại: ba cột thành các tab. Trên desktop thanh tab này bị CSS ẩn đi. */}
          <div className="task-tabs" role="tablist" aria-label="Trạng thái việc dọn">
            {visibleTabs.map((tab) => (
              <button
                key={tab}
                type="button"
                role="tab"
                aria-selected={mobileTab === tab}
                className={`room-tab ${mobileTab === tab ? 'is-active' : ''}`}
                onClick={() => setMobileTab(tab)}
              >
                {labelOf(tab)} <span className="chip">{tasksOf(tab).length}</span>
              </button>
            ))}
          </div>

          <div className="task-board">
            {visibleTabs.map((tab) => (
              <section
                key={tab}
                className={`task-column ${mobileTab === tab ? 'is-active-tab' : ''} ${tab === CLOSED ? 'task-column--closed' : ''}`}
              >
                <header className="task-column__head">
                  <h2>{labelOf(tab)}</h2>
                  <span className="chip">{tasksOf(tab).length}</span>
                </header>

                {tasksOf(tab).length === 0 && !loading && (
                  <p className="state state--empty">
                    {tab === 'UNASSIGNED' ? 'Không còn phòng nào chờ phân công.' : 'Chưa có việc nào.'}
                  </p>
                )}

                {tasksOf(tab).map((task) => (
                  <TaskCard
                    key={task.id}
                    task={task}
                    today={today}
                    actions={actionsFor(task)}
                    onShowPreviousInspection={setPreviousOf}
                  />
                ))}
              </section>
            ))}
          </div>
        </>
      )}

      <div className="note">
        <span aria-hidden="true">ⓘ</span>
        <div>
          <b>Quy định giao việc dọn phòng</b>
          <p>
            Việc dọn tự sinh khi khách trả phòng. Chỉ giao được cho nhân viên có quyền Dọn dẹp và{' '}
            <b>có ca trong ngày</b>; không giới hạn số việc mỗi người. Một phòng giao được cho{' '}
            <b>nhiều người cùng dọn</b> — một người bấm Hoàn thành là xong cho cả nhóm. Việc chỉ giao
            trong ngày, vì phòng chuyển sang <b>Đang dọn</b> ngay khi giao. Hết ca chưa xong thì việc{' '}
            <b>tồn đọng</b> sang hôm sau chứ không tự hủy. Người còn việc dọn đang làm trong ngày thì
            không gỡ, xóa hay dời ca của họ được — gỡ người khỏi các việc đó trước.
          </p>
        </div>
      </div>

      {assigning && (
        <AssignTaskModal task={assigning} onClose={() => setAssigning(null)} onAssigned={finishAssign} />
      )}

      {assigningPerson && (
        <AssignToStaffModal
          person={assigningPerson.person}
          date={assigningPerson.date}
          today={today}
          tasks={assigningPerson.tasks}
          onClose={() => setAssigningPerson(null)}
          onDone={finishBulkAssign}
        />
      )}

      {releasing && (
        <ReleaseTaskModal task={releasing} onClose={() => setReleasing(null)} onReleased={finishRelease} />
      )}

      {inspecting && (
        <InspectTaskModal task={inspecting} onClose={() => setInspecting(null)} onInspected={finishInspection} />
      )}

      {previousOf && (
        <PreviousInspectionModal task={previousOf} onClose={() => setPreviousOf(null)} />
      )}
    </div>
  );
}
