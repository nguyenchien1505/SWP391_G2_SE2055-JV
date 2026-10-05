import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { fetchAssignableStaff, fetchTasks } from '../../api/housekeeping';
import { fetchShifts } from '../../api/scheduling';
import { formatDate } from '../../pages/rooms/format';
import { canAssignOn, compareQueue, initials, isRedo } from '../../pages/rooms/cleaningBoard';
import { compareNatural, taskStatusMeta } from '../../pages/rooms/roomLabels';
import { addDays, formatLongDate, timeRange } from '../../pages/scheduling/scheduleFormat';
import TaskCard from './TaskCard';
import TaskStatusBadge from './TaskStatusBadge';

/** Đủ cho mọi việc của một khách sạn 2–3 sao trong một ngày — không phân trang. */
const PAGE_SIZE = 500;

/** Thứ tự việc trong một hàng: còn phải làm → chờ kiểm tra → xong. Việc đã hủy không hiện. */
const ROW_ORDER = ['IN_PROGRESS', 'PENDING_INSPECTION', 'COMPLETED'];

/**
 * Lịch dọn theo ngày, theo NGƯỜI — chế độ "Theo nhân viên" của màn Công việc dọn phòng.
 *
 * Theo BR, lịch dọn không phải lịch riêng mà là các việc dọn gắn (người, ngày) — DM-04 — với điều
 * kiện người nhận có ca ngày đó và có quyền Dọn dẹp (BR-HK-03). Vì vậy mỗi HÀNG là đúng một người
 * như thế (lấy thẳng từ `assignable-staff`), kèm giờ ca để Quản lý chia việc cho đều.
 *
 * - **Hàng chờ**: việc chưa ai nhận, dọn lại xếp đầu.
 * - **Tồn đọng**: việc hôm trước chưa xong (BR-HK-04) — chỉ hiện khi xem hôm nay.
 * - **Cần xử lý**: việc đang làm trong ngày nhưng người giữ không còn trong danh sách giao được
 *   (mất ca / mất quyền, dữ liệu cũ) — hiện riêng để không việc nào bị khuất.
 *
 * Ngày đã qua chỉ xem; ngày tương lai chỉ giao được việc dọn hằng ngày (Q5).
 *
 * Mọi thao tác (giao, gỡ, kiểm tra, hủy) do trang cha giữ để dùng chung hộp thoại với bảng theo
 * trạng thái; `version` tăng sau mỗi thao tác thì bảng tải lại.
 */
