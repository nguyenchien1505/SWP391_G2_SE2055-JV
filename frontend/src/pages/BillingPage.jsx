import { useEffect, useState } from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { readErrorMessage } from '../api/client';
import { fetchBillingOverview, fetchInvoices, purchasePlan, renewPlan, upgradePlan } from '../api/billing';
import { homePathFor } from '../homePath';
import TenantStatusBadge, { SUSPEND_REASON_LABEL } from '../components/TenantStatusBadge';
import { formatDate, formatDateTime, formatVnd } from './Admin_platform/format';
import './Admin_platform/admin.css';

// Khớp hằng số của BillingService (backend).
const CYCLE_DAYS = 30;
/** Chỉ gia hạn khi còn tối đa bấy nhiêu ngày tới hạn — trả trước nhiều nhất 2 kỳ. */
const MAX_PREPAY_DAYS = 60;
/** Mua thêm quota: số tiền chênh lệch tối thiểu, thấp hơn thì từ chối. */
const MIN_UPGRADE_AMOUNT = 5000;

const INVOICE_KIND_LABEL = { MUA_GOI: 'Mua gói', GIA_HAN: 'Gia hạn', MUA_THEM: 'Mua thêm quota' };

/** Ba loại tài nguyên tính tiền — BR-SAAS-02, BR-SAAS-03. */
const RESOURCES = [
  { key: 'location', label: 'Khách sạn', icon: '🏢', usage: 'locations', quota: 'quotaLocation', price: 'pricePerLocation' },
  { key: 'user', label: 'Nhân viên', icon: '👥', usage: 'staff', quota: 'quotaUser', price: 'pricePerUser' },
  { key: 'room', label: 'Phòng', icon: '🛏', usage: 'rooms', quota: 'quotaRoom', price: 'pricePerRoom' },
];

// ── Tiện ích ngày / số ─────────────────────────────────────────────────────

/** Hôm nay dạng "YYYY-MM-DD" (giờ địa phương). */
function todayIso() {
  return new Date().toLocaleDateString('sv-SE');
}

/** "YYYY-MM-DD" cộng n ngày (giờ địa phương). */
function addDays(isoDate, n) {
  const d = new Date(`${isoDate}T00:00:00`);
  d.setDate(d.getDate() + n);
  return d.toLocaleDateString('sv-SE');
}

/** Số ngày từ hôm nay tới một ngày "YYYY-MM-DD", không âm. */
function daysUntil(isoDate) {
  if (!isoDate) return 0;
  const diff = new Date(`${isoDate}T00:00:00`) - new Date(`${todayIso()}T00:00:00`);
  return Math.max(0, Math.round(diff / 86400000));
}

function toNumber(value) {
  const n = Number(value);
  return Number.isInteger(n) && n >= 0 ? n : null;
}

/** Số lượng ban đầu của form: số của lần thanh toán vừa thất bại (nếu có), ngược lại quota hiện tại. */
function initialQuota(overview, retry) {
  return Object.fromEntries(RESOURCES.map((r) => [r.key, String(retry?.[r.quota] ?? overview[r.quota])]));
}

function toRequest(values) {
  return { quotaLocation: toNumber(values.location), quotaUser: toNumber(values.user), quotaRoom: toNumber(values.room) };
}

// ── Thanh toán dùng chung cho Mua gói / Mua thêm / Gia hạn ─────────────────

/**
 * Gọi API thanh toán: có `paymentUrl` thì chuyển sang VNPay (VNPay đưa về /thanh-toan/ket-qua);
 * không có thì hiện `message` — giao dịch trước đó còn dở đã được backend tự hỏi VNPay.
 */
function usePayment(fallbackError) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  async function pay(request) {
    setError('');
    setSubmitting(true);
    try {
      const { paymentUrl, message, activated } = await request();
      if (paymentUrl) {
        window.location.href = paymentUrl;
        return;
      }
      if (activated) {
        // Giao dịch trước đó đã thành công: tải lại để thấy gói mới, gỡ chế độ chỉ đọc nếu có.
        setInfo(message);
        setTimeout(() => window.location.assign('/goi-dich-vu'), 2000);
        return;
      }
      setError(message);
    } catch (err) {
      setError(readErrorMessage(err, fallbackError));
    }
    setSubmitting(false);
  }

  return { pay, submitting, error, info };
}

