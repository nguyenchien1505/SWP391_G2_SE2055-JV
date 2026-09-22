import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { fetchRoomStatusSummary, fetchRooms } from '../../api/rooms';
import RoomCard from '../../components/rooms/RoomCard';
import RoomStatusBadge from '../../components/rooms/RoomStatusBadge';
import RoomStatusFilter from '../../components/rooms/RoomStatusFilter';
import { ROOM_STATUS_ORDER, roomStatusMeta } from './roomLabels';
import { useRoomTypes, useTenantLocations } from './useRoomLookups';
import './rooms.css';

const PAGE_SIZE = 20;
/** Ô "Tầng" là ô gõ chữ: chờ người dùng ngừng gõ 0,4 giây rồi mới gọi API, tránh gọi mỗi phím. */
const FLOOR_DEBOUNCE_MS = 400;
const NO_FILTER = { locationId: '', status: '', floor: '', roomTypeId: '' };

/**
 * S-02 Danh sách phòng — Giám đốc (toàn chuỗi) và Manager (khách sạn của mình). RM-06.
 *
 * Mọi bộ lọc chạy ở SERVER (BR-ROOM-06): đổi bộ lọc là gọi lại API, không lọc trên dữ liệu đã
 * tải — điện thoại không phải tải toàn bộ phòng. Dưới 860px, bảng tự đổi thành lưới thẻ (CSS).
 */
