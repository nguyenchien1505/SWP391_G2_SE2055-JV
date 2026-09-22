import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { fetchRoom } from '../../api/rooms';
import RoomStatusBadge from '../../components/rooms/RoomStatusBadge';
import { formatDateTime } from './format';
import { useTenantLocations } from './useRoomLookups';
import './rooms.css';

/**
 * S-04 Chi tiết phòng — mọi vai trò trong Tenant xem được, trong phạm vi Location của mình
 * (backend trả 404 nếu ngoài phạm vi). RM-06.
 *
 * Các khu "Lịch sử trạng thái" (S-05) và nút đổi trạng thái sẽ gắn thêm vào trang này ở các
 * tính năng sau; F1 chỉ hiển thị thông tin phòng.
 */
export default function RoomDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const isDirector = user?.role === 'DIRECTOR';
  // Danh sách phòng (S-02) dành cho Giám đốc/Manager; Staff quay về sơ đồ phòng.
  const canSeeList = isDirector || user?.role === 'MANAGER';

  const locations = useTenantLocations(isDirector);

  const [room, setRoom] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

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

  const locationName = (locations ?? []).find((location) => location.id === room?.locationId)?.name;

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

          <section className="panel">
            <div className="panel__head">
              <h2>Thông tin phòng</h2>
            </div>

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
              {isDirector && (
                <div>
                  <dt>Khách sạn</dt>
                  <dd>{locationName ?? '—'}</dd>
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
                  Chỉ Quản lý chi nhánh đưa phòng vào hoặc ra khỏi trạng thái này (BR-ROOM-03).
                </small>
              </div>
            )}

            <div className="readonly-box room-detail__note">
              <b>Ghi chú vận hành</b>
              <p>{room.note || 'Chưa có ghi chú.'}</p>
            </div>
          </section>
        </>
      )}
    </div>
  );
}