function PaymentFeedback({ error, info }) {
  return (
    <>
      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}
      {info && (
        <div className="alert alert--success" role="status">
          {info}
        </div>
      )}
    </>
  );
}

// ── Thành phần giao diện ───────────────────────────────────────────────────

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

/** Mua gói lần đầu: chọn số lượng, giá theo bảng giá hiện hành, kỳ 30 ngày từ ngày thanh toán. */
function BuyPlanPanel({ overview, retry }) {
  const [values, setValues] = useState(() => initialQuota(overview, retry));
  const { pay, submitting, error, info } = usePayment('Không tạo được thanh toán.');

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
      <p className="muted">Chọn số lượng cần dùng. Giá theo bảng giá hiện hành, chu kỳ 30 ngày tính từ ngày thanh toán.</p>
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
      <PaymentFeedback error={error} info={info} />
      <button
        type="button"
        className="btn btn--primary"
        disabled={!valid || submitting}
        onClick={() => pay(() => purchasePlan(toRequest(values)))}
      >
        {submitting ? 'Đang chuyển sang VNPay…' : 'Thanh toán qua VNPay'}
      </button>
    </section>
  );
}

/** Mua thêm quota giữa kỳ: chỉ tăng, trả chênh lệch cho số ngày còn lại tới ngày đến hạn. */
function BuyMorePanel({ overview, retry }) {
  const [values, setValues] = useState(() => initialQuota(overview, retry));
  const { pay, submitting, error, info } = usePayment('Không tạo được thanh toán mua thêm.');
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
  const amount = Math.round((fullCycleDiff * daysLeft) / CYCLE_DAYS);
  const belowMinimum = increased && amount < MIN_UPGRADE_AMOUNT;
  const valid = Object.keys(errors).length === 0 && increased && daysLeft > 0 && !belowMinimum;

  return (
    <section className="panel">
      <div className="panel__head">
        <h2>Mua thêm quota</h2>
      </div>
      <p className="muted">
        Tăng số lượng giữa kỳ, chỉ trả phần chênh lệch cho {daysLeft} ngày còn lại tới ngày đến hạn (tối thiểu{' '}
        {formatVnd(MIN_UPGRADE_AMOUNT)}). Quota mới có hiệu lực ngay sau khi thanh toán thành công.
      </p>
      <QuotaInputs values={values} onChange={(k, v) => setValues((p) => ({ ...p, [k]: v }))} errors={errors} />
      <dl className="kv">
        <div>
          <dt>
            Phí chênh lệch ({daysLeft}/{CYCLE_DAYS} ngày)
          </dt>
          <dd>
            <b>{increased ? formatVnd(amount) : '—'}</b>
          </dd>
        </div>
      </dl>
      {belowMinimum && (
        <p className="field__help field__help--error">
          Số tiền thấp hơn mức tối thiểu {formatVnd(MIN_UPGRADE_AMOUNT)}. Vui lòng mua thêm nhiều hơn hoặc mua sau khi gia hạn.
        </p>
      )}
      <PaymentFeedback error={error} info={info} />
      <button
        type="button"
        className="btn btn--primary"
        disabled={!valid || submitting}
        onClick={() => pay(() => upgradePlan(toRequest(values)))}
      >
        {submitting ? 'Đang chuyển sang VNPay…' : 'Thanh toán phần chênh lệch'}
      </button>
    </section>
  );
}

/**
 * Gia hạn 1 kỳ 30 ngày theo quota hiện tại. Kỳ nối tiếp ngày đến hạn; đang bị khóa vì chưa gia hạn
 * thì tính từ hôm nay (khớp backend).
 */
