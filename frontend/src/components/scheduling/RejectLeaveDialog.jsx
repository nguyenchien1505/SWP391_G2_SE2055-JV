import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { rejectLeaveRequest } from '../../api/leaveRequests';
import { formatLeaveRange, leaveTypeMeta } from '../../pages/scheduling/leaveLabels';

/** Khớp @Size(max = 500) của RejectLeaveRequestRequest. */
const REASON_MAX = 500;

/**
 * Từ chối đơn nghỉ đang chờ duyệt — BR-SCH-08, BR-SCH-23. Bắt buộc lý do (chốt 31/10/2026): người gửi đọc được lý do
 * ngay trên đơn của mình ở «Đơn xin nghỉ». Lịch làm việc không đổi.
 *
 * @param onRejected nhận đơn sau khi từ chối để màn hình báo lại và tải lại danh sách
 */
export default function RejectLeaveDialog({ leave, onClose, onRejected }) {
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
      setError('Nhập lý do từ chối — người gửi đơn sẽ đọc được.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      onRejected(await rejectLeaveRequest(leave.id, reason.trim()));
    } catch (err) {
      setError(readErrorMessage(err, 'Không từ chối được đơn nghỉ này.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="leave-reject-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="leave-reject-title">Từ chối đơn nghỉ</h2>

        <div className="modal__body">
          <div className="readonly-box">
            <p>
              <b>{leave.requesterName}</b>
              {type.label} · {formatLeaveRange(leave.fromDate, leave.toDate)} ({leave.days} ngày)
            </p>
            <p>Lý do xin nghỉ: {leave.reason}</p>
            <p className="muted">Lịch làm việc giữ nguyên.</p>
          </div>

          <label className="field">
            <span className="field__label">
              Lý do từ chối <span className="req">*</span>
            </span>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={REASON_MAX}
              rows={3}
              autoFocus
              disabled={submitting}
              placeholder="Ví dụ: hôm đó khách đoàn nhận nhiều phòng, cần đủ người"
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
            {submitting ? 'Đang từ chối…' : 'Từ chối đơn'}
          </button>
        </div>
      </form>
    </div>
  );
}
