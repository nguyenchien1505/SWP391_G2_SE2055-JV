import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { changePassword } from '../api/auth';
import { readErrorMessage } from '../api/client';
import Logo from '../components/Logo';

const ROLE_LABEL = {
  PLATFORM_ADMIN: 'Quản trị nền tảng',
  DIRECTOR: 'Giám đốc điều hành',
  MANAGER: 'Quản lý chi nhánh',
  STAFF: 'Nhân viên',
};

/**
 * Tiêu chuẩn mật khẩu theo thiết kế "Đổi mật khẩu lần đầu" trong docs/FE_Lâm_Dũng.
 * Backend hiện chỉ bắt buộc tối thiểu 8 ký tự (ChangePasswordRequest), nên 3 quy tắc còn
 * lại mới chỉ được kiểm ở đây.
 */
const RULES = [
  { id: 'len', label: 'Tối thiểu 8 ký tự', test: (v) => v.length >= 8 },
  { id: 'case', label: 'Ít nhất 1 chữ hoa và 1 chữ thường', test: (v) => /[a-z]/.test(v) && /[A-Z]/.test(v) },
  { id: 'digit', label: 'Bao gồm ít nhất 1 chữ số', test: (v) => /\d/.test(v) },
  { id: 'special', label: '1 ký tự đặc biệt (@, #, $...)', test: (v) => /[^A-Za-z0-9]/.test(v) },
];

const STRENGTH_LABEL = ['Chưa nhập', 'Yếu', 'Trung bình', 'Khá', 'Mạnh'];

export default function ChangePasswordPage() {
  const { user, refresh, signOut } = useAuth();
  const navigate = useNavigate();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [reveal, setReveal] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const passed = useMemo(() => RULES.filter((rule) => rule.test(newPassword)).length, [newPassword]);
  const strength = newPassword === '' ? 0 : passed;
  const mismatch = confirmPassword !== '' && confirmPassword !== newPassword;
  const canSubmit =
    currentPassword !== '' && passed === RULES.length && !mismatch && confirmPassword !== '';

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');

    if (newPassword === currentPassword) {
      setError('Mật khẩu mới phải khác mật khẩu hiện tại.');
      return;
    }
    setSubmitting(true);

    try {
      await changePassword(currentPassword, newPassword);

      // Backend giữ principal trong session nên cờ mustChangePassword có thể vẫn còn cũ.
      // Nếu vậy thì buộc đăng nhập lại; khi backend đọc lại user mỗi request thì nhánh
      // này tự hết tác dụng và người dùng vào thẳng hệ thống.
      const me = await refresh();
      if (me?.mustChangePassword) {
        await signOut();
        navigate('/dang-nhap?notice=password_changed', { replace: true });
      } else {
        navigate('/khach-san', { replace: true });
      }
    } catch (err) {
      setError(readErrorMessage(err, 'Đổi mật khẩu không thành công.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <header className="login-card__head">
          <Logo />
          <span className="version-chip">Bảo mật tài khoản</span>
        </header>

        <div className="login-card__body">
          <h1>Thiết lập mật khẩu mới</h1>
          <p className="muted">
            Tài khoản đang dùng mật khẩu tạm do quản lý cấp. Đổi mật khẩu để tiếp tục sử dụng
            hệ thống.
          </p>

          <div className="readonly-box">
            <b>Tài khoản đang đăng nhập</b>
            <p>
              {user?.email}
              <br />
              <small className="muted">{ROLE_LABEL[user?.role] ?? user?.role}</small>
            </p>
          </div>

          <form onSubmit={handleSubmit} noValidate>
            <label className="field" htmlFor="current-password">
              <span className="field__label">
                Mật khẩu tạm hiện tại <b className="req">*</b>
              </span>
              <span className="field__control">
                <span className="field__icon" aria-hidden="true">
                  🔑
                </span>
                <input
                  id="current-password"
                  type={reveal ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  required
                />
              </span>
            </label>

            <label className="field" htmlFor="new-password">
              <span className="field__label field__label--row">
                <span>
                  Mật khẩu mới <b className="req">*</b>
                </span>
                <button type="button" className="link-button" onClick={() => setReveal((v) => !v)}>
                  {reveal ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                </button>
              </span>
              <span className="field__control">
                <span className="field__icon" aria-hidden="true">
                  🔒
                </span>
                <input
                  id="new-password"
                  type={reveal ? 'text' : 'password'}
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  required
                />
              </span>
            </label>

            <div className="pw-strength">
              <span className="pw-strength__label">
                Độ mạnh mật khẩu: <b>{STRENGTH_LABEL[strength]}</b>
              </span>
              <span className="pw-strength__bar">
                <i className={`pw-strength__fill pw-strength__fill--${strength}`} />
              </span>
            </div>

            <ul className="pw-rules">
              {RULES.map((rule) => {
                const ok = rule.test(newPassword);
                return (
                  <li key={rule.id} className={`pw-rule ${ok ? 'pw-rule--ok' : ''}`}>
                    <span aria-hidden="true">{ok ? '✓' : '○'}</span> {rule.label}
                  </li>
                );
              })}
            </ul>

            <label className="field" htmlFor="confirm-password">
              <span className="field__label">
                Xác nhận mật khẩu mới <b className="req">*</b>
              </span>
              <span className="field__control">
                <span className="field__icon" aria-hidden="true">
                  🔒
                </span>
                <input
                  id="confirm-password"
                  type={reveal ? 'text' : 'password'}
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                />
              </span>
            </label>
            {mismatch && <p className="field__help field__help--error">Mật khẩu xác nhận không khớp.</p>}

            {error && (
              <div className="alert alert--error" role="alert">
                {error}
              </div>
            )}

            <button
              type="submit"
              className="btn btn--primary btn--block"
              disabled={submitting || !canSubmit}
            >
              {submitting ? 'Đang cập nhật…' : 'Xác nhận & vào hệ thống'}
            </button>
          </form>

          <button
            type="button"
            className="btn btn--ghost btn--block"
            onClick={async () => {
              await signOut();
              navigate('/dang-nhap', { replace: true });
            }}
          >
            Đăng xuất
          </button>
        </div>

        <footer className="login-card__foot">
          <span className="dot dot--online" aria-hidden="true" />
          <span>Mật khẩu tạm chỉ dùng một lần — BR-USER-07</span>
        </footer>
      </div>
    </div>
  );
}
