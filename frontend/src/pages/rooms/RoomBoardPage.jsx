import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { hasPermission } from '../../permissions';
import { readErrorMessage } from '../../api/client';
import { fetchTasks } from '../../api/housekeeping';
import { createRoom, deleteRoom, fetchAllRooms, fetchRoom, updateRoom } from '../../api/rooms';
import { fetchStaffDirectory } from '../../api/users';
import AssetAuditModal from '../../components/AssetAuditModal';
import ConfirmDialog from '../../components/ConfirmDialog';
import FormModal from '../../components/FormModal';
import { BatchCreateAssetsModal } from '../../components/modals/BatchCreateAssetsModal';
import { DamageReportModal } from '../../components/modals/DamageReportModal';
import { groupReportsBy } from '../../components/damage-reports/ViewReportButton';
import { assetService } from '../../services/assetApi';
import AssignTaskModal from '../../components/rooms/AssignTaskModal';
import LockRoomModal from '../../components/rooms/LockRoomModal';
import RoomActionDialog from '../../components/rooms/RoomActionDialog';
import RoomBoardPanel from '../../components/rooms/RoomBoardPanel';
import RoomCard from '../../components/rooms/RoomCard';
import RoomForm from '../../components/rooms/RoomForm';
import RoomListView from '../../components/rooms/RoomListView';
import RoomNoteModal from '../../components/rooms/RoomNoteModal';
import RoomStatusFilter from '../../components/rooms/RoomStatusFilter';
import { formatClock } from './format';
import { DELETE_ROOM_WARNING, LOCK, roomActionsFor, statusChangedMessage } from './roomActions';
import {
  countByStatus,
  groupByFloor,
  indexOpenTasks,
  matchesBoardFilter,
  roomCardHint,
  roomTypeNamesOf,
} from './roomBoard';
import { ROOM_STATUS_ORDER, roomStatusMeta } from './roomLabels';
import { useRoomAction } from './useRoomAction';
import { useRoomTypes, useTenantLocations } from './useRoomLookups';
import './rooms.css';

/** Từ khổ này bảng chi tiết nằm HẲN bên phải lưới; nhỏ hơn thì mở đè lên (ngăn kéo / trượt từ dưới). */
const WIDE_QUERY = '(min-width: 1200px)';

/** Ba trạng thái "đang mở" của một việc dọn (BR-HK-06) — đủ để biết phòng nào đang chờ gì. */
const OPEN_TASK_STATUSES = ['UNASSIGNED', 'IN_PROGRESS', 'PENDING_INSPECTION'];

const NO_FILTER = { status: '', floor: '', roomType: '', search: '' };

/**
 * S-06 Sơ đồ phòng / S-15 Sơ đồ phòng của Lễ tân — toàn bộ phòng của MỘT khách sạn dạng lưới thẻ
 * màu, nhóm theo tầng (design.md màn 30 ⭐). Bố cục theo bản mẫu Stitch, đã lọc theo nghiệp vụ:
 * không có dữ liệu khách / đặt phòng (BR-ROOM-09, BR-OUT-01), không có "đồng bộ thời gian thực"
 * (chỉ có nút Làm mới), không có nút chuyển nhanh sang trạng thái bất kỳ (BR-ROOM-02).
 *
 * Tải TẤT CẢ phòng một lần rồi lọc ngay trên trình duyệt (trạng thái, tầng, loại, số phòng): sơ đồ
 * cần thấy cả khách sạn và lọc phải tức thì.
 *
 * Bấm thẻ → bảng chi tiết (`RoomBoardPanel`): thao tác đổi trạng thái lấy từ `room.allowedTargets`
 * (Quản lý khóa / mở khóa — F2; Lễ tân đặt / nhận / trả phòng — F4). Riêng Quản lý chi nhánh thấy
 * thêm việc dọn đang mở của phòng để phân công, kiểm tra, gỡ người ngay tại sơ đồ (F5, F6).
 *
 * Đổi trạng thái xong chỉ thay đúng thẻ đó bằng phòng trong response — không tải lại cả sơ đồ,
 * để Lễ tân đang xếp khách không bị mất chỗ đang xem.
 */
