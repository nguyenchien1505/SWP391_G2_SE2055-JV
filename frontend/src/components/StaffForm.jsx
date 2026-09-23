import { useMemo, useState } from 'react';

const GENDERS = [
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
  { value: 'OTHER', label: 'Khác' },
];

/** Loại Position quyết định quyền nghiệp vụ — BR-ORG-08. */
export const POSITION_TYPE_LABEL = {
  RECEPTION: 'Lễ tân',
  HOUSEKEEPING: 'Dọn dẹp',
  OTHER: 'Khác',
};

const EMPTY = {
  fullName: '',
  email: '',
  phone: '',
  positionId: '',
  startWorkDate: '',
  dateOfBirth: '',
  gender: '',
  address: '',
  avatarUrl: '',
};

/**
 * Tạo / sửa hồ sơ nhân viên — thiết kế "Tạo Hồ Sơ Nhân Viên Mới" (danh_s_ch_form_nh_n_vi_n) trong
 * docs/FE_Lâm_Dũng. Đủ 10 trường bắt buộc của BR-USER-01.
 *
 * <p>Khác thiết kế, theo BR:
 * <ul>
 *   <li>Bỏ Mã nhân viên, CCCD, Loại hợp đồng — DB không có; Milestone 1 không quản lý hợp đồng
 *       (BR-USER-02).</li>
 *   <li>Không chọn Phòng ban riêng: Phòng ban tự suy ra từ Vị trí (BR-ORG-07). Ô Vị trí nhóm theo
 *       Phòng ban để dễ tìm.</li>
 *   <li>Thêm Địa chỉ — BR-USER-01 bắt buộc nhưng thiết kế thiếu.</li>
 *   <li>Khách sạn luôn là khách sạn của Manager đang đăng nhập (BR-PERM-03), không chọn.</li>
 * </ul>
 *
 * <p>Sửa: không đổi được email (username unique toàn hệ thống — BR-USER-06).
 */