export default function CleaningDayBoard({
  today,
  canManage,
  version,
  staffNames,
  actionsFor,
  onAssignToPerson,
  onShowPreviousInspection,
}) {
  const [date, setDate] = useState(today);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    Promise.all([
      fetchAssignableStaff(date),
      fetchShifts({ from: date, to: date }),
      fetchTasks({ assignedDate: date, size: PAGE_SIZE }),
      fetchTasks({ status: 'UNASSIGNED', size: PAGE_SIZE }),
      // Tồn đọng chỉ có nghĩa khi đang xem hôm nay.
      date === today ? fetchTasks({ status: 'IN_PROGRESS', size: PAGE_SIZE }) : null,
    ])
      .then(([people, shifts, ofDay, unassigned, inProgress]) => {
        if (cancelled) return;
        setData({
          people: people ?? [],
          shifts,
          dayTasks: (ofDay.content ?? []).filter((task) => task.status !== 'CANCELLED'),
          queue: [...(unassigned.content ?? [])].sort(compareQueue),
          backlog: (inProgress?.content ?? [])
            .filter((task) => task.assignedDate && task.assignedDate < date)
            .sort((a, b) => compareNatural(a.roomNumber, b.roomNumber)),
        });
      })
      .catch((err) => !cancelled && setError(readErrorMessage(err, 'Không tải được lịch dọn theo ngày.')))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [date, today, version]);

  const rows = useMemo(() => {
    if (!data) return [];
    return data.people
      .map((person) => {
        const shifts = data.shifts
          .filter((shift) => shift.staffId === person.id)
          .sort((a, b) => String(a.startTime).localeCompare(String(b.startTime)));
        const tasks = data.dayTasks
          .filter((task) => task.assignedStaffId === person.id)
          .sort((a, b) => ROW_ORDER.indexOf(a.status) - ROW_ORDER.indexOf(b.status)
            || compareNatural(a.roomNumber, b.roomNumber));
        const count = (status) => tasks.filter((task) => task.status === status).length;
        return {
          person,
          shiftLabel: shifts.map((shift) => timeRange(shift.startTime, shift.endTime)).join(' · '),
          tasks,
          working: count('IN_PROGRESS'),
          waiting: count('PENDING_INSPECTION'),
          done: count('COMPLETED'),
        };
      })
      .sort((a, b) => a.person.fullName.localeCompare(b.person.fullName, 'vi'));
  }, [data]);

  const orphans = useMemo(() => {
    if (!data) return [];
    const onShift = new Set(data.people.map((person) => person.id));
    return data.dayTasks
      .filter((task) => task.status === 'IN_PROGRESS' && !onShift.has(task.assignedStaffId))
      .sort((a, b) => compareNatural(a.roomNumber, b.roomNumber));
  }, [data]);

  const readOnly = !canManage || date < today;
  const assignableQueue = (data?.queue ?? []).filter((task) => canAssignOn(task, date, today));
  const isFuture = date > today;

  /** Thẻ trong hàng chờ: ngày tương lai bỏ nút giao của việc dọn sau trả phòng (Q5). */
  function queueActions(task) {
    if (readOnly) return [];
    return actionsFor(task, { date }).filter((action) => action.key !== 'assign' || canAssignOn(task, date, today));
  }

  return (
    <div className="day-board-wrap">
      <div className="day-board__toolbar">
        <div className="day-nav" role="group" aria-label="Chọn ngày">
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setDate(addDays(date, -1))}>
            ‹ Hôm trước
          </button>
          <input
            type="date"
            value={date}
            aria-label="Ngày xem lịch dọn"
            onChange={(e) => e.target.value && setDate(e.target.value)}
          />
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setDate(addDays(date, 1))}>
            Hôm sau ›
          </button>
          {date !== today && (
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setDate(today)}>
              Hôm nay
            </button>
          )}
        </div>
        <p className="day-board__title">
          {formatLongDate(date)}
          {date === today && <span className="chip chip--green">Hôm nay</span>}
          {date < today && <span className="chip">Chỉ xem</span>}
        </p>
        {data && (
          <div className="day-board__stats">
            <span className="chip">{rows.length} người có ca</span>
            <span className="chip">{data.dayTasks.length} việc trong ngày</span>
            <span className="chip">{data.queue.length} chờ giao</span>
          </div>
        )}
      </div>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}
      {loading && !data && <p className="state">Đang tải lịch dọn…</p>}

      {data && (
        <div className={`day-board ${loading ? 'is-loading' : ''}`}>
          <aside className="day-board__side">
            <section className="day-panel">
              <header className="day-panel__head">
                <h2>Hàng chờ</h2>
                <span className="chip">{data.queue.length}</span>
              </header>
              {isFuture && data.queue.some((task) => !canAssignOn(task, date, today)) && (
                <p className="day-panel__note">
                  Ngày tương lai chỉ giao được việc dọn hằng ngày; việc dọn sau trả phòng chỉ giao trong ngày.
                </p>
              )}
              {data.queue.length === 0 && <p className="state state--empty">Không còn việc nào chờ giao.</p>}
              {data.queue.map((task) => (
                <TaskCard
                  key={task.id}
                  task={task}
                  today={today}
                  actions={queueActions(task)}
                  onShowPreviousInspection={onShowPreviousInspection}
                />
              ))}
            </section>

            {data.backlog.length > 0 && (
              <section className="day-panel day-panel--warn">
                <header className="day-panel__head">
                  <h2>Tồn đọng</h2>
                  <span className="chip">{data.backlog.length}</span>
                </header>
                <p className="day-panel__note">
                  Việc hôm trước chưa xong (BR-HK-04). Gỡ người rồi giao lại cho người có ca hôm nay nếu cần.
                </p>
                {data.backlog.map((task) => (
                  <TaskCard
                    key={task.id}
                    task={task}
                    staffName={staffNames[task.assignedStaffId]}
                    today={today}
                    actions={readOnly ? [] : actionsFor(task)}
                    onShowPreviousInspection={onShowPreviousInspection}
                  />
                ))}
              </section>
            )}
          </aside>

          <div className="day-board__main">
            {orphans.length > 0 && (
              <section className="day-panel day-panel--alert">
                <header className="day-panel__head">
                  <h2>Cần xử lý</h2>
                  <span className="chip">{orphans.length}</span>
                </header>
                <p className="day-panel__note">
                  Người giữ các việc này không còn ca hoặc quyền Dọn dẹp ngày {formatDate(date)}. Gỡ người rồi
                  giao lại cho người có ca.
                </p>
                <ul className="staff-row__tasks">
                  {orphans.map((task) => (
                    <TaskChip
                      key={task.id}
                      task={task}
                      who={staffNames[task.assignedStaffId] ?? 'Nhân viên đã nghỉ'}
                      actions={readOnly ? [] : actionsFor(task)}
                      onShowPreviousInspection={onShowPreviousInspection}
                    />
                  ))}
                </ul>
              </section>
            )}

            {rows.length === 0 && (
              <div className="state state--empty day-board__empty">
                <p>Không có nhân viên dọn phòng nào có ca ngày {formatDate(date)}.</p>
                <p className="muted">
                  Việc dọn chỉ giao được cho người có ca và có quyền Dọn dẹp. Xếp ca ở màn{' '}
                  <Link to="/xep-lich">Xếp lịch làm việc</Link> trước rồi quay lại giao việc.
                </p>
              </div>
            )}

            {rows.map((row) => (
              <section key={row.person.id} className="staff-row">
                <header className="staff-row__head">
                  <span className="avatar" aria-hidden="true">{initials(row.person.fullName)}</span>
                  <div className="staff-row__who">
                    <b>{row.person.fullName}</b>
                    <small>Ca {row.shiftLabel || '—'}{row.person.phone ? ` · ${row.person.phone}` : ''}</small>
                  </div>
                  <div className="staff-row__counts">
                    <span className="chip">{row.working} đang làm</span>
                    {row.waiting > 0 && <span className="chip">{row.waiting} chờ kiểm tra</span>}
                    <span className="chip chip--green">{row.done} xong</span>
                  </div>
                  {!readOnly && (
                    <button
                      type="button"
                      className="btn btn--primary btn--sm"
                      disabled={assignableQueue.length === 0}
                      title={assignableQueue.length === 0 ? 'Hàng chờ không còn việc giao được cho ngày này' : undefined}
                      onClick={() => onAssignToPerson({
                        person: { ...row.person, shiftLabel: row.shiftLabel },
                        date,
                        tasks: data.queue,
                      })}
                    >
                      + Giao việc
                    </button>
                  )}
                </header>

                {row.tasks.length === 0 ? (
                  <p className="staff-row__empty">Chưa có việc nào trong ngày.</p>
                ) : (
                  <ul className="staff-row__tasks">
                    {row.tasks.map((task) => (
                      <TaskChip
                        key={task.id}
                        task={task}
                        actions={readOnly ? [] : actionsFor(task)}
                        onShowPreviousInspection={onShowPreviousInspection}
                      />
                    ))}
                  </ul>
                )}
              </section>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/** Một việc dạng gọn trong hàng của một người — số phòng, loại, trạng thái và nút thao tác. */
function TaskChip({ task, who, actions, onShowPreviousInspection }) {
  return (
    <li className={`hk-chip room-tone--${taskStatusMeta(task.status).tone}`}>
      <div className="hk-chip__main">
        <span className="hk-chip__room">{task.roomNumber ?? '—'}</span>
        <span className="hk-chip__type">{task.taskType === 'CHECKOUT' ? 'Sau trả phòng' : 'Hằng ngày'}</span>
        <TaskStatusBadge status={task.status} />
      </div>
      {who && <p className="hk-chip__who">{who}</p>}
      {isRedo(task) && task.parentTaskId && (
        <button type="button" className="link-btn" onClick={() => onShowPreviousInspection(task)}>
          Dọn lại · xem lý do
        </button>
      )}
      {actions.length > 0 && (
        <div className="hk-chip__actions">
          {actions.map((action) => (
            <button
              key={action.key}
              type="button"
              className={`btn btn--sm ${action.danger ? 'btn--danger' : 'btn--ghost'}`}
              onClick={action.onClick}
            >
              {action.label}
            </button>
          ))}
        </div>
      )}
    </li>
  );
}
