import { useState } from 'react';

// Backend validate múi giờ bằng ZoneId.of() và chặn giá trị lạ (BR-SCH-17 dùng nó để xác
// định "ca tương lai"), nên cho chọn từ danh sách thay vì gõ tay.
const TIMEZONES = [
  'Asia/Ho_Chi_Minh',
  'Asia/Bangkok',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Asia/Seoul',
];

const EMPTY = {
  name: '',
  address: '',
  phone: '',
  starRating: '3',
  timezone: 'Asia/Ho_Chi_Minh',
};

export default function LocationForm({ editing, canManage, onSubmit, onCancel }) {
  const [values, setValues] = useState(
    editing
      ? {
          name: editing.name ?? '',
          address: editing.address ?? '',
          phone: editing.phone ?? '',
          starRating: editing.starRating ? String(editing.starRating) : '',
          timezone: editing.timezone ?? 'Asia/Ho_Chi_Minh',
        }
      : EMPTY,
  );
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // BR-ORG-03: Manager chỉ sửa được thông tin vận hành của Location mình phụ trách.
  const contactOnly = Boolean(editing) && !canManage;

  function setField(field, value) {
    setValues((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);

    const payload = {
      name: values.name.trim(),
      address: values.address.trim(),
      phone: values.phone.trim(),
      starRating: values.starRating === '' ? null : Number(values.starRating),
      timezone: values.timezone,
    };

    const message = await onSubmit(payload);
    setSubmitting(false);

    if (message) {
      setError(message);
    } else if (!editing) {
      setValues(EMPTY);
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <header className="form__head">
        <span className="form__icon" aria-hidden="true">
          🏨
        </span>
        <div>
          <h2>{editing ? 'Cập nhật khách sạn' : 'Thêm khách sạn mới'}</h2>
          <p className="muted">
            {editing
              ? contactOnly
                ? 'Bạn chỉ được sửa địa chỉ và số điện thoại liên hệ'
                : 'Chỉnh sửa thông tin chi nhánh trong chuỗi'
              : 'Thiết lập chi nhánh mới vào chuỗi vận hành'}
          </p>
        </div>
      </header>

      <label className="field" htmlFor="loc-name">
        <span className="field__label">
          Tên khách sạn <b className="req">*</b>
        </span>
        <input
          id="loc-name"
          value={values.name}
          onChange={(e) => setField('name', e.target.value)}
          placeholder="Sao Mai Nha Trang"
          disabled={contactOnly}
          required
        />
      </label>

      <label className="field" htmlFor="loc-address">
        <span className="field__label">
          Địa chỉ chi tiết <b className="req">*</b>
        </span>
        <input
          id="loc-address"
          value={values.address}
          onChange={(e) => setField('address', e.target.value)}
          placeholder="12 Trần Phú, TP. Nha Trang"
          required
        />
      </label>

      <div className="field-row">
        <label className="field" htmlFor="loc-phone">
          <span className="field__label">
            Số điện thoại <b className="req">*</b>
          </span>
          <input
            id="loc-phone"
            value={values.phone}
            onChange={(e) => setField('phone', e.target.value)}
            placeholder="0258.3912.888"
            required
          />
        </label>

        <label className="field" htmlFor="loc-star">
          <span className="field__label">Hạng sao</span>
          <select
            id="loc-star"
            value={values.starRating}
            onChange={(e) => setField('starRating', e.target.value)}
            disabled={contactOnly}
          >
            <option value="">Chưa xếp hạng</option>
            {[1, 2, 3, 4, 5].map((n) => (
              <option key={n} value={String(n)}>
                {n} sao
              </option>
            ))}
          </select>
        </label>
      </div>

      <label className="field" htmlFor="loc-tz">
        <span className="field__label">Múi giờ hoạt động</span>
        <select
          id="loc-tz"
          value={values.timezone}
          onChange={(e) => setField('timezone', e.target.value)}
          disabled={contactOnly}
        >
          {TIMEZONES.map((tz) => (
            <option key={tz} value={tz}>
              {tz}
            </option>
          ))}
        </select>
        <span className="field__help">
          Dùng để xác định ca làm việc thuộc ngày nào tại chi nhánh này.
        </span>
      </label>

      <div className="readonly-box">
        <b>Trạng thái vận hành</b>
        <p>
          {editing
            ? editing.status === 'OPERATIONAL'
              ? 'Đang hoạt động — chi nhánh đã có quản lý phụ trách.'
              : 'Chưa vận hành — cần gán một Quản lý chi nhánh để kích hoạt.'
            : 'Khách sạn mới sẽ ở trạng thái “Chưa vận hành” cho tới khi được gán quản lý.'}
        </p>
        <small className="muted">
          Trạng thái do hệ thống tự đặt theo việc có quản lý hay không, không chỉnh tay được.
        </small>
      </div>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      <div className="form__actions">
        {editing && (
          <button type="button" className="btn btn--ghost" onClick={onCancel}>
            Hủy bỏ
          </button>
        )}
        <button type="submit" className="btn btn--primary" disabled={submitting}>
          {submitting ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Lưu khách sạn'}
        </button>
      </div>
    </form>
  );
}
