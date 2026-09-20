import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { readErrorMessage } from '../api/client';
import { startGoogleLogin } from '../api/auth';
import Logo from '../components/Logo';

const REMEMBERED_EMAIL_KEY = 'saomai.rememberedEmail';

/** Backend đẩy về đây kèm query khi luồng Google kết thúc — xem app.frontend.* trong application.yaml. */
const REDIRECT_MESSAGES = {
  oauth_unauthorized:
    'Tài khoản Google này chưa được cấp quyền truy cập. Liên hệ Quản lý hoặc Giám đốc để được tạo tài khoản (BR-USER-03).',
  oauth_failed: 'Đăng nhập Google không thành công. Vui lòng thử lại.',
};

const REDIRECT_NOTICES = {
  password_changed: 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại bằng mật khẩu mới.',
};

export default function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const remembered = localStorage.getItem(REMEMBERED_EMAIL_KEY) ?? '';
  const [email, setEmail] = useState(remembered);
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(Boolean(remembered));
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState(REDIRECT_MESSAGES[searchParams.get('error')] ?? '');
  const [hint, setHint] = useState(REDIRECT_NOTICES[searchParams.get('notice')] ?? '');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setHint('');
    setSubmitting(true);

    try {
      const me = await signIn(email.trim(), password);

      if (remember) {
        localStorage.setItem(REMEMBERED_EMAIL_KEY, email.trim());
      } else {
        localStorage.removeItem(REMEMBERED_EMAIL_KEY);
      }

      // BR-USER-07: tài khoản còn mật khẩu tạm phải đổi trước khi vào hệ thống.
      navigate(me.mustChangePassword ? '/doi-mat-khau' : '/khach-san', { replace: true });
    } catch (err) {
      setError(
        err?.response?.status === 401
          ? 'Email hoặc mật khẩu không đúng.'
          : readErrorMessage(err, 'Đăng nhập không thành công.'),
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <header className="login-card__head">
          <Logo />
          <span className="version-chip">v2.4.1</span>
        </header>

        <div className="login-card__body">
          <h1>Đăng nhập vào hệ thống</h1>
          <p className="muted">
            Nhập thông tin tài khoản được cấp để truy cập không gian làm việc của bạn
          </p>

          <form onSubmit={handleSubmit} noValidate>
            <label className="field" htmlFor="email">
              <span className="field__label">
                Email đăng nhập <b className="req">*</b>
              </span>
              <span className="field__control">
                <span className="field__icon" aria-hidden="true">
                  ✉
                </span>
                <input
                  id="email"
                  type="email"
                  autoComplete="username"
                  placeholder="vi_du@saomaihotels.vn"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </span>
            </label>
            <p className="field__help">
              <span aria-hidden="true">ⓘ</span> Email định danh duy nhất toàn hệ thống, áp dụng
              cho mọi cơ sở.
            </p>

            <label className="field" htmlFor="password">
              <span className="field__label field__label--row">
                <span>
                  Mật khẩu <b className="req">*</b>
                </span>
                <button
                  type="button"
                  className="link-button"
                  onClick={() =>
                    setHint(
                      'Hệ thống chưa có chức năng tự đặt lại mật khẩu. Liên hệ Quản lý hoặc Giám đốc để được cấp mật khẩu tạm (BR-USER-03).',
                    )
                  }
                >
                  Quên mật khẩu?
                </button>
              </span>
              <span className="field__control">
                <span className="field__icon" aria-hidden="true">
                  🔒
                </span>
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Nhập mật khẩu"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="field__reveal"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showPassword ? '🙈' : '👁'}
                </button>
              </span>
            </label>

            <label className="checkbox">
              <input
                type="checkbox"
                checked={remember}
                onChange={(e) => setRemember(e.target.checked)}
              />
              <span>Ghi nhớ email đăng nhập trên thiết bị này</span>
            </label>

            {error && (
              <div className="alert alert--error" role="alert">
                {error}
              </div>
            )}
            {hint && !error && (
              <div className="alert alert--info" role="status">
                {hint}
              </div>
            )}

            <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
              {submitting ? 'Đang đăng nhập…' : 'Đăng nhập hệ thống'}
              {!submitting && <span aria-hidden="true"> →</span>}
            </button>
          </form>

          <div className="or-divider">
            <span>hoặc</span>
          </div>

          {/* Điều hướng cả trang sang backend: luồng OAuth2 đi qua redirect, axios không theo được. */}
          <button
            type="button"
            className="btn btn--ghost btn--block btn--google"
            onClick={startGoogleLogin}
            disabled={submitting}
          >
            <span className="btn__g" aria-hidden="true">
              G
            </span>
            Đăng nhập bằng Google
          </button>
          <p className="field__help">
            <span aria-hidden="true">ⓘ</span> Chỉ dùng được với email đã được cấp tài khoản trong
            hệ thống.
          </p>

          <div className="support-box">
            <span className="support-box__icon" aria-hidden="true">
              ☎
            </span>
            <p>
              Cần hỗ trợ truy cập? Liên hệ <b>Quản trị viên chi nhánh</b> hoặc hotline nội bộ:
              <br />
              <a href="tel:19006868">1900 6868</a>
            </p>
          </div>
        </div>

        <footer className="login-card__foot">
          <span className="dot dot--online" aria-hidden="true" />
          <span>Sao Mai Hospitality PMS &amp; Ops · Multi-Tenant</span>
          <span className="spacer" />
          <span className="version-chip">v2.4.1 Production-VN</span>
          <span>© 2025 Sao Mai Group</span>
        </footer>
      </div>
    </div>
  );
}
