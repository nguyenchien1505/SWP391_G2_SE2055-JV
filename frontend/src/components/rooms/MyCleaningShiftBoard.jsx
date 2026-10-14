import { Fragment, useEffect, useMemo, useRef, useState } from 'react';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { formatShortDateTime } from '../../pages/rooms/format';
import { NO_SHIFT_ROW, OTHER_SHIFT_ROW, buildMyShiftBoard } from '../../pages/rooms/cleaningShifts';
import { taskRoomLabel, taskSourceLabel, teamNames } from '../../pages/rooms/roomLabels';
import {
  WEEKDAY_SHORT,
  dayClass,
  formatDayMonth,
  formatLongDate,
  shiftTone,
  timeRange,
} from '../../pages/scheduling/scheduleFormat';
import TimeRangeText from '../scheduling/TimeRangeText';
import WeekTimetable, { TIMETABLE_QUERY } from '../scheduling/WeekTimetable';
import CleaningStatusBadge, { cleaningTone } from './CleaningStatusBadge';
import RoomStatusBadge from './RoomStatusBadge';
import TaskStatusBadge from './TaskStatusBadge';

/**
 * "Lịch dọn của tôi" — cùng cách chia hàng / cột với bảng xếp lịch của quản lý (chốt 06/10/2026): hàng
 * là MỌI ca của chi nhánh (Ca sáng, Ca chiều, Ca đêm… — chốt 12/10/2026; "Ca giờ khác" / "Ngoài ca" khi
 * có), cột là 7 ngày. Ô nào có lịch thì hiện KHỐI của ca đó: có việc dọn thì khối liệt kê phòng ("305 -
 * Deluxe") kèm trạng thái, bấm để xem chi tiết từng phòng và người dọn cùng; có ca mà chưa có việc thì
 * khối ca chỉ để xem.
 *
 * Điện thoại (≤ 640px) không dùng bảng mà dùng thời khóa biểu dạng danh sách theo ngày
 * ({@link WeekTimetable}, kiểu "Weekly timetable" — chốt 13/10/2026): mỗi mục một ca có lịch, thẻ bên
 * phải liệt kê phòng dọn kèm trạng thái và nút xem chi tiết.
 *
 * @param templates mẫu ca đang dùng của chi nhánh — mỗi mẫu luôn có hàng (bảng) / có trong chú thích
 * @param meId      người đang xem — để "dọn cùng" không ghi chính mình
 * @param onPrevWeek / onNextWeek / onThisWeek  đổi tuần — thời khóa biểu trên điện thoại tự có nút
 */
