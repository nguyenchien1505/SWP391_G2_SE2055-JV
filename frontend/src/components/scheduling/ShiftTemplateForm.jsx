import { useState } from 'react';
import { durationHours, formatHours, hhmm, isOvernight, timeRange } from '../../pages/scheduling/scheduleFormat';

/**
 * Form thêm / sửa mẫu ca — BR-SCH-22: tên, giờ bắt đầu, giờ kết thúc, mô tả (tùy chọn). Bật/tắt mẫu
 * nằm ở bảng danh sách, không ở đây.
 *
 * Bộ mẫu (chốt 06/10/2026): mẫu thuộc bộ CHUNG của chuỗi, hoặc bộ RIÊNG của một chi nhánh; mỗi chi
 * nhánh dùng đúng một bộ. Chỉ chọn bộ lúc tạo — sửa thì không đổi được, vì ca đã xếp đang trỏ vào mẫu.
 * Thêm vào bộ riêng KHÔNG tự bật bộ riêng cho chi nhánh.
 *
 * Giờ kết thúc nhỏ hơn giờ bắt đầu là hợp lệ: đó là ca qua đêm, tính trọn giờ vào ngày bắt đầu
 * (BR-SCH-03).
 *
 * @param locations         danh sách chi nhánh của chuỗi — cho ô "Thuộc bộ"
 * @param initialLocationId mở sẵn bộ riêng của chi nhánh này (nút "+ Thêm mẫu riêng" trên bảng chi nhánh)
 * @param onSubmit  nhận payload, trả về câu lỗi (chuỗi) nếu lưu thất bại, hoặc null khi thành công —
 *                  cùng quy ước với LocationForm.
 */
export default function ShiftTemplateForm({ editing, locations = [], initialLocationId = null, onSubmit, onCancel }) {
  const [values, setValues] = useState({
    locationId: editing?.locationId ?? initialLocationId ?? '',
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
  const setName = (locationId) => (locationId
    ? `Bộ riêng của ${locations.find((l) => l.id === locationId)?.name ?? 'một chi nhánh'}`
    : 'Bộ mẫu chung của chuỗi');

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    const message = await onSubmit({
      // Phạm vi chỉ gửi khi tạo — backend không đổi phạm vi của mẫu đã có.
      ...(editing ? {} : { locationId: values.locationId || null }),
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
          <p className="muted">Manager chọn mẫu khi xếp ca, trong bộ mẫu chi nhánh mình đang dùng.</p>
        </div>
      </header>

      {editing ? (
        <div className="readonly-box">
          <b>Thuộc: {setName(editing.locationId)}</b>
          <p>Bộ của mẫu chọn lúc tạo và không đổi được.</p>
        </div>
      ) : (
        <label className="field" htmlFor="template-scope">
          <span className="field__label">
            Thuộc bộ <span className="req">*</span>
          </span>
          <select id="template-scope" value={values.locationId} onChange={set('locationId')}>
            <option value="">Bộ mẫu chung của chuỗi</option>
            {locations.map((location) => (
              <option key={location.id} value={location.id}>
                Bộ riêng — {location.name}
              </option>
            ))}
          </select>
          <span className="field__help">
            {values.locationId
              ? 'Chi nhánh chỉ dùng bộ riêng sau khi bạn bấm «Chuyển sang bộ riêng» ở bảng chi nhánh.'
              : 'Chi nhánh đang dùng bộ chung sẽ thấy mẫu này.'}{' '}
            Không đổi được bộ sau khi tạo.
          </span>
        </label>
      )}

      <label className="field" htmlFor="template-name">
        <span className="field__label">
          Tên mẫu ca <span className="req">*</span>
        </span>
        <input id="template-name" maxLength={100} value={values.name} onChange={set('name')} placeholder="Ví dụ: Ca sáng" />
        <span className="field__help">
          Tên không trùng với mẫu khác trong cùng bộ.
        </span>
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
