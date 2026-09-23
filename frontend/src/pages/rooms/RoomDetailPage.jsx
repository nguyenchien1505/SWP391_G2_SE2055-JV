import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { deleteRoom, fetchRoom, updateRoom } from '../../api/rooms';
import ConfirmDialog from '../../components/ConfirmDialog';
import LockRoomModal from '../../components/rooms/LockRoomModal';
import RoomForm from '../../components/rooms/RoomForm';
import RoomNoteModal from '../../components/rooms/RoomNoteModal';
import RoomHistoryList from '../../components/rooms/RoomHistoryList';
import RoomStatusBadge from '../../components/rooms/RoomStatusBadge';
import { formatDateTime } from './format';
import { roomActionsFor, statusChangedMessage } from './roomActions';
import { useRoomTypes, useTenantLocations } from './useRoomLookups';
import './rooms.css';

const TABS = [
  { key: 'info', label: 'Thông tin phòng' },
  { key: 'history', label: 'Lịch sử trạng thái' },
];

/**
 * Trạng thái mà phòng còn xóa được — chỉ dùng để ẨN nút cho đỡ vướng. Backend vẫn kiểm tra đủ
 * ba điều kiện (trạng thái, việc dọn đang mở, tài sản gắn vào) và trả lỗi nếu gọi thẳng API.
 */
const DELETABLE_STATUSES = ['AVAILABLE', 'UNAVAILABLE'];

/**
 * S-04 Chi tiết phòng — mọi vai trò trong Tenant xem được, trong phạm vi Location của mình
 * (backend trả 404 nếu ngoài phạm vi). RM-06.
 *
 * F2 thêm:
 *   - Tab "Lịch sử trạng thái" (S-05, RM-07, BR-ROOM-09) cho mọi vai trò.
 *   - Nút khóa / mở khóa (S-08, RM-11, RM-12) — chỉ hiện khi `room.allowedTargets` cho phép,
 *     tức chỉ Manager. Sau khi đổi, phòng lấy thẳng từ response và lịch sử nạp lại từ đầu.
 *
 * F3 thêm:
 *   - Giám đốc: "Sửa thông tin" (form S-03 ngay trong trang) và "Xóa phòng" (RM-03, RM-05).
 *   - Quản lý chi nhánh: "Sửa ghi chú" (S-07, RM-04).
 */
