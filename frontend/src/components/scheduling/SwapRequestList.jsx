import { useCallback, useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { fetchSwapRequests } from '../../api/scheduling';
import { formatShortDateTime } from '../../pages/rooms/format';
import { SWAP_STATUS, swapProgress } from '../../pages/scheduling/swapRequests';

/**
 * Yêu cầu đổi ca của một hộp (`box`: SENT / RECEIVED / LOCATION) trong nhóm trạng thái `statuses`,
 * mới nhất trước. `pageSize` nhỏ thì có «Xem thêm». `version` đổi thì nạp lại từ đầu — trang tăng nó
 * sau mỗi thao tác, kể cả thao tác bị từ chối, để yêu cầu vừa tự hủy hiện đúng trạng thái mới.
 *
 * @param renderItem (request) => nội dung thẻ: phần tiêu đề, hai ca, nút — huy hiệu và dòng thời gian
 *                   do danh sách thêm
 */
export default function SwapRequestList({ box, statuses, pageSize = 100, version, emptyText, renderItem }) {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async (nextPage) => {
    setLoading(true);
    setError('');
    try {
      const data = await fetchSwapRequests({ box, statuses, page: nextPage, size: pageSize });
      setItems((prev) => (nextPage === 0 ? data.content : [...prev, ...data.content]));
      setPage(nextPage);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(readErrorMessage(err, 'Không tải được danh sách yêu cầu đổi ca.'));
    } finally {
      setLoading(false);
    }
  }, [box, statuses, pageSize]);

  useEffect(() => {
    load(0);
  }, [load, version]);

  return (
    <>
      {error && <div className="alert alert--error" role="alert">{error}</div>}
      {!loading && !error && items.length === 0 && (
        <div className="state state--empty">
          <p>{emptyText}</p>
        </div>
      )}

      <ul className="swap-list" aria-busy={loading}>
        {items.map((request) => {
          const status = SWAP_STATUS[request.status] ?? { label: request.status, badge: 'badge--grey' };
          const { title, body, actions } = renderItem(request);
          return (
            <li key={request.id} className="swap-card">
              <div className="swap-card__head">
                <span className="swap-card__title">{title}</span>
                <span className={`badge ${status.badge}`}>{status.label}</span>
              </div>
              {body}
              <p className="swap-card__meta">
                Gửi lúc {formatShortDateTime(request.createdAt)} · {swapProgress(request)}
              </p>
              {actions && <div className="swap-card__actions">{actions}</div>}
            </li>
          );
        })}
      </ul>

      {loading && <p className="state">Đang tải dữ liệu…</p>}
      {!loading && page + 1 < totalPages && (
        <button type="button" className="btn btn--ghost" onClick={() => load(page + 1)}>
          Xem thêm
        </button>
      )}
    </>
  );
}

/** Hai ca của một yêu cầu, mỗi dòng "ai — đưa ca nào". Hai người có thể trùng tên nên khóa theo vị trí. */
export function SwapPair({ rows }) {
  return (
    <dl className="swap-pair">
      {rows.map((row, index) => (
        <div key={index} className="swap-pair__row">
          <dt>{row.label}</dt>
          <dd>{row.value}</dd>
        </div>
      ))}
    </dl>
  );
}