export default function RoomBoardPage() {
  const { user } = useAuth();
  const isDirector = user?.role === 'DIRECTOR';
  const isManager = user?.role === 'MANAGER';
  const isReception = user?.role === 'STAFF' && hasPermission(user, 'RECEPTION');
  const role = useMemo(() => ({ isDirector, isManager, isReception }), [isDirector, isManager, isReception]);

  const wide = useMediaQuery(WIDE_QUERY);
  // Đọc trong callback sau khi gọi API xong — ref để không phải tạo lại callback mỗi lần đổi khổ.
  const wideRef = useRef(wide);
  wideRef.current = wide;

  // Giám đốc chọn khách sạn cần xem. Manager/Staff không chọn: backend tự ép về Location của họ.
  const locations = useTenantLocations(isDirector);
  const [locationId, setLocationId] = useState('');

  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [updatedAt, setUpdatedAt] = useState(null);
  // Xóa phòng ở trang chi tiết (S-04) xong thì quay về đây kèm câu báo (react-router state).
  const { state: navigationState, pathname } = useLocation();
  const navigate = useNavigate();
  const [banner, setBanner] = useState(navigationState?.banner ?? null); // { type, text }

  // Kiểu xem nằm trên URL (?view=list) để quay lại từ trang chi tiết vẫn đúng dạng đang xem.
  const [searchParams, setSearchParams] = useSearchParams();
  const view = searchParams.get('view') === 'list' && (isDirector || isManager) ? 'list' : 'grid';
  // Dạng danh sách không có bảng chi tiết bên cạnh (bảng chiếm hết chiều ngang) → bỏ phòng đang chọn.
  const setView = (next) => {
    setSelectedId(null);
    setSearchParams(next === 'list' ? { view: 'list' } : {}, { replace: true });
  };
  // Bảng chi tiết phòng chỉ đi cùng dạng sơ đồ.
  const showPanel = view === 'grid';

  // Giám đốc: thêm / sửa phòng (form S-03 trong hộp thoại) và xóa phòng ngay trên danh sách.
  const roomTypes = useRoomTypes(isDirector);
  const [roomForm, setRoomForm] = useState(null); // null | { mode: 'create' } | { mode: 'edit', room }
  const [deleting, setDeleting] = useState(null);

  const [filters, setFilters] = useState(NO_FILTER);
  const [collapsedFloors, setCollapsedFloors] = useState(() => new Set());
  const [selectedId, setSelectedId] = useState(null);
  const [historyKey, setHistoryKey] = useState(0);
  // Tăng sau mỗi lần kiểm kê → bảng chi tiết nạp lại danh sách tài sản của phòng.
  const [assetsKey, setAssetsKey] = useState(0);

  // Chỉ Quản lý chi nhánh: việc dọn đang mở theo phòng + tên người làm (DTO chỉ có id).
  const [tasksByRoom, setTasksByRoom] = useState({});
  const [staffNames, setStaffNames] = useState({});

  const [assigning, setAssigning] = useState(null);
  const [noteRoom, setNoteRoom] = useState(null);
  const [auditing, setAuditing] = useState(null);
  const [addingAssetTo, setAddingAssetTo] = useState(null);
  // Chỉ Quản lý chi nhánh: báo hỏng theo phòng (roomId → danh sách) + phiếu đang mở trong hộp thoại.
  const [reportsByRoom, setReportsByRoom] = useState({});
  const [openReportId, setOpenReportId] = useState(null);

  const replaceRoom = useCallback((updated) => {
    setRooms((prev) => prev.map((room) => (room.id === updated.id ? updated : room)));
  }, []);

  /**
   * Lỗi tải việc dọn không chặn sơ đồ: thẻ chỉ thiếu dòng gợi ý, thao tác đổi trạng thái vẫn dùng
   * được. Vai trò khác không gọi — nhân viên gọi API này chỉ thấy việc của chính mình.
   */
  const loadTasks = useCallback(async () => {
    if (!isManager) return;
    try {
      const pages = await Promise.all(OPEN_TASK_STATUSES.map((status) => fetchTasks({ status, size: 100 })));
      setTasksByRoom(indexOpenTasks(pages.flatMap((page) => page.content ?? [])));
    } catch {
      setTasksByRoom({});
    }
  }, [isManager]);

  /** Báo hỏng của cả khách sạn, một lần gọi — lỗi thì chỉ thiếu nút "Xem báo hỏng", sơ đồ vẫn dùng được. */
  const loadReports = useCallback(async () => {
    if (!isManager) return;
    try {
      setReportsByRoom(groupReportsBy(await assetService.getAllDamageReports(), 'roomId'));
    } catch {
      setReportsByRoom({});
    }
  }, [isManager]);

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
      const [list] = await Promise.all([
        fetchAllRooms({ locationId: isDirector ? locationId : undefined }),
        loadTasks(),
        loadReports(),
      ]);
      setRooms(list);
      setUpdatedAt(new Date());
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được sơ đồ phòng.'));
    } finally {
      setLoading(false);
    }
  }, [waitingForLocation, isDirector, locationId, loadTasks, loadReports]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!isManager) return undefined;
    let cancelled = false;
    fetchStaffDirectory()
      .then((data) => {
        if (cancelled) return;
        const list = data.content ?? data ?? [];
        setStaffNames(Object.fromEntries(list.map((person) => [person.id, person.fullName])));
      })
      .catch(() => !cancelled && setStaffNames({}));
    return () => {
      cancelled = true;
    };
  }, [isManager]);

  /**
   * Sau mỗi thao tác: tải lại việc dọn (đổi trạng thái phòng có thể sinh / hủy việc — BR-HK-01,
   * BR-HK-09, BR-HK-10) và lịch sử trong bảng chi tiết. Màn hẹp thì đóng bảng chi tiết để câu
   * báo kết quả và thẻ vừa đổi màu hiện ra ngay trước mắt.
   */
  const afterChange = useCallback(() => {
    loadTasks();
    setHistoryKey((key) => key + 1);
    if (!wideRef.current) setSelectedId(null);
  }, [loadTasks]);

  /** Phòng đổi trạng thái do thao tác trên VIỆC DỌN — response không có phòng nên tải lại đúng phòng đó. */
  const refreshRoom = useCallback(
    (roomId) => fetchRoom(roomId).then(replaceRoom).catch(() => load()),
    [replaceRoom, load],
  );

  const handleStatusChanged = useCallback(
    (updated, before) => {
      setBanner({ type: 'success', text: statusChangedMessage(before, updated) });
      replaceRoom(updated);
      afterChange();
    },
    [replaceRoom, afterChange],
  );

  // Đọc câu báo một lần rồi bỏ khỏi lịch sử duyệt, để F5 không hiện lại thông báo cũ.
  useEffect(() => {
    if (navigationState?.banner) navigate(pathname + window.location.search, { replace: true });
  }, [navigationState, pathname, navigate]);

  const showError = useCallback((text) => setBanner({ type: 'error', text }), []);
  const action = useRoomAction({ onChanged: handleStatusChanged, onError: showError });

  function selectLocation(id) {
    setLocationId(id);
    setSelectedId(null);
    // Tầng và loại phòng của khách sạn khác chưa chắc tồn tại.
    setFilters((prev) => ({ ...prev, floor: '', roomType: '' }));
  }

  const setFilter = (field, value) => setFilters((prev) => ({ ...prev, [field]: value }));

  function toggleFloor(floor) {
    setCollapsedFloors((prev) => {
      const next = new Set(prev);
      if (next.has(floor)) next.delete(floor);
      else next.add(floor);
      return next;
    });
  }

  function finishAssign(updated) {
    setAssigning(null);
    setBanner({
      type: 'success',
      text: `Đã giao việc dọn phòng ${updated.roomNumber}.`
        + (updated.taskType === 'CHECKOUT' ? ' Phòng chuyển sang «Đang dọn».' : ''),
    });
    refreshRoom(updated.roomId);
    afterChange();
  }

  function saveNote(updated) {
    setNoteRoom(null);
    replaceRoom(updated);
    setBanner({ type: 'success', text: `Đã lưu ghi chú vận hành của phòng ${updated.roomNumber}.` });
  }

  /**
   * RM-02 / RM-03 — Giám đốc thêm / sửa phòng. Trả câu lỗi cho form tự hiện, hoặc `null` khi thành
   * công; câu lỗi của backend đã nêu rõ vi phạm gì (trùng số phòng, hết hạn mức…).
   */
  async function submitRoomForm(payload) {
    const editing = roomForm?.mode === 'edit' ? roomForm.room : null;
    try {
      const saved = editing ? await updateRoom(editing.id, payload) : await createRoom(payload);
      setRoomForm(null);
      setBanner({
        type: 'success',
        text: editing
          ? `Đã cập nhật phòng ${saved.roomNumber}.`
          : `Đã thêm phòng ${saved.roomNumber}. Phòng đang ở trạng thái «Chờ dọn» và đã có một `
            + 'việc dọn phòng chờ phân công.',
      });
      load();
      return null;
    } catch (err) {
      return readErrorMessage(err, editing ? 'Không lưu được thông tin phòng.' : 'Không tạo được phòng.');
    }
  }

  /** RM-05 — bị backend chặn (còn việc dọn, còn tài sản…) thì hiện nguyên văn câu lỗi. */
  async function confirmDelete() {
    const room = deleting;
    setDeleting(null);
    try {
      await deleteRoom(room.id);
      setBanner({ type: 'success', text: `Đã xóa phòng ${room.roomNumber}.` });
      if (selectedId === room.id) setSelectedId(null);
      load();
    } catch (err) {
      showError(readErrorMessage(err, 'Không xóa được phòng.'));
    }
  }

  /** Nút thao tác trên từng dòng của dạng danh sách — cùng bộ với màn Danh sách phòng cũ. */
  const rowActions = {
    isDirector,
    isManager,
    onEdit: (room) => {
      setBanner(null);
      setRoomForm({ mode: 'edit', room });
    },
    onDelete: setDeleting,
    onEditNote: setNoteRoom,
    // Khóa / mở khóa đi qua cùng luồng với bảng chi tiết (hộp thoại S-08).
    onLock: (room) => {
      setBanner(null);
      action.start(room, roomActionsFor(room).find((item) => item.flow === LOCK));
    },
  };

  function finishAudit(room, changedCount) {
    setAuditing(null);
    setAssetsKey((key) => key + 1);
    setBanner({
      type: 'success',
      text: `Đã lưu kiểm kê phòng ${room.roomNumber}: cập nhật tình trạng ${changedCount} tài sản.`,
    });
  }

  const selectedRoom = rooms.find((room) => room.id === selectedId) ?? null;
  const modalOpen = Boolean(
    assigning || noteRoom || auditing || addingAssetTo || openReportId || roomForm || deleting
      || action.pending,
  );

  // Esc đóng bảng chi tiết khi nó đang đè lên trang — trừ lúc có hộp thoại mở (Esc là của hộp thoại).
  useEffect(() => {
    if (wide || !selectedRoom || modalOpen) return undefined;
    const onKey = (e) => e.key === 'Escape' && setSelectedId(null);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [wide, selectedRoom, modalOpen]);

  const counts = useMemo(() => countByStatus(rooms), [rooms]);
  const allFloors = useMemo(() => groupByFloor(rooms), [rooms]);
  const typeNames = useMemo(() => roomTypeNamesOf(rooms), [rooms]);
  const visibleFloors = useMemo(
    () => groupByFloor(rooms.filter((room) => matchesBoardFilter(room, filters))),
    [rooms, filters],
  );
  const hasFilter = Boolean(filters.status || filters.floor || filters.roomType || filters.search.trim());
  const locationName = isDirector ? (locations ?? []).find((item) => item.id === locationId)?.name : null;

  const panelProps = selectedRoom && {
    room: selectedRoom,
    role,
    locationName,
    tasks: tasksByRoom[selectedRoom.id] ?? [],
    historyKey,
    assetsKey,
    busy: action.running,
    onClose: () => setSelectedId(null),
    onAction: (picked) => {
      setBanner(null);
      action.start(selectedRoom, picked);
    },
    onAssign: setAssigning,
    onEditNote: setNoteRoom,
    // Hộp thoại thêm tài sản hàng loạt, chọn sẵn phòng này làm vị trí đặt.
    onAddAsset: setAddingAssetTo,
    reports: reportsByRoom[selectedRoom.id] ?? [],
    onOpenReport: (report) => setOpenReportId(report.id),
    onAuditAssets: (room) => {
      setBanner(null);
      setAuditing(room);
    },
  };

  return (
    <div className="page room-board">
      <div className="page__head board-head">
        <div className="board-head__main">
          {/* Đường dẫn bên trái, thông tin tóm tắt dồn về cuối cùng hàng. */}
          <div className="board-head__crumbs">
            <p className="breadcrumb">Vận hành › Sơ đồ phòng</p>
            <p className="board-head__sub">
              {rooms.length} phòng
              {locationName ? ` · ${locationName}` : ''}
              {updatedAt ? ` · cập nhật lúc ${formatClock(updatedAt)}` : ''}
            </p>
          </div>
          <h1>Sơ đồ phòng</h1>
        </div>

        {/* Giám đốc chọn khách sạn ở đầu trang; các công cụ khác nằm trên thanh lọc bên dưới. */}
        {isDirector && (locations?.length ?? 0) > 0 && (
          <div className="board-head__tools">
            <select
              className="board-select"
              value={locationId}
              onChange={(e) => selectLocation(e.target.value)}
              aria-label="Chọn khách sạn"
            >
              {locations.map((location) => (
                <option key={location.id} value={location.id}>
                  {location.name}
                </option>
              ))}
            </select>
            <button type="button" className="btn btn--primary" onClick={() => setRoomForm({ mode: 'create' })}>
              + Thêm phòng
            </button>
          </div>
        )}
      </div>

      {banner && (
        <div
          className={`alert alert--${banner.type === 'error' ? 'error' : 'success'} board-banner`}
          role="status"
        >
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">
            ×
          </button>
        </div>
      )}

      <RoomStatusFilter
        variant="tiles"
        counts={counts}
        total={rooms.length}
        selected={filters.status}
        onSelect={(status) => setFilter('status', status)}
      />

      {/* Một hàng: Sơ đồ / Danh sách → lọc tầng, loại phòng → tìm số phòng. */}
      <div className="board-filters">
        {/* Dạng danh sách (có nút sửa / xóa / khóa trên từng dòng) chỉ dành cho Giám đốc / Quản lý. */}
        {(isDirector || isManager) && (
          <div className="view-switch" role="group" aria-label="Kiểu xem">
            <button
              type="button"
              className={`view-switch__item ${view === 'grid' ? 'is-active' : ''}`}
              aria-pressed={view === 'grid'}
              onClick={() => setView('grid')}
            >
              <span className="material-symbols-outlined" aria-hidden="true">grid_view</span>
              Sơ đồ
            </button>
            <button
              type="button"
              className={`view-switch__item ${view === 'list' ? 'is-active' : ''}`}
              aria-pressed={view === 'list'}
              onClick={() => setView('list')}
            >
              <span className="material-symbols-outlined" aria-hidden="true">view_list</span>
              Danh sách
            </button>
          </div>
        )}
        {rooms.length > 0 && (
          <div className="floor-tabs" role="group" aria-label="Lọc theo tầng">
            <button
              type="button"
              className={`floor-tab ${filters.floor ? '' : 'is-active'}`}
              aria-pressed={!filters.floor}
              onClick={() => setFilter('floor', '')}
            >
              Tất cả tầng <span className="chip">{rooms.length}</span>
            </button>
            {allFloors.map(({ floor, rooms: floorRooms }) => (
              <button
                key={floor}
                type="button"
                className={`floor-tab ${filters.floor === floor ? 'is-active' : ''}`}
                aria-pressed={filters.floor === floor}
                onClick={() => setFilter('floor', filters.floor === floor ? '' : floor)}
              >
                Tầng {floor} <span className="chip">{floorRooms.length}</span>
              </button>
            ))}
          </div>
        )}
        {typeNames.length > 1 && (
          <select
            className="board-select"
            value={filters.roomType}
            onChange={(e) => setFilter('roomType', e.target.value)}
            aria-label="Lọc theo loại phòng"
          >
            <option value="">Loại phòng: Tất cả</option>
            {typeNames.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        )}
        <label className="board-search">
          <span className="material-symbols-outlined" aria-hidden="true">search</span>
          <input
            type="search"
            value={filters.search}
            onChange={(e) => setFilter('search', e.target.value)}
            onKeyDown={(e) => e.key === 'Escape' && setFilter('search', '')}
            placeholder="Tìm số phòng…"
            aria-label="Tìm theo số phòng"
          />
        </label>
        {hasFilter && (
          <button type="button" className="btn btn--ghost" onClick={() => setFilters(NO_FILTER)}>
            Xóa bộ lọc
          </button>
        )}
      </div>

      {loadError && (
        <div className="alert alert--error" role="alert">
          {loadError}
        </div>
      )}

      <div
        className={`board-layout ${wide && showPanel ? 'board-layout--with-panel' : ''}`}
      >
        <div className="board-main">
          {loading && rooms.length === 0 && <p className="state">Đang tải dữ liệu…</p>}

          {isDirector && locations?.length === 0 && (
            <div className="state state--empty">
              <p>Chưa có khách sạn nào trong chuỗi.</p>
            </div>
          )}

          {!loading && !loadError && !waitingForLocation && rooms.length === 0 && (
            <div className="state state--empty">
              <p>Khách sạn chưa có phòng nào.</p>
              <p className="muted">Giám đốc thêm phòng bằng nút «Thêm phòng» ở đầu trang.</p>
            </div>
          )}

          {rooms.length > 0 && visibleFloors.length === 0 && (
            <div className="state state--empty">
              <p>Không có phòng nào khớp bộ lọc.</p>
              <button type="button" className="btn btn--ghost" onClick={() => setFilters(NO_FILTER)}>
                Xóa bộ lọc
              </button>
            </div>
          )}

          {view === 'list' && visibleFloors.length > 0 && (
            <RoomListView
              rooms={visibleFloors.flatMap((group) => group.rooms)}
              selectedId={selectedId}
              onSelect={(picked) => navigate(`/phong/${picked.id}`)}
              rowActions={rowActions}
            />
          )}

          {view === 'grid' && visibleFloors.map(({ floor, rooms: floorRooms }) => {
            const collapsed = collapsedFloors.has(floor);
            const gridId = `floor-grid-${floor}`;
            return (
              <section className="floor-section" key={floor}>
                <header className="floor-section__head">
                  <span className="floor-section__badge" aria-hidden="true">{floor}</span>
                  <div className="floor-section__title">
                    <h2>
                      Tầng {floor} <span className="chip">{floorRooms.length} phòng</span>
                    </h2>
                    <FloorSummary rooms={floorRooms} />
                  </div>
                  <button
                    type="button"
                    className="floor-section__toggle"
                    onClick={() => toggleFloor(floor)}
                    aria-expanded={!collapsed}
                    aria-controls={gridId}
                  >
                    <span className="floor-section__toggle-text">{collapsed ? 'Mở rộng' : 'Thu gọn'}</span>
                    <span className={`material-symbols-outlined ${collapsed ? 'is-flipped' : ''}`} aria-hidden="true">
                      expand_less
                    </span>
                  </button>
                </header>
                {!collapsed && (
                  <div className="room-grid" id={gridId}>
                    {floorRooms.map((room) => (
                      <RoomCard
                        key={room.id}
                        room={room}
                        hint={roomCardHint(room, tasksByRoom[room.id], staffNames)}
                        selected={room.id === selectedId}
                        onSelect={(picked) => setSelectedId(picked.id)}
                      />
                    ))}
                  </div>
                )}
              </section>
            );
          })}
        </div>

        {wide && showPanel && (selectedRoom ? (
          <RoomBoardPanel {...panelProps} />
        ) : (
          <aside className="board-panel board-panel--empty">
            <span className="material-symbols-outlined" aria-hidden="true">touch_app</span>
            <p>Chọn một phòng để xem chi tiết và thao tác.</p>
          </aside>
        ))}
      </div>

      {!wide && showPanel && selectedRoom && (
        <div className="board-overlay" onClick={() => setSelectedId(null)}>
          <div
            className="board-overlay__sheet"
            role="dialog"
            aria-modal="true"
            aria-labelledby="board-panel-title"
            onClick={(e) => e.stopPropagation()}
          >
            <RoomBoardPanel {...panelProps} autoFocusClose />
          </div>
        </div>
      )}

      {/* Khóa / mở khóa có màn riêng (chọn đích + lý do bắt buộc — BR-ROOM-07); các bước của Lễ tân
          dùng hộp thoại chung. Cả hai cùng kết thúc ở action.finish. */}
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

      {assigning && (
        <AssignTaskModal task={assigning} onClose={() => setAssigning(null)} onAssigned={finishAssign} />
      )}
      {roomForm && (
        <FormModal onClose={() => setRoomForm(null)}>
          {/* key: đổi phòng đang sửa (hoặc chuyển sang thêm mới) thì form nạp lại từ đầu. */}
          <RoomForm
            key={roomForm.mode === 'edit' ? roomForm.room.id : 'create'}
            editing={roomForm.mode === 'edit' ? roomForm.room : null}
            locations={locations}
            roomTypes={roomTypes}
            onSubmit={submitRoomForm}
            onCancel={() => setRoomForm(null)}
          />
        </FormModal>
      )}
      {deleting && (
        <ConfirmDialog
          title={`Xóa phòng ${deleting.roomNumber}?`}
          message={DELETE_ROOM_WARNING}
          confirmLabel="Xóa phòng"
          onCancel={() => setDeleting(null)}
          onConfirm={confirmDelete}
        />
      )}
      {noteRoom && <RoomNoteModal room={noteRoom} onClose={() => setNoteRoom(null)} onSaved={saveNote} />}
      {openReportId && (
        <DamageReportModal
          incidentId={openReportId}
          onClose={() => setOpenReportId(null)}
          onResolved={() => {
            // Đóng phiếu có thể đổi trạng thái tài sản → nạp lại báo hỏng và danh sách tài sản.
            loadReports();
            setAssetsKey((key) => key + 1);
          }}
        />
      )}
      {addingAssetTo && (
        <BatchCreateAssetsModal
          initialRoomId={addingAssetTo.id}
          onClose={() => setAddingAssetTo(null)}
          onCreated={() => setAssetsKey((key) => key + 1)}
        />
      )}
      {auditing && (
        <AssetAuditModal
          scope={{ roomId: auditing.id }}
          title={`Kiểm kê tài sản phòng ${auditing.roomNumber}`}
          emptyText="Phòng này chưa có tài sản cố định nào."
          onClose={() => setAuditing(null)}
          onSaved={(changedCount) => finishAudit(auditing, changedCount)}
        />
      )}
    </div>
  );
}

/** "2 Trống / Sẵn sàng · 1 Chờ dọn" — tóm tắt từng tầng, có chấm màu nhưng luôn kèm chữ. */
function FloorSummary({ rooms }) {
  const counts = countByStatus(rooms);
  const parts = ROOM_STATUS_ORDER.filter((status) => counts[status]);
  return (
    <p className="floor-section__sum">
      {parts.map((status) => {
        const meta = roomStatusMeta(status);
        return (
          <span key={status} className={`floor-sum room-tone--${meta.tone}`}>
            <span className="floor-sum__dot" aria-hidden="true" />
            {counts[status]} {meta.label}
          </span>
        );
      })}
    </p>
  );
}
