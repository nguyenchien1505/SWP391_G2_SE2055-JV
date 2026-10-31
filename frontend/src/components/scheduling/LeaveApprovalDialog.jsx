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
 *   <li>ca sẽ mất lễ tân duy nhất: mỗi ca phải có lễ tân, nên chọn lễ tân thay cho TỪNG ca thì mới duyệt được
 *       (BR-SCH-29 sửa 31/10/2026). Danh sách chỉ có người backend đã kiểm là xếp được;</li>
 *   <li>phép năm: quỹ của người gửi, bị trừ khi duyệt.</li>
 * </ul>
 *
 * @param onApproved nhận kết quả duyệt (các ca ĐÃ gỡ, lễ tân đã xếp thay) để màn hình báo lại
 */
export default function LeaveApprovalDialog({ leave, onClose, onApproved }) {
  const [preview, setPreview] = useState(null);
  const [chosen, setChosen] = useState({}); // shiftId → staffId của lễ tân thay
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

  const gaps = preview?.receptionGaps ?? [];
  const missing = gaps.filter((gap) => !chosen[gap.shift.shiftId]).length;
  // Có ca không ai thay được thì chọn gì cũng không duyệt được — dòng báo đỏ của ca đó đã nói phải làm gì.
  const noCandidate = gaps.some((gap) => gap.candidates.length === 0);

  async function approve() {
    setSubmitting(true);
    setError('');
    try {
      const replacements = gaps.map((gap) => ({ shiftId: gap.shift.shiftId, staffId: chosen[gap.shift.shiftId] }));
      onApproved(await approveLeaveRequest(leave.id, replacements));
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

              {gaps.length > 0 && (
                <section className="leave-gaps" aria-labelledby="leave-gaps-title">
                  <h3 id="leave-gaps-title">Chọn lễ tân thay ({gaps.length})</h3>
                  <p className="muted">
                    Mỗi ca phải có lễ tân. {leave.requesterName ?? 'Người này'} là lễ tân duy nhất của{' '}
                    {gaps.length > 1 ? 'các ca dưới đây' : 'ca dưới đây'} — chọn người thay vào ca thì mới duyệt được.
                  </p>
                  <ul className="leave-impact">
                    {gaps.map((gap) => (
                      <ShiftLine key={gap.shift.shiftId} shift={gap.shift}>
                        {gap.candidates.length === 0 ? (
                          <span className="leave-gap__none" role="alert">
                            Không có lễ tân nào xếp được vào ca này (trùng ca, vượt giờ hoặc đang nghỉ). Sắp lại lịch ở
                            «Xếp lịch làm việc» rồi duyệt lại, hoặc từ chối đơn.
                          </span>
                        ) : (
                          <label className="field leave-gap__field">
                            <span className="field__label">
                              Lễ tân thay <span className="req">*</span>
                            </span>
                            <select
                              value={chosen[gap.shift.shiftId] ?? ''}
                              onChange={(e) => setChosen((prev) => ({ ...prev, [gap.shift.shiftId]: e.target.value }))}
                              disabled={submitting}
                            >
                              <option value="">— Chọn lễ tân —</option>
                              {gap.candidates.map((person) => (
                                <option key={person.staffId} value={person.staffId}>
                                  {person.fullName}
                                  {person.positionName ? ` · ${person.positionName}` : ''}
                                </option>
                              ))}
                            </select>
                          </label>
                        )}
                      </ShiftLine>
                    ))}
                  </ul>
                  {missing > 0 && !noCandidate && (
                    <p className="leave-gaps__hint">
                      Còn {missing} ca chưa có lễ tân thay — chọn đủ thì nút «Xếp lễ tân & duyệt» mới bấm được.
                    </p>
                  )}
                </section>
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
          <button type="button" className="btn btn--primary" onClick={approve}
                  disabled={submitting || !preview || missing > 0}>
            {submitting ? 'Đang duyệt…' : gaps.length > 0 ? 'Xếp lễ tân & duyệt' : 'Duyệt đơn'}
          </button>
        </div>
      </div>
    </div>
  );
}

/** "Ca chiều · 28/10/2026 · 14:00 – 22:00", kèm lý do giữ nếu có; `children` là phần chọn lễ tân thay. */
function ShiftLine({ shift, children }) {
  return (
    <li>
      <b>{shift.templateName ?? 'Ca giờ khác'}</b> · {formatDate(shift.shiftDate)} ·{' '}
      {timeRange(shift.startTime, shift.endTime)}
      {shift.note && <span className="leave-impact__note">{shift.note}</span>}
      {children}
    </li>
  );
}
