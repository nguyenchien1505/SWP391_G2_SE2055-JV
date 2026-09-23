import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { fetchTasks, unassignTask } from '../../api/housekeeping';
import { fetchStaffDirectory } from '../../api/users';
import ConfirmDialog from '../../components/ConfirmDialog';
import AssignTaskModal from '../../components/rooms/AssignTaskModal';
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

/**
 * S-09 Hàng chờ phân công + S-11 Danh sách việc dọn — RM-13, RM-14, RM-15, RM-16.
 *
 * Người dùng chính là Quản lý chi nhánh; Giám đốc mở được nhưng chỉ để xem (backend chặn mọi
 * thao tác của Giám đốc trên lịch dọn).
 *
 * Bố cục là **bảng ba cột theo trạng thái** chứ không phải một danh sách dài: việc của Quản lý
 * là nhìn ra ngay "còn bao nhiêu phòng chưa có ai dọn". Dưới 860px ba cột đổi thành ba tab (CSS
 * + state `mobileTab`), vì ba cột cạnh nhau trên điện thoại thì cột nào cũng quá hẹp.
 *
 * Mỗi cột gọi API riêng với `status` tương ứng — đơn giản hơn tải hết rồi lọc ở trình duyệt, và
 * mỗi cột tự phân trang được khi dữ liệu lớn.
 */
export default function HousekeepingPage() {
  const { user } = useAuth();
  const canManage = user?.role === 'MANAGER' || user?.role === 'PLATFORM_ADMIN';
  const today = todayIso();

  const [columns, setColumns] = useState({});   // { [status]: task[] }
  const [closedTasks, setClosedTasks] = useState([]);
  const [staffNames, setStaffNames] = useState({});
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [banner, setBanner] = useState(null);   // { type, text }

  const [mobileTab, setMobileTab] = useState(COLUMNS[0]);
  const [assigning, setAssigning] = useState(null);      // việc đang mở hộp thoại phân công
  const [releasing, setReleasing] = useState(null);      // việc đang hỏi xác nhận gỡ người
  const [stayoverOpen, setStayoverOpen] = useState(false);

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

  useEffect(() => {
    load();
  }, [load]);

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

  /** Giao việc xong: đóng hộp thoại và tải lại — việc vừa đổi trạng thái nên nhảy sang cột khác. */
  function finishAssign(successText) {
    setBanner({ type: 'success', text: successText });
    setAssigning(null);
    load();
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
      await load();
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không gỡ được người khỏi việc dọn.') });
    }
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
    if (task.status === 'UNASSIGNED') {
      return [{ key: 'assign', label: 'Phân công', onClick: () => setAssigning(task) }];
    }
    if (task.status === 'IN_PROGRESS') {
      return [{ key: 'unassign', label: 'Gỡ người', danger: true, onClick: () => setReleasing(task) }];
    }
    // PENDING_INSPECTION: nút "Kiểm tra" thuộc F6, chưa có.
    return [];
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
        )}
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
              />
            ))}
          </section>
        ))}
      </div>

      <div className="note">
        <span aria-hidden="true">ⓘ</span>
        <div>
          <b>Quy định giao việc dọn phòng</b>
          <p>
            Chỉ giao được cho nhân viên dọn phòng <b>có ca trong ngày</b> đó; không giới hạn số
            việc mỗi người. Việc dọn sau khi khách trả phòng chỉ giao trong ngày, vì phòng chuyển
            sang <b>Đang dọn</b> ngay khi giao. Hết ca chưa xong thì việc <b>tồn đọng</b> sang
            hôm sau chứ không tự hủy.
          </p>
        </div>
      </div>

      {assigning && (
        <AssignTaskModal
          task={assigning}
          onClose={() => setAssigning(null)}
          onAssigned={(updated) =>
            finishAssign(`Đã giao việc dọn phòng ${updated.roomNumber}.`
              + (updated.taskType === 'CHECKOUT' ? ' Phòng chuyển sang «Đang dọn».' : ''))}
        />
      )}

      {stayoverOpen && (
        <StayoverTaskModal
          onClose={() => setStayoverOpen(false)}
          onCreated={(created) => {
            setStayoverOpen(false);
            setBanner({ type: 'success', text: `Đã tạo việc dọn hằng ngày cho phòng ${created.roomNumber}.` });
            load();
            // Tạo xong thường là muốn giao luôn — mở tiếp hộp thoại phân công cho đỡ một bước.
            setAssigning(created);
          }}
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
