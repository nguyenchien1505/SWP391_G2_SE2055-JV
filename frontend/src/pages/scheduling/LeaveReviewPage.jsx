import { useCallback, useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { fetchLeaveReviewQueue } from '../../api/leaveRequests';
import LeaveApprovalDialog from '../../components/scheduling/LeaveApprovalDialog';
import LeaveTicket from '../../components/scheduling/LeaveTicket';
import RejectLeaveDialog from '../../components/scheduling/RejectLeaveDialog';
import RevokeLeaveDialog from '../../components/scheduling/RevokeLeaveDialog';
import { useAuth } from '../../context/AuthContext';
import { formatDate, todayIso } from '../rooms/format';
import { daysInclusive } from './leaveLabels';
import '../rooms/rooms.css';
import './leave.css';

const PAGE_SIZE = 20;

/** Mở sẵn tab "Chờ duyệt" — màn này là hộp thư việc cần làm. `status` rỗng = mọi trạng thái. */
const TABS = [
  { key: 'PENDING', label: 'Chờ duyệt' },
  { key: 'APPROVED', label: 'Đã duyệt' },
  { key: 'REJECTED', label: 'Từ chối' },
  { key: '', label: 'Tất cả' },
];

/**
 * Duyệt đơn nghỉ theo cấp — BR-SCH-08: quản lý khách sạn duyệt đơn của nhân viên khách sạn mình, giám đốc duyệt
 * đơn của các quản lý. Giám sát vận hành không duyệt (BR-PERM-07) nên không vào được màn này.
 *
 * "Duyệt" mở hộp thoại xem trước các ca sẽ bị gỡ và cảnh báo mất lễ tân (BR-SCH-29) rồi mới xác nhận; "Từ chối"
 * bắt buộc nhập lý do, người gửi đọc được (chốt 31/10/2026) — lịch làm việc không đổi. Đơn đã duyệt còn ngày chưa
 * tới thì người duyệt hủy được phần đó, kèm lý do (BR-SCH-16 sửa 28/10/2026).
 */
export default function LeaveReviewPage() {
  const { user } = useAuth();
  const isDirector = user?.role === 'DIRECTOR';
  const today = todayIso();

  const [status, setStatus] = useState('PENDING');
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [approving, setApproving] = useState(null); // đơn đang mở hộp thoại duyệt
  const [rejecting, setRejecting] = useState(null); // đơn đang mở hộp thoại từ chối
  const [revoking, setRevoking] = useState(null); // đơn đã duyệt đang mở hộp thoại hủy phần còn lại
  const [banner, setBanner] = useState(null); // { type, text }

  const load = useCallback(async (nextPage) => {
    setLoading(true);
    setLoadError('');
    try {
      const data = await fetchLeaveReviewQueue({ status, page: nextPage, size: PAGE_SIZE });
      setItems((prev) => (nextPage === 0 ? data.content ?? [] : [...prev, ...(data.content ?? [])]));
      setPage(nextPage);
      setTotalPages(data.totalPages ?? 0);
      setTotal(data.totalElements ?? 0);
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được danh sách đơn nghỉ.'));
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    load(0);
  }, [load]);

  /** Báo lại đúng điều đã xảy ra với LỊCH: bao nhiêu ca bị gỡ, ai được xếp thay lễ tân, ca nào giữ. */
  function finishApprove(result) {
    const leave = result.request;
    setApproving(null);
    const parts = [`Đã duyệt đơn nghỉ của ${leave.requesterName}.`];
    parts.push(result.releasedShifts.length > 0
      ? `Đã gỡ khỏi ${result.releasedShifts.length} ca trong khoảng nghỉ.`
      : 'Lịch làm việc không đổi.');
    if (result.replacements.length > 0) {
      const names = result.replacements.map(({ shift, fullName }) =>
        `${fullName} (${shift.templateName ?? 'Ca giờ khác'} ${formatDate(shift.shiftDate)})`);
      parts.push(`Lễ tân thay: ${names.join(', ')}.`);
    }
    if (result.keptShifts.length > 0) {
      parts.push(`${result.keptShifts.length} ca giữ nguyên (ca hôm nay hoặc đã check-in) — gỡ tay ở Xếp lịch làm việc nếu cần.`);
    }
    setBanner({ type: 'success', text: parts.join(' ') });
    load(0);
  }

  function finishReject(updated) {
    setRejecting(null);
    setBanner({
      type: 'success',
      text: `Đã từ chối đơn nghỉ của ${updated.requesterName}. Người gửi sẽ thấy lý do trên đơn của mình.`,
    });
    load(0);
  }

  /** Số ngày vừa hủy đọc từ chính kết quả backend: hủy cả đơn, hoặc phần sau ngày kết thúc mới. */
  function finishRevoke(updated) {
    setRevoking(null);
    const cancelled = updated.status === 'CANCELLED'
      ? updated.days
      : daysInclusive(updated.toDate, updated.originalToDate) - 1;
    const parts = [`Đã hủy ${cancelled} ngày nghỉ chưa tới của ${updated.requesterName}.`];
    if (updated.leaveType === 'ANNUAL') parts.push(`Quỹ phép năm hoàn lại ${cancelled} ngày.`);
    if (!isDirector) parts.push('Các ca đã gỡ lúc duyệt vẫn để trống — xếp lại ở «Xếp lịch làm việc» nếu cần.');
    setBanner({ type: 'success', text: parts.join(' ') });
    load(0);
  }

  function actionsFor(leave) {
    if (leave.status === 'PENDING') {
      return [
        { key: 'approve', label: 'Duyệt…', onClick: () => setApproving(leave) },
        { key: 'reject', label: 'Từ chối…', tone: 'ghost', onClick: () => setRejecting(leave) },
      ];
    }
    // BR-SCH-16 sửa 28/10/2026: đơn đã duyệt còn ngày chưa tới thì người duyệt hủy được phần đó.
    if (leave.status === 'APPROVED' && leave.toDate > today) {
      const label = leave.fromDate > today ? 'Hủy đơn…' : 'Hủy phần còn lại…';
      return [{ key: 'revoke', label, tone: 'ghost', onClick: () => setRevoking(leave) }];
    }
    return [];
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Lịch làm việc › Duyệt đơn nghỉ</p>
          <h1>Duyệt đơn nghỉ</h1>
          <p className="muted">
            {isDirector
              ? 'Đơn nghỉ của các quản lý khách sạn trong chuỗi.'
              : `Đơn nghỉ của nhân viên ${user?.locationName ?? 'khách sạn'}. Duyệt đơn sẽ tự gỡ người khỏi các ca từ ngày mai trong khoảng nghỉ.`}
          </p>
        </div>
      </div>

      {banner && (
        <div className={`alert alert--${banner.type}`} role="status">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">
            ×
          </button>
        </div>
      )}

      <section className="panel">
        <div className="panel__head">
          <h2>Đơn nghỉ</h2>
          {!loading && <span className="chip">{total} đơn</span>}
        </div>

        <div className="room-tabs" role="tablist" aria-label="Lọc theo trạng thái">
          {TABS.map((tab) => (
            <button
              key={tab.key || 'all'}
              type="button"
              role="tab"
              aria-selected={status === tab.key}
              className={`room-tab ${status === tab.key ? 'is-active' : ''}`}
              onClick={() => setStatus(tab.key)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {loadError && (
          <div className="alert alert--error" role="alert">
            {loadError}
          </div>
        )}
        {!loading && !loadError && items.length === 0 && (
          <div className="state state--empty">
            <p>{status === 'PENDING' ? 'Không có đơn nào đang chờ duyệt.' : 'Không có đơn nghỉ nào.'}</p>
          </div>
        )}

        <ul className="leave-list">
          {items.map((leave) => (
            <LeaveTicket
              key={leave.id}
              leave={leave}
              showRequester
              showLocation={isDirector}
              actions={actionsFor(leave)}
            />
          ))}
        </ul>

        {loading && <p className="state">Đang tải dữ liệu…</p>}
        {!loading && page + 1 < totalPages && (
          <button type="button" className="btn btn--ghost" onClick={() => load(page + 1)}>
            Xem thêm
          </button>
        )}
      </section>

      {approving && (
        <LeaveApprovalDialog leave={approving} onClose={() => setApproving(null)} onApproved={finishApprove} />
      )}

      {revoking && (
        <RevokeLeaveDialog leave={revoking} onClose={() => setRevoking(null)} onRevoked={finishRevoke} />
      )}

      {rejecting && (
        <RejectLeaveDialog leave={rejecting} onClose={() => setRejecting(null)} onRejected={finishReject} />
      )}
    </div>
  );
}