export default function RoomDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const isDirector = user?.role === 'DIRECTOR';
  const isManager = user?.role === 'MANAGER';
  // Danh sách phòng (S-02) dành cho Giám đốc/Manager; Staff quay về sơ đồ phòng.
  const canSeeList = isDirector || user?.role === 'MANAGER';

  const locations = useTenantLocations(isDirector);
  // Danh mục loại phòng chỉ cần khi Giám đốc mở form sửa; Staff gọi API này sẽ nhận 403.
  const roomTypes = useRoomTypes(isDirector);

  const [room, setRoom] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [tab, setTab] = useState('info');
  const [banner, setBanner] = useState(null); // { type, text }
  const [activeAction, setActiveAction] = useState(null);
  // Tăng lên sau mỗi lần đổi trạng thái → RoomHistoryList dựng lại, nạp từ trang đầu.
  const [historyVersion, setHistoryVersion] = useState(0);

  const [editing, setEditing] = useState(false);
  const [noteOpen, setNoteOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError('');
    fetchRoom(id)
      .then((data) => !cancelled && setRoom(data))
      .catch((err) => {
        if (cancelled) return;
        setRoom(null);
        // 404 gồm cả "phòng thuộc khách sạn khác" — backend cố ý không phân biệt để không lộ dữ liệu.
        setLoadError(
          err?.response?.status === 404
            ? 'Không tìm thấy phòng, hoặc phòng không thuộc khách sạn bạn phụ trách.'
            : readErrorMessage(err, 'Không tải được thông tin phòng.'),
        );
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [id]);

  function handleStatusChanged(updated) {
    setBanner({ type: 'success', text: statusChangedMessage(room, updated) });
    setRoom(updated);
    setActiveAction(null);
    setHistoryVersion((v) => v + 1);
  }

  /** RM-03 — lưu thông tin cấu trúc. Trả câu lỗi cho form, hoặc `null` khi thành công. */
  async function handleUpdate(payload) {
    try {
      const updated = await updateRoom(room.id, payload);
      setRoom(updated);
      setEditing(false);
      setBanner({ type: 'success', text: `Đã cập nhật phòng ${updated.roomNumber}.` });
      return null;
    } catch (err) {
      return readErrorMessage(err, 'Không lưu được thông tin phòng.');
    }
  }

  /** RM-04 — Quản lý chi nhánh vừa lưu ghi chú; phòng mới lấy thẳng từ response. */
  function handleNoteSaved(updated) {
    setRoom(updated);
    setNoteOpen(false);
    setBanner({ type: 'success', text: 'Đã lưu ghi chú vận hành.' });
  }

  /**
   * RM-05 — xóa phòng. Xóa xong thì không còn gì để xem ở trang này nữa, nên quay về danh sách
   * và mang theo câu báo. Bị backend chặn thì ở lại và hiện nguyên văn câu lỗi.
   */
  async function handleConfirmDelete() {
    setPendingDelete(false);
    try {
      await deleteRoom(room.id);
      navigate('/phong', { state: { banner: { type: 'success', text: `Đã xóa phòng ${room.roomNumber}.` } } });
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không xóa được phòng.') });
    }
  }

  const locationName = (locations ?? []).find((location) => location.id === room?.locationId)?.name;
  const actions = roomActionsFor(room);
  const canDelete = isDirector && DELETABLE_STATUSES.includes(room?.status);

  return (
    <div className="page">
      <Link className="back-link" to={canSeeList ? '/phong' : '/so-do-phong'}>
        ‹ Quay lại {canSeeList ? 'danh sách phòng' : 'sơ đồ phòng'}
      </Link>

      {loading && <p className="state">Đang tải dữ liệu…</p>}
      {loadError && (
        <div className="alert alert--error" role="alert">
          {loadError}
        </div>
      )}

      {room && (
        <>
          <div className="page__head">
            <div>
              <p className="breadcrumb">Vận hành › Chi tiết phòng</p>
              <h1>Phòng {room.roomNumber}</h1>
            </div>
            <RoomStatusBadge status={room.status} large />
          </div>

          {banner && (
            <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="status">
              {banner.text}
              <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">
                ×
              </button>
            </div>
          )}

          {!editing && (actions.length > 0 || isDirector || isManager) && (
            <div className="room-actions">
              {actions.map((action) => (
                <button
                  key={action.key}
                  type="button"
                  className={`btn ${action.danger ? 'btn--danger' : 'btn--primary'}`}
                  onClick={() => setActiveAction(action.key)}
                >
                  {action.label}
                </button>
              ))}

              {/* BR-ROOM-04: Giám đốc sửa cấu trúc, Quản lý chi nhánh chỉ sửa ghi chú. */}
              {isDirector && (
                <button
                  type="button"
                  className="btn btn--ghost"
                  onClick={() => {
                    setEditing(true);
                    setTab('info');
                    setBanner(null);
                  }}
                >
                  Sửa thông tin
                </button>
              )}
              {isManager && (
                <button type="button" className="btn btn--ghost" onClick={() => setNoteOpen(true)}>
                  Sửa ghi chú
                </button>
              )}
              {canDelete && (
                <button type="button" className="btn btn--danger" onClick={() => setPendingDelete(true)}>
                  Xóa phòng
                </button>
              )}
            </div>
          )}

          {!editing && (
          <div className="room-tabs" role="tablist" aria-label="Nội dung phòng">
            {TABS.map((item) => (
              <button
                key={item.key}
                type="button"
                role="tab"
                id={`room-tab-${item.key}`}
                aria-selected={tab === item.key}
                aria-controls={`room-panel-${item.key}`}
                className={`room-tab ${tab === item.key ? 'is-active' : ''}`}
                onClick={() => setTab(item.key)}
              >
                {item.label}
              </button>
            ))}
          </div>
          )}

          <section
            className="panel"
            role={editing ? undefined : 'tabpanel'}
            id={editing ? undefined : `room-panel-${tab}`}
            aria-labelledby={editing ? undefined : `room-tab-${tab}`}
          >
            {editing && (
              <RoomForm
                editing={room}
                locations={locations}
                roomTypes={roomTypes}
                onSubmit={handleUpdate}
                onCancel={() => setEditing(false)}
              />
            )}
            {!editing && tab === 'info' && (
              <RoomInfo room={room} locationName={isDirector ? locationName ?? '—' : null} />
            )}
            {!editing && tab === 'history' && (
              <RoomHistoryList key={`${room.id}-${historyVersion}`} roomId={room.id} />
            )}
          </section>

          {activeAction && (
            <LockRoomModal room={room} onClose={() => setActiveAction(null)} onChanged={handleStatusChanged} />
          )}

          {noteOpen && (
            <RoomNoteModal room={room} onClose={() => setNoteOpen(false)} onSaved={handleNoteSaved} />
          )}

          {pendingDelete && (
            <ConfirmDialog
              title={`Xóa phòng ${room.roomNumber}?`}
              message={
                <>
                  Thao tác này không hoàn tác được. Hệ thống sẽ từ chối nếu phòng đang có khách
                  hoặc đang trong quy trình dọn, còn việc dọn phòng chưa kết thúc, hoặc còn tài sản
                  cố định gắn vào. Phòng đã xóa vẫn tính vào hạn mức của gói dịch vụ.
                </>
              }
              confirmLabel="Xóa phòng"
              onCancel={() => setPendingDelete(false)}
              onConfirm={handleConfirmDelete}
            />
          )}
        </>
      )}
    </div>
  );
}

/**
 * Phần thông tin của S-04 (F1).
 * @param locationName chỉ Giám đốc thấy dòng "Khách sạn" (null = ẩn dòng này)
 */
function RoomInfo({ room, locationName }) {
  return (
    <>
      <dl className="room-facts">
        <div>
          <dt>Số phòng</dt>
          <dd>{room.roomNumber}</dd>
        </div>
        <div>
          <dt>Tầng</dt>
          <dd>{room.floor}</dd>
        </div>
        <div>
          <dt>Loại phòng</dt>
          <dd>{room.roomTypeName ?? '—'}</dd>
        </div>
        <div>
          <dt>Sức chứa</dt>
          <dd>{room.capacity} người</dd>
        </div>
        {locationName !== null && (
          <div>
            <dt>Khách sạn</dt>
            <dd>{locationName}</dd>
          </div>
        )}
        <div>
          <dt>Cập nhật lần cuối</dt>
          <dd>{formatDateTime(room.updatedAt ?? room.createdAt)}</dd>
        </div>
      </dl>

      {room.status === 'UNAVAILABLE' && (
        <div className="readonly-box room-detail__note">
          <b>Lý do không khả dụng</b>
          <p>{room.unavailableReason}</p>
          <small className="muted">
            Chỉ Quản lý chi nhánh đưa phòng vào hoặc ra khỏi trạng thái này.
          </small>
        </div>
      )}

      <div className="readonly-box room-detail__note">
        <b>Ghi chú vận hành</b>
        <p>{room.note || 'Chưa có ghi chú.'}</p>
      </div>
    </>
  );
}