export default function MyCleaningShiftBoard({
  days,
  today,
  shifts,
  tasks,
  templates = [],
  meId,
  onShowPreviousInspection,
  onPrevWeek,
  onNextWeek,
  onThisWeek,
}) {
  const board = useMemo(() => buildMyShiftBoard(shifts, tasks, templates), [shifts, tasks, templates]);
  const [opened, setOpened] = useState(null); // { row, day, cell }
  const phone = useMediaQuery(TIMETABLE_QUERY);

  /** Nội dung một ô (ca, ngày) của bảng tuần. */
  function renderCell(row, day) {
    const cell = board.cellOf(row.id, day);
    const freeTimes = row.id === OTHER_SHIFT_ROW
      ? cell.shifts.map((shift) => timeRange(shift.startTime, shift.endTime)).join(', ')
      : '';
    if (cell.tasks.length > 0) {
      return (
        <WorkBlock
          row={row}
          day={day}
          today={today}
          cell={cell}
          freeTimes={freeTimes}
          onOpen={() => setOpened({ row, day, cell })}
        />
      );
    }
    return cell.shifts.length > 0 ? <IdleShiftBlock shifts={cell.shifts} /> : null;
  }

  /** Thời khóa biểu điện thoại: mỗi ca CÓ LỊCH trong ngày (có ca, hoặc có việc dọn) là một mục. */
  function entriesOf(day) {
    return board.rows.flatMap((row) => {
      const cell = board.cellOf(row.id, day);
      if (cell.shifts.length === 0 && cell.tasks.length === 0) return [];
      const shift = cell.shifts[0];
      return [{
        key: row.id,
        tone: shift ? shiftTone(shift.startTime) : 'other',
        label: row.name,
        start: shift?.startTime ?? null,
        end: shift?.endTime ?? null,
        content: <CleaningCard cell={cell} today={today} onOpen={() => setOpened({ row, day, cell })} />,
      }];
    });
  }

  if (phone) {
    return (
      <>
        <WeekTimetable
          days={days}
          today={today}
          legend={templates.map((template) => ({
            tone: shiftTone(template.startTime),
            label: template.name,
            time: timeRange(template.startTime, template.endTime),
          }))}
          emptyText="Không có ca"
          entriesOf={entriesOf}
          onPrevWeek={onPrevWeek}
          onNextWeek={onNextWeek}
          onThisWeek={onThisWeek}
        />
        {opened && (
          <ShiftWorkDialog
            row={opened.row}
            day={opened.day}
            cell={opened.cell}
            meId={meId}
            onClose={() => setOpened(null)}
            onShowPreviousInspection={(task) => {
              setOpened(null);
              onShowPreviousInspection(task);
            }}
          />
        )}
      </>
    );
  }

  if (board.rows.length === 0) {
    return (
      <div className="state state--empty">
        <p>Tuần này bạn không có ca nào.</p>
        <p className="muted">Quản lý xếp ca thì ca và việc dọn của bạn sẽ hiện ở đây.</p>
      </div>
    );
  }

  return (
    <>
      {/* Vẫn đủ các hàng ca, chỉ chưa có khối nào — nói rõ để khỏi tưởng bảng lỗi. */}
      {shifts.length === 0 && tasks.length === 0 && (
        <p className="muted">Tuần này bạn không có ca và việc dọn nào. Quản lý xếp ca thì sẽ hiện ở đây.</p>
      )}
      <div className="sched-scroll">
        <table className="sched-grid sched-grid--personal">
          <thead>
            <tr>
              <th scope="col" className="sched-grid__person">Ca làm việc</th>
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
            {board.rows.map((row) => (
              <tr key={row.id} className={row.start ? '' : 'sched-row--other'}>
                <th
                  scope="row"
                  className={`sched-grid__person person-cell shift-row-head ${row.start ? `shift-tone--${shiftTone(row.start)}` : ''}`}
                >
                  <b>{row.name}</b>
                  <small>{row.timeLabel}</small>
                </th>
                {days.map((day) => (
                  <td key={day} className={dayClass(day, today)}>
                    {renderCell(row, day)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {opened && (
        <ShiftWorkDialog
          row={opened.row}
          day={opened.day}
          cell={opened.cell}
          meId={meId}
          onClose={() => setOpened(null)}
          onShowPreviousInspection={(task) => {
            setOpened(null);
            onShowPreviousInspection(task);
          }}
        />
      )}
    </>
  );
}

/**
 * Thẻ chi tiết một ca trên thời khóa biểu điện thoại: các phòng dọn (số phòng, loại, trạng thái) và nút
 * xem chi tiết — có cả người dọn cùng. Ca chưa có phòng chỉ ghi «Chưa có phòng».
 */
function CleaningCard({ cell, today, onOpen }) {
  const { tasks } = cell;
  return (
    <>
      <div className="tt-box">
        <span className="tt-box__label">Phòng dọn</span>
        <b className={`tt-box__value ${tasks.length === 0 ? 'is-muted' : ''}`}>
          {tasks.length > 0 ? tasks.map((task) => task.roomNumber).join(' · ') : 'Chưa có phòng'}
        </b>
      </div>
      {tasks.length > 0 && (
        <>
          <ul className="tt-rooms">
            {tasks.map((task) => (
              <li key={task.id} className={`room-tone--${cleaningTone(task)}`}>
                <span className="tt-rooms__name">{taskRoomLabel(task)}</span>
                <CleaningStatusBadge task={task} />
                {task.status === 'IN_PROGRESS' && task.assignedDate < today && (
                  <span className="clean-entry__flag">Tồn đọng</span>
                )}
              </li>
            ))}
          </ul>
          <button type="button" className="tt-action" onClick={onOpen}>
            Xem chi tiết
          </button>
        </>
      )}
    </>
  );
}

/** Khối của ca có làm mà chưa có phòng dọn: khung giờ ca, chỉ để xem (như khối ca ở «Lịch cá nhân»). */
function IdleShiftBlock({ shifts }) {
  return (
    <div className={`cell-block cell-block--static shift-tone--${shiftTone(shifts[0].startTime)}`}>
      <b className="my-shift__time">
        {shifts.map((shift, index) => (
          <Fragment key={shift.id}>
            {index > 0 && ', '}
            <TimeRangeText start={shift.startTime} end={shift.endTime} />
          </Fragment>
        ))}
      </b>
      <span className="cell-block__meta">Có ca · chưa có phòng</span>
    </div>
  );
}

/** Khối việc của một ca: số phòng, từng phòng "305 - Deluxe" kèm trạng thái. Bấm để xem chi tiết. */
function WorkBlock({ row, day, today, cell, freeTimes, onOpen }) {
  const tone = row.start ? shiftTone(row.start) : 'morning';
  return (
    <button
      type="button"
      className={`cell-block work-block shift-tone--${tone}`}
      onClick={onOpen}
      aria-label={`${row.name} ${formatLongDate(day)}: ${cell.tasks.length} phòng. Xem chi tiết`}
      title="Xem chi tiết"
    >
      <span className="cell-block__count">
        <b>{cell.tasks.length}</b> phòng
      </span>
      {freeTimes && <span className="cell-block__meta">{freeTimes}</span>}
      <span className="work-block__rooms">
        {cell.tasks.map((task) => (
          <span key={task.id} className={`work-block__room room-tone--${cleaningTone(task)}`}>
            <span className="work-block__name">{taskRoomLabel(task)}</span>
            <CleaningStatusBadge task={task} />
            {task.status === 'IN_PROGRESS' && task.assignedDate < today && (
              <span className="clean-entry__flag">Tồn đọng</span>
            )}
          </span>
        ))}
      </span>
    </button>
  );
}

/** Chi tiết việc dọn của một ca: từng phòng, trạng thái, và những người dọn cùng. */
function ShiftWorkDialog({ row, day, cell, meId, onClose, onShowPreviousInspection }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  // Hộp này thường dài hơn màn hình: đưa focus lên tiêu đề mà không cuộn, để mở ra thấy ngay ca nào.
  const titleRef = useRef(null);
  useEffect(() => {
    titleRef.current?.focus({ preventScroll: true });
  }, []);

  const times = cell.shifts.map((shift) => timeRange(shift.startTime, shift.endTime)).join(' · ');

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="shift-work-title" onClick={onClose}>
      <div className="modal room-modal" onClick={(e) => e.stopPropagation()}>
        <h2 id="shift-work-title" ref={titleRef} tabIndex={-1}>
          {row.id === NO_SHIFT_ROW ? 'Việc dọn ngoài ca' : row.name} · {formatLongDate(day)}
        </h2>

        <div className="modal__body">
          <p className="muted">
            {times ? `Ca ${times} · ` : ''}
            {cell.tasks.length} phòng
          </p>

          <ul className="work-detail-list">
            {cell.tasks.map((task) => {
              const partners = teamNames(task, meId);
              return (
                <li key={task.id} className={`work-detail room-tone--${cleaningTone(task)}`}>
                  <div className="work-detail__head">
                    <b>Phòng {taskRoomLabel(task)}</b>
                    <CleaningStatusBadge task={task} />
                  </div>
                  <p className="work-detail__team">
                    Dọn cùng: <b>{partners || 'không có — bạn dọn một mình'}</b>
                  </p>
                  <dl className="clean-detail">
                    <div>
                      <dt>Trạng thái phòng (hiện tại)</dt>
                      <dd><RoomStatusBadge status={task.roomStatus} /></dd>
                    </div>
                    <div>
                      <dt>Việc dọn</dt>
                      <dd>
                        <TaskStatusBadge status={task.status} />{' '}
                        <span className="muted">{taskSourceLabel(task.createdSource)}</span>
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
                </li>
              );
            })}
          </ul>
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--primary" onClick={onClose}>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
