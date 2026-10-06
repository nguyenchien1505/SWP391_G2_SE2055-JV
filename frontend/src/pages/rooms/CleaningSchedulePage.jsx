import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { fetchTasks } from '../../api/housekeeping';
import { fetchShifts } from '../../api/scheduling';
import { fetchStaffDirectory } from '../../api/users';
import CleaningStatusBadge, { cleaningTone } from '../../components/rooms/CleaningStatusBadge';
import MyCleaningShiftBoard from '../../components/rooms/MyCleaningShiftBoard';
import PreviousInspectionModal from '../../components/rooms/PreviousInspectionModal';
import RoomStatusBadge from '../../components/rooms/RoomStatusBadge';
import TaskStatusBadge from '../../components/rooms/TaskStatusBadge';
import { formatDate, formatShortDateTime, todayIso } from './format';
import { initials } from './cleaningBoard';
import { compareNatural, taskRoomLabel, taskSourceLabel, teamNames } from './roomLabels';
import {
  WEEKDAY_SHORT,
  addDays,
  dayClass,
  formatDayMonth,
  formatLongDate,
  isoWeekNumber,
  timeRange,
  weekDays,
  weekStartOf,
} from '../scheduling/scheduleFormat';
import './rooms.css';
import '../scheduling/scheduling.css';

/** Đủ cho mọi việc dọn trong một tuần của khách sạn 2–3 sao — không phân trang. */
const PAGE_SIZE = 500;

/**
 * Lịch dọn phòng theo tuần (chốt 06/10/2026), hai cách chia theo người xem:
 *
 * - Quản lý chi nhánh: hàng là TỪNG NHÂN VIÊN DỌN, cột là 7 ngày; mỗi ô là ca của người đó hôm ấy và
 *   các phòng họ dọn ("301 - Deluxe" kèm trạng thái). Gồm mọi nhân viên có quyền Dọn dẹp của khách sạn,
 *   cộng những người khác còn việc dọn trong tuần (mất quyền, đã chuyển đi). Bấm một phòng để xem chi
 *   tiết, có tên những người dọn cùng.
 * - Nhân viên dọn: cùng cách chia với bảng xếp lịch của quản lý — hàng là CA của mình, cột là ngày; ca
 *   có việc thì hiện khối phòng, bấm khối để xem chi tiết ({@link MyCleaningShiftBoard}). Backend chỉ
 *   trả việc có mình trong nhóm và ca của mình.
 *
 * Trạng thái cạnh phòng xem {@link CleaningStatusBadge}. Chỉ xem: giao việc, thêm / gỡ người, kiểm tra
 * phòng làm ở màn «Công việc dọn phòng».
 */
