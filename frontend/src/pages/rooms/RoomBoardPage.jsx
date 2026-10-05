import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { hasPermission } from '../../permissions';
import { readErrorMessage } from '../../api/client';
import { cancelTask, createStayoverTask, fetchTasks, unassignTask } from '../../api/housekeeping';
import { fetchAllRooms, fetchRoom } from '../../api/rooms';
import { fetchStaffDirectory } from '../../api/users';
import ConfirmDialog from '../../components/ConfirmDialog';
import AssignTaskModal from '../../components/rooms/AssignTaskModal';
import InspectTaskModal from '../../components/rooms/InspectTaskModal';
import LockRoomModal from '../../components/rooms/LockRoomModal';
import PreviousInspectionModal from '../../components/rooms/PreviousInspectionModal';
import RoomActionDialog from '../../components/rooms/RoomActionDialog';
import RoomBoardPanel from '../../components/rooms/RoomBoardPanel';
import RoomCard from '../../components/rooms/RoomCard';
import RoomNoteModal from '../../components/rooms/RoomNoteModal';
import RoomStatusFilter from '../../components/rooms/RoomStatusFilter';
import { formatClock } from './format';
import { LOCK, statusChangedMessage } from './roomActions';
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
import { useTenantLocations } from './useRoomLookups';
import './rooms.css';

/** Từ khổ này bảng chi tiết nằm HẲN bên phải lưới; nhỏ hơn thì mở đè lên (ngăn kéo / trượt từ dưới). */
const WIDE_QUERY = '(min-width: 1200px)';

/** Ba trạng thái "đang mở" của một việc dọn (BR-HK-06) — đủ để biết phòng nào đang chờ gì. */
const OPEN_TASK_STATUSES = ['UNASSIGNED', 'IN_PROGRESS', 'PENDING_INSPECTION'];

const NO_FILTER = { status: '', floor: '', roomType: '', search: '' };

