import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { readErrorMessage } from '../api/client';
import { fetchBillingOverview, fetchInvoices } from '../api/billing';
import { homePathFor } from '../homePath';
import TenantStatusBadge, { SUSPEND_REASON_LABEL } from '../components/TenantStatusBadge';
import { formatDate, formatDateTime, formatVnd } from './Admin_platform/format';
import './Admin_platform/admin.css';

const INVOICE_TYPE_LABEL = {
  PERIODIC: 'Gói / Gia hạn',
  UPGRADE_DIFF: 'Mua thêm quota',
};

const INVOICE_STATUS = {
  PENDING: { label: 'Chờ thanh toán', tone: 'orange' },
  PAID: { label: 'Đã thanh toán', tone: 'green' },
  FAILED: { label: 'Thất bại', tone: 'red' },
};

/** Ba loại tài nguyên tính tiền — BR-SAAS-02, BR-SAAS-03. */
const RESOURCES = [
  { key: 'location', label: 'Khách sạn', icon: '🏢', usage: 'locations', quota: 'quotaLocation', price: 'pricePerLocation' },
  { key: 'user', label: 'Nhân viên', icon: '👥', usage: 'staff', quota: 'quotaUser', price: 'pricePerUser' },
  { key: 'room', label: 'Phòng', icon: '🛏', usage: 'rooms', quota: 'quotaRoom', price: 'pricePerRoom' },
];

/** Số ngày từ hôm nay tới một ngày "YYYY-MM-DD" (giờ địa phương), không âm. */
function daysUntil(isoDate) {
  if (!isoDate) return 0;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(`${isoDate}T00:00:00`);
  return Math.max(0, Math.round((target - today) / 86400000));
}

function Meter({ label, icon, metric }) {
  const percent = Math.min(metric?.usedPercent ?? 0, 100);
  return (
    <div className={`meter ${metric?.overQuota ? 'meter--over' : ''}`}>
      <div className="meter__label">
        <span>{label}</span>
        <span aria-hidden="true">{icon}</span>
      </div>
      <p className="meter__value">
        {metric?.used ?? 0} <small>/ {metric?.quota ?? '—'}</small>
      </p>
      <div className="meter__bar" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
        <span className="meter__fill" style={{ width: `${percent}%` }} />
      </div>
      <p className="meter__note">
        {metric?.overQuota
          ? `Vượt hạn mức ${Math.abs(metric.remaining ?? 0)} đơn vị`
          : `Còn ${metric?.remaining ?? 0} đơn vị`}
      </p>
    </div>
  );
}

/** Ba ô nhập số lượng dùng chung cho Mua gói và Mua thêm. */
function QuotaInputs({ values, onChange, errors }) {
  return (
    <div className="meter-grid">
      {RESOURCES.map((r) => (
        <div key={r.key}>
          <label className="field" htmlFor={`quota-${r.key}`}>
            <span className="field__label">
              {r.label} <b className="req">*</b>
            </span>
            <span className="field__control">
              <span className="field__icon" aria-hidden="true">{r.icon}</span>
              <input
                id={`quota-${r.key}`}
                type="number"
                min={0}
                step={1}
                value={values[r.key]}
                onChange={(e) => onChange(r.key, e.target.value)}
              />
            </span>
          </label>
          {errors[r.key] && <p className="field__help field__help--error">{errors[r.key]}</p>}
        </div>
      ))}
    </div>
  );
}

function toNumber(value) {
  const n = Number(value);
  return Number.isInteger(n) && n >= 0 ? n : null;
}

/** Mua gói lần đầu: chọn số lượng, giá theo bảng giá hiện hành, chu kỳ 30 ngày (BR-SAAS-05). */
function BuyPlanPanel({ overview }) {
  const [values, setValues] = useState(() =>
    Object.fromEntries(RESOURCES.map((r) => [r.key, String(overview[r.quota])])),
  );

  const errors = {};
  let total = 0;
  for (const r of RESOURCES) {
    const n = toNumber(values[r.key]);
    const used = overview[r.usage]?.used ?? 0;
    if (n == null) errors[r.key] = 'Nhập số nguyên không âm';
    else if (n < used) errors[r.key] = `Không được ít hơn số đang dùng (${used})`;
    else if (r.key === 'location' && n < 1) errors[r.key] = 'Cần ít nhất 1 khách sạn';
    else total += n * overview.currentPricing[r.price];
  }
  const valid = Object.keys(errors).length === 0;

  return (
    <section className="panel">
      <div className="panel__head">
        <h2>Mua gói dịch vụ</h2>
      </div>
      <p className="muted">
        Chọn số lượng cần dùng. Giá theo bảng giá hiện hành, chu kỳ 30 ngày tính từ ngày thanh toán.
      </p>
      <QuotaInputs values={values} onChange={(k, v) => setValues((p) => ({ ...p, [k]: v }))} errors={errors} />
      <dl className="kv">
        {RESOURCES.map((r) => (
          <div key={r.key}>
            <dt>Đơn giá {r.label.toLowerCase()}</dt>
            <dd>{formatVnd(overview.currentPricing[r.price])} / 30 ngày</dd>
          </div>
        ))}
        <div>
          <dt>Tổng thanh toán</dt>
          <dd>
            <b>{valid ? formatVnd(total) : '—'}</b> / 30 ngày
          </dd>
        </div>
      </dl>
      <button type="button" className="btn btn--primary" disabled={!valid}>
        Thanh toán qua VNPay
      </button>
    </section>
  );
}

