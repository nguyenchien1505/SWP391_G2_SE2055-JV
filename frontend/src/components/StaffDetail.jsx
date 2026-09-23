import { POSITION_TYPE_LABEL } from './StaffForm';

const GENDER_LABEL = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString('vi-VN') : '—';
}

/** Xem hồ sơ nhân viên (chỉ đọc). Đặt trong FormModal; `onEdit` null thì ẩn nút sửa. */
export default function StaffDetail({ staff, position, locationName, status, onClose, onEdit }) {
  return (
    <div className="form">
      <header className="form__head">
        {staff.avatarUrl ? (
          <img className="avatar avatar--img avatar--lg" src={staff.avatarUrl} alt="" />
        ) : (
          <span className="form__icon" aria-hidden="true">
            👤
          </span>
        )}
        <div>
          <h2>{staff.fullName}</h2>
          <p className="muted">
            {position?.name ?? '—'} · {position?.departmentName ?? '—'}
          </p>
          <span className={`badge badge--${status.tone}`}>{status.label}</span>
        </div>
      </header>

      <dl className="pw-dialog__info">
        <dt>Email đăng nhập</dt>
        <dd>{staff.email}</dd>
        <dt>Số điện thoại</dt>
        <dd>{staff.phone}</dd>
        <dt>Loại vị trí</dt>
        <dd>{POSITION_TYPE_LABEL[position?.positionType] ?? '—'}</dd>
        <dt>Khách sạn công tác</dt>
        <dd>🏨 {locationName ?? '—'}</dd>
        <dt>Ngày bắt đầu làm việc</dt>
        <dd>{formatDate(staff.startWorkDate)}</dd>
        <dt>Ngày sinh</dt>
        <dd>{formatDate(staff.dateOfBirth)}</dd>
        <dt>Giới tính</dt>
        <dd>{GENDER_LABEL[staff.gender] ?? '—'}</dd>
        <dt>Địa chỉ</dt>
        <dd>{staff.address ?? '—'}</dd>
        <dt>Ngày tạo tài khoản</dt>
        <dd>{formatDate(staff.createdAt)}</dd>
        {staff.terminatedAt && (
          <>
            <dt>Ngày nghỉ việc</dt>
            <dd>{formatDate(staff.terminatedAt)}</dd>
          </>
        )}
      </dl>

      <div className="form__actions">
        <button type="button" className="btn btn--ghost" onClick={onClose}>
          Đóng
        </button>
        {onEdit && (
          <button type="button" className="btn btn--primary" onClick={onEdit}>
            ✏️ Sửa hồ sơ
          </button>
        )}
      </div>
    </div>
  );
}
