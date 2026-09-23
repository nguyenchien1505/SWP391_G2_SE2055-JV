import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { fetchAllRooms } from '../../api/rooms';
import LockRoomModal from '../../components/rooms/LockRoomModal';
import RoomActionDialog from '../../components/rooms/RoomActionDialog';
import RoomActionSheet from '../../components/rooms/RoomActionSheet';
import RoomCard from '../../components/rooms/RoomCard';
import RoomStatusFilter from '../../components/rooms/RoomStatusFilter';
import { formatClock } from './format';
import { LOCK, roomActionsFor, statusChangedMessage } from './roomActions';
import { compareNatural } from './roomLabels';
import { useRoomAction } from './useRoomAction';
import { useTenantLocations } from './useRoomLookups';
import './rooms.css';

/**
 * Gom phòng theo tầng, tầng và số phòng đều sắp kiểu "tự nhiên" (2 trước 10).
 * @returns [{ floor: '1', rooms: [...] }, …]
 */
function groupByFloor(rooms) {
  const byFloor = new Map();
  for (const room of rooms) {
    if (!byFloor.has(room.floor)) byFloor.set(room.floor, []);
    byFloor.get(room.floor).push(room);
  }
  return [...byFloor.entries()]
    .sort(([a], [b]) => compareNatural(a, b))
    .map(([floor, list]) => ({
      floor,
      rooms: list.sort((a, b) => compareNatural(a.roomNumber, b.roomNumber)),
    }));
}

/** Đếm theo trạng thái trên chính dữ liệu đã tải — sơ đồ đã có đủ phòng nên không gọi thêm API. */
function countByStatus(rooms) {
  const counts = {};
  for (const room of rooms) counts[room.status] = (counts[room.status] ?? 0) + 1;
  return counts;
}

/**
 * S-06 Sơ đồ phòng — toàn bộ phòng của MỘT khách sạn dạng lưới thẻ màu, nhóm theo tầng
 * (design.md màn 30 ⭐). Người dùng chính: Manager và Lễ tân; Dọn dẹp cũng xem được.
 *
 * Khác S-02: tải TẤT CẢ phòng một lần rồi lọc trạng thái ngay trên trình duyệt, vì sơ đồ cần
 * thấy cả khách sạn cùng lúc và lọc phải tức thì.
 *
 * Bấm thẻ: nếu người dùng có thao tác trên phòng đó (theo `room.allowedTargets` — Manager
 * khóa/mở khóa ở F2, Lễ tân đặt/nhận/trả phòng ở F4) thì mở bảng thao tác; không có thì vào
 * thẳng trang chi tiết như F1. Đổi trạng thái xong chỉ thay đúng thẻ đó bằng phòng trong
 * response — không tải lại cả sơ đồ, để Lễ tân đang xếp khách không bị mất chỗ đang xem.
 *
 * Đây là màn hình S-15 của Lễ tân, làm mobile-first: quầy lễ tân thao tác trên điện thoại /
 * máy tính bảng nhiều hơn trên máy bàn.
 */
