import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { readErrorMessage } from '../api/client';
import { verifyEmail } from '../api/auth';
import Logo from '../components/Logo';

/**
 * Trang mở từ nút "Xác thực tài khoản" trong email. Người dùng bấm nút ở đây mới gọi API —
 * bộ quét link của Gmail/Outlook chỉ mở trang chứ không bấm nút, nên không tiêu mất mã.
 */
export default function VerifyEmailPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  const [error, setError] = useState(token ? '' : 'Link xác thực không hợp lệ.');
  const [submitting, setSubmitting] = useState(false);

  async function handleVerify() {
    setError('');
    setSubmitting(true);
    try {
      await verifyEmail(token);
      navigate('/dang-nhap?notice=email_verified', { replace: true });
    } catch (err) {
      setError(readErrorMessage(err, 'Xác thực không thành công.'));
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
          <h1>Xác thực tài khoản</h1>
          <p className="muted">
            Bấm nút bên dưới để kích hoạt tài khoản Giám đốc của bạn. Sau khi xác thực, bạn có thể
            đăng nhập vào hệ thống.
          </p>

          {error && (
            <div className="alert alert--error" role="alert">
              {error}
            </div>
          )}

          <button
            type="button"
            className="btn btn--primary btn--block"
            onClick={handleVerify}
            disabled={submitting || !token}
          >
            {submitting ? 'Đang xác thực…' : 'Xác thực tài khoản'}
          </button>

          <p className="muted">
            <Link to="/dang-nhap" className="link-button">Quay lại đăng nhập</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
