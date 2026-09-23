import { useState } from 'react';

const GENDERS = [
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
  { value: 'OTHER', label: 'Khác' },
];

const EMPTY = {
  fullName: '',
  email: '',
  phone: '',
  locationId: '',
  startWorkDate: '',
  dateOfBirth: '',
  gender: '',
  address: '',
  avatarUrl: '',
};

/**
 * Tạo / sửa tài khoản Manager — thiết kế "Cấp tài khoản Quản lý mới" trong docs/FE_Lâm_Dũng.
 *
 * <p>Thiết kế chỉ có 4 trường, nhưng BR-USER-05 bắt buộc Manager có đủ hồ sơ như Staff (trừ
 * Position): ngày bắt đầu làm việc, ngày sinh, giới tính, địa chỉ, ảnh đại diện — backend từ
 * chối nếu thiếu. Khi thiết kế và BR mâu thuẫn thì theo BR.
 *
 * <p>Không đổi được email (username unique toàn hệ thống — BR-USER-06) và khách sạn (đổi
 * Location phải qua luồng điều chuyển có duyệt — BR-TRF-02) khi sửa.
 */
export default function ManagerForm({ editing, freeLocations, locationName, onSubmit, onCancel, onTerminate }) {
  const [values, setValues] = useState(
    editing
      ? {
          ...EMPTY,
          fullName: editing.fullName ?? '',
          phone: editing.phone ?? '',
          startWorkDate: editing.startWorkDate ?? '',
          dateOfBirth: editing.dateOfBirth ?? '',
          gender: editing.gender ?? '',
          address: editing.address ?? '',
          avatarUrl: editing.avatarUrl ?? '',
        }
      : EMPTY,
  );
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const noFreeLocation = !editing && freeLocations.length === 0;

  function setField(field, value) {
    setValues((prev) => ({ ...prev, [field]: value }));
  }

  /** Chưa có chức năng tải ảnh lên: cho dùng ảnh chữ cái viết tắt làm ảnh đại diện. */
  function fillInitialsAvatar() {
    const name = values.fullName.trim() || 'Manager';
    setField('avatarUrl', `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}`);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);

    const profile = {
      fullName: values.fullName.trim(),
      phone: values.phone.trim(),
      startWorkDate: values.startWorkDate || null,
      dateOfBirth: values.dateOfBirth || null,
      gender: values.gender || null,
      address: values.address.trim(),
      avatarUrl: values.avatarUrl.trim(),
    };
    const payload = editing
      ? profile
      : { ...profile, role: 'MANAGER', email: values.email.trim(), locationId: values.locationId || null };

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
          🪪
        </span>
        <div>
          <h2>{editing ? 'Cập nhật hồ sơ Quản lý' : 'Cấp tài khoản Quản lý mới'}</h2>
          <p className="muted">
            {editing
              ? 'Email đăng nhập và khách sạn phụ trách không đổi được tại đây.'
              : 'Hệ thống tạo tài khoản và cấp mật khẩu tạm, bắt buộc đổi ở lần đăng nhập đầu.'}
          </p>
        </div>
      </header>

      <label className="field" htmlFor="mgr-name">
        <span className="field__label">
          Họ và tên Quản lý <b className="req">*</b>
        </span>
        <input
          id="mgr-name"
          value={values.fullName}
          onChange={(e) => setField('fullName', e.target.value)}
          placeholder="Nhập đầy đủ họ và tên…"
        />
      </label>

      {editing ? (
        <div className="readonly-box">
          <b>Email đăng nhập</b>
          <p>{editing.email}</p>
          <b>Khách sạn phụ trách</b>
          <p>{locationName ?? '—'}</p>
        </div>
      ) : (
        <>
          <label className="field" htmlFor="mgr-email">
            <span className="field__label">
              Email (tên đăng nhập) <b className="req">*</b>
            </span>
            <input
              id="mgr-email"
              type="email"
              value={values.email}
              onChange={(e) => setField('email', e.target.value)}
              placeholder="name@saomaihotels.vn"
            />
            <span className="field__help">Duy nhất toàn hệ thống, không đổi được sau khi tạo.</span>
          </label>

          <label className="field" htmlFor="mgr-location">
            <span className="field__label">
              Khách sạn điều hành <b className="req">*</b>
            </span>
            <select
              id="mgr-location"
              value={values.locationId}
              onChange={(e) => setField('locationId', e.target.value)}
              disabled={noFreeLocation}
            >
              <option value="">— Chọn khách sạn chưa có quản lý —</option>
              {freeLocations.map((loc) => (
                <option key={loc.id} value={loc.id}>
                  {loc.name} ({loc.totalRooms} phòng)
                </option>
              ))}
            </select>
            <span className="field__help">
              {noFreeLocation
                ? 'Mọi khách sạn đều đã có quản lý. Thêm khách sạn mới ở màn "Danh sách khách sạn".'
                : 'Mỗi khách sạn chỉ có một Quản lý; có quản lý thì khách sạn chuyển "Đang hoạt động".'}
            </span>
          </label>
        </>
      )}

      <div className="field-row">
        <label className="field" htmlFor="mgr-phone">
          <span className="field__label">
            Số điện thoại <b className="req">*</b>
          </span>
          <input
            id="mgr-phone"
            type="tel"
            value={values.phone}
            onChange={(e) => setField('phone', e.target.value)}
            placeholder="09xx xxx xxx"
          />
        </label>

        <label className="field" htmlFor="mgr-gender">
          <span className="field__label">
            Giới tính <b className="req">*</b>
          </span>
          <select id="mgr-gender" value={values.gender} onChange={(e) => setField('gender', e.target.value)}>
            <option value="">— Chọn —</option>
            {GENDERS.map((g) => (
              <option key={g.value} value={g.value}>
                {g.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="field-row">
        <label className="field" htmlFor="mgr-dob">
          <span className="field__label">
            Ngày sinh <b className="req">*</b>
          </span>
          <input
            id="mgr-dob"
            type="date"
            value={values.dateOfBirth}
            onChange={(e) => setField('dateOfBirth', e.target.value)}
          />
        </label>

        <label className="field" htmlFor="mgr-start">
          <span className="field__label">
            Ngày bắt đầu làm <b className="req">*</b>
          </span>
          <input
            id="mgr-start"
            type="date"
            value={values.startWorkDate}
            onChange={(e) => setField('startWorkDate', e.target.value)}
          />
        </label>
      </div>

      <label className="field" htmlFor="mgr-address">
        <span className="field__label">
          Địa chỉ <b className="req">*</b>
        </span>
        <input
          id="mgr-address"
          value={values.address}
          onChange={(e) => setField('address', e.target.value)}
          placeholder="Số nhà, đường, quận/huyện, tỉnh/thành"
        />
      </label>

      <label className="field" htmlFor="mgr-avatar">
        <span className="field__label field__label--row">
          <span>
            Ảnh đại diện (đường dẫn) <b className="req">*</b>
          </span>
          <button type="button" className="link-button" onClick={fillInitialsAvatar}>
            Dùng ảnh chữ cái viết tắt
          </button>
        </span>
        <input
          id="mgr-avatar"
          value={values.avatarUrl}
          onChange={(e) => setField('avatarUrl', e.target.value)}
          placeholder="https://…"
        />
      </label>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      <div className="form__actions">
        {editing && (
          <>
            <button type="button" className="btn btn--danger" onClick={onTerminate}>
              Cho nghỉ việc
            </button>
            <span className="spacer" />
          </>
        )}
        <button type="button" className="btn btn--ghost" onClick={onCancel}>
          Hủy bỏ
        </button>
        <button type="submit" className="btn btn--primary" disabled={submitting || noFreeLocation}>
          {submitting ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : '🔑 Khởi tạo & cấp mật khẩu tạm'}
        </button>
      </div>
    </form>
  );
}