function useMediaQuery(query) {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const mql = window.matchMedia(query);
    const onChange = () => setMatches(mql.matches);
    mql.addEventListener('change', onChange);
    onChange();
    return () => mql.removeEventListener('change', onChange);
  }, [query]);
  return matches;
}

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
  const [banner, setBanner] = useState(null); // { type, text }

  const [filters, setFilters] = useState(NO_FILTER);
  const [collapsedFloors, setCollapsedFloors] = useState(() => new Set());
  const [selectedId, setSelectedId] = useState(null);
  const [historyKey, setHistoryKey] = useState(0);

  // Chỉ Quản lý chi nhánh: việc dọn đang mở theo phòng + tên người làm (DTO chỉ có id).
  const [tasksByRoom, setTasksByRoom] = useState({});
  const [staffNames, setStaffNames] = useState({});

  const [assigning, setAssigning] = useState(null);
  const [inspecting, setInspecting] = useState(null);
  const [releasing, setReleasing] = useState(null);
  const [cancelling, setCancelling] = useState(null);
  const [noteRoom, setNoteRoom] = useState(null);
  const [previousOf, setPreviousOf] = useState(null);

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
      ]);
      setRooms(list);
      setUpdatedAt(new Date());
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được sơ đồ phòng.'));
    } finally {
      setLoading(false);
    }
  }, [waitingForLocation, isDirector, locationId, loadTasks]);

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

  function finishInspection(record) {
    const task = inspecting;
    setInspecting(null);
    setBanner({
      type: 'success',
      text: record.result === 'PASS'
        ? `Phòng ${task.roomNumber} đã Sẵn sàng.`
        : `Đã tạo việc dọn lại cho phòng ${task.roomNumber}. Phòng quay về «Chờ dọn».`,
    });
    refreshRoom(task.roomId);
    afterChange();
  }

  async function confirmRelease() {
    const task = releasing;
    setReleasing(null);
    try {
      await unassignTask(task.id);
      setBanner({
        type: 'success',
        text: `Đã gỡ người khỏi việc dọn phòng ${task.roomNumber}.`
          + (task.taskType === 'CHECKOUT' ? ' Phòng quay về «Chờ dọn».' : ''),
      });
      refreshRoom(task.roomId);
      afterChange();
    } catch (err) {
      showError(readErrorMessage(err, 'Không gỡ được người khỏi việc dọn.'));
    }
  }

  /** Hủy tay một việc dọn hằng ngày — phòng không đổi gì, vẫn «Đang sử dụng» (BR-HK-05). */
  async function confirmCancel() {
    const task = cancelling;
    setCancelling(null);
    try {
      await cancelTask(task.id);
      setBanner({ type: 'success', text: `Đã hủy việc dọn hằng ngày của phòng ${task.roomNumber}.` });
      afterChange();
    } catch (err) {
      showError(readErrorMessage(err, 'Không hủy được việc dọn.'));
    }
  }

  /** BR-HK-05 — tạo việc dọn hằng ngày rồi mở luôn hộp thoại phân công, đỡ một bước. */
  async function createStayover(room) {
    setBanner(null);
    try {
      const created = await createStayoverTask(room.id);
      setBanner({ type: 'success', text: `Đã tạo việc dọn hằng ngày cho phòng ${room.roomNumber}.` });
      loadTasks();
      setAssigning(created);
    } catch (err) {
      showError(readErrorMessage(err, 'Không tạo được việc dọn hằng ngày.'));
    }
  }

  function saveNote(updated) {
    setNoteRoom(null);
    replaceRoom(updated);
    setBanner({ type: 'success', text: `Đã lưu ghi chú vận hành của phòng ${updated.roomNumber}.` });
  }

  const selectedRoom = rooms.find((room) => room.id === selectedId) ?? null;
  const modalOpen = Boolean(
    assigning || inspecting || releasing || cancelling || noteRoom || previousOf || action.pending,
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
    staffNames,
    historyKey,
    busy: action.running,
    onClose: () => setSelectedId(null),
    onAction: (picked) => {
      setBanner(null);
      action.start(selectedRoom, picked);
    },
    onAssign: setAssigning,
    onInspect: setInspecting,
    onUnassign: setReleasing,
    onCancel: setCancelling,
    onCreateStayover: createStayover,
    onEditNote: setNoteRoom,
    onShowPreviousInspection: setPreviousOf,
  };

  return (
    <div className="page room-board">
      <div className="page__head board-head">
        <div>
          <p className="breadcrumb">Vận hành › Sơ đồ phòng</p>
          <h1>Sơ đồ phòng</h1>
          <p className="board-head__sub">
            {rooms.length} phòng
            {locationName ? ` · ${locationName}` : ''}
            {updatedAt ? ` · cập nhật lúc ${formatClock(updatedAt)}` : ''}
          </p>
        </div>

        <div className="board-head__tools">
          {isDirector && (locations?.length ?? 0) > 0 && (
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
          {/* Danh sách phòng (S-02) chỉ dành cho Giám đốc / Quản lý — nhân viên không có lối vào đó. */}
          {(isDirector || isManager) && (
            <div className="view-switch" role="group" aria-label="Kiểu xem">
              <span className="view-switch__item is-active" aria-current="page">
                <span className="material-symbols-outlined" aria-hidden="true">grid_view</span>
                Sơ đồ
              </span>
              <Link className="view-switch__item" to="/phong">
                <span className="material-symbols-outlined" aria-hidden="true">view_list</span>
                Danh sách
              </Link>
            </div>
          )}
          <button
            type="button"
            className="btn btn--ghost board-refresh"
            onClick={load}
            disabled={loading}
            aria-label="Làm mới sơ đồ"
            title="Làm mới sơ đồ"
          >
            <span className={`material-symbols-outlined ${loading ? 'is-spinning' : ''}`} aria-hidden="true">
              refresh
            </span>
          </button>
        </div>
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

      {rooms.length > 0 && (
        <div className="board-filters">
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
          {hasFilter && (
            <button type="button" className="btn btn--ghost" onClick={() => setFilters(NO_FILTER)}>
              Xóa bộ lọc
            </button>
          )}
        </div>
      )}

      {loadError && (
        <div className="alert alert--error" role="alert">
          {loadError}
        </div>
      )}

      <div className={`board-layout ${wide ? 'board-layout--with-panel' : ''}`}>
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
              <p className="muted">Phòng do Giám đốc tạo ở màn Danh sách phòng.</p>
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

          {visibleFloors.map(({ floor, rooms: floorRooms }) => {
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

        {wide && (selectedRoom ? (
          <RoomBoardPanel {...panelProps} />
        ) : (
          <aside className="board-panel board-panel--empty">
            <span className="material-symbols-outlined" aria-hidden="true">touch_app</span>
            <p>Chọn một phòng để xem chi tiết và thao tác.</p>
          </aside>
        ))}
      </div>

      {!wide && selectedRoom && (
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
      {inspecting && (
        <InspectTaskModal
          task={inspecting}
          staffName={staffNames[inspecting.assignedStaffId]}
          onClose={() => setInspecting(null)}
          onInspected={finishInspection}
        />
      )}
      {releasing && (
        <ConfirmDialog
          title={`Gỡ người khỏi việc dọn phòng ${releasing.roomNumber}?`}
          message={
            <>
              Việc dọn quay lại hàng chờ phân công.
              {releasing.taskType === 'CHECKOUT'
                ? ' Phòng cũng quay về trạng thái «Chờ dọn».'
                : ' Phòng giữ nguyên «Đang sử dụng».'}
            </>
          }
          confirmLabel="Gỡ người"
          onCancel={() => setReleasing(null)}
          onConfirm={confirmRelease}
        />
      )}
      {cancelling && (
        <ConfirmDialog
          title={`Hủy việc dọn hằng ngày của phòng ${cancelling.roomNumber}?`}
          message="Việc dọn đóng lại vĩnh viễn với lý do «Quản lý hủy». Phòng giữ nguyên «Đang sử dụng»."
          confirmLabel="Hủy việc dọn"
          onCancel={() => setCancelling(null)}
          onConfirm={confirmCancel}
        />
      )}
      {noteRoom && <RoomNoteModal room={noteRoom} onClose={() => setNoteRoom(null)} onSaved={saveNote} />}
      {previousOf && <PreviousInspectionModal task={previousOf} onClose={() => setPreviousOf(null)} />}
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