function RenewPanel({ overview }) {
  const { pay, submitting, error, info } = usePayment('Không tạo được thanh toán gia hạn.');
  const { status, suspendReason, nextBillingDate: due } = overview;
  const locked = status === 'SUSPENDED' && suspendReason === 'PAYMENT_FAILED';
  const canRenew = daysUntil(due) <= MAX_PREPAY_DAYS;
  const nextStart = locked ? todayIso() : due;

  return (
    <section className="panel">
      <div className="panel__head">
        <h2>Gia hạn gói</h2>
      </div>
      {(locked || status === 'PAYMENT_OVERDUE') && (
        <div className="alert alert--warn" role="status">
          {locked
            ? 'Tài khoản đang bị khóa do chưa gia hạn. Gia hạn ngay để mở lại.'
            : 'Gói đã quá hạn thanh toán. Gia hạn ngay để tránh bị khóa tài khoản.'}
        </div>
      )}
      <dl className="kv">
        <div>
          <dt>Kỳ tới</dt>
          <dd>
            {formatDate(nextStart)} → {formatDate(addDays(nextStart, CYCLE_DAYS - 1))}
          </dd>
        </div>
        <div>
          <dt>Số tiền (quota hiện tại)</dt>
          <dd>{formatVnd(overview.periodFee)}</dd>
        </div>
      </dl>
      {!canRenew && (
        <p className="muted">
          Bạn đã thanh toán trước. Có thể gia hạn tiếp từ ngày {formatDate(addDays(due, -MAX_PREPAY_DAYS))}.
        </p>
      )}
      <PaymentFeedback error={error} info={info} />
      <button type="button" className="btn btn--primary" disabled={!canRenew || submitting} onClick={() => pay(renewPlan)}>
        {submitting ? 'Đang chuyển sang VNPay…' : 'Gia hạn'}
      </button>
    </section>
  );
}

// ── Trang ──────────────────────────────────────────────────────────────────

/**
 * "Gói dịch vụ" của Giám đốc: gói đang dùng, hạn dùng, mức sử dụng, thao tác theo tình trạng gói
 * (mua gói / mua thêm / gia hạn — đều qua VNPay) và lịch sử hóa đơn đã thanh toán.
 */
export default function BillingPage() {
  const { user } = useAuth();
  // Trang kết quả đưa về đây khi thanh toán thất bại / bị hủy: ?msg=&ql=&qu=&qr=
  const [searchParams] = useSearchParams();
  const retryNotice = searchParams.get('msg');
  const retry = searchParams.get('ql')
    ? { quotaLocation: searchParams.get('ql'), quotaUser: searchParams.get('qu'), quotaRoom: searchParams.get('qr') }
    : null;

  const [overview, setOverview] = useState(null);
  const [invoices, setInvoices] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    if (user?.role !== 'DIRECTOR') return undefined;
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
                <dt>Đã thanh toán đến</dt>
                <dd>
                  {formatDate(addDays(overview.nextBillingDate, -1))} (còn {daysUntil(overview.nextBillingDate)} ngày)
                </dd>
              </div>
              <div>
                <dt>Ngày đến hạn gia hạn</dt>
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
      {retryNotice && (
        <div className="alert alert--warn" role="status">
          {retryNotice}
        </div>
      )}
      {!adminLocked && trial && <BuyPlanPanel overview={overview} retry={retry} />}
      {!adminLocked && !trial && status === 'ACTIVE' && <BuyMorePanel overview={overview} retry={retry} />}
      {!adminLocked && !trial && <RenewPanel overview={overview} />}

      {/* C. Lịch sử hóa đơn đã thanh toán */}
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
                </tr>
              </thead>
              <tbody>
                {invoices.map((inv) => (
                  <tr key={inv.id}>
                    <td>{formatDateTime(inv.issuedAt)}</td>
                    <td>{INVOICE_KIND_LABEL[inv.kind] ?? inv.kind}</td>
                    <td>
                      {formatDate(inv.periodStart)} → {formatDate(inv.periodEnd)}
                    </td>
                    <td>
                      {inv.quotaLocation} / {inv.quotaUser} / {inv.quotaRoom}
                    </td>
                    <td>{formatVnd(inv.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
