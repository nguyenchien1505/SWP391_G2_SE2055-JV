import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { fetchRoomHistory } from '../../api/rooms';
import { useAuth } from '../../context/AuthContext';
import { canReportDamage } from '../../permissions';
import { assetService } from '../../services/assetApi';
import { formatDateTime } from '../../pages/rooms/format';
import { LOCK, roomActionsFor } from '../../pages/rooms/roomActions';
import { whyNoRoomActions } from '../../pages/rooms/roomBoard';
import { changeSourceLabel, roomStatusMeta, taskTypeLabel } from '../../pages/rooms/roomLabels';
import AssetReportList from '../damage-reports/AssetReportList';
import ViewReportButton, { groupReportsBy } from '../damage-reports/ViewReportButton';
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
 * Thứ tự các khối theo mức khẩn: THAO TÁC lên đầu (Lễ tân cần 1–2 chạm), rồi thông tin + ghi chú,
 * tài sản trong phòng, lịch sử.
 *
 * Quản lý chi nhánh: đầu bảng báo "Chưa phân công" + nút Phân công khi phòng có việc dọn chưa có
 * người làm; ngay dưới nút đổi trạng thái là hàng công cụ Khóa / Mở khóa phòng, Thêm tài sản, Kiểm
 * kê. Các thao tác khác trên việc dọn (kiểm tra phòng, gỡ người, hủy, tạo việc dọn hằng ngày) ở màn
 * Công việc dọn phòng.
 *
 * Nút đổi trạng thái vẽ từ `room.allowedTargets` do backend tính (F2) — không có dãy nút "chuyển
 * nhanh sang trạng thái bất kỳ": Đang dọn / Chờ kiểm tra chỉ đổi qua việc dọn (BR-ROOM-02).
 *
 * @param role      { isDirector, isManager, isReception } — chỉ để ẨN/HIỆN cho dễ dùng
 * @param tasks     việc dọn ĐANG MỞ của phòng (chỉ Quản lý chi nhánh có dữ liệu này) — dùng để
 *                  biết việc nào chưa phân công
 * @param historyKey đổi giá trị để nạp lại lịch sử sau mỗi lần phòng đổi trạng thái
 * @param assetsKey  đổi giá trị để nạp lại danh sách tài sản sau khi kiểm kê
 * @param reports    báo hỏng của tài sản trong phòng (chỉ Quản lý chi nhánh) — có thì hiện nút
 *                   "Xem báo hỏng" cạnh tên tài sản
 * @param onOpenReport bấm "Xem báo hỏng" → hộp thoại xử lý báo hỏng
 */