export default function RoomsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const isDirector = user?.role === 'DIRECTOR';

  const locations = useTenantLocations(isDirector);
  const roomTypes = useRoomTypes(true);

  const [filters, setFilters] = useState(NO_FILTER);
  const [floorInput, setFloorInput] = useState('');
  const [page, setPage] = useState(0);

  const [pageData, setPageData] = useState(null);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  /** Đổi bất kỳ bộ lọc nào cũng quay về trang đầu — trang 3 của bộ lọc cũ không còn ý nghĩa. */
  function applyFilter(field, value) {
    setFilters((prev) => ({ ...prev, [field]: value }));
    setPage(0);
  }

  function clearFilters() {
    setFilters(NO_FILTER);
    setFloorInput('');
    setPage(0);
  }

  // Ô tầng: chỉ đẩy vào bộ lọc sau khi người dùng ngừng gõ.
  useEffect(() => {
    const floor = floorInput.trim();
    if (floor === filters.floor) return undefined;
    const timer = setTimeout(() => applyFilter('floor', floor), FLOOR_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [floorInput, filters.floor]);

  // Tải danh sách mỗi khi bộ lọc hoặc trang đổi. Cờ `cancelled` bỏ qua phản hồi của lần gọi cũ
  // nếu người dùng đổi bộ lọc nhanh hơn tốc độ mạng — tránh bảng hiện kết quả của bộ lọc trước.
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError('');
    fetchRooms({ ...filters, page, size: PAGE_SIZE })
      .then((data) => !cancelled && setPageData(data))
      .catch((err) => !cancelled && setLoadError(readErrorMessage(err, 'Không tải được danh sách phòng.')))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [filters, page]);

  // Thẻ số liệu chỉ phụ thuộc khách sạn đang xem — không đổi khi lọc trạng thái/tầng/loại.
  useEffect(() => {
    let cancelled = false;
    fetchRoomStatusSummary({ locationId: filters.locationId })
      .then((data) => !cancelled && setSummary(data))
      .catch(() => !cancelled && setSummary(null));
    return () => {
      cancelled = true;
    };
  }, [filters.locationId]);

  const rooms = pageData?.content ?? [];
  const hasFilter = Object.values(filters).some(Boolean) || floorInput.trim() !== '';

  /** id → tên khách sạn, chỉ Giám đốc cần (cột "Khách sạn"). */
  const locationNames = useMemo(
    () => Object.fromEntries((locations ?? []).map((location) => [location.id, location.name])),
    [locations],
  );

  const openRoom = (room) => navigate(`/phong/${room.id}`);

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Vận hành › Danh sách phòng</p>
          <h1>Danh sách phòng</h1>
        </div>
      </div>

      <RoomStatusFilter
        counts={summary?.counts}
        total={summary?.total}
        selected={filters.status}
        onSelect={(status) => applyFilter('status', status)}
      />

      <section className="panel">
        <div className="toolbar rooms-toolbar">
          {isDirector && (
            <select
              value={filters.locationId}
              onChange={(e) => applyFilter('locationId', e.target.value)}
              aria-label="Lọc theo khách sạn"
            >
              <option value="">Khách sạn: Tất cả</option>
              {(locations ?? []).map((location) => (
                <option key={location.id} value={location.id}>
                  {location.name}
                </option>
              ))}
            </select>
          )}
          <select
            value={filters.status}
            onChange={(e) => applyFilter('status', e.target.value)}
            aria-label="Lọc theo trạng thái"
          >
            <option value="">Trạng thái: Tất cả</option>
            {ROOM_STATUS_ORDER.map((status) => (
              <option key={status} value={status}>
                {roomStatusMeta(status).label}
              </option>
            ))}
          </select>
          <input
            type="search"
            value={floorInput}
            onChange={(e) => setFloorInput(e.target.value)}
            placeholder="Tầng: 2, G, B1…"
            aria-label="Lọc theo tầng"
          />
          <select
            value={filters.roomTypeId}
            onChange={(e) => applyFilter('roomTypeId', e.target.value)}
            aria-label="Lọc theo loại phòng"
          >
            <option value="">Loại phòng: Tất cả</option>
            {roomTypes.map((type) => (
              <option key={type.id} value={type.id}>
                {type.name}
                {type.active ? '' : ' (đã ẩn)'}
              </option>
            ))}
          </select>
          {hasFilter && (
            <button type="button" className="btn btn--ghost" onClick={clearFilters}>
              Xóa bộ lọc
            </button>
          )}
        </div>

        <div className="panel__head">
          <h2>{isDirector && !filters.locationId ? 'Phòng trong toàn chuỗi' : 'Phòng của khách sạn'}</h2>
          <span className="chip">{pageData?.totalElements ?? 0} phòng</span>
        </div>

        {/* Chỉ hiện "Đang tải" ở lần đầu; các lần sau giữ bảng cũ cho tới khi có dữ liệu mới. */}
        {loading && !pageData && <p className="state">Đang tải dữ liệu…</p>}
        {loadError && (
          <div className="alert alert--error" role="alert">
            {loadError}
          </div>
        )}

        {!loading && !loadError && rooms.length === 0 && (
          <div className="state state--empty">
            <p>{hasFilter ? 'Không có phòng nào khớp bộ lọc.' : 'Chưa có phòng nào.'}</p>
            {!hasFilter && isDirector && (
              <p className="muted">Phòng do Giám đốc tạo cho từng khách sạn (BR-ROOM-04).</p>
            )}
          </div>
        )}

        {rooms.length > 0 && (
          <>
            <div className="table-wrap rooms-table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Số phòng</th>
                    <th>Tầng</th>
                    <th>Loại phòng</th>
                    <th>Sức chứa</th>
                    <th>Trạng thái</th>
                    {isDirector && <th>Khách sạn</th>}
                  </tr>
                </thead>
                <tbody>
                  {rooms.map((room) => (
                    <tr key={room.id} className="is-clickable" onClick={() => openRoom(room)}>
                      <td>
                        {/* Link để dùng được bằng bàn phím / mở tab mới; cả dòng vẫn bấm được. */}
                        <Link
                          className="room-number-link"
                          to={`/phong/${room.id}`}
                          onClick={(e) => e.stopPropagation()}
                        >
                          {room.roomNumber}
                        </Link>
                      </td>
                      <td>{room.floor}</td>
                      <td>{room.roomTypeName ?? '—'}</td>
                      <td>{room.capacity} người</td>
                      <td>
                        <RoomStatusBadge status={room.status} />
                      </td>
                      {isDirector && <td>{locationNames[room.locationId] ?? '—'}</td>}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="room-grid rooms-cards">
              {rooms.map((room) => (
                <RoomCard key={room.id} room={room} onSelect={openRoom} showFloor />
              ))}
            </div>
          </>
        )}

        <div className="panel__foot">
          <span className="muted">Nhấn vào một phòng để xem chi tiết.</span>
          <div className="pager">
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              disabled={(pageData?.number ?? 0) === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              aria-label="Trang trước"
            >
              ‹
            </button>
            <span>
              Trang {(pageData?.number ?? 0) + 1} / {Math.max(1, pageData?.totalPages ?? 1)}
            </span>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              disabled={(pageData?.number ?? 0) + 1 >= (pageData?.totalPages ?? 1)}
              onClick={() => setPage((p) => p + 1)}
              aria-label="Trang sau"
            >
              ›
            </button>
          </div>
        </div>
      </section>

      <div className="note">
        <span aria-hidden="true">ⓘ</span>
        <div>
          <b>Quy định về phòng</b>
          <p>
            Chỉ Giám đốc được thêm, sửa, xóa phòng; Quản lý chi nhánh chỉ cập nhật thông tin vận
            hành (BR-ROOM-04). Phòng mới tạo luôn ở trạng thái <b>Chờ dọn</b> và tự sinh việc dọn
            phòng (BR-ROOM-10). Trạng thái phòng chỉ thay đổi theo đúng quy trình: Lễ tân nhận / trả
            phòng, Quản lý khóa phòng và kiểm tra phòng sau khi dọn (BR-ROOM-02).
          </p>
        </div>
      </div>
    </div>
  );
}
