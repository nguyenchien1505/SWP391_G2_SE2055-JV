import { useCallback, useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import {
  approveAdjustment,
  fetchAdjustmentReviewQueue,
  rejectAdjustment,
} from '../../api/attendanceAdjustments';
import AdjustmentDetails from '../../components/scheduling/AdjustmentDetails';
import '../rooms/rooms.css';
import './scheduling.css';

const TABS = [
  { key: 'pending', label: 'Chờ duyệt', statuses: ['PENDING'], empty: 'Không có đơn nào chờ duyệt.' },
  { key: 'closed', label: 'Đã xử lý', statuses: ['APPROVED', 'REJECTED', 'CANCELLED'], empty: 'Chưa có đơn nào đã xử lý.' },
];

/** Khớp @Size của ReviewAttendanceAdjustmentRequest. */
const NOTE_MAX = 500;

/**
 * Duyệt điều chỉnh công — quản lý khách sạn xử lý đơn sửa giờ vào / ra của nhân viên khách sạn mình (chấm công M2,
 * chốt 01/11/2026). Duyệt thì giờ được ghi vào ca như quản lý nhập (lý do của đơn vào lịch sử chấm công); từ chối
 * phải có lý do. Giờ không còn hợp lệ (quản lý đã sửa tay, ca đổi người…) thì câu lỗi của backend hiện nguyên văn.
 */
export default function AdjustmentReviewPage() {
  const [tab, setTab] = useState('pending');
  const [page, setPage] = useState(null);
  const [error, setError] = useState('');
  const [banner, setBanner] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [notes, setNotes] = useState({});   // id → ghi chú / lý do từ chối đang gõ

  const current = TABS.find((t) => t.key === tab);

  const load = useCallback(async () => {
    try {
      setPage(await fetchAdjustmentReviewQueue({ statuses: current.statuses }));
      setError('');
    } catch (err) {
      setError(readErrorMessage(err, 'Không tải được danh sách đơn.'));
    }
  }, [current]);

  useEffect(() => {
    setPage(null);
    load();
  }, [load]);

  async function run(request, action) {
    setBusyId(request.id);
    setBanner(null);
    const note = (notes[request.id] ?? '').trim();
    try {
      if (action === 'approve') {
        await approveAdjustment(request.id, note);
        setBanner({ type: 'success', text: `Đã duyệt — giờ của ${request.requesterName ?? 'nhân viên'} đã được ghi vào ca.` });
      } else {
        await rejectAdjustment(request.id, note);
        setBanner({ type: 'success', text: `Đã từ chối đơn của ${request.requesterName ?? 'nhân viên'}.` });
      }
      setNotes((all) => ({ ...all, [request.id]: '' }));
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không xử lý được đơn.') });
    } finally {
      setBusyId(null);
      load();
    }
  }

  const requests = page?.content ?? [];

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Đơn từ › Duyệt điều chỉnh công</p>
          <h1>Duyệt điều chỉnh công</h1>
          <p className="muted">Duyệt thì giờ trong đơn được ghi vào ca; từ chối phải nêu lý do để nhân viên biết.</p>
        </div>
      </div>

      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            className={`tab ${tab === t.key ? 'tab--active' : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {banner && (
        <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="status">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">×</button>
        </div>
      )}
      {error && <div className="alert alert--error" role="alert">{error}</div>}
      {!page && !error && <p className="state">Đang tải…</p>}
      {page && requests.length === 0 && <div className="state state--empty"><p>{current.empty}</p></div>}

      {requests.length > 0 && (
        <ul className="adjustment-list">
          {requests.map((request) => (
            <li key={request.id}>
              <AdjustmentDetails request={request} showRequester />
              {request.status === 'PENDING' && (
                <div className="adjustment__review">
                  <label className="field">
                    <span className="field__label">Ghi chú (bắt buộc khi từ chối)</span>
                    <input
                      value={notes[request.id] ?? ''}
                      maxLength={NOTE_MAX}
                      onChange={(e) => setNotes((all) => ({ ...all, [request.id]: e.target.value }))}
                      placeholder="Ví dụ: đã đối chiếu camera lễ tân"
                    />
                  </label>
                  <div className="adjustment__actions">
                    <button
                      type="button"
                      className="btn btn--ghost"
                      disabled={busyId !== null || (notes[request.id] ?? '').trim() === ''}
                      onClick={() => run(request, 'reject')}
                    >
                      Từ chối
                    </button>
                    <button
                      type="button"
                      className="btn btn--primary"
                      disabled={busyId !== null}
                      onClick={() => run(request, 'approve')}
                    >
                      {busyId === request.id ? 'Đang xử lý…' : 'Duyệt'}
                    </button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
