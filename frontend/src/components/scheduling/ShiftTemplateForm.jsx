import { useState } from 'react';
import { durationHours, formatHours, hhmm, isOvernight, timeRange } from '../../pages/scheduling/scheduleFormat';

/**
 * Form thêm / sửa mẫu ca — BR-SCH-22: tên, giờ bắt đầu, giờ kết thúc, mô tả (tùy chọn). Bật/tắt mẫu
 * nằm ở bảng danh sách, không ở đây.
 *
 * Giờ kết thúc nhỏ hơn giờ bắt đầu là hợp lệ: đó là ca qua đêm, tính trọn giờ vào ngày bắt đầu
 * (BR-SCH-03).
 *
 * @param onSubmit nhận payload, trả về câu lỗi (chuỗi) nếu lưu thất bại, hoặc null khi thành công —
 *                 cùng quy ước với LocationForm.
 */
export default function ShiftTemplateForm({ editing, onSubmit, onCancel }) {
  const [values, setValues] = useState({
    name: editing?.name ?? '',
    startTime: hhmm(editing?.startTime),
    endTime: hhmm(editing?.endTime),
    description: editing?.description ?? '',
  });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const set = (field) => (event) => setValues((prev) => ({ ...prev, [field]: event.target.value }));
  const hasHours = Boolean(values.startTime && values.endTime);
  const nameMissing = values.name.trim() === '';

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    const message = await onSubmit({
      name: values.name.trim(),
      startTime: values.startTime,
      endTime: values.endTime,
      description: values.description.trim() || null,
    });
    setSubmitting(false);
    if (message) setError(message);
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <header className="form__head">
        <span className="form__icon" aria-hidden="true">🕘</span>
        <div>
          <h2>{editing ? 'Sửa mẫu ca' : 'Thêm mẫu ca'}</h2>
          <p className="muted">Mẫu dùng chung cho mọi khách sạn; Manager chọn mẫu khi xếp ca.</p>
        </div>
      </header>

      <label className="field" htmlFor="template-name">
        <span className="field__label">
          Tên mẫu ca <span className="req">*</span>
        </span>
        <input id="template-name" maxLength={100} value={values.name} onChange={set('name')} placeholder="Ví dụ: Ca sáng" />
      </label>

      <div className="field-row">
        <label className="field" htmlFor="template-start">
          <span className="field__label">
            Giờ bắt đầu <span className="req">*</span>
          </span>
          <input id="template-start" type="time" value={values.startTime} onChange={set('startTime')} />
        </label>
        <label className="field" htmlFor="template-end">
          <span className="field__label">
            Giờ kết thúc <span className="req">*</span>
          </span>
          <input id="template-end" type="time" value={values.endTime} onChange={set('endTime')} />
        </label>
      </div>

      {hasHours && (
        <p className={`shift-preview ${durationHours(values.startTime, values.endTime) >= 24 ? 'shift-preview--warn' : ''}`}>
          <b>{timeRange(values.startTime, values.endTime)}</b> ·{' '}
          <b>{formatHours(durationHours(values.startTime, values.endTime))}</b>
          {isOvernight(values.startTime, values.endTime)
            && (durationHours(values.startTime, values.endTime) >= 24
              ? ' — giờ bắt đầu trùng giờ kết thúc nên thành ca 24 giờ.'
              : ' — ca qua đêm, toàn bộ giờ tính vào ngày bắt đầu ca.')}
        </p>
      )}

      <label className="field" htmlFor="template-description">
        <span className="field__label">Mô tả</span>
        <input
          id="template-description"
          maxLength={500}
          value={values.description}
          onChange={set('description')}
          placeholder="Không bắt buộc"
        />
      </label>

      {editing && (
        <p className="field__help">
          Đổi giờ mẫu chỉ áp cho ca xếp về sau; các ca đã xếp từ mẫu này giữ nguyên giờ cũ.
        </p>
      )}

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      <div className="form__actions">
        <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={submitting}>
          Hủy bỏ
        </button>
        <button type="submit" className="btn btn--primary" disabled={submitting || nameMissing || !hasHours}>
          {submitting ? 'Đang lưu…' : 'Lưu mẫu ca'}
        </button>
      </div>
    </form>
  );
}
