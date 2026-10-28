import { useEffect, useState } from 'react';
import { readErrorMessage } from '../api/client';
import { fetchMyProfile } from '../api/users';
import { useAuth } from '../context/AuthContext';
import { permissionSummary } from '../permissions';
import { formatDate } from './rooms/format';
import './profile.css';

const ROLE_LABEL = {
  DIRECTOR: 'Giám đốc điều hành',
  MANAGER: 'Quản lý chi nhánh',
  STAFF: 'Nhân viên',
};

const GENDER_LABEL = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };

/** Ai sửa được hồ sơ của vai trò này (BR-PERM-02/03) — để người xem biết báo ai khi thông tin sai. */
function EditorNote({ user }) {
  if (user?.role === 'STAFF') {
    return (
      <p className="muted">
        Hồ sơ do Quản lý khách sạn cập nhật. Thông tin chưa đúng thì báo Quản lý
        {user.locationManagerEmail && (
          <>
            {' '}
            (<a href={`mailto:${user.locationManagerEmail}`}>{user.locationManagerEmail}</a>)
          </>
        )}{' '}
        để sửa.
      </p>
    );
  }
  if (user?.role === 'MANAGER') {
    return <p className="muted">Hồ sơ do Giám đốc cập nhật. Thông tin chưa đúng thì báo Giám đốc để sửa.</p>;
  }
  return <p className="muted">Hồ sơ được tạo khi đăng ký doanh nghiệp.</p>;
}

/** Dòng phụ dưới tên: nhân viên thấy vị trí, quản lý thấy khách sạn phụ trách. */
function subtitleOf(profile) {
  if (profile.role === 'STAFF') {
    return [profile.positionName, profile.departmentName].filter(Boolean).join(' · ') || 'Chưa gán vị trí';
  }
  if (profile.role === 'MANAGER') return profile.locationName ?? 'Chưa gán khách sạn';
  return 'Cấp chuỗi khách sạn';
}

/**
 * Hồ sơ cá nhân — BR-PERM-06: ai làm việc trong Tenant cũng xem được hồ sơ của chính mình. Chỉ để xem: nhân viên
 * do Quản lý khách sạn sửa, Quản lý do Giám đốc sửa (BR-PERM-02/03).
 */
export default function ProfilePage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchMyProfile()
      .then(setProfile)
      .catch((err) => setError(readErrorMessage(err, 'Không tải được hồ sơ.')));
  }, []);

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Tài khoản › Hồ sơ cá nhân</p>
          <h1>Hồ sơ cá nhân</h1>
          <EditorNote user={user} />
        </div>
      </div>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}
      {!profile && !error && <p className="muted">Đang tải hồ sơ…</p>}

      {profile && (
        <section className="panel profile-card">
          <header className="form__head">
            {profile.avatarUrl ? (
              <img className="avatar avatar--img avatar--lg" src={profile.avatarUrl} alt="" />
            ) : (
              <span className="form__icon" aria-hidden="true">
                👤
              </span>
            )}
            <div>
              <h2>{profile.fullName}</h2>
              <p className="muted">{subtitleOf(profile)}</p>
              <span className="badge badge--blue">{ROLE_LABEL[profile.role] ?? profile.role}</span>
            </div>
          </header>

          <dl className="pw-dialog__info">
            <dt>Email đăng nhập</dt>
            <dd>{profile.email}</dd>
            <dt>Số điện thoại</dt>
            <dd>{profile.phone ?? '—'}</dd>
            {profile.role === 'STAFF' && (
              <>
                <dt>Vị trí công việc</dt>
                <dd>{profile.positionName ?? '—'}</dd>
                <dt>Phòng ban</dt>
                <dd>{profile.departmentName ?? '—'}</dd>
                <dt>Quyền nghiệp vụ</dt>
                <dd>{permissionSummary(profile.permissions)}</dd>
              </>
            )}
            {profile.role !== 'DIRECTOR' && (
              <>
                <dt>Khách sạn công tác</dt>
                <dd>🏨 {profile.locationName ?? '—'}</dd>
              </>
            )}
            <dt>Ngày bắt đầu làm việc</dt>
            <dd>{formatDate(profile.startWorkDate)}</dd>
            <dt>Ngày sinh</dt>
            <dd>{formatDate(profile.dateOfBirth)}</dd>
            <dt>Giới tính</dt>
            <dd>{GENDER_LABEL[profile.gender] ?? '—'}</dd>
            <dt>Địa chỉ</dt>
            <dd>{profile.address ?? '—'}</dd>
            <dt>Ngày tạo tài khoản</dt>
            <dd>{formatDate(profile.createdAt?.slice(0, 10))}</dd>
          </dl>
        </section>
      )}
    </div>
  );
}
