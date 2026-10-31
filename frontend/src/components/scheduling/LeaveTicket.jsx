import { formatDateTime } from '../../pages/rooms/format';
import { formatLeaveRange, leaveStatusMeta, leaveTypeMeta, revokeSummary } from '../../pages/scheduling/leaveLabels';

/**
 * Một đơn xin nghỉ dạng thẻ — dùng ở cả màn "Đơn xin nghỉ của tôi" lẫn màn "Duyệt đơn nghỉ".
 *
 * @param showRequester màn duyệt: tiêu đề là người gửi (kèm vị trí), khoảng nghỉ xuống dòng dưới
 * @param showLocation  Giám đốc duyệt đơn của quản lý nhiều khách sạn — ghi tên khách sạn
 * @param actions       [{ key, label, tone, onClick }] — `tone` là hậu tố `btn--*`, mặc định primary
 */
export default function LeaveTicket({ leave, showRequester = false, showLocation = false, actions = [] }) {
  const type = leaveTypeMeta(leave.leaveType);
  const status = leaveStatusMeta(leave.status);
  const range = `${formatLeaveRange(leave.fromDate, leave.toDate)} · ${leave.days} ngày`;
  const requester = [leave.requesterName ?? 'Không rõ', leave.positionName].filter(Boolean).join(' · ');
  const revoked = revokeSummary(leave);

  return (
    <li className="leave-ticket">
      <div className="leave-ticket__head">
        <span className="leave-ticket__title">{showRequester ? requester : range}</span>
        <span className="leave-ticket__badges">
          <span className={`badge badge--${type.tone}`}>{type.label}</span>
          <span className={`badge badge--${status.tone}`}>{status.label}</span>
          {leave.originalToDate && <span className="badge badge--grey">Rút ngắn</span>}
        </span>
      </div>
      {showRequester && <span className="leave-ticket__when">{range}</span>}
      <p className="leave-ticket__reason">{leave.reason}</p>
      <span className="leave-ticket__meta">
        Gửi lúc {formatDateTime(leave.createdAt)}
        {showLocation && leave.locationName ? ` · ${leave.locationName}` : ''}
      </span>
      {leave.decidedAt && (
        <span className="leave-ticket__meta">
          {/* Đơn bị hủy sau khi duyệt vẫn giữ lần duyệt — dòng dưới mới là lần hủy. */}
          {revoked ? 'Đã duyệt' : status.label} bởi {leave.approverName ?? '—'} lúc {formatDateTime(leave.decidedAt)}
        </span>
      )}
      {/* Chốt 31/10/2026: từ chối bắt buộc lý do — người gửi đọc được ngay trên đơn của mình. */}
      {leave.rejectReason && (
        <span className="leave-ticket__reject">Lý do từ chối: {leave.rejectReason}</span>
      )}
      {revoked && (
        <span className="leave-ticket__revoke">
          {revoked} — {leave.revokedByName ?? '—'}, {formatDateTime(leave.revokedAt)}: {leave.revokeReason}
        </span>
      )}
      {actions.length > 0 && (
        <div className="leave-ticket__actions">
          {actions.map((action) => (
            <button
              key={action.key}
              type="button"
              className={`btn btn--sm btn--${action.tone ?? 'primary'}`}
              onClick={action.onClick}
            >
              {action.label}
            </button>
          ))}
        </div>
      )}
    </li>
  );
}
