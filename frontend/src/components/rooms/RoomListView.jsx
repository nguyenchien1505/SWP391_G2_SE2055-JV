import { Link } from 'react-router-dom';
import RoomCard from './RoomCard';
import RoomRowActions from './RoomRowActions';
import RoomStatusBadge from './RoomStatusBadge';

/**
 * Dạng "Danh sách" của Sơ đồ phòng (S-06) — thay cho lưới thẻ theo tầng khi người dùng bấm nút
 * "Danh sách". Lấy từ phần bảng của màn Danh sách phòng (S-02) cũ: cùng cột, cùng nút thao tác
 * trên từng dòng (`RoomRowActions`). Dữ liệu và bộ lọc dùng chung với sơ đồ, không gọi API riêng.
 *
 * Dạng này chiếm hết chiều ngang, không có bảng chi tiết bên cạnh: bấm một dòng (`onSelect`) →
 * trang chi tiết phòng (S-04), như màn Danh sách phòng cũ. Dưới 860px, bảng tự đổi thành lưới thẻ
 * (CSS `rooms-table-wrap` / `rooms-cards`).
 *
 * @param rowActions { isDirector, isManager, onEdit, onDelete, onEditNote, onLock }
 */
export default function RoomListView({ rooms, selectedId, onSelect, rowActions }) {
  return (
    <section className="panel room-list-view">
      <div className="table-wrap rooms-table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Số phòng</th>
              <th>Tầng</th>
              <th>Loại phòng</th>
              <th className="col-optional">Sức chứa</th>
              <th>Trạng thái</th>
              <th className="col-actions">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {rooms.map((room) => (
              <tr
                key={room.id}
                className={`is-clickable ${room.id === selectedId ? 'is-editing' : ''}`}
                onClick={() => onSelect(room)}
              >
                <td>
                  {/* Link để dùng được bằng bàn phím / mở tab mới; cả dòng vẫn bấm được. */}
                  <Link className="room-number-link" to={`/phong/${room.id}`} onClick={(e) => e.stopPropagation()}>
                    {room.roomNumber}
                  </Link>
                </td>
                <td>{room.floor}</td>
                <td>{room.roomTypeName ?? '—'}</td>
                <td className="col-optional">{room.capacity} người</td>
                <td>
                  <RoomStatusBadge status={room.status} />
                </td>
                <td className="col-actions">
                  <RoomRowActions room={room} {...rowActions} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="room-grid rooms-cards">
        {rooms.map((room) => (
          <div key={room.id} className="room-card-item">
            <RoomCard room={room} onSelect={onSelect} showFloor selected={room.id === selectedId} />
            <RoomRowActions room={room} {...rowActions} />
          </div>
        ))}
      </div>
    </section>
  );
}
