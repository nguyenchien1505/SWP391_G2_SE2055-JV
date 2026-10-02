import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { fetchRoomHistory } from '../../api/rooms';
import { useAuth } from '../../context/AuthContext';
import { canReportDamage } from '../../permissions';
import { formatDate, formatDateTime, formatShortDateTime } from '../../pages/rooms/format';
import { roomActionsFor } from '../../pages/rooms/roomActions';
import { isOverdue, whyNoRoomActions } from '../../pages/rooms/roomBoard';
import {
  changeSourceLabel,
  roomStatusMeta,
  taskSourceLabel,
  taskTypeLabel,
  unassignedReasonLabel,
} from '../../pages/rooms/roomLabels';
import RoomStatusBadge from './RoomStatusBadge';
import TaskStatusBadge from './TaskStatusBadge';

/** Số lần đổi trạng thái hiện ngay trong bảng; muốn xem hết thì sang S-04. */
const HISTORY_PREVIEW = 5;

/**
 * Bảng chi tiết một phòng trên sơ đồ — S-06 / S-15. Máy tính màn rộng: cột bên phải, luôn nằm
 * cạnh lưới. Máy tính bảng: ngăn kéo trượt từ phải. Điện thoại: trượt lên từ cạnh dưới — vùng
 * ngón cái với tới được (design.md mục 2). Trang cha quyết định cách bọc; component này chỉ vẽ
 * nội dung.
 *
 * Thứ tự các khối theo mức khẩn: THAO TÁC lên đầu (Lễ tân cần 1–2 chạm), rồi việc dọn đang mở
 * (Quản lý chi nhánh), rồi thông tin, ghi chú, lịch sử.
 *
 * Nút đổi trạng thái vẽ từ `room.allowedTargets` do backend tính (F2) — không có dãy nút "chuyển
 * nhanh sang trạng thái bất kỳ": Đang dọn / Chờ kiểm tra chỉ đổi qua việc dọn (BR-ROOM-02).
 *
 * @param role      { isDirector, isManager, isReception } — chỉ để ẨN/HIỆN cho dễ dùng
 * @param tasks     việc dọn ĐANG MỞ của phòng (chỉ Quản lý chi nhánh có dữ liệu này)
 * @param historyKey đổi giá trị để nạp lại lịch sử sau mỗi lần phòng đổi trạng thái
 */