export default function RoomBoardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const isDirector = user?.role === 'DIRECTOR';

  // Giám đốc chọn khách sạn cần xem. Manager/Staff không chọn: backend tự ép về Location của họ.
  const locations = useTenantLocations(isDirector);
  const [locationId, setLocationId] = useState('');

  const [rooms, setRooms] = useState([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [updatedAt, setUpdatedAt] = useState(null);

  const [banner, setBanner] = useState(null); // { type, text }
  const [sheetRoom, setSheetRoom] = useState(null); // phòng đang mở bảng thao tác

  /** Thay đúng thẻ vừa đổi bằng phòng trong response; `before` để dựng câu báo cho đúng ngữ cảnh. */
  const handleChanged = useCallback((updated, before) => {
    setBanner({ type: 'success', text: statusChangedMessage(before, updated) });
    setRooms((prev) => prev.map((room) => (room.id === updated.id ? updated : room)));
  }, []);

  const showError = useCallback((text) => setBanner({ type: 'error', text }), []);

  const action = useRoomAction({ onChanged: handleChanged, onError: showError });

  // Giám đốc: mặc định mở khách sạn đầu tiên — sơ đồ luôn là của MỘT khách sạn.
  useEffect(() => {
    if (isDirector && !locationId && locations?.length > 0) {
      setLocationId(locations[0].id);
    }
  }, [isDirector, locationId, locations]);

  const waitingForLocation = isDirector && !locationId;

  const load = useCallback(async () => {
    if (waitingForLocation) return;
    setLoading(true);
    setLoadError('');
    try {
      setRooms(await fetchAllRooms({ locationId: isDirector ? locationId : undefined }));
      setUpdatedAt(new Date());
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được sơ đồ phòng.'));
    } finally {
      setLoading(false);
    }
  }, [waitingForLocation, isDirector, locationId]);

  useEffect(() => {
    load();
  }, [load]);

  function handleSelectRoom(room) {
    // Không có thao tác nào (Giám đốc, nhân viên dọn, hoặc phòng đang ở trạng thái do task quyết
    // định) thì bấm thẻ = xem chi tiết, giống F1.
    if (roomActionsFor(room).length === 0) {
      navigate(`/phong/${room.id}`);
      return;
    }
    setSheetRoom(room);
  }

  function handlePickAction(picked) {
    const room = sheetRoom;
    setSheetRoom(null);       // đóng bảng thao tác trước, để hộp thoại xác nhận không chồng lên
    setBanner(null);
    action.start(room, picked);
  }

  const counts = useMemo(() => countByStatus(rooms), [rooms]);
  const floors = useMemo(
    () => groupByFloor(statusFilter ? rooms.filter((room) => room.status === statusFilter) : rooms),
    [rooms, statusFilter],
  );

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Vận hành › Sơ đồ phòng</p>
          <h1>Sơ đồ phòng</h1>
        </div>
      </div>

      {banner && (
        <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="status">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">
            ×
          </button>
        </div>
      )}

      <RoomStatusFilter
        variant="chips"
        counts={counts}
        total={rooms.length}
        selected={statusFilter}
        onSelect={setStatusFilter}
      />

      <section className="panel">
        <div className="toolbar rooms-toolbar">
          {isDirector && (
            <select
              value={locationId}
              onChange={(e) => setLocationId(e.target.value)}
              aria-label="Chọn khách sạn"
            >
              {(locations ?? []).map((location) => (
                <option key={location.id} value={location.id}>
                  {location.name}
                </option>
              ))}
            </select>
          )}
          <button type="button" className="btn btn--ghost" onClick={load} disabled={loading}>
            {loading ? 'Đang tải…' : '↻ Làm mới'}
          </button>
          {updatedAt && <span className="muted">Cập nhật lúc {formatClock(updatedAt)}</span>}
        </div>

        {loadError && (
          <div className="alert alert--error" role="alert">
            {loadError}
          </div>
        )}

        {isDirector && locations?.length === 0 && (
          <div className="state state--empty">
            <p>Chưa có khách sạn nào trong chuỗi.</p>
          </div>
        )}

        {!loading && !loadError && !waitingForLocation && floors.length === 0 && (
          <div className="state state--empty">
            <p>{statusFilter ? 'Không có phòng nào ở trạng thái này.' : 'Khách sạn chưa có phòng nào.'}</p>
          </div>
        )}

        {floors.map(({ floor, rooms: floorRooms }) => (
          <section className="floor-group" key={floor}>
            <h2 className="floor-group__title">
              Tầng {floor} <small>{floorRooms.length} phòng</small>
            </h2>
            <div className="room-grid">
              {floorRooms.map((room) => (
                <RoomCard key={room.id} room={room} onSelect={handleSelectRoom} />
              ))}
            </div>
          </section>
        ))}
      </section>

      {sheetRoom && (
        <RoomActionSheet
          room={sheetRoom}
          actions={roomActionsFor(sheetRoom)}
          onPick={handlePickAction}
          onOpenDetail={() => navigate(`/phong/${sheetRoom.id}`)}
          onClose={() => setSheetRoom(null)}
        />
      )}

      {/* Khóa / mở khóa có màn riêng (chọn đích + lý do bắt buộc); các bước của Lễ tân dùng
          hộp thoại chung. Cả hai cùng kết thúc ở action.finish. */}
      {action.pending?.action.flow === LOCK && (
        <LockRoomModal room={action.pending.room} onClose={action.cancel} onChanged={action.finish} />
      )}
      {action.pending && action.pending.action.flow !== LOCK && (
        <RoomActionDialog
          room={action.pending.room}
          action={action.pending.action}
          onDone={action.finish}
          onClose={action.cancel}
        />
      )}
    </div>
  );
}
