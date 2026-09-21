import { useCallback, useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { createPricing, fetchCurrentPricing, fetchPricingHistory } from '../../api/platformConfig';
import StatCard from '../../components/StatCard';
import { formatDate, formatVnd } from './format';
import './admin.css';

const EMPTY = { pricePerLocation: '', pricePerUser: '', pricePerRoom: '', effectiveFrom: '' };

const PRICE_FIELDS = [
  { key: 'pricePerLocation', label: 'Đơn giá / Location (₫)' },
  { key: 'pricePerUser', label: 'Đơn giá / Staff (₫)' },
  { key: 'pricePerRoom', label: 'Đơn giá / Phòng (₫)' },
];

/**
 * Gói dịch vụ & bảng giá — Admin Platform (BR-SAAS-02).
 *
 * <p>Hệ thống dùng mô hình tự tùy chỉnh gói: giá = đơn giá × số lượng Location, Staff, Phòng.
 * Admin chỉ quản lý ĐƠN GIÁ, không tạo gói dựng sẵn. Bảng giá chỉ thêm mới, không sửa hay xóa,
 * vì gói đã bán giữ snapshot giá tại lúc chốt (BR-SAAS-05).
 */
export default function PricingPage() {
  const [history, setHistory] = useState([]);
  const [current, setCurrent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [values, setValues] = useState(EMPTY);
  const [formError, setFormError] = useState('');
  const [banner, setBanner] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoadError('');
    try {
      const [all, cur] = await Promise.all([fetchPricingHistory(), fetchCurrentPricing()]);
      setHistory(all);
      setCurrent(cur);
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được bảng giá.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  function setField(field, value) {
    setValues((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setFormError('');
    setBanner('');

    // Ô trống mà ép về số sẽ thành 0 và gửi đi sai lệch, nên chặn trước.
    if (Object.values(values).some((v) => v === '')) {
      setFormError('Vui lòng nhập đầy đủ ba đơn giá và ngày bắt đầu hiệu lực.');
      return;
    }

    setSubmitting(true);
    try {
      await createPricing({
        pricePerLocation: Number(values.pricePerLocation),
        pricePerUser: Number(values.pricePerUser),
        pricePerRoom: Number(values.pricePerRoom),
        effectiveFrom: values.effectiveFrom,
      });
      setValues(EMPTY);
      setBanner(`Đã thêm bảng giá hiệu lực từ ${formatDate(values.effectiveFrom)}.`);
      await load();
    } catch (err) {
      setFormError(readErrorMessage(err, 'Không thêm được bảng giá.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page admin-page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Cấu hình &amp; Gói › Gói dịch vụ &amp; Đơn giá</p>
          <h1>Gói dịch vụ &amp; Bảng giá</h1>
          <p className="muted">
            Giá tính theo Location, Staff và Phòng cho mỗi chu kỳ 30 ngày.
          </p>
        </div>
      </div>

      {loadError && <div className="alert alert--error">{loadError}</div>}

      <div className="stat-grid">
        <StatCard
          label="Đơn giá / Location"
          value={current ? formatVnd(current.pricePerLocation) : '—'}
          note={current ? `Hiệu lực từ ${formatDate(current.effectiveFrom)}` : 'Chưa có bảng giá hiệu lực'}
          icon="🏢"
          tone="blue"
        />
        <StatCard
          label="Đơn giá / Staff"
          value={current ? formatVnd(current.pricePerUser) : '—'}
          note="Chỉ tính Staff, không tính Giám đốc và Quản lý"
          icon="👥"
          tone="indigo"
        />
        <StatCard
          label="Đơn giá / Phòng"
          value={current ? formatVnd(current.pricePerRoom) : '—'}
          note="Mỗi phòng đang có trong hệ thống"
          icon="🛏"
          tone="green"
        />
      </div>

      {banner && (
        <div className="alert alert--success" role="status">
          {banner}
          <button type="button" className="alert__close" onClick={() => setBanner('')}>
            ✕
          </button>
        </div>
      )}

      <div className="split">
        <section className="panel">
          <div className="panel__head">
            <h2>Lịch sử bảng giá</h2>
            <span className="chip">{history.length} bảng giá</span>
          </div>

          {loading && <p className="state">Đang tải dữ liệu…</p>}

          {!loading && history.length === 0 && (
            <div className="state state--empty">
              <p>Chưa có bảng giá nào.</p>
            </div>
          )}

          {!loading && history.length > 0 && (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Hiệu lực từ</th>
                    <th>Mỗi Location</th>
                    <th>Mỗi Staff</th>
                    <th>Mỗi Phòng</th>
                    <th aria-label="Trạng thái" />
                  </tr>
                </thead>
                <tbody>
                  {history.map((row) => (
                    <tr key={row.id}>
                      <td>{formatDate(row.effectiveFrom)}</td>
                      <td>{formatVnd(row.pricePerLocation)}</td>
                      <td>{formatVnd(row.pricePerUser)}</td>
                      <td>{formatVnd(row.pricePerRoom)}</td>
                      <td>
                        {current?.id === row.id && (
                          <span className="badge badge--green">Đang áp dụng</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <aside className="panel panel--form">
          <form className="form" onSubmit={handleSubmit} noValidate>
            <header className="form__head">
              <span className="form__icon" aria-hidden="true">
                💰
              </span>
              <div>
                <h2>Thêm bảng giá mới</h2>
                <p className="muted">Bảng giá chỉ thêm mới, không sửa hay xóa</p>
              </div>
            </header>

            {PRICE_FIELDS.map(({ key, label }) => (
              <label className="field" htmlFor={key} key={key}>
                <span className="field__label">
                  {label} <b className="req">*</b>
                </span>
                <input
                  id={key}
                  type="number"
                  min="0"
                  max="1000000000"
                  value={values[key]}
                  onChange={(e) => setField(key, e.target.value)}
                />
              </label>
            ))}

            <label className="field" htmlFor="effectiveFrom">
              <span className="field__label">
                Ngày bắt đầu hiệu lực <b className="req">*</b>
              </span>
              <input
                id="effectiveFrom"
                type="date"
                value={values.effectiveFrom}
                onChange={(e) => setField('effectiveFrom', e.target.value)}
              />
              <span className="field__help">
                Không được ở quá khứ, và mỗi ngày chỉ có một bảng giá. Bảng giá mới áp dụng cho gói
                mua từ ngày này; gói đã bán giữ nguyên giá đã chốt.
              </span>
            </label>

            {formError && (
              <div className="alert alert--error" role="alert">
                {formError}
              </div>
            )}

            <div className="form__actions">
              <button type="submit" className="btn btn--primary" disabled={submitting}>
                {submitting ? 'Đang lưu…' : 'Thêm bảng giá'}
              </button>
            </div>
          </form>
        </aside>
      </div>
    </div>
  );
}