/** Mua thêm quota giữa kỳ: trả phần chênh lệch theo số ngày còn lại (BR-SAAS-06). */
function BuyMorePanel({ overview }) {
  const [values, setValues] = useState(() =>
    Object.fromEntries(RESOURCES.map((r) => [r.key, String(overview[r.quota])])),
  );
  const daysLeft = daysUntil(overview.nextBillingDate);

  const errors = {};
  let increased = false;
  let fullCycleDiff = 0;
  for (const r of RESOURCES) {
    const n = toNumber(values[r.key]);
    const current = overview[r.quota];
    if (n == null) errors[r.key] = 'Nhập số nguyên không âm';
    else if (n < current) errors[r.key] = `Chưa hỗ trợ giảm — tối thiểu ${current}`;
    else {
      if (n > current) increased = true;
      fullCycleDiff += (n - current) * overview[r.price];
    }
  }
  const valid = Object.keys(errors).length === 0 && increased;
  const amount = Math.round((fullCycleDiff * daysLeft) / 30);

  return (
    <section className="panel">
      <div className="panel__head">
        <h2>Mua thêm quota</h2>
      </div>
      <p className="muted">
        Tăng số lượng giữa kỳ, chỉ trả phần chênh lệch cho {daysLeft} ngày còn lại của chu kỳ. Quota mới
        có hiệu lực sau khi thanh toán thành công.
      </p>
      <QuotaInputs values={values} onChange={(k, v) => setValues((p) => ({ ...p, [k]: v }))} errors={errors} />
      <dl className="kv">
        <div>
          <dt>Phí chênh lệch ({daysLeft}/30 ngày)</dt>
          <dd>
            <b>{valid ? formatVnd(amount) : '—'}</b>
          </dd>
        </div>
      </dl>
      <button type="button" className="btn btn--primary" disabled={!valid}>
        Thanh toán phần chênh lệch
      </button>
    </section>
  );
}

/** Gia hạn chu kỳ 30 ngày (BR-SAAS-05, BR-SAAS-10). */
function RenewPanel({ overview, pendingInvoice, urgent }) {
  return (
    <section className="panel">
      <div className="panel__head">
        <h2>Gia hạn gói</h2>
      </div>
      {urgent && (
        <div className="alert alert--warn" role="status">
          {overview.status === 'SUSPENDED'
            ? 'Tài khoản đang bị khóa do thanh toán thất bại. Thanh toán hóa đơn gia hạn để mở lại.'
            : 'Hóa đơn gia hạn đã quá hạn. Thanh toán ngay để tránh bị khóa tài khoản.'}
        </div>
      )}
      <dl className="kv">
        <div>
          <dt>Ngày gia hạn</dt>
          <dd>{formatDate(overview.nextBillingDate)}</dd>
        </div>
        <div>
          <dt>Phí mỗi chu kỳ</dt>
          <dd>{formatVnd(overview.periodFee)}</dd>
        </div>
        {pendingInvoice && (
          <div>
            <dt>Hóa đơn cần thanh toán</dt>
            <dd>
              {formatVnd(pendingInvoice.amount)} · kỳ {formatDate(pendingInvoice.periodStart)} →{' '}
              {formatDate(pendingInvoice.periodEnd)}
            </dd>
          </div>
        )}
      </dl>
      <button type="button" className="btn btn--primary">
        Gia hạn gói
      </button>
    </section>
  );
}

/**
 * "Gói dịch vụ" của Giám đốc: gói đang dùng, hạn dùng, mức sử dụng, các thao tác mua gói / mua
 * thêm / gia hạn (hiện theo tình trạng gói) và lịch sử hóa đơn.
 *
 * Các nút thanh toán chưa gắn API — làm ở các đợt sau.
 */
