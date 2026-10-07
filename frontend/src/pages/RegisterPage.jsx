import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { readErrorMessage } from '../api/client';
import { registerTenant } from '../api/auth';
import Logo from '../components/Logo';

/** BR-SAAS-13: 5 trường khớp RegisterTenantRequest; "Nhập lại mật khẩu" chỉ kiểm ở FE. */
const FIELDS = [
  { key: 'companyName', label: 'Tên công ty / chuỗi khách sạn', type: 'text', icon: '🏢', autoComplete: 'organization', placeholder: 'Công ty TNHH Khách sạn Sao Mai' },
  { key: 'representativeName', label: 'Họ tên người đại diện', type: 'text', icon: '👤', autoComplete: 'name', placeholder: 'Nguyễn Văn A' },
  { key: 'email', label: 'Email đăng nhập', type: 'email', icon: '✉', autoComplete: 'username', placeholder: 'giamdoc@congty.vn' },
  { key: 'phone', label: 'Số điện thoại', type: 'tel', icon: '☎', autoComplete: 'tel', placeholder: '0901234567' },
  { key: 'password', label: 'Mật khẩu', type: 'password', icon: '🔒', autoComplete: 'new-password', placeholder: '8–72 ký tự' },
  { key: 'confirmPassword', label: 'Nhập lại mật khẩu', type: 'password', icon: '🔒', autoComplete: 'new-password', placeholder: 'Nhập lại mật khẩu' },
];

const EMPTY_FORM = Object.fromEntries(FIELDS.map((f) => [f.key, '']));
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// Cùng biểu thức với @Pattern của RegisterTenantRequest.phone.
const PHONE_PATTERN = /^\+?[0-9]{8,15}$/;

/** Cùng ràng buộc với @Valid phía backend để báo lỗi ngay, không phải chờ 422. */
function validate(form) {
  const errors = {};
  const company = form.companyName.trim();
  const name = form.representativeName.trim();
  const email = form.email.trim();

  if (!company) errors.companyName = 'Tên công ty là bắt buộc';
  else if (company.length > 255) errors.companyName = 'Tên công ty tối đa 255 ký tự';

  if (!name) errors.representativeName = 'Họ tên người đại diện là bắt buộc';
  else if (name.length > 255) errors.representativeName = 'Họ tên tối đa 255 ký tự';

  if (!email) errors.email = 'Email là bắt buộc';
  else if (email.length > 255 || !EMAIL_PATTERN.test(email)) errors.email = 'Email không đúng định dạng';

  if (!PHONE_PATTERN.test(form.phone.trim())) errors.phone = 'Số điện thoại phải gồm 8–15 chữ số';

  if (form.password.length < 8 || form.password.length > 72) {
    errors.password = 'Mật khẩu phải từ 8 đến 72 ký tự';
  }
  if (form.confirmPassword !== form.password) errors.confirmPassword = 'Mật khẩu nhập lại không khớp';
  return errors;
}

export default function RegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  function update(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }));
    // Sửa ô nào thì xóa lỗi của ô đó.
    setFieldErrors((prev) => ({ ...prev, [key]: undefined }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      await registerTenant({
        companyName: form.companyName.trim(),
        representativeName: form.representativeName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        password: form.password,
      });
      // Tenant và tài khoản Giám đốc đã được tạo nhưng chưa đăng nhập được: phải bấm link xác
      // thực trong email trước.
      navigate('/dang-nhap?notice=verify_email', { replace: true });
    } catch (err) {
      const list = err?.response?.data?.fieldErrors;
      if (Array.isArray(list) && list.length > 0) {
        setFieldErrors(Object.fromEntries(list.map((fe) => [fe.field, fe.message])));
      }
      // 409: hai request cùng email chạy song song, UNIQUE ở DB chặn một request.
      setError(
        err?.response?.status === 409
          ? 'Email đã được sử dụng.'
          : readErrorMessage(err, 'Đăng ký không thành công.'),
      );
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
          <h1>Đăng ký doanh nghiệp</h1>
          <p className="muted">
            Tạo không gian làm việc cho công ty của bạn. Tài khoản Giám đốc được tạo cùng lúc và
            được dùng thử miễn phí.
          </p>

          <form onSubmit={handleSubmit} noValidate>
            {FIELDS.map(({ key, label, type, icon, autoComplete, placeholder }) => (
              <div key={key}>
                <label className="field" htmlFor={key}>
                  <span className="field__label">
                    {label} <b className="req">*</b>
                  </span>
                  <span className="field__control">
                    <span className="field__icon" aria-hidden="true">{icon}</span>
                    <input
                      id={key}
                      type={type === 'password' && showPassword ? 'text' : type}
                      autoComplete={autoComplete}
                      placeholder={placeholder}
                      value={form[key]}
                      onChange={(e) => update(key, e.target.value)}
                      aria-invalid={Boolean(fieldErrors[key])}
                      required
                    />
                    {key === 'password' && (
                      <button
                        type="button"
                        className="field__reveal"
                        onClick={() => setShowPassword((v) => !v)}
                        aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                      >
                        {showPassword ? '🙈' : '👁'}
                      </button>
                    )}
                  </span>
                </label>
                {fieldErrors[key] && (
                  <p className="field__help field__help--error">{fieldErrors[key]}</p>
                )}
                {key === 'email' && !fieldErrors.email && (
                  <p className="field__help">
                    <span aria-hidden="true">ⓘ</span> Email này là tên đăng nhập của Giám đốc,
                    duy nhất toàn hệ thống.
                  </p>
                )}
              </div>
            ))}

            {error && (
              <div className="alert alert--error" role="alert">
                {error}
              </div>
            )}

            <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
              {submitting ? 'Đang đăng ký…' : 'Đăng ký dùng thử'}
              {!submitting && <span aria-hidden="true"> →</span>}
            </button>
          </form>

          <p className="muted">
            Đã có tài khoản? <Link to="/dang-nhap" className="link-button">Đăng nhập</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
