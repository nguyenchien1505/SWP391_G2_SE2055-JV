import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { fetchRoomHistory } from '../../api/rooms';
import { formatDateTime } from '../../pages/rooms/format';
import { changeSourceLabel } from '../../pages/rooms/roomLabels';
import RoomStatusBadge from './RoomStatusBadge';

const PAGE_SIZE = 10;

/**
 * S-05 Lịch sử trạng thái phòng — RM-07, BR-ROOM-09. Mọi vai trò xem được phòng thì xem được
 * lịch sử. Mới nhất trước; mỗi dòng: thời điểm · từ → đến · người/nguồn · lý do.
 *
 * Tự tải dữ liệu và tự phân trang. Muốn nạp lại từ đầu (sau khi đổi trạng thái), nơi dùng đổi
 * `key` của component — React dựng lại từ trang đầu, không cần thêm prop "refresh".
 */
export default function RoomHistoryList({ roomId }) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    fetchRoomHistory(roomId, { page, size: PAGE_SIZE })
      .then((result) => !cancelled && setData(result))
      .catch((err) => !cancelled && setError(readErrorMessage(err, 'Không tải được lịch sử trạng thái.')))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [roomId, page]);

  const rows = data?.content ?? [];
  const totalPages = Math.max(1, data?.totalPages ?? 1);

  if (loading && !data) return <p className="state">Đang tải lịch sử…</p>;
  if (error) {
    return (
      <div className="alert alert--error" role="alert">
        {error}
      </div>
    );
  }
  if (rows.length === 0) {
    return (
      <div className="state state--empty">
        <p>Chưa có lần đổi trạng thái nào.</p>
        <p className="muted">Phòng có từ trước khi bật ghi lịch sử sẽ có dòng đầu tiên ở lần đổi kế tiếp.</p>
      </div>
    );
  }

  return (
    <>
      <ol className="history-list">
        {rows.map((row) => (
          <HistoryItem key={row.id} row={row} />
        ))}
      </ol>

      <div className="panel__foot">
        <span className="muted">{data.totalElements} lần đổi trạng thái</span>
        <div className="pager">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={page === 0 || loading}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            aria-label="Trang trước"
          >
            ‹
          </button>
          <span>
            Trang {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={page + 1 >= totalPages || loading}
            onClick={() => setPage((p) => p + 1)}
            aria-label="Trang sau"
          >
            ›
          </button>
        </div>
      </div>
    </>
  );
}

/**
 * Một lần đổi trạng thái. Nguồn SYSTEM không có người thực hiện → ghi "Hệ thống"; có người thì
 * ghi họ tên kèm tư cách (Quản lý, Lễ tân…). Dòng đầu của phòng mới tạo không có trạng thái cũ.
 */
function HistoryItem({ row }) {
  const source = changeSourceLabel(row.changeSource);
  return (
    <li className="history-item">
      <time className="history-item__when" dateTime={row.changedAt}>
        {formatDateTime(row.changedAt)}
      </time>

      <div className="history-item__change">
        {row.fromStatus ? <RoomStatusBadge status={row.fromStatus} /> : <span className="muted">Phòng mới tạo</span>}
        <span className="history-item__arrow" aria-label="chuyển sang">
          →
        </span>
        <RoomStatusBadge status={row.toStatus} />
      </div>

      <div className="history-item__actor">
        <b>{row.changedByName ?? source}</b>
        {row.changedByName && <small>{source}</small>}
        {row.relatedTaskId && <small>Theo task dọn phòng</small>}
      </div>

      {row.reason && <p className="history-item__reason">Lý do: {row.reason}</p>}
    </li>
  );
}