export default function BillingPage() {
  const { user } = useAuth();
  const [overview, setOverview] = useState(null);
  const [invoices, setInvoices] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    if (user?.role !== 'DIRECTOR') return;
    let cancelled = false;
    Promise.all([fetchBillingOverview(), fetchInvoices()])
      .then(([ov, inv]) => {
        if (cancelled) return;
        setOverview(ov);
        setInvoices(inv);
      })
      .catch((err) => !cancelled && setError(readErrorMessage(err, 'Không tải được thông tin gói dịch vụ.')));
    return () => {
      cancelled = true;
    };
  }, [user?.role]);

  if (user?.role !== 'DIRECTOR') {
    return <Navigate to={homePathFor(user)} replace />;
  }
  if (error) {
    return (
      <div className="page admin-page">
        <div className="alert alert--error">{error}</div>
      </div>
    );
  }
  if (!overview) {
    return <p className="state">Đang tải…</p>;
  }

  const { status, suspendReason, trial } = overview;
  const adminLocked = status === 'SUSPENDED' && suspendReason === 'ADMIN_LOCKED';
  const paymentProblem =
    status === 'PAYMENT_OVERDUE' || (status === 'SUSPENDED' && suspendReason === 'PAYMENT_FAILED');
  const pendingRenewal = invoices.find((i) => i.invoiceType === 'PERIODIC' && i.status !== 'PAID');

  return (
    <div className="page admin-page">
      <div className="page__head">
        <div>
          <h1>Gói dịch vụ</h1>
          <p className="muted">{overview.tenantName}</p>
        </div>
      </div>

      {/* A. Gói hiện tại */}
      <section className="panel">
        <div className="panel__head">
          <h2>Gói hiện tại</h2>
          <TenantStatusBadge status={status} />
        </div>

        {status === 'SUSPENDED' && (
          <div className="alert alert--warn" role="status">
            Tài khoản đang bị khóa do <b>{SUSPEND_REASON_LABEL[suspendReason] ?? suspendReason ?? '—'}</b>.
            {suspendReason === 'TRIAL_EXPIRED' && ' Mua gói để tiếp tục sử dụng hệ thống.'}
          </div>
        )}

        <dl className="kv">
          {trial ? (
            <div>
              <dt>Hạn dùng thử</dt>
              <dd>
                {formatDate(overview.trialEndsAt)}
                {overview.trialDaysLeft != null && ` (còn ${overview.trialDaysLeft} ngày)`}
              </dd>
            </div>
          ) : (
            <>
              <div>
                <dt>Chu kỳ hiện tại</dt>
                <dd>
                  {formatDate(overview.currentPeriodStart)} → {formatDate(overview.nextBillingDate)}
                </dd>
              </div>
              <div>
                <dt>Ngày gia hạn</dt>
                <dd>{formatDate(overview.nextBillingDate)}</dd>
              </div>
              <div>
                <dt>Phí mỗi chu kỳ</dt>
                <dd>{formatVnd(overview.periodFee)}</dd>
              </div>
            </>
          )}
        </dl>

        <div className="meter-grid">
          {RESOURCES.map((r) => (
            <Meter key={r.key} label={r.label} icon={r.icon} metric={overview[r.usage]} />
          ))}
        </div>
      </section>

      {/* B. Thao tác theo tình trạng gói */}
      {!adminLocked && trial && <BuyPlanPanel overview={overview} />}
      {!adminLocked && !trial && status === 'ACTIVE' && <BuyMorePanel overview={overview} />}
      {!adminLocked && !trial && (
        <RenewPanel overview={overview} pendingInvoice={pendingRenewal} urgent={paymentProblem} />
      )}

      {/* C. Lịch sử hóa đơn */}
      <section className="panel">
        <div className="panel__head">
          <h2>Lịch sử hóa đơn</h2>
        </div>
        {invoices.length === 0 ? (
          <p className="state">Chưa có hóa đơn nào.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Ngày phát hành</th>
                  <th>Loại</th>
                  <th>Kỳ áp dụng</th>
                  <th>Số lượng (KS / NV / Phòng)</th>
                  <th>Số tiền</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {invoices.map((inv) => {
                  const st = INVOICE_STATUS[inv.status] ?? { label: inv.status, tone: 'grey' };
                  return (
                    <tr key={inv.id}>
                      <td>{formatDateTime(inv.issuedAt)}</td>
                      <td>{INVOICE_TYPE_LABEL[inv.invoiceType] ?? inv.invoiceType}</td>
                      <td>
                        {formatDate(inv.periodStart)} → {formatDate(inv.periodEnd)}
                      </td>
                      <td>
                        {inv.quotaLocation} / {inv.quotaUser} / {inv.quotaRoom}
                      </td>
                      <td>{formatVnd(inv.amount)}</td>
                      <td>
                        <span className={`badge badge--${st.tone}`}>{st.label}</span>
                      </td>
                      <td>
                        {inv.status !== 'PAID' && (
                          <button type="button" className="btn btn--ghost">
                            Thanh toán
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