export default function RoomBoardPanel({
  room,
  role,
  locationName,
  tasks = [],
  staffNames = {},
  historyKey = 0,
  busy = false,
  autoFocusClose = false,
  onClose,
  onAction,
  onAssign,
  onInspect,
  onUnassign,
  onCancel,
  onCreateStayover,
  onEditNote,
  onShowPreviousInspection,
}) {
  const { user } = useAuth();
  const meta = roomStatusMeta(room.status);
  const actions = roomActionsFor(room);
  const hasStayover = tasks.some((task) => task.taskType === 'STAYOVER');

  return (
    <aside className="board-panel" aria-labelledby="board-panel-title">
      <header className={`board-panel__head room-tone--${meta.tone}`}>
        <span className="board-panel__tile" aria-hidden="true">{room.roomNumber}</span>
        <div className="board-panel__title">
          <h2 id="board-panel-title">Phòng {room.roomNumber}</h2>
          <RoomStatusBadge status={room.status} />
          <p className="muted">
            Tầng {room.floor} · {room.roomTypeName ?? 'Chưa rõ loại'} · {room.capacity} người
          </p>
        </div>
        <button
          type="button"
          className="board-panel__close"
          onClick={onClose}
          aria-label="Đóng bảng chi tiết"
          autoFocus={autoFocusClose}
        >
          <span className="material-symbols-outlined" aria-hidden="true">close</span>
        </button>
      </header>

      <div className="board-panel__body">
        <section className="board-panel__section" aria-label="Thao tác">
          {actions.length > 0 ? (
            <div className="board-panel__actions">
              {actions.map((action) => (
                <button
                  key={action.key}
                  type="button"
                  className={`btn ${action.danger ? 'btn--danger' : 'btn--primary'}`}
                  disabled={busy}
                  onClick={() => onAction(action)}
                >
                  {action.label}
                </button>
              ))}
            </div>
          ) : (
            <p className="board-panel__why">
              <span className="material-symbols-outlined" aria-hidden="true">info</span>
              {whyNoRoomActions(room, role)}
            </p>
          )}
        </section>

        {room.status === 'UNAVAILABLE' && (
          <div className="readonly-box">
            <b>Lý do không khả dụng</b>
            <p>{room.unavailableReason || '—'}</p>
          </div>
        )}

        {role.isManager && (
          <section className="board-panel__section">
            <h3 className="board-panel__heading">Việc dọn đang mở</h3>
            {tasks.length === 0 && <p className="muted">Không có việc dọn nào đang mở.</p>}
            {tasks.map((task) => (
              <BoardTask
                key={task.id}
                task={task}
                staffName={staffNames[task.assignedStaffId]}
                onAssign={onAssign}
                onInspect={onInspect}
                onUnassign={onUnassign}
                onCancel={onCancel}
                onShowPreviousInspection={onShowPreviousInspection}
              />
            ))}
            {/* BR-HK-05: khách đang ở cần dọn hằng ngày thì Quản lý tạo tay; tối đa 1 việc mở mỗi
                loại (BR-HK-11) nên đã có thì ẩn nút. */}
            {room.status === 'OCCUPIED' && !hasStayover && (
              <button type="button" className="btn btn--ghost btn--block" onClick={() => onCreateStayover(room)}>
                + Tạo việc dọn hằng ngày
              </button>
            )}
          </section>
        )}

        <section className="board-panel__section">
          <h3 className="board-panel__heading">Thông tin phòng</h3>
          <dl className="board-panel__facts">
            <div>
              <dt>Tầng</dt>
              <dd>{room.floor}</dd>
            </div>
            <div>
              <dt>Sức chứa</dt>
              <dd>{room.capacity} người</dd>
            </div>
            <div>
              <dt>Loại phòng</dt>
              <dd>{room.roomTypeName ?? '—'}</dd>
            </div>
            {locationName && (
              <div>
                <dt>Khách sạn</dt>
                <dd>{locationName}</dd>
              </div>
            )}
          </dl>
          <div className="readonly-box board-panel__note">
            <div className="board-panel__note-head">
              <b>Ghi chú vận hành</b>
              {/* BR-ROOM-04: Quản lý chi nhánh chỉ sửa được ghi chú; Giám đốc sửa được mọi thông tin
                  phòng. Sơ đồ không có form "Sửa thông tin" nên Giám đốc cũng sửa ghi chú ở đây —
                  cùng endpoint PATCH /rooms/{id}, backend cho cả hai vai trò. */}
              {(role.isManager || role.isDirector) && (
                <button type="button" className="link-btn" onClick={() => onEditNote(room)}>
                  Sửa ghi chú
                </button>
              )}
            </div>
            <p>{room.note || 'Chưa có ghi chú.'}</p>
          </div>
        </section>

        <section className="board-panel__section">
          <h3 className="board-panel__heading">Đổi trạng thái gần đây</h3>
          <RecentHistory key={`${room.id}-${historyKey}`} roomId={room.id} />
          <Link className="board-panel__more" to={`/phong/${room.id}`} state={{ tab: 'history' }}>
            Xem chi tiết và toàn bộ lịch sử ›
          </Link>
        </section>

        <section className="board-panel__section">
          <h3 className="board-panel__heading">Tài sản trong phòng</h3>
          {/* BR-ASSET-05: Lễ tân / Dọn dẹp báo hỏng ở tab tài sản của chi tiết phòng. */}
          <Link className="board-panel__more" to={`/phong/${room.id}`} state={{ tab: 'assets' }}>
            {canReportDamage(user) ? 'Xem tài sản và báo hỏng ›' : 'Xem tài sản trong phòng ›'}
          </Link>
        </section>
      </div>
    </aside>
  );
}

