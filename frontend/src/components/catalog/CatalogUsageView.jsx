import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { fetchDepartmentUsage, fetchPositionUsage, fetchRoomTypeUsage } from '../../api/organization';
import RoomStatusBadge from '../rooms/RoomStatusBadge';
import { POSITION_TYPE_LABEL } from '../StaffForm';
import { CATALOG_KINDS } from './CatalogItemForm';
// Màu huy hiệu trạng thái phòng (room-tone--*) khai báo ở đây.
import '../../pages/rooms/rooms.css';

const FETCH = {
  roomType: fetchRoomTypeUsage,
  department: fetchDepartmentUsage,
  position: fetchPositionUsage,
};

const STAFF_STATUS = {
  ACTIVE: { label: 'Đang làm', tone: 'green' },
  INACTIVE: { label: 'Tạm khóa', tone: 'orange' },
  TERMINATED: { label: 'Đã nghỉ việc', tone: 'grey' },
};

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString('vi-VN') : '—';
}

/**
 * Những gì đang dùng một mục danh mục — chính là thứ chặn việc XÓA mục đó (BR-ORG-10, BR-ROOM-08),
 * và là những chỗ chịu ảnh hưởng khi SỬA. Đặt trong FormModal (rộng).
 *
 * <ul>
 *   <li>Loại phòng → các phòng thuộc loại, kể cả phòng đã xóa (vẫn giữ lịch sử nên vẫn chặn).</li>
 *   <li>Phòng ban → các vị trí thuộc phòng ban, kèm số nhân viên; bấm tên vị trí để xem nhân viên.</li>
 *   <li>Vị trí → các nhân viên giữ vị trí, kể cả người đã nghỉ việc.</li>
 * </ul>
 *
 * <p>Nút Xóa chỉ hiện khi không còn gì dùng mục này; `onBack` (nếu có) quay về phòng ban vừa xem.
 */
export default function CatalogUsageView({
  kind,
  item,
  onClose,
  onEdit,
  onDelete,
  onToggleActive,
  onOpenPosition,
  onBack,
  backLabel,
}) {
  const [usage, setUsage] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setUsage(null);
    setError('');
    FETCH[kind](item.id)
      .then((data) => !cancelled && setUsage(data))
      .catch((err) => !cancelled && setError(readErrorMessage(err, 'Không tải được danh sách đang dùng.')));
    return () => {
      cancelled = true;
    };
  }, [kind, item.id]);

  const meta = CATALOG_KINDS[kind];
  const list = usage ? (kind === 'roomType' ? usage.rooms : kind === 'department' ? usage.positions : usage.staff) ?? [] : [];
  const inUse = list.length > 0;

  return (
    <div className="form">
      <header className="form__head">
        <span className="form__icon" aria-hidden="true">
          {meta.icon}
        </span>
        <div>
          <h2>{item.name}</h2>
          <p className="muted">
            {meta.title} · {item.active ? 'Đang dùng' : 'Đã ẩn'} — những gì đang dùng mục này
          </p>
        </div>
      </header>

      {!usage && !error && <p className="state">Đang tải…</p>}
      {error && <div className="alert alert--error">{error}</div>}

      {usage && <UsageSummary kind={kind} list={list} />}

      {usage && inUse && kind === 'roomType' && <RoomTable rooms={list} />}
      {usage && inUse && kind === 'department' && <PositionTable positions={list} onOpenPosition={onOpenPosition} />}
      {usage && inUse && kind === 'position' && <StaffTable staff={list} />}

      <div className="form__actions">
        {onBack && (
          <button type="button" className="btn btn--ghost form__back" onClick={onBack}>
            ‹ {backLabel}
          </button>
        )}
        <button type="button" className="btn btn--ghost" onClick={onClose}>
          Đóng
        </button>
        <button type="button" className="btn btn--ghost" onClick={onToggleActive}>
          {item.active ? '🙈 Ẩn' : '👁 Dùng lại'}
        </button>
        <button type="button" className="btn btn--ghost" onClick={onEdit}>
          ✏️ Sửa
        </button>
        {usage && !inUse && (
          <button type="button" className="btn btn--danger" onClick={onDelete}>
            🗑 Xóa
          </button>
        )}
      </div>
    </div>
  );
}

