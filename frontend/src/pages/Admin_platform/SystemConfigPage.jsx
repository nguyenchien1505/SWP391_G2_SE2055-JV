import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { fetchSystemConfig, updateSystemConfig } from '../../api/platformConfig';
import { formatDateTime } from './format';
import './admin.css';

/**
 * Cấu hình dùng thử & ân hạn — Admin Platform (BR-SAAS-08, BR-SAAS-10).
 *
 * <p>Đây là cấu hình cấp HỆ THỐNG: chỉ có một bản ghi cho toàn nền tảng, sửa đè. Số ngày dùng thử
 * chỉ áp dụng cho Tenant đăng ký MỚI; Tenant đang dùng thử đã có hạn cụ thể nên không đổi.
 */
export default function SystemConfigPage() {
  const [config, setConfig] = useState(null);
  const [trialDays, setTrialDays] = useState('');
  const [gracePeriodDays, setGracePeriodDays] = useState('');
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [banner, setBanner] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function apply(data) {
    setConfig(data);
    setTrialDays(String(data.trialDays));
    setGracePeriodDays(String(data.gracePeriodDays));
  }

  useEffect(() => {
    fetchSystemConfig()
      .then(apply)
      .catch((err) => setLoadError(readErrorMessage(err, 'Không tải được cấu hình.')));
  }, []);

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setBanner('');

    if (trialDays === '' || gracePeriodDays === '') {
      setError('Vui lòng nhập đầy đủ số ngày dùng thử và số ngày ân hạn.');
      return;
    }

    setSubmitting(true);
    try {
      apply(
        await updateSystemConfig({
          trialDays: Number(trialDays),
          gracePeriodDays: Number(gracePeriodDays),
        }),
      );
      setBanner('Đã lưu cấu hình.');
    } catch (err) {
      setError(readErrorMessage(err, 'Không lưu được cấu hình.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page admin-page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Cấu hình &amp; Gói › Dùng thử &amp; Ân hạn</p>
          <h1>Cấu hình dùng thử &amp; ân hạn</h1>
          <p className="muted">
            {config?.updatedAt
              ? `Cập nhật lần cuối: ${formatDateTime(config.updatedAt)}`
              : 'Chưa chỉnh sửa từ khi khởi tạo'}
          </p>
        </div>
      </div>

      {loadError && <div className="alert alert--error">{loadError}</div>}

      {banner && (
        <div className="alert alert--success" role="status">
          {banner}
          <button type="button" className="alert__close" onClick={() => setBanner('')}>
            ✕
          </button>
        </div>
      )}

      <section className="panel panel--narrow">
        <form className="form" onSubmit={handleSubmit} noValidate>
          <label className="field" htmlFor="trialDays">
            <span className="field__label">
              Số ngày dùng thử (1–365) <b className="req">*</b>
            </span>
            <input
              id="trialDays"
              type="number"
              min="1"
              max="365"
              value={trialDays}
              onChange={(e) => setTrialDays(e.target.value)}
              disabled={!config}
            />
            <span className="field__help">
              Chỉ áp dụng cho Tenant đăng ký mới, không ảnh hưởng Tenant đang dùng thử.
            </span>
          </label>

          <label className="field" htmlFor="gracePeriodDays">
            <span className="field__label">
              Số ngày ân hạn khi quá hạn thanh toán (0–90) <b className="req">*</b>
            </span>
            <input
              id="gracePeriodDays"
              type="number"
              min="0"
              max="90"
              value={gracePeriodDays}
              onChange={(e) => setGracePeriodDays(e.target.value)}
              disabled={!config}
            />
            <span className="field__help">
              Sau số ngày này Tenant quá hạn thanh toán sẽ bị khóa. Phần thanh toán chưa triển khai
              nên giá trị này chưa có hiệu lực.
            </span>
          </label>

          {error && (
            <div className="alert alert--error" role="alert">
              {error}
            </div>
          )}

          <div className="form__actions">
            <button type="submit" className="btn btn--primary" disabled={submitting || !config}>
              {submitting ? 'Đang lưu…' : 'Lưu cấu hình'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
