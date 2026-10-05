import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { cancelTask, createStayoverBatch, fetchTasks, unassignTask } from '../../api/housekeeping';
import { fetchRoomStatusSummary } from '../../api/rooms';
import { fetchStaffDirectory } from '../../api/users';
import ConfirmDialog from '../../components/ConfirmDialog';
import AssignTaskModal from '../../components/rooms/AssignTaskModal';
import AssignToStaffModal from '../../components/rooms/AssignToStaffModal';
import CleaningDayBoard from '../../components/rooms/CleaningDayBoard';
import InspectTaskModal from '../../components/rooms/InspectTaskModal';
import PreviousInspectionModal from '../../components/rooms/PreviousInspectionModal';
import StayoverTaskModal from '../../components/rooms/StayoverTaskModal';
import TaskCard from '../../components/rooms/TaskCard';
import { todayIso } from './format';
import { TASK_STATUS_ORDER, compareNatural, taskStatusMeta } from './roomLabels';
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
  // Tạo hàng loạt cần một khách sạn cụ thể — chỉ Manager có.
  const isManager = user?.role === 'MANAGER';
  const today = todayIso();

  const [view, setView] = useState(readSavedView);
  const [version, setVersion] = useState(0);    // tăng sau mỗi thao tác để bảng theo nhân viên tải lại
  const [columns, setColumns] = useState({});   // { [status]: task[] }
  const [closedTasks, setClosedTasks] = useState([]);
  const [staffNames, setStaffNames] = useState({});
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [banner, setBanner] = useState(null);   // { type: 'success' | 'error' | 'info', text }

  const [mobileTab, setMobileTab] = useState(COLUMNS[0]);
  const [assigning, setAssigning] = useState(null);      // { task, initialDate } — hộp thoại phân công
  const [assigningPerson, setAssigningPerson] = useState(null); // { person, date, tasks } — giao nhiều việc
  const [releasing, setReleasing] = useState(null);      // việc đang hỏi xác nhận gỡ người
  const [inspecting, setInspecting] = useState(null);    // việc đang mở hộp thoại kiểm tra (S-13)
  const [cancelling, setCancelling] = useState(null);    // việc đang hỏi xác nhận hủy (S-14)
  const [previousOf, setPreviousOf] = useState(null);    // việc dọn lại đang xem biên bản của task cha
  const [stayoverOpen, setStayoverOpen] = useState(false);
  const [batchConfirm, setBatchConfirm] = useState(null); // { occupied } — đang hỏi tạo hàng loạt

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

  // Tên người làm: DTO của việc dọn chỉ có `assignedStaffId`. Tra một lần từ danh bạ nhân sự
  // thay vì sửa DTO của module Lịch làm việc — cùng cách LocationsPage đang làm.
  useEffect(() => {
    let cancelled = false;
    fetchStaffDirectory()
      .then((data) => {
        if (cancelled) return;
        const list = data.content ?? data ?? [];
        setStaffNames(Object.fromEntries(list.map((person) => [person.id, person.fullName])));
      })
      .catch(() => !cancelled && setStaffNames({}));
    return () => {
      cancelled = true;
    };
  }, []);

  /** Giao việc xong: đóng hộp thoại và tải lại — việc vừa đổi trạng thái nên nhảy sang chỗ khác. */
  function finishAssign(successText) {
    setBanner({ type: 'success', text: successText });
    setAssigning(null);
    refresh();
  }

  /** Giao nhiều việc cho một người: báo cả phần giao được lẫn phần không giao được. */
  function finishBulkAssign({ assigned, failed }) {
    const name = assigningPerson.person.fullName;
    setAssigningPerson(null);
    const rooms = (tasks) => tasks.map((task) => task.roomNumber).join(', ');
    if (failed.length === 0) {
      setBanner({ type: 'success', text: `Đã giao ${assigned.length} việc cho ${name}: phòng ${rooms(assigned)}.` });
    } else {
      setBanner({
        type: 'error',
        text: (assigned.length > 0 ? `Đã giao phòng ${rooms(assigned)} cho ${name}. ` : '')
          + `Không giao được: ${failed.map(({ task, message }) => `phòng ${task.roomNumber} — ${message}`).join('; ')}`,
      });
    }
    refresh();
  }

  async function handleConfirmRelease() {
    const task = releasing;
    setReleasing(null);
    try {
      await unassignTask(task.id);
      setBanner({
        type: 'success',
        text: `Đã gỡ người khỏi việc dọn phòng ${task.roomNumber}.`
          + (task.taskType === 'CHECKOUT' ? ' Phòng quay lại «Chờ dọn».' : ''),
      });
      refresh();
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không gỡ được người khỏi việc dọn.') });
    }
  }

  /** S-14 — hủy tay một việc dọn hằng ngày. Phòng không đổi gì (BR-HK-05). */
  async function handleConfirmCancel() {
    const task = cancelling;
    setCancelling(null);
    try {
      await cancelTask(task.id);
      setBanner({ type: 'success', text: `Đã hủy việc dọn hằng ngày của phòng ${task.roomNumber}.` });
      refresh();
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không hủy được việc dọn.') });
    }
  }

  /**
   * Tạo việc dọn hằng ngày cho mọi phòng đang có khách (BR-HK-05 — vẫn là Quản lý chủ động bấm).
   * Đếm phòng có khách trước để hộp xác nhận nói rõ sẽ tạo cho bao nhiêu phòng.
   */
  async function askStayoverBatch() {
    setBanner(null);
    try {
      const summary = await fetchRoomStatusSummary();
      const occupied = summary?.counts?.OCCUPIED ?? 0;
      if (occupied === 0) {
        setBanner({ type: 'info', text: 'Hiện không có phòng nào đang có khách — không có việc dọn hằng ngày nào để tạo.' });
        return;
      }
      setBatchConfirm({ occupied });
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không đếm được số phòng đang có khách.') });
    }
  }

  async function handleConfirmBatch() {
    setBatchConfirm(null);
    try {
      const result = await createStayoverBatch();
      setBanner({
        type: result.created > 0 ? 'success' : 'info',
        text: result.created > 0
          ? `Đã tạo ${result.created} việc dọn hằng ngày`
            + (result.skipped > 0 ? ` (bỏ qua ${result.skipped} phòng đã có việc đang mở)` : '')
            + '. Các việc nằm ở hàng chờ, sẵn sàng giao.'
          : `Không tạo thêm việc nào: cả ${result.skipped} phòng đang có khách đều đã có việc dọn hằng ngày đang mở.`,
      });
      refresh();
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không tạo được việc dọn hằng ngày.') });
    }
  }

  /**
   * Kiểm tra xong: hai kết quả dẫn tới hai tình huống khác hẳn nhau nên câu thông báo phải nói
   * rõ điều gì vừa xảy ra với PHÒNG, chứ không chỉ "đã lưu".
   */
  function finishInspection(record) {
    const room = inspecting.roomNumber;
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

  /**
   * Nút trên mỗi thẻ — chỉ Quản lý chi nhánh có; Giám đốc mở trang này chỉ để xem.
   * `date`: ngày bảng theo nhân viên đang xem, để hộp thoại phân công mở sẵn đúng ngày đó.
   */
  function actionsFor(task, { date } = {}) {
    if (!canManage) return [];
    const actions = [];

    if (task.status === 'UNASSIGNED') {
      actions.push({ key: 'assign', label: 'Phân công', onClick: () => setAssigning({ task, initialDate: date }) });
    }
    if (task.status === 'IN_PROGRESS') {
      actions.push({ key: 'unassign', label: 'Gỡ người', danger: true, onClick: () => setReleasing(task) });
    }
    if (task.status === 'PENDING_INSPECTION') {
      actions.push({ key: 'inspect', label: 'Kiểm tra', onClick: () => setInspecting(task) });
    }
    // Hủy tay chỉ dành cho việc dọn HẰNG NGÀY đang mở: hủy việc dọn sau trả phòng sẽ để
    // phòng «Chờ dọn» mà không còn việc nào. Backend cũng chặn, ẩn nút chỉ để khỏi bấm nhầm.
    if (task.taskType === 'STAYOVER' && COLUMNS.includes(task.status)) {
      actions.push({ key: 'cancel', label: 'Hủy việc', danger: true, onClick: () => setCancelling(task) });
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
        {canManage && (
          <div className="page__actions">
            {isManager && (
              <button type="button" className="btn btn--ghost" onClick={askStayoverBatch}>
                Dọn hằng ngày cho mọi phòng có khách
              </button>
            )}
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => {
                setStayoverOpen(true);
                setBanner(null);
              }}
            >
              + Dọn hằng ngày
            </button>
          </div>
        )}
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
          staffNames={staffNames}
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
                    staffName={staffNames[task.assignedStaffId]}
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
            Chỉ giao được cho nhân viên có quyền Dọn dẹp và <b>có ca trong ngày</b> đó; không giới hạn
            số việc mỗi người. Việc dọn sau khi khách trả phòng chỉ giao trong ngày, vì phòng chuyển
            sang <b>Đang dọn</b> ngay khi giao. Hết ca chưa xong thì việc <b>tồn đọng</b> sang
            hôm sau chứ không tự hủy. Người còn việc dọn đang làm trong ngày thì không gỡ, xóa hay
            dời ca của họ được — gỡ người khỏi các việc đó trước.
          </p>
        </div>
      </div>

      {assigning && (
        <AssignTaskModal
          task={assigning.task}
          initialDate={assigning.initialDate}
          onClose={() => setAssigning(null)}
          onAssigned={(updated) =>
            finishAssign(`Đã giao việc dọn phòng ${updated.roomNumber}.`
              + (updated.taskType === 'CHECKOUT' ? ' Phòng chuyển sang «Đang dọn».' : ''))}
        />
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

      {stayoverOpen && (
        <StayoverTaskModal
          onClose={() => setStayoverOpen(false)}
          onCreated={(created) => {
            setStayoverOpen(false);
            setBanner({ type: 'success', text: `Đã tạo việc dọn hằng ngày cho phòng ${created.roomNumber}.` });
            refresh();
            // Tạo xong thường là muốn giao luôn — mở tiếp hộp thoại phân công cho đỡ một bước.
            setAssigning({ task: created });
          }}
        />
      )}

      {inspecting && (
        <InspectTaskModal
          task={inspecting}
          staffName={staffNames[inspecting.assignedStaffId]}
          onClose={() => setInspecting(null)}
          onInspected={finishInspection}
        />
      )}

      {previousOf && (
        <PreviousInspectionModal task={previousOf} onClose={() => setPreviousOf(null)} />
      )}

      {batchConfirm && (
        <ConfirmDialog
          title={`Tạo việc dọn hằng ngày cho ${batchConfirm.occupied} phòng đang có khách?`}
          message="Phòng đã có việc dọn hằng ngày đang mở thì bỏ qua. Việc mới vào hàng chờ, chưa giao cho ai; phòng vẫn giữ «Đang sử dụng»."
          confirmLabel="Tạo việc dọn"
          confirmTone="primary"
          onCancel={() => setBatchConfirm(null)}
          onConfirm={handleConfirmBatch}
        />
      )}

      {cancelling && (
        <ConfirmDialog
          title={`Hủy việc dọn hằng ngày của phòng ${cancelling.roomNumber}?`}
          message="Việc dọn đóng lại vĩnh viễn với lý do «Quản lý hủy». Phòng giữ nguyên «Đang sử dụng»."
          confirmLabel="Hủy việc dọn"
          onCancel={() => setCancelling(null)}
          onConfirm={handleConfirmCancel}
        />
      )}

      {releasing && (
        <ConfirmDialog
          title={`Gỡ người khỏi việc dọn phòng ${releasing.roomNumber}?`}
          message={
            <>
              Việc dọn quay lại hàng chờ phân công.
              {releasing.taskType === 'CHECKOUT'
                ? ' Phòng cũng quay về trạng thái «Chờ dọn».'
                : ' Phòng giữ nguyên «Đang sử dụng».'}
            </>
          }
          confirmLabel="Gỡ người"
          onCancel={() => setReleasing(null)}
          onConfirm={handleConfirmRelease}
        />
      )}
    </div>
  );
}
