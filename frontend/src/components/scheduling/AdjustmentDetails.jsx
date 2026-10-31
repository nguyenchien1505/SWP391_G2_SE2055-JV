import { formatDate, formatDateTime } from '../../pages/rooms/format';
import { timeRange } from '../../pages/scheduling/scheduleFormat';
import { adjustmentStatusMeta } from '../../pages/scheduling/attendance';

/**
 * Nội dung một đơn điều chỉnh công — dùng chung cho trang của nhân viên và trang duyệt của quản lý: ca, giờ đang
 * có so với giờ đề nghị, lý do, và kết quả xử lý.
 *
 * @param showRequester hiện tên người gửi (trang duyệt)
 */
export default function AdjustmentDetails({ request, showRequester = false }) {
  const { shift } = request;
  const status = adjustmentStatusMeta(request.status);
  return (
    <div className="adjustment">
      <div className="adjustment__head">
        <b>
          {showRequester && `${request.requesterName ?? 'Nhân viên'} · `}
          Ca {shift ? `${formatDate(shift.shiftDate)} · ${timeRange(shift.startTime, shift.endTime)}` : '—'}
        </b>
        <span className={`tt-pill tt-pill--${status.tone}`}>{status.label}</span>
      </div>
      <dl className="adjustment__times">
        <dt>Giờ vào</dt>
        <dd>
          {formatDateTime(shift?.checkInAt)}
          {request.requestedCheckInAt && <> → <b>{formatDateTime(request.requestedCheckInAt)}</b></>}
        </dd>
        <dt>Giờ ra</dt>
        <dd>
          {formatDateTime(shift?.checkOutAt)}
          {request.requestedCheckOutAt && <> → <b>{formatDateTime(request.requestedCheckOutAt)}</b></>}
        </dd>
      </dl>
      <p className="adjustment__reason">Lý do: {request.reason}</p>
      <small className="muted">
        Gửi lúc {formatDateTime(request.createdAt)}
        {request.decidedAt && ` · xử lý lúc ${formatDateTime(request.decidedAt)}`}
        {request.reviewerName && ` bởi ${request.reviewerName}`}
      </small>
      {request.reviewNote && <p className="adjustment__note">Ghi chú của quản lý: {request.reviewNote}</p>}
    </div>
  );
}
