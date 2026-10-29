import { useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { createLeaveRequest } from '../../api/leaveRequests';
import { formatDate, todayIso } from '../../pages/rooms/format';
import { daysInclusive } from '../../pages/scheduling/leaveLabels';

/** Khớp @Size(max = 500) của CreateLeaveRequestRequest và cột leave_requests.reason. */
const REASON_MAX = 500;

/**
 * Gửi đơn xin nghỉ — BR-SCH-06 bản sửa M2, BR-SCH-07, BR-SCH-16, BR-SCH-28. Đặt trong FormModal.
 *
 * Ô ngày chặn sẵn ngày chắc chắn không hợp lệ (ngày đã qua; phép năm chưa đủ báo trước) để người dùng khỏi
 * bấm rồi mới nhận lỗi. Mọi luật vẫn do backend kiểm lại — câu lỗi của backend hiện nguyên văn.
 *
 * @param balance       quỹ phép năm (GET /balance); null khi chưa tải được — vẫn gửi được, backend kiểm
 * @param approverLabel ai duyệt đơn: "Quản lý khách sạn" / "Giám đốc"
 * @param onCreated     nhận đơn vừa tạo
 */
export default function LeaveRequestForm({ balance, approverLabel, onCreated, onCancel }) {
  const today = todayIso();
  // Hồ sơ chưa có ngày bắt đầu làm việc thì chưa tính được quỹ — chỉ còn nghỉ không lương.
  const annualAllowed = balance ? Boolean(balance.startWorkDate) : true;
  const [values, setValues] = useState({
    leaveType: annualAllowed ? 'ANNUAL' : 'UNPAID',
    fromDate: '',
    toDate: '',
    reason: '',
  });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const annual = values.leaveType === 'ANNUAL';
  const minFrom = annual ? balance?.earliestAnnualStart ?? today : today;
  const days = daysInclusive(values.fromDate, values.toDate);
  const remainingAfter = annual && balance ? balance.remainingDays - days : null;

  function chooseType(leaveType) {
    // Đổi loại thì mốc sớm nhất đổi theo — ngày đã chọn không còn hợp lệ thì chọn lại.
    const nextMin = leaveType === 'ANNUAL' ? balance?.earliestAnnualStart ?? today : today;
    setValues((prev) => (prev.fromDate && prev.fromDate < nextMin
      ? { ...prev, leaveType, fromDate: '', toDate: '' }
      : { ...prev, leaveType }));
  }

  /** Chọn ngày bắt đầu thì ngày kết thúc theo sang, nếu chưa chọn hoặc đang trước ngày bắt đầu. */
  function chooseFrom(fromDate) {
    setValues((prev) => ({
      ...prev,
      fromDate,
      toDate: !prev.toDate || prev.toDate < fromDate ? fromDate : prev.toDate,
    }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    if (!values.fromDate || !values.toDate) {
      setError('Chọn ngày bắt đầu và ngày kết thúc nghỉ.');
      return;
    }
    if (values.toDate < values.fromDate) {
      setError('Ngày kết thúc không được trước ngày bắt đầu.');
      return;
    }
    if (!values.reason.trim()) {
      setError('Nhập lý do nghỉ.');
      return;
    }
    setSubmitting(true);
    try {
      onCreated(await createLeaveRequest({ ...values, reason: values.reason.trim() }));
    } catch (err) {
      setError(readErrorMessage(err, 'Không gửi được đơn nghỉ.'));
      setSubmitting(false);
    }
  }

  return (
    <form className="form leave-form" onSubmit={handleSubmit} noValidate>
      <header className="form__head">
        <span className="form__icon" aria-hidden="true">
          🗓
        </span>
        <div>
          <h2>Tạo đơn xin nghỉ</h2>
          <p className="muted">
            Đơn của bạn do <b>{approverLabel}</b> duyệt. Nghỉ theo ngày nguyên, tính cả ngày bắt đầu và ngày kết
            thúc.
          </p>
        </div>
      </header>

      {/* Không bọc trong .field: luật ".field input" kéo nút radio rộng hết thẻ. */}
      <fieldset className="choice-group choice-fieldset">
        <legend className="field__label">
          Loại nghỉ <b className="req">*</b>
        </legend>
        <label className={`choice ${annual ? 'choice--active' : ''} ${annualAllowed ? '' : 'is-disabled'}`}>
          <input
            type="radio"
            name="leaveType"
            value="ANNUAL"
            checked={annual}
            disabled={!annualAllowed}
            onChange={() => chooseType('ANNUAL')}
          />
          <span>
            <b>Nghỉ phép năm</b>
            <small>
              Có lương, trừ quỹ phép
              {balance ? ` — còn ${balance.remainingDays} ngày` : ''}. Gửi trước ít nhất {balance?.noticeDays ?? 3}{' '}
              ngày.
            </small>
          </span>
        </label>
        <label className={`choice ${!annual ? 'choice--active' : ''}`}>
          <input
            type="radio"
            name="leaveType"
            value="UNPAID"
            checked={!annual}
            onChange={() => chooseType('UNPAID')}
          />
          <span>
            <b>Nghỉ không lương</b>
            <small>Không trừ quỹ phép. Gửi được cả cho hôm nay.</small>
          </span>
        </label>
      </fieldset>
      {!annualAllowed && (
        <p className="field__help">
          Hồ sơ của bạn chưa có ngày bắt đầu làm việc nên chưa tính được quỹ phép năm — nhờ quản lý cập nhật hồ sơ.
        </p>
      )}

      <div className="field-row">
        <label className="field" htmlFor="leave-from">
          <span className="field__label">
            Từ ngày <b className="req">*</b>
          </span>
          <input
            id="leave-from"
            type="date"
            min={minFrom}
            value={values.fromDate}
            onChange={(e) => chooseFrom(e.target.value)}
          />
        </label>
        <label className="field" htmlFor="leave-to">
          <span className="field__label">
            Đến ngày <b className="req">*</b>
          </span>
          <input
            id="leave-to"
            type="date"
            min={values.fromDate || minFrom}
            value={values.toDate}
            onChange={(e) => setValues((prev) => ({ ...prev, toDate: e.target.value }))}
          />
        </label>
      </div>
      <p className="leave-days">
        {days > 0 ? `Nghỉ ${days} ngày.` : `Sớm nhất được nghỉ từ ngày ${formatDate(minFrom)}.`}
        {remainingAfter !== null && days > 0 && (remainingAfter >= 0
          ? ` Sau đơn này quỹ phép năm còn ${remainingAfter} ngày.`
          : ` Vượt quỹ phép năm ${-remainingAfter} ngày — rút ngắn đơn hoặc chọn nghỉ không lương.`)}
      </p>

      <label className="field" htmlFor="leave-reason">
        <span className="field__label">
          Lý do <b className="req">*</b>
        </span>
        <textarea
          id="leave-reason"
          rows={3}
          maxLength={REASON_MAX}
          value={values.reason}
          onChange={(e) => setValues((prev) => ({ ...prev, reason: e.target.value }))}
          placeholder="Ví dụ: về quê dự đám cưới, đưa con đi khám bệnh"
        />
        <span className="field__help">
          {values.reason.length}/{REASON_MAX}
        </span>
      </label>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      <div className="form__actions">
        <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={submitting}>
          Hủy bỏ
        </button>
        <button type="submit" className="btn btn--primary" disabled={submitting}>
          {submitting ? 'Đang gửi…' : 'Gửi đơn'}
        </button>
      </div>
    </form>
  );
}