export default function StaffForm({ editing, positions, locationName, onSubmit, onCancel, onTerminate }) {
  const [values, setValues] = useState(
    editing
      ? {
          ...EMPTY,
          fullName: editing.fullName ?? '',
          phone: editing.phone ?? '',
          positionId: editing.positionId ?? '',
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

  // BR-ORG-14: Position đã ẩn không được chọn mới, nhưng hồ sơ đang giữ nó thì vẫn hiện để không
  // bị đổi ngầm khi bấm lưu.
  const groups = useMemo(() => {
    const selectable = positions.filter((p) => p.active || p.id === editing?.positionId);
    const byDepartment = {};
    for (const p of selectable) {
      (byDepartment[p.departmentName ?? 'Khác'] ??= []).push(p);
    }
    return Object.entries(byDepartment).sort(([a], [b]) => a.localeCompare(b, 'vi'));
  }, [positions, editing]);

  const selected = positions.find((p) => p.id === values.positionId);
  const noPosition = groups.length === 0;

  function setField(field, value) {
    setValues((prev) => ({ ...prev, [field]: value }));
  }

  /** Chưa có chức năng tải ảnh lên: cho dùng ảnh chữ cái viết tắt làm ảnh đại diện. */
  function fillInitialsAvatar() {
    const name = values.fullName.trim() || 'Nhan vien';
    setField('avatarUrl', `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}`);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);

    const profile = {
      fullName: values.fullName.trim(),
      phone: values.phone.trim(),
      positionId: values.positionId || null,
      startWorkDate: values.startWorkDate || null,
      dateOfBirth: values.dateOfBirth || null,
      gender: values.gender || null,
      address: values.address.trim(),
      avatarUrl: values.avatarUrl.trim(),
    };
    const payload = editing ? profile : { ...profile, role: 'STAFF', email: values.email.trim() };

    const message = await onSubmit(payload);
    setSubmitting(false);
    if (message) {
      setError(message);
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <header className="form__head">
        <span className="form__icon" aria-hidden="true">
          👤
        </span>
        <div>
          <h2>{editing ? 'Cập nhật hồ sơ nhân viên' : 'Tạo hồ sơ nhân viên mới'}</h2>
          <p className="muted">
            Chi nhánh chỉ định: <b>{locationName ?? '—'}</b>
            {editing ? ' · Email đăng nhập không đổi được.' : ' · Hệ thống cấp mật khẩu tạm, bắt buộc đổi ở lần đăng nhập đầu.'}
          </p>
        </div>
      </header>

      <label className="field" htmlFor="stf-avatar">
        <span className="field__label field__label--row">
          <span>
            Ảnh hồ sơ (đường dẫn) <b className="req">*</b>
          </span>
          <button type="button" className="link-button" onClick={fillInitialsAvatar}>
            Dùng ảnh chữ cái viết tắt
          </button>
        </span>
        <input
          id="stf-avatar"
          value={values.avatarUrl}
          onChange={(e) => setField('avatarUrl', e.target.value)}
          placeholder="https://…"
        />
      </label>

      <label className="field" htmlFor="stf-name">
        <span className="field__label">
          Họ và tên đầy đủ <b className="req">*</b>
        </span>
        <input
          id="stf-name"
          value={values.fullName}
          onChange={(e) => setField('fullName', e.target.value)}
          placeholder="Nhập đầy đủ họ và tên…"
        />
      </label>

      <div className="field-row">
        <label className="field" htmlFor="stf-phone">
          <span className="field__label">
            Số điện thoại <b className="req">*</b>
          </span>
          <input
            id="stf-phone"
            type="tel"
            value={values.phone}
            onChange={(e) => setField('phone', e.target.value)}
            placeholder="09xx xxx xxx"
          />
        </label>

        {editing ? (
          <div className="field">
            <span className="field__label">Email (tên đăng nhập)</span>
            <div className="readonly-box">{editing.email}</div>
          </div>
        ) : (
          <label className="field" htmlFor="stf-email">
            <span className="field__label">
              Email (tên đăng nhập) <b className="req">*</b>
            </span>
            <input
              id="stf-email"
              type="email"
              value={values.email}
              onChange={(e) => setField('email', e.target.value)}
              placeholder="name@saomaihotels.vn"
            />
          </label>
        )}
      </div>

      <div className="field-row">
        <label className="field" htmlFor="stf-dob">
          <span className="field__label">
            Ngày sinh <b className="req">*</b>
          </span>
          <input
            id="stf-dob"
            type="date"
            value={values.dateOfBirth}
            onChange={(e) => setField('dateOfBirth', e.target.value)}
          />
        </label>

        <label className="field" htmlFor="stf-gender">
          <span className="field__label">
            Giới tính <b className="req">*</b>
          </span>
          <select id="stf-gender" value={values.gender} onChange={(e) => setField('gender', e.target.value)}>
            <option value="">— Chọn —</option>
            {GENDERS.map((g) => (
              <option key={g.value} value={g.value}>
                {g.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      <label className="field" htmlFor="stf-position">
        <span className="field__label">
          Vị trí chức danh <b className="req">*</b>
        </span>
        <select
          id="stf-position"
          value={values.positionId}
          onChange={(e) => setField('positionId', e.target.value)}
          disabled={noPosition}
        >
          <option value="">{noPosition ? '— Chưa có vị trí nào —' : '— Chọn chức danh —'}</option>
          {groups.map(([department, items]) => (
            <optgroup key={department} label={department}>
              {items.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({POSITION_TYPE_LABEL[p.positionType] ?? p.positionType})
                  {p.active ? '' : ' — đã ngừng sử dụng'}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
        <span className="field__help">
          {noPosition
            ? 'Giám đốc chưa tạo vị trí nào ở "Danh mục" — cần có vị trí trước khi tạo nhân viên.'
            : selected
              ? `Phòng ban: ${selected.departmentName ?? '—'} (tự suy ra từ vị trí).`
              : 'Phòng ban tự suy ra từ vị trí; quyền nghiệp vụ theo loại vị trí (Lễ tân / Dọn dẹp / Khác).'}
        </span>
      </label>

      <div className="field-row">
        <label className="field" htmlFor="stf-start">
          <span className="field__label">
            Ngày bắt đầu làm việc <b className="req">*</b>
          </span>
          <input
            id="stf-start"
            type="date"
            value={values.startWorkDate}
            onChange={(e) => setField('startWorkDate', e.target.value)}
          />
        </label>

        <div className="field">
          <span className="field__label">Khách sạn công tác</span>
          <div className="readonly-box">🏨 {locationName ?? '—'}</div>
        </div>
      </div>

      <label className="field" htmlFor="stf-address">
        <span className="field__label">
          Địa chỉ <b className="req">*</b>
        </span>
        <input
          id="stf-address"
          value={values.address}
          onChange={(e) => setField('address', e.target.value)}
          placeholder="Số nhà, đường, quận/huyện, tỉnh/thành"
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
        <button type="submit" className="btn btn--primary" disabled={submitting || noPosition}>
          {submitting ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : '✓ Lưu hồ sơ nhân viên'}
        </button>
      </div>
    </form>
  );
}
