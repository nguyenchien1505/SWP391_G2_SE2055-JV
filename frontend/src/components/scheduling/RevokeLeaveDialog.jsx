import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { revokeLeaveRequest } from '../../api/leaveRequests';
import { formatDate, todayIso } from '../../pages/rooms/format';
import { daysInclusive, formatLeaveRange, leaveTypeMeta } from '../../pages/scheduling/leaveLabels';

/** Khớp @Size(max = 500) của RevokeLeaveRequestRequest. */
const REASON_MAX = 500;

/**
 * Hủy phần ngày CHƯA TỚI của đơn đã duyệt — BR-SCH-16 bản sửa 28/10/2026. Người gửi không tự hủy được đơn đã
 * duyệt; người duyệt làm việc này, bắt buộc lý do:
 * <ul>
 *   <li>chưa tới ngày nghỉ → hủy cả đơn;</li>
 *   <li>đang nghỉ dở → rút ngắn tới hết hôm nay (hôm nay coi như đã tới — BR-SCH-17).</li>
 * </ul>
 * Phép năm tự hoàn đúng số ngày bị hủy. Ca đã gỡ lúc duyệt không tự gán lại — quản lý xếp lại nếu cần.
 * Backend tự tính lại mọi thứ; hộp thoại chỉ nói trước điều sẽ xảy ra.
 *
 * @param onRevoked nhận đơn sau khi hủy để màn hình báo lại và tải lại danh sách
 */
export default function RevokeLeaveDialog({ leave, onClose, onRevoked }) {
  const today = todayIso();
  const whole = leave.fromDate > today;
  const cancelledDays = whole ? leave.days : daysInclusive(today, leave.toDate) - 1;
  const type = leaveTypeMeta(leave.leaveType);

  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Esc để đóng, trừ lúc đang gửi — tránh đóng giữa chừng rồi không biết kết quả.
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!reason.trim()) {
      setError('Nhập lý do hủy — người gửi đơn sẽ đọc được.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      onRevoked(await revokeLeaveRequest(leave.id, reason.trim()));
    } catch (err) {
      setError(readErrorMessage(err, 'Không hủy được đơn nghỉ này.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="leave-revoke-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="leave-revoke-title">{whole ? 'Hủy đơn nghỉ đã duyệt' : 'Hủy phần còn lại của đơn nghỉ'}</h2>

        <div className="modal__body">
          <div className="readonly-box">
            <p>
              <b>{leave.requesterName}</b>
              {type.label} · {formatLeaveRange(leave.fromDate, leave.toDate)} ({leave.days} ngày)
            </p>
            <p>
              {whole
                ? `Chưa tới ngày nghỉ: hủy cả đơn (${cancelledDays} ngày).`
                : `Đang nghỉ dở: nghỉ tới hết hôm nay ${formatDate(today)}, hủy ${cancelledDays} ngày còn lại.`}
              {leave.leaveType === 'ANNUAL' ? ` Quỹ phép năm hoàn lại ${cancelledDays} ngày.` : ''}
            </p>
            {/* Quản lý khách sạn không có ca (chốt 05/10/2026) — đơn của họ không gỡ ca nào. */}
            {leave.requesterRole !== 'MANAGER' && (
              <p className="muted">Các ca đã gỡ lúc duyệt không tự gán lại — xếp lại ở «Xếp lịch làm việc» nếu cần.</p>
            )}
          </div>

          <label className="field">
            <span className="field__label">
              Lý do hủy <span className="req">*</span>
            </span>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={REASON_MAX}
              rows={3}
              autoFocus
              disabled={submitting}
              placeholder="Ví dụ: khách đoàn đến sớm, cần đủ người"
            />
            <small className="field__help">
              Người gửi đơn đọc được lý do này. {reason.length}/{REASON_MAX}
            </small>
          </label>

          {error && (
            <div className="alert alert--error" role="alert">
              {error}
            </div>
          )}
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={submitting}>
            Đóng
          </button>
          <button type="submit" className="btn btn--danger" disabled={submitting}>
            {submitting ? 'Đang hủy…' : whole ? 'Hủy cả đơn' : `Hủy ${cancelledDays} ngày còn lại`}
          </button>
        </div>
      </form>
    </div>
  );
}