export default function CleaningSchedulePage() {
  const { user } = useAuth();
  const isManager = user?.role === 'MANAGER' || user?.role === 'PLATFORM_ADMIN';
  const today = todayIso();

  const [weekStart, setWeekStart] = useState(() => weekStartOf(today));
  const days = useMemo(() => weekDays(weekStart), [weekStart]);

  const [directory, setDirectory] = useState(null); // nhân viên của khách sạn (chỉ Quản lý)
  const [week, setWeek] = useState(null);           // { shifts, tasks } của tuần đang xem
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [opened, setOpened] = useState(null);       // { task, person } — đang xem chi tiết
  const [previousOf, setPreviousOf] = useState(null);

  useEffect(() => {
    if (!isManager) return undefined;
    let cancelled = false;
    fetchStaffDirectory({ size: 500 })
      .then((data) => !cancelled && setDirectory(data?.content ?? data ?? []))
      .catch(() => !cancelled && setDirectory([]));
    return () => {
      cancelled = true;
    };
  }, [isManager]);

  // Bấm chuyển tuần nhanh hơn tốc độ mạng: chỉ nhận kết quả của lần gọi mới nhất.
  const latestRequest = useRef(0);
  const load = useCallback(async () => {
    const requestId = ++latestRequest.current;
    setLoading(true);
    setError('');
    try {
      const [shifts, tasks] = await Promise.all([
        fetchShifts({ from: days[0], to: days[6] }),
        fetchTasks({ from: days[0], to: days[6], size: PAGE_SIZE }),
      ]);
      if (requestId === latestRequest.current) setWeek({ shifts, tasks: tasks.content ?? [] });
    } catch (err) {
      if (requestId === latestRequest.current) setError(readErrorMessage(err, 'Không tải được lịch dọn tuần này.'));
    } finally {
      if (requestId === latestRequest.current) setLoading(false);
    }
  }, [days]);

  useEffect(() => {
    load();
  }, [load]);

  /** Hàng của bảng theo người (Quản lý): mọi người dọn phòng của khách sạn. */
  const people = useMemo(() => {
    if (!isManager) return [];
    const rows = new Map();
    for (const person of directory ?? []) {
      if (person.role === 'STAFF' && person.status !== 'TERMINATED' && (person.permissions ?? []).includes('HOUSEKEEPING')) {
        rows.set(person.id, { id: person.id, fullName: person.fullName, phone: person.phone });
      }
    }
    // Người không còn trong danh sách trên mà vẫn có việc dọn trong tuần.
    for (const task of week?.tasks ?? []) {
      for (const member of task.assignees ?? []) {
        if (!rows.has(member.staffId)) {
          rows.set(member.staffId, { id: member.staffId, fullName: member.fullName ?? 'Nhân viên đã nghỉ', former: true });
        }
      }
    }
    return [...rows.values()].sort((a, b) => (a.fullName ?? '').localeCompare(b.fullName ?? '', 'vi'));
  }, [isManager, directory, week]);

  /** Ô (người, ngày): ca và các phòng dọn hôm đó. */
  const cellOf = useCallback((personId, day) => {
    const shifts = (week?.shifts ?? [])
      .filter((shift) => shift.staffId === personId && shift.shiftDate === day)
      .sort((a, b) => String(a.startTime).localeCompare(String(b.startTime)));
    const tasks = (week?.tasks ?? [])
      .filter((task) => task.assignedDate === day && (task.assignedStaffIds ?? []).includes(personId))
      .sort((a, b) => compareNatural(a.roomNumber, b.roomNumber));
    return { shifts, tasks };
  }, [week]);

  const counts = useMemo(() => {
    const tasks = week?.tasks ?? [];
    const of = (status) => tasks.filter((task) => task.status === status).length;
    return { total: tasks.length, working: of('IN_PROGRESS'), waiting: of('PENDING_INSPECTION'), done: of('COMPLETED') };
  }, [week]);

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">{isManager ? 'Vận hành' : 'Dọn dẹp'} › Lịch dọn phòng</p>
          <h1>{isManager ? 'Lịch dọn phòng theo nhân viên' : 'Lịch dọn của tôi'}</h1>
          <p className="muted">
            {isManager
              ? 'Mỗi ô là ca làm và các phòng dọn trong ngày. Bấm vào một phòng để xem chi tiết và người dọn cùng.'
              : 'Hàng là ca làm của bạn, cột là ngày. Ca có việc dọn hiện khối các phòng — bấm vào để xem chi tiết và người dọn cùng.'}
          </p>
        </div>
      </div>

      <div className="sched-toolbar">
        <div className="week-nav" role="group" aria-label="Chọn tuần">
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setWeekStart(addDays(weekStart, -7))}>
            ‹ Tuần trước
          </button>
          <div className="week-nav__label" aria-live="polite">
            Tuần {isoWeekNumber(weekStart)}
            <small>
              {formatDate(days[0])} – {formatDate(days[6])}
            </small>
          </div>
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setWeekStart(addDays(weekStart, 7))}>
            Tuần sau ›
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setWeekStart(weekStartOf(today))}
            disabled={days.includes(today)}
          >
            Tuần này
          </button>
        </div>
        {week && (
          <div className="sched-summary">
            <span className="chip">{counts.total} lượt dọn</span>
            <span className="chip">{counts.working} đang dọn</span>
            <span className="chip">{counts.waiting} chờ kiểm tra</span>
            <span className="chip chip--green">{counts.done} đã xong</span>
          </div>
        )}
      </div>

      {error && <div className="alert alert--error" role="alert">{error}</div>}
      {loading && !week && <p className="state">Đang tải lịch dọn…</p>}

      {week && !isManager && (
        <div aria-busy={loading}>
          <MyCleaningShiftBoard
            days={days}
            today={today}
            shifts={week.shifts}
            tasks={week.tasks}
            meId={user?.id}
            onShowPreviousInspection={setPreviousOf}
          />
        </div>
      )}

      {week && isManager && (
        <div className="sched-scroll" aria-busy={loading}>
          <table className="sched-grid clean-grid">
            <thead>
              <tr>
                <th scope="col" className="sched-grid__person">Nhân viên dọn</th>
                {days.map((day, index) => (
                  <th key={day} scope="col" className={dayClass(day, today)}>
                    {WEEKDAY_SHORT[index]}
                    <b>{formatDayMonth(day)}</b>
                    {day === today && <span className="today-chip">Hôm nay</span>}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {people.length === 0 && (
                <tr>
                  <td colSpan={8} className="state state--empty">
                    Khách sạn chưa có nhân viên nào có quyền Dọn dẹp.
                  </td>
                </tr>
              )}
              {people.map((person) => (
                <tr key={person.id}>
                  <th scope="row" className="sched-grid__person person-cell">
                    <span className="clean-person">
                      <span className="avatar avatar--sm" aria-hidden="true">{initials(person.fullName)}</span>
                      <span>
                        <b>{person.fullName}</b>
                        {person.former && <small>Không còn quyền Dọn dẹp / đã chuyển đi</small>}
                        {!person.former && person.phone && <small>{person.phone}</small>}
                      </span>
                    </span>
                  </th>
                  {days.map((day) => (
                    <DayCell
                      key={day}
                      day={day}
                      today={today}
                      cell={cellOf(person.id, day)}
                      onOpen={(task) => setOpened({ task, person })}
                    />
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {opened && (
        <CleaningDetailModal
          task={opened.task}
          person={opened.person}
          shifts={cellOf(opened.person.id, opened.task.assignedDate).shifts}
          onClose={() => setOpened(null)}
          onShowPreviousInspection={(task) => {
            setOpened(null);
            setPreviousOf(task);
          }}
        />
      )}
      {previousOf && <PreviousInspectionModal task={previousOf} onClose={() => setPreviousOf(null)} />}
    </div>
  );
}

/** Một ô (người, ngày): giờ ca, rồi từng phòng "301 - Deluxe" kèm trạng thái. */
function DayCell({ day, today, cell, onOpen }) {
  const { shifts, tasks } = cell;
  return (
    <td className={dayClass(day, today)}>
      {shifts.length > 0 && (
        <p className="clean-cell__shift">Ca {shifts.map((s) => timeRange(s.startTime, s.endTime)).join(' · ')}</p>
      )}
      {tasks.map((task) => {
        const overdue = task.status === 'IN_PROGRESS' && task.assignedDate < today;
        return (
          <button
            key={task.id}
            type="button"
            className={`clean-entry room-tone--${cleaningTone(task)}`}
            onClick={() => onOpen(task)}
            title="Xem chi tiết"
          >
            <span className="clean-entry__room">{taskRoomLabel(task)}</span>
            <CleaningStatusBadge task={task} />
            {overdue && <span className="clean-entry__flag">Tồn đọng</span>}
          </button>
        );
      })}
      {shifts.length > 0 && tasks.length === 0 && <p className="clean-cell__empty">Chưa có phòng</p>}
      {shifts.length === 0 && tasks.length === 0 && <p className="clean-cell__off">—</p>}
    </td>
  );
}

/** Chi tiết một lần dọn của một người: phòng, trạng thái, ca, và những người dọn cùng. */
function CleaningDetailModal({ task, person, shifts, onClose, onShowPreviousInspection }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const partners = teamNames(task, person.id);

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="clean-detail-title" onClick={onClose}>
      <div className="modal room-modal" onClick={(e) => e.stopPropagation()}>
        <h2 id="clean-detail-title">Phòng {taskRoomLabel(task)}</h2>

        <div className="modal__body">
          <div className="readonly-box">
            <b>{formatLongDate(task.assignedDate)}</b>
            <p>
              {person.fullName}
              {shifts.length > 0 ? ` · ca ${shifts.map((s) => timeRange(s.startTime, s.endTime)).join(' · ')}` : ''}
            </p>
          </div>

          <dl className="clean-detail">
            <div>
              <dt>Người dọn cùng</dt>
              <dd><b>{partners || 'Không có — dọn một mình'}</b></dd>
            </div>
            <div>
              <dt>Trạng thái phòng (hiện tại)</dt>
              <dd><RoomStatusBadge status={task.roomStatus} /></dd>
            </div>
            <div>
              <dt>Việc dọn</dt>
              <dd>
                <TaskStatusBadge status={task.status} /> <span className="muted">{taskSourceLabel(task.createdSource)}</span>
              </dd>
            </div>
            <div>
              <dt>Tầng</dt>
              <dd>{task.floor ?? '—'}</dd>
            </div>
            {task.assignedAt && (
              <div>
                <dt>Giao lúc</dt>
                <dd>{formatShortDateTime(task.assignedAt)}</dd>
              </div>
            )}
            {task.completedAt && (
              <div>
                <dt>Kiểm tra xong lúc</dt>
                <dd>{formatShortDateTime(task.completedAt)}</dd>
              </div>
            )}
          </dl>

          {task.parentTaskId && (
            <button type="button" className="link-btn" onClick={() => onShowPreviousInspection(task)}>
              Dọn lại — xem lý do lần kiểm tra trước
            </button>
          )}
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--primary" onClick={onClose} autoFocus>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