/** Một đoạn nói thẳng: xóa được không, vì sao; sửa thì ảnh hưởng tới đâu. */
function UsageSummary({ kind, list }) {
  if (kind === 'roomType') {
    const deleted = list.filter((r) => !r.active).length;
    return list.length === 0 ? (
      <div className="alert alert--success">Chưa có phòng nào thuộc loại này — xóa được.</div>
    ) : (
      <div className="alert alert--warn">
        <b>Không xóa được:</b> {list.length} phòng đang thuộc loại này
        {deleted > 0 && ` (trong đó ${deleted} phòng đã xóa — vẫn tính vì còn lịch sử)`}. Đổi tên thì tên mới hiện ở
        mọi phòng này. Muốn ngừng dùng cho phòng mới, hãy ẩn loại phòng.
      </div>
    );
  }
  if (kind === 'department') {
    const working = list.reduce((sum, p) => sum + p.workingStaff, 0);
    return list.length === 0 ? (
      <div className="alert alert--success">Chưa có vị trí nào thuộc phòng ban này — xóa được.</div>
    ) : (
      <div className="alert alert--warn">
        <b>Không xóa được:</b> còn {list.length} vị trí thuộc phòng ban này ({working} nhân viên đang làm). Vị trí không
        đổi được phòng ban, nên phải xóa hết các vị trí trước (vị trí chỉ xóa được khi không còn ai giữ) — hoặc ẩn phòng
        ban. Ẩn phòng ban thì {list.length} vị trí này không chọn được khi tạo nhân viên.
      </div>
    );
  }
  const terminated = list.filter((s) => s.status === 'TERMINATED').length;
  return list.length === 0 ? (
    <div className="alert alert--success">Chưa có nhân viên nào giữ vị trí này — xóa được.</div>
  ) : (
    <div className="alert alert--warn">
      <b>Không xóa được:</b> {list.length} nhân viên giữ vị trí này
      {terminated > 0 && ` (trong đó ${terminated} người đã nghỉ việc — vẫn tính vì còn hồ sơ lịch sử)`}. Phòng ban của
      vị trí không đổi được; đổi loại vị trí chỉ đổi ô tick sẵn khi tạo nhân viên mới, không đổi quyền của những người
      này.
    </div>
  );
}

function RoomTable({ rooms }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Phòng</th>
            <th>Khách sạn</th>
            <th>Tầng</th>
            <th>Trạng thái</th>
          </tr>
        </thead>
        <tbody>
          {rooms.map((room) => (
            <tr key={room.id}>
              <td>
                {/* Phòng đã xóa không mở được trang chi tiết nữa. */}
                {room.active ? (
                  <Link className="link-button" to={`/phong/${room.id}`}>
                    Phòng {room.roomNumber}
                  </Link>
                ) : (
                  <b>Phòng {room.roomNumber}</b>
                )}
              </td>
              <td>{room.locationName ?? '—'}</td>
              <td>{room.floor}</td>
              <td>
                {room.active ? (
                  <RoomStatusBadge status={room.status} />
                ) : (
                  <span className="badge badge--grey">Đã xóa</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PositionTable({ positions, onOpenPosition }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Vị trí</th>
            <th>Loại</th>
            <th>Trạng thái</th>
            <th>Nhân viên</th>
          </tr>
        </thead>
        <tbody>
          {positions.map((position) => (
            <tr key={position.id}>
              <td>
                <button
                  type="button"
                  className="link-button"
                  title="Xem nhân viên giữ vị trí này"
                  onClick={() => onOpenPosition(position)}
                >
                  {position.name}
                </button>
              </td>
              <td>{POSITION_TYPE_LABEL[position.positionType] ?? position.positionType}</td>
              <td>
                <span className={`badge badge--${position.active ? 'green' : 'grey'}`}>
                  {position.active ? 'Đang dùng' : 'Đã ẩn'}
                </span>
              </td>
              <td>
                {position.workingStaff} đang làm
                {position.terminatedStaff > 0 && ` · ${position.terminatedStaff} đã nghỉ`}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StaffTable({ staff }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Nhân viên</th>
            <th>Khách sạn</th>
            <th>Ngày vào làm</th>
            <th>Trạng thái</th>
          </tr>
        </thead>
        <tbody>
          {staff.map((person) => {
            const status = STAFF_STATUS[person.status] ?? { label: person.status, tone: 'grey' };
            return (
              <tr key={person.id}>
                <td>
                  <div className="cell-manager">
                    <b>{person.fullName}</b>
                    <small>{person.email}</small>
                  </div>
                </td>
                <td>{person.locationName ?? '—'}</td>
                <td>{formatDate(person.startWorkDate)}</td>
                <td>
                  <span className={`badge badge--${status.tone}`}>{status.label}</span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
