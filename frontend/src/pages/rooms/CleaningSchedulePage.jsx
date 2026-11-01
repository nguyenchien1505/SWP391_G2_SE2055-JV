import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { fetchAssignableStaff, fetchTasks } from '../../api/housekeeping';
import { fetchShiftTemplates, fetchShifts } from '../../api/scheduling';
import { fetchStaffDirectory } from '../../api/users';
import AssignToStaffModal from '../../components/rooms/AssignToStaffModal';
import CleaningStatusBadge, { cleaningTone } from '../../components/rooms/CleaningStatusBadge';
import MyCleaningShiftBoard from '../../components/rooms/MyCleaningShiftBoard';
import { TIMETABLE_QUERY } from '../../components/scheduling/WeekTimetable';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import PreviousInspectionModal from '../../components/rooms/PreviousInspectionModal';
import RoomStatusBadge from '../../components/rooms/RoomStatusBadge';
import TaskStatusBadge from '../../components/rooms/TaskStatusBadge';
import { formatDate, formatShortDateTime, todayIso } from './format';
import { compareQueue, initials } from './cleaningBoard';
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
 * - Nhân viên dọn: cùng cách chia với bảng xếp lịch của quản lý — hàng là MỌI ca của chi nhánh (chốt
 *   12/10/2026), cột là ngày; ca có lịch thì hiện khối của ca đó, có việc thì khối phòng, bấm khối để xem
 *   chi tiết ({@link MyCleaningShiftBoard}). Backend chỉ trả việc có mình trong nhóm và ca của mình.
 *
 * Trạng thái cạnh phòng xem {@link CleaningStatusBadge}.
 *
 * Quản lý giao phòng ngay trên lịch (chốt 01/11/2026): ô HÔM NAY của người có ca có nút «+ Giao phòng», mở cùng hộp
 * thoại giao nhiều việc với bảng theo nhân viên. Chỉ hôm nay — giao là phòng sang «Đang dọn» ngay (Q5). Ai giao được
 * và vì sao không (lễ tân duy nhất của ca phải trực quầy) do backend trả qua `assignable-staff`. Thêm / gỡ người,
 * kiểm tra phòng vẫn làm ở màn «Công việc dọn phòng».
 */