/** Một việc dọn đang mở của phòng, kèm đúng nút mà trạng thái của việc đó cho phép (F5, F6). */
function BoardTask({ task, staffName, onAssign, onInspect, onUnassign, onCancel, onShowPreviousInspection }) {
  return (
    <div className="board-task">
      <div className="board-task__head">
        <b>{taskTypeLabel(task.taskType)}</b>
        <TaskStatusBadge status={task.status} />
      </div>
      <p className="board-task__meta">{taskSourceLabel(task.createdSource)}</p>

      {task.assignedStaffId && (
        <p className="board-task__meta">
          Người làm: <b>{staffName ?? 'Nhân viên đã nghỉ'}</b>
          {task.assignedDate ? ` · ngày ${formatDate(task.assignedDate)}` : ''}
        </p>
      )}
      {task.status === 'IN_PROGRESS' && task.assignedAt && (
        <p className="board-task__meta">Giao lúc {formatShortDateTime(task.assignedAt)}</p>
      )}
      {task.status === 'PENDING_INSPECTION' && task.completedAt && (
        <p className="board-task__meta">Báo xong lúc {formatShortDateTime(task.completedAt)}</p>
      )}
      {isOverdue(task) && (
        <p className="board-task__flag">Tồn đọng từ {formatDate(task.assignedDate)}</p>
      )}
      {task.status === 'UNASSIGNED' && unassignedReasonLabel(task.unassignedReason) && (
        <p className="board-task__meta">{unassignedReasonLabel(task.unassignedReason)}</p>
      )}
      {/* BR-HK-12: việc dọn lại phải xem được lý do không đạt ở biên bản của việc gốc. */}
      {task.parentTaskId && (
        <button type="button" className="link-btn" onClick={() => onShowPreviousInspection(task)}>
          Xem lần kiểm tra trước
        </button>
      )}

      <div className="board-task__actions">
        {task.status === 'UNASSIGNED' && (
          <button type="button" className="btn btn--primary" onClick={() => onAssign(task)}>
            Phân công
          </button>
        )}
        {task.status === 'PENDING_INSPECTION' && (
          <button type="button" className="btn btn--primary" onClick={() => onInspect(task)}>
            Kiểm tra phòng
          </button>
        )}
        {task.status === 'IN_PROGRESS' && (
          <button type="button" className="btn btn--ghost" onClick={() => onUnassign(task)}>
            Gỡ người
          </button>
        )}
        {/* Hủy tay chỉ dành cho việc dọn HẰNG NGÀY: hủy việc dọn sau trả phòng sẽ để phòng «Chờ dọn»
            mà không còn việc nào. Backend cũng chặn, ẩn nút chỉ để khỏi bấm nhầm — giống S-11. */}
        {task.taskType === 'STAYOVER' && (
          <button type="button" className="btn btn--danger" onClick={() => onCancel(task)}>
            Hủy việc
          </button>
        )}
      </div>
    </div>
  );
}

/** Vài lần đổi trạng thái mới nhất (BR-ROOM-09) — đọc thẳng từ lịch sử, không bịa nhật ký. */
function RecentHistory({ roomId }) {
  const [rows, setRows] = useState(null); // null = đang tải
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    fetchRoomHistory(roomId, { page: 0, size: HISTORY_PREVIEW })
      .then((data) => !cancelled && setRows(data.content ?? []))
      .catch((err) => !cancelled && setError(readErrorMessage(err, 'Không tải được lịch sử trạng thái.')));
    return () => {
      cancelled = true;
    };
  }, [roomId]);

  if (error) return <p className="muted">{error}</p>;
  if (rows === null) return <p className="muted">Đang tải lịch sử…</p>;
  if (rows.length === 0) return <p className="muted">Chưa có lần đổi trạng thái nào.</p>;

  return (
    <ol className="board-timeline">
      {rows.map((row) => (
        <li key={row.id} className={`board-timeline__item room-tone--${roomStatusMeta(row.toStatus).tone}`}>
          <p className="board-timeline__change">
            {row.fromStatus ? roomStatusMeta(row.fromStatus).label : 'Phòng mới tạo'}
            {' → '}
            <b>{roomStatusMeta(row.toStatus).label}</b>
          </p>
          <p className="board-timeline__meta">
            {formatDateTime(row.changedAt)} · {row.changedByName ?? changeSourceLabel(row.changeSource)}
          </p>
          {row.reason && <p className="board-timeline__reason">Lý do: {row.reason}</p>}
        </li>
      ))}
    </ol>
  );
}