export default function RoomBoardPanel({
  room,
  role,
  locationName,
  tasks = [],
  historyKey = 0,
  assetsKey = 0,
  busy = false,
  autoFocusClose = false,
  onClose,
  onAction,
  onAssign,
  onEditNote,
  onAddAsset,
  onAuditAssets,
  reports = [],
  onOpenReport,
}) {
  const { user } = useAuth();
  const meta = roomStatusMeta(room.status);
  const actions = roomActionsFor(room);
  // Khóa / mở khóa nằm ở hàng công cụ của Quản lý, cạnh Thêm tài sản và Kiểm kê.
  const statusActions = actions.filter((action) => action.flow !== LOCK);
  const lockAction = actions.find((action) => action.flow === LOCK);
  // Việc dọn chưa có người làm → nút "Phân công" ở đầu bảng (tối đa 1 việc mở mỗi loại — BR-HK-11).
  const unassigned = role.isManager ? tasks.filter((task) => task.status === 'UNASSIGNED') : [];

  return (
    <aside className="board-panel" aria-labelledby="board-panel-title">
      <header className={`board-panel__head room-tone--${meta.tone}`}>
        <span className="board-panel__tile" aria-hidden="true">{room.roomNumber}</span>
        <div className="board-panel__title">
          <div className="board-panel__title-row">
            <h2 id="board-panel-title">Phòng {room.roomNumber}</h2>
            <RoomStatusBadge status={room.status} />
            {/* Việc dọn chưa có người làm: báo ngay cạnh trạng thái phòng, nút Phân công ở bên phải. */}
            {unassigned.length > 0 && <TaskStatusBadge status="UNASSIGNED" />}
          </div>
          <p className="muted">
            Tầng {room.floor} · {room.roomTypeName ?? 'Chưa rõ loại'} · {room.capacity} người
          </p>
        </div>
        {unassigned.length > 0 && (
          <div className="board-panel__head-actions">
            {unassigned.map((task) => (
              <button key={task.id} type="button" className="btn btn--primary" onClick={() => onAssign(task)}>
                <span className="material-symbols-outlined" aria-hidden="true">person_add</span>
                {/* Hai việc chờ cùng lúc (sau trả phòng + hằng ngày) thì ghi rõ loại để khỏi nhầm. */}
                {unassigned.length > 1 ? `Phân công · ${taskTypeLabel(task.taskType)}` : 'Phân công'}
              </button>
            ))}
          </div>
        )}
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
          {statusActions.length > 0 && (
            <div className="board-panel__actions">
              {statusActions.map((action) => (
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
          )}
          {actions.length === 0 && (
            <p className="board-panel__why">
              <span className="material-symbols-outlined" aria-hidden="true">info</span>
              {whyNoRoomActions(room, role)}
            </p>
          )}
          {role.isManager && (
            <div className="board-panel__tools">
              {lockAction && (
                <button
                  type="button"
                  className={`btn board-tool ${lockAction.danger ? 'btn--danger' : 'btn--primary'}`}
                  disabled={busy}
                  onClick={() => onAction(lockAction)}
                >
                  <span className="material-symbols-outlined" aria-hidden="true">
                    {lockAction.danger ? 'lock' : 'lock_open'}
                  </span>
                  {lockAction.label}
                </button>
              )}
              <button type="button" className="btn btn--ghost board-tool" onClick={() => onAddAsset(room)}>
                <span className="material-symbols-outlined" aria-hidden="true">add_box</span>
                Thêm tài sản
              </button>
              <button type="button" className="btn btn--ghost board-tool" onClick={() => onAuditAssets(room)}>
                <span className="material-symbols-outlined" aria-hidden="true">fact_check</span>
                Kiểm kê
              </button>
            </div>
          )}
        </section>

        {room.status === 'UNAVAILABLE' && (
          <div className="readonly-box">
            <b>Lý do không khả dụng</b>
            <p>{room.unavailableReason || '—'}</p>
          </div>
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
          <h3 className="board-panel__heading">Tài sản trong phòng</h3>
          <RoomAssets
            key={`${room.id}-${assetsKey}`}
            roomId={room.id}
            reportsByAsset={role.isManager ? groupReportsBy(reports, 'assetId') : {}}
            onOpenReport={onOpenReport}
          />
          {/* BR-ASSET-05: Lễ tân / Dọn dẹp báo hỏng ở tab tài sản của chi tiết phòng. */}
          {canReportDamage(user) && (
            <Link className="board-panel__more" to={`/phong/${room.id}`} state={{ tab: 'assets' }}>
              Báo hỏng tài sản ›
            </Link>
          )}
        </section>

        <section className="board-panel__section">
          <h3 className="board-panel__heading">Đổi trạng thái gần đây</h3>
          <RecentHistory key={`${room.id}-${historyKey}`} roomId={room.id} />
          <Link className="board-panel__more" to={`/phong/${room.id}`} state={{ tab: 'history' }}>
            Xem chi tiết và toàn bộ lịch sử ›
          </Link>
        </section>
      </div>
    </aside>
  );
}

/** Tài sản cố định đang vận hành trong phòng (không gồm đã thanh lý — BR-ASSET-14), chỉ xem. */
function RoomAssets({ roomId, reportsByAsset, onOpenReport }) {
  const [assets, setAssets] = useState(null); // null = đang tải
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    assetService
      .getReportableAssets({ roomId })
      .then((rows) => !cancelled && setAssets(rows))
      .catch((err) => !cancelled && setError(err?.message || 'Không tải được tài sản trong phòng.'));
    return () => {
      cancelled = true;
    };
  }, [roomId]);

  if (error) return <p className="muted">{error}</p>;
  if (assets === null) return <p className="muted">Đang tải tài sản…</p>;
  return (
    <AssetReportList
      assets={assets}
      compact
      rowAction={(asset) => <ViewReportButton reports={reportsByAsset[asset.id]} onOpen={onOpenReport} />}
      emptyText="Phòng này chưa có tài sản cố định nào."
    />
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