export default function CleaningSchedulePage() {
  const { user } = useAuth();
  const isManager = user?.role === 'MANAGER' || user?.role === 'PLATFORM_ADMIN';
  const today = todayIso();
  // Nhân viên trên điện thoại xem thời khóa biểu dạng danh sách (có sẵn nút đổi tuần) chứ không theo
  // bảng — câu hướng dẫn và thanh chọn tuần đổi theo.
  const phone = useMediaQuery(TIMETABLE_QUERY);
  const staffPhone = phone && !isManager;

  const [weekStart, setWeekStart] = useState(() => weekStartOf(today));
  const days = useMemo(() => weekDays(weekStart), [weekStart]);

  const [directory, setDirectory] = useState(null); // nhân viên của khách sạn (chỉ Quản lý)
  const [week, setWeek] = useState(null);           // { shifts, tasks } của tuần đang xem
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [opened, setOpened] = useState(null);       // { task, person } — đang xem chi tiết
  const [previousOf, setPreviousOf] = useState(null);
  const [assignContext, setAssignContext] = useState(null); // { byId: Map người giao được hôm nay, queue }
  const [assigningPerson, setAssigningPerson] = useState(null); // { person, tasks } — đang mở hộp giao phòng
  const [banner, setBanner] = useState(null);       // { type, text } — kết quả giao phòng
  const weekHasToday = days.includes(today);

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

  // Nhân viên: mẫu ca đang dùng của chi nhánh — bảng của mình luôn đủ các ca làm hàng. Lỗi thì như cũ:
  // chỉ hàng của các ca mình có.
  const [templates, setTemplates] = useState(null);
  useEffect(() => {
    if (isManager) return undefined;
    let cancelled = false;
    fetchShiftTemplates()
      .then((list) => !cancelled && setTemplates(list))
      .catch(() => !cancelled && setTemplates([]));
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

  /**
   * Quản lý, tuần có hôm nay: ai giao được NGAY (kèm lý do nếu không) và hàng chờ. Lỗi thì chỉ là không hiện nút
   * giao — lịch vẫn xem được.
   */
  const loadAssignContext = useCallback(async () => {
    if (!isManager || !weekHasToday) {
      setAssignContext(null);
      return;
    }
    try {
      const [people, unassigned] = await Promise.all([
        fetchAssignableStaff(today, { ongoingOnly: true }),
        fetchTasks({ status: 'UNASSIGNED', size: PAGE_SIZE, locationWide: true }),
      ]);
      setAssignContext({
        byId: new Map((people ?? []).map((person) => [person.id, person])),
        queue: [...(unassigned.content ?? [])].sort(compareQueue),
      });
    } catch {
      setAssignContext(null);
    }
  }, [isManager, weekHasToday, today]);

  useEffect(() => {
    loadAssignContext();
  }, [loadAssignContext]);

  /** Nút giao phòng trong ô (người, hôm nay); {@code null} = không hiện nút. */
  function assignFor(person, day, cell) {
    const candidate = day === today ? assignContext?.byId.get(person.id) : null;
    if (!candidate || cell.shifts.length === 0) return null;
    return {
      blockedReason: candidate.blockedReason,
      queueEmpty: assignContext.queue.length === 0,
      onClick: () => setAssigningPerson({
        person: {
          id: person.id,
          fullName: person.fullName,
          shiftLabel: cell.shifts.map((shift) => timeRange(shift.startTime, shift.endTime)).join(' · '),
        },
        tasks: assignContext.queue,
      }),
    };
  }

  /** Giao xong: báo cả phần giao được lẫn phần không, rồi tải lại lịch và hàng chờ. */
  function finishAssign({ assigned, failed }) {
    const name = assigningPerson.person.fullName;
    setAssigningPerson(null);
    const rooms = (tasks) => tasks.map(taskRoomLabel).join(', ');
    setBanner(failed.length === 0
      ? { type: 'success', text: `Đã giao ${assigned.length} phòng cho ${name}: ${rooms(assigned)}.` }
      : {
        type: 'error',
        text: (assigned.length > 0 ? `Đã giao phòng ${rooms(assigned)} cho ${name}. ` : '')
          + `Không giao được: ${failed.map(({ task, message }) => `phòng ${taskRoomLabel(task)} — ${message}`).join('; ')}`,
      });
    load();
    loadAssignContext();
  }

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
              ? 'Mỗi ô là ca làm và các phòng dọn trong ngày. Bấm vào một phòng để xem chi tiết và người dọn cùng; ô hôm nay của người có ca có nút «+ Giao phòng» để giao việc ngay.'
              : phone
                ? 'Mỗi mục là một ca của bạn kèm các phòng dọn, xếp theo ngày — bấm «Xem chi tiết» để biết người dọn cùng.'
                : 'Hàng là các ca của khách sạn, cột là ngày. Ca bạn làm hiện khối của ca đó; có việc dọn thì khối liệt kê phòng — bấm vào để xem chi tiết và người dọn cùng.'}
          </p>
        </div>
      </div>

      <div className="sched-toolbar">
        {/* Nhân viên trên điện thoại: đổi tuần ngay trên thời khóa biểu (◀ tháng ▶). */}
        {!staffPhone && (
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
        )}
        {week && (
          <div className="sched-summary">
            <span className="chip">{counts.total} lượt dọn</span>
            <span className="chip">{counts.working} đang dọn</span>
            <span className="chip">{counts.waiting} chờ kiểm tra</span>
            <span className="chip chip--green">{counts.done} đã xong</span>
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
      {error && <div className="alert alert--error" role="alert">{error}</div>}
      {loading && !week && <p className="state">Đang tải lịch dọn…</p>}
      {week && !isManager && templates === null && <p className="state">Đang tải lịch dọn…</p>}

      {/* Chờ cả mẫu ca: vẽ bảng trước rồi mới thêm hàng thì bảng nhảy từ 1 lên 3 hàng. */}
      {week && !isManager && templates !== null && (
        <div aria-busy={loading}>
          <MyCleaningShiftBoard
            days={days}
            today={today}
            shifts={week.shifts}
            tasks={week.tasks}
            templates={templates}
            meId={user?.id}
            onShowPreviousInspection={setPreviousOf}
            onPrevWeek={() => setWeekStart(addDays(weekStart, -7))}
            onNextWeek={() => setWeekStart(addDays(weekStart, 7))}
            onThisWeek={() => setWeekStart(weekStartOf(today))}
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
                  {days.map((day) => {
                    const cell = cellOf(person.id, day);
                    return (
                      <DayCell
                        key={day}
                        day={day}
                        today={today}
                        cell={cell}
                        assign={assignFor(person, day, cell)}
                        onOpen={(task) => setOpened({ task, person })}
                      />
                    );
                  })}
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
      {assigningPerson && (
        <AssignToStaffModal
          person={assigningPerson.person}
          date={today}
          today={today}
          tasks={assigningPerson.tasks}
          onClose={() => setAssigningPerson(null)}
          onDone={finishAssign}
        />
      )}
    </div>
  );
}

/**
 * Một ô (người, ngày): giờ ca, rồi từng phòng "301 - Deluxe" kèm trạng thái.
 *
 * @param assign nút «+ Giao phòng» cho ô hôm nay: `{ blockedReason, queueEmpty, onClick }`; null = không có nút
 */
function DayCell({ day, today, cell, assign, onOpen }) {
  const { shifts, tasks } = cell;
  return (
    <td className={dayClass(day, today)}>
      {shifts.length > 0 && (
        <p className="clean-cell__shift">Ca {shifts.map((s) => timeRange(s.startTime, s.endTime)).join(' · ')}</p>
      )}
      {assign && (
        <div className="clean-cell__assign">
          <button
            type="button"
            className="btn btn--primary btn--sm"
            disabled={Boolean(assign.blockedReason) || assign.queueEmpty}
            title={assign.blockedReason
              ?? (assign.queueEmpty ? 'Hàng chờ không còn phòng nào' : 'Giao phòng trong hàng chờ cho người này')}
            onClick={assign.onClick}
          >
            + Giao phòng
          </button>
          {assign.blockedReason && <small className="clean-cell__desk">Trực quầy lễ tân</small>}
        </div>
      )}
      {tasks.map((task) => {
        const overdue = Boolean(task.overdue);   // BR-HK-04 — cờ do backend tính (ca qua đêm)
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
