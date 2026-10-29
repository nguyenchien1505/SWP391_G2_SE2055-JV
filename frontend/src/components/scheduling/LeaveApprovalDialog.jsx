import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { approveLeaveRequest, fetchLeaveApprovalPreview } from '../../api/leaveRequests';
import { formatDate } from '../../pages/rooms/format';
import { formatLeaveRange, leaveTypeMeta } from '../../pages/scheduling/leaveLabels';
import { timeRange } from '../../pages/scheduling/scheduleFormat';

/**
 * Duyệt đơn nghỉ — xem hệ quả TRƯỚC rồi mới xác nhận (BR-SCH-07, BR-SCH-17, BR-SCH-29, BR-SCH-31):
 * <ul>
 *   <li>ca từ ngày mai trong khoảng nghỉ sẽ bị gỡ người;</li>
 *   <li>ca của chính hôm nay (và ca đã check-in) giữ nguyên — quản lý tự gỡ tay nếu cần;</li>
 *   <li>ca sẽ mất lễ tân duy nhất chỉ là CẢNH BÁO — vẫn duyệt được;</li>
 *   <li>phép năm: quỹ của người gửi, bị trừ khi duyệt.</li>
 * </ul>
 *
 * @param onApproved nhận kết quả duyệt (các ca ĐÃ gỡ, cảnh báo) để màn hình báo lại
 */
export default function LeaveApprovalDialog({ leave, onClose, onApproved }) {
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchLeaveApprovalPreview(leave.id)
      .then((data) => !cancelled && setPreview(data))
      .catch((err) => !cancelled && setError(readErrorMessage(err, 'Không tải được các ca bị ảnh hưởng.')));
    return () => {
      cancelled = true;
    };
  }, [leave.id]);

  // Esc để đóng, trừ lúc đang gửi — tránh đóng giữa chừng rồi không biết kết quả.
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  async function approve() {
    setSubmitting(true);
    setError('');
    try {
      onApproved(await approveLeaveRequest(leave.id));
    } catch (err) {
      setError(readErrorMessage(err, 'Không duyệt được đơn này.'));
      setSubmitting(false);
    }
  }

  const balance = preview?.balance;

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="leave-approve-title"
         onClick={() => !submitting && onClose()}>
      <div className="modal leave-approve" onClick={(e) => e.stopPropagation()}>
        <h2 id="leave-approve-title">Duyệt đơn nghỉ của {leave.requesterName ?? 'nhân viên'}</h2>

        <div className="modal__body">
          <div className="readonly-box">
            <b>
              {leaveTypeMeta(leave.leaveType).label} · {formatLeaveRange(leave.fromDate, leave.toDate)} ·{' '}
              {leave.days} ngày
            </b>
            {leave.reason}
          </div>

          {!preview && !error && <p className="state">Đang tính các ca bị ảnh hưởng…</p>}

          {preview && (
            <>
              {balance && (
                <p className="muted">
                  Quỹ phép năm {balance.year}: đã tích lũy {balance.entitledDays}/{balance.daysPerYear} ngày, đã dùng{' '}
                  {balance.usedDays}, đang chờ duyệt {balance.pendingDays} (gồm đơn này). Duyệt xong quỹ bị trừ{' '}
                  {leave.days} ngày.
                </p>
              )}

              <h3>Ca sẽ gỡ khỏi lịch ({preview.releasedShifts.length})</h3>
              {preview.releasedShifts.length === 0 ? (
                <p className="muted">Không có ca nào từ ngày mai trong khoảng nghỉ — lịch làm việc không đổi.</p>
              ) : (
                <ul className="leave-impact">
                  {preview.releasedShifts.map((shift) => (
                    <ShiftLine key={shift.shiftId} shift={shift} />
                  ))}
                </ul>
              )}

              {preview.keptShifts.length > 0 && (
                <>
                  <h3>Ca giữ nguyên ({preview.keptShifts.length})</h3>
                  <ul className="leave-impact">
                    {preview.keptShifts.map((shift) => (
                      <ShiftLine key={shift.shiftId} shift={shift} />
                    ))}
                  </ul>
                </>
              )}

              {preview.receptionWarnings.length > 0 && (
                <div className="alert alert--warn" role="status">
                  <b>Ca sẽ không còn lễ tân:</b> {preview.receptionWarnings.join(', ')}. Bạn vẫn duyệt được — nhớ xếp
                  một lễ tân khác cho {preview.receptionWarnings.length > 1 ? 'các ca này' : 'ca này'}.
                </div>
              )}
            </>
          )}

          {error && (
            <div className="alert alert--error" role="alert">
              {error}
            </div>
          )}
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={submitting}>
            Hủy
          </button>
          <button type="button" className="btn btn--primary" onClick={approve} disabled={submitting || !preview}>
            {submitting ? 'Đang duyệt…' : 'Duyệt đơn'}
          </button>
        </div>
      </div>
    </div>
  );
}

/** "Ca chiều · 28/10/2026 · 14:00 – 22:00", kèm lý do giữ nếu có. */
function ShiftLine({ shift }) {
  return (
    <li>
      <b>{shift.templateName ?? 'Ca giờ khác'}</b> · {formatDate(shift.shiftDate)} ·{' '}
      {timeRange(shift.startTime, shift.endTime)}
      {shift.note && <span className="leave-impact__note">{shift.note}</span>}
    </li>
  );
}
