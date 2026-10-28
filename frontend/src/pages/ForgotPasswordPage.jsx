import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { readErrorMessage } from '../api/client';
import { requestPasswordReset } from '../api/auth';
import Logo from '../components/Logo';

/**
 * Nhân viên quên mật khẩu — gửi yêu cầu, hệ thống email cho Quản lý khách sạn của họ. Quản lý cấp mật khẩu tạm ở
 * «Nhân viên chi nhánh» như thường lệ; trang này không đổi gì trên tài khoản. Kết quả hiện giống nhau dù email có
 * tài khoản hay không, để không ai dò được email nào đã đăng ký.
 */
export default function ForgotPasswordPage() {
  const [searchParams] = useSearchParams();
  const [email, setEmail] = useState(searchParams.get('email') ?? '');
  const [error, setError] = useState('');
  const [sentTo, setSentTo] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    const value = email.trim();
    if (!value) {
      setError('Nhập email đăng nhập của bạn.');
      return;
    }
    setError('');
    setSubmitting(true);
    try {
      await requestPasswordReset(value);
      setSentTo(value);
    } catch (err) {
      setError(readErrorMessage(err, 'Không gửi được yêu cầu. Vui lòng thử lại.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <header className="login-card__head">
          <Logo />
        </header>

        <div className="login-card__body">
          <h1>Quên mật khẩu</h1>

          {sentTo ? (
            <div className="alert alert--success" role="status">
              Đã ghi nhận yêu cầu. Nếu <b>{sentTo}</b> là tài khoản nhân viên đang làm việc, Quản lý khách sạn của
              bạn sẽ nhận được email và cấp mật khẩu tạm mới — gửi tới email này hoặc báo trực tiếp cho bạn. Trong lúc
              chờ, mật khẩu cũ vẫn giữ nguyên.
            </div>
          ) : (
            <>
              <p className="muted">
                Nhập email đăng nhập. Hệ thống sẽ báo Quản lý khách sạn của bạn để cấp mật khẩu tạm mới.
              </p>

              <form onSubmit={handleSubmit} noValidate>
                <label className="field" htmlFor="forgot-email">
                  <span className="field__label">
                    Email đăng nhập <b className="req">*</b>
                  </span>
                  <span className="field__control">
                    <span className="field__icon" aria-hidden="true">
                      ✉
                    </span>
                    <input
                      id="forgot-email"
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
                  <span aria-hidden="true">ⓘ</span> Quản lý cấp lại xong, mật khẩu tạm được gửi về email này.
                </p>

                {error && (
                  <div className="alert alert--error" role="alert">
                    {error}
                  </div>
                )}

                <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
                  {submitting ? 'Đang gửi…' : 'Gửi yêu cầu cho Quản lý'}
                </button>
              </form>
            </>
          )}

          <div className="support-box">
            <span className="support-box__icon" aria-hidden="true">
              ☎
            </span>
            <p>
              Form này dành cho nhân viên. Quản lý khách sạn quên mật khẩu thì liên hệ Giám đốc để được cấp lại; Giám
              đốc liên hệ hotline nội bộ: <a href="tel:19006868">1900 6868</a>
            </p>
          </div>

          <p className="muted">
            <Link to="/dang-nhap" className="link-button">Quay lại đăng nhập</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
