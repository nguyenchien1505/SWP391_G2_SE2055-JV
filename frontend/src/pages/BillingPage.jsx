import { useEffect, useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { readErrorMessage } from '../api/client';
import { fetchBillingOverview, fetchInvoices, purchasePlan, renewPlan, startTrial, upgradePlan } from '../api/billing';
import { homePathFor } from '../homePath';
import TenantStatusBadge, { SUSPEND_REASON_LABEL } from '../components/TenantStatusBadge';
import ConfirmDialog from '../components/ConfirmDialog';
import FormModal from '../components/FormModal';
import { formatDate, formatDateTime, formatVnd } from './Admin_platform/format';
import './Admin_platform/admin.css';
import './billing.css';

// ── Hằng số nghiệp vụ BillingService (backend) ──────────────────────────────
const CYCLE_DAYS = 30;
/** Chỉ gia hạn khi còn tối đa bấy nhiêu ngày tới hạn — trả trước nhiều nhất 2 kỳ. */
const MAX_PREPAY_DAYS = 60;
/** Mua thêm quota: số tiền chênh lệch tối thiểu, thấp hơn thì từ chối. */
const MIN_UPGRADE_AMOUNT = 5000;

const INVOICE_KIND_LABEL = {
  MUA_GOI: 'Mua gói',
  GIA_HAN: 'Gia hạn',
  MUA_THEM: 'Mua thêm quota',
};

/** Ba loại tài nguyên tính tiền — BR-SAAS-02, BR-SAAS-03. */
const RESOURCES = [
  {
    key: 'location',
    label: 'Khách sạn',
    unit: 'khách sạn',
    icon: '🏢',
    usage: 'locations',
    quota: 'quotaLocation',
    planned: 'plannedQuotaLocation',
    price: 'pricePerLocation',
  },
  {
    key: 'user',
    label: 'Nhân viên',
    unit: 'nhân sự',
    icon: '👥',
    usage: 'staff',
    quota: 'quotaUser',
    planned: 'plannedQuotaUser',
    price: 'pricePerUser',
  },
  {
    key: 'room',
    label: 'Phòng',
    unit: 'phòng',
    icon: '🛏',
    usage: 'rooms',
    quota: 'quotaRoom',
    planned: 'plannedQuotaRoom',
    price: 'pricePerRoom',
  },
];

/** Đã gia hạn trước kỳ tới với số lượng khác gói hiện tại (đổi gói từ kỳ tới). */
function plannedDiffers(overview) {
  return overview.plannedFrom != null && RESOURCES.some((r) => overview[r.planned] !== overview[r.quota]);
}

// ── Tiện ích ngày / số ─────────────────────────────────────────────────────

function todayIso() {
  return new Date().toLocaleDateString('sv-SE');
}

function addDays(isoDate, n) {
  const d = new Date(`${isoDate}T00:00:00`);
  d.setDate(d.getDate() + n);
  return d.toLocaleDateString('sv-SE');
}

function daysUntil(isoDate) {
  if (!isoDate) return 0;
  const diff = new Date(`${isoDate}T00:00:00`) - new Date(`${todayIso()}T00:00:00`);
  return Math.max(0, Math.round(diff / 86400000));
}

function toNumber(value) {
  const n = Number(value);
  return Number.isInteger(n) && n >= 0 ? n : null;
}

function initialQuota(overview, retry) {
  return Object.fromEntries(RESOURCES.map((r) => [r.key, String(retry?.[r.quota] ?? overview[r.quota])]));
}

function toRequest(values) {
  return {
    quotaLocation: toNumber(values.location),
    quotaUser: toNumber(values.user),
    quotaRoom: toNumber(values.room),
  };
}

// ── Icons SVG ──────────────────────────────────────────────────────────────

function IconBuilding({ className = 'billing-meter-card__icon' }) {
  return (
    <svg className={className} width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18Z" />
      <path d="M6 12H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2" />
      <path d="M18 9h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2" />
      <path d="M10 6h4" />
      <path d="M10 10h4" />
      <path d="M10 14h4" />
      <path d="M10 18h4" />
    </svg>
  );
}

function IconUsers({ className = 'billing-meter-card__icon' }) {
  return (
    <svg className={className} width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  );
}

function IconBed({ className = 'billing-meter-card__icon' }) {
  return (
    <svg className={className} width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M2 4v16" />
      <path d="M2 8h18a2 2 0 0 1 2 2v10" />
      <path d="M2 17h20" />
      <path d="M6 8v9" />
    </svg>
  );
}

function IconCalendar({ className }) {
  return (
    <svg className={className} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect width="18" height="18" x="3" y="4" rx="2" ry="2" />
      <line x1="16" x2="16" y1="2" y2="6" />
      <line x1="8" x2="8" y1="2" y2="6" />
      <line x1="3" x2="21" y1="10" y2="10" />
    </svg>
  );
}

function IconShieldCheck({ className }) {
  return (
    <svg className={className} width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

function IconAlertCircle({ className = 'billing-alert__icon' }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" />
      <line x1="12" x2="12" y1="8" y2="12" />
      <line x1="12" x2="12.01" y1="16" y2="16" />
    </svg>
  );
}

function IconCheck({ className }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}

function getResourceIcon(key) {
  if (key === 'location') return <IconBuilding />;
  if (key === 'user') return <IconUsers />;
  return <IconBed />;
}

// ── Hook Thanh toán dùng chung cho Mua gói / Mua thêm / Gia hạn ───────────

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
        <div className="billing-alert billing-alert--danger" role="alert">
          <IconAlertCircle />
          <div>{error}</div>
        </div>
      )}
      {info && (
        <div className="billing-alert billing-alert--success" role="status">
          <IconShieldCheck className="billing-alert__icon" />
          <div>{info}</div>
        </div>
      )}
    </>
  );
}

// ── Thẻ theo dõi hạn mức tài nguyên (Meter) ────────────────────────────────

function Meter({ resourceKey, label, metric }) {
  const percent = Math.min(metric?.usedPercent ?? 0, 100);
  const isOver = Boolean(metric?.overQuota);
  const isHigh = percent >= 85 && !isOver;

  let badgeClass = 'billing-meter-card__badge--normal';
  let fillClass = 'billing-meter-card__fill--normal';
  if (isOver) {
    badgeClass = 'billing-meter-card__badge--danger';
    fillClass = 'billing-meter-card__fill--danger';
  } else if (isHigh) {
    badgeClass = 'billing-meter-card__badge--warn';
    fillClass = 'billing-meter-card__fill--warn';
  }

  return (
    <div className={`billing-meter-card ${isOver ? 'billing-meter-card--over' : ''}`}>
      <div className="billing-meter-card__top">
        <div className="billing-meter-card__label-group">
          <div className={`billing-meter-card__icon-box billing-meter-card__icon-box--${resourceKey}`}>
            {getResourceIcon(resourceKey)}
          </div>
          <span className="billing-meter-card__name">{label}</span>
        </div>
        <span className={`billing-meter-card__badge ${badgeClass}`}>
          {isOver ? 'Vượt hạn mức' : `${metric?.usedPercent ?? 0}%`}
        </span>
      </div>

      <div className="billing-meter-card__value-row">
        <span className="billing-meter-card__used">{metric?.used ?? 0}</span>
        <span className="billing-meter-card__quota">/ {metric?.quota ?? '—'}</span>
      </div>

      <div
        className="billing-meter-card__bar"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
      >
        <div className={`billing-meter-card__fill ${fillClass}`} style={{ width: `${percent}%` }} />
      </div>

      <div className="billing-meter-card__foot">
        <span>{metric?.quota != null ? `Hạn mức: ${metric.quota}` : 'Chưa có hạn mức'}</span>
        <span style={{ fontWeight: 600, color: isOver ? '#dc2626' : undefined }}>
          {isOver
            ? `Vượt ${Math.abs(metric.remaining ?? 0)} đơn vị`
            : `Còn ${metric?.remaining ?? 0} trống`}
        </span>
      </div>
    </div>
  );
}

// ── Ô nhập số lượng nâng cao với nút tăng / giảm (QuotaInputs) ──────────────

function QuotaInputs({ values, onChange, errors, overview }) {
  const handleStep = (key, delta) => {
    const cur = toNumber(values[key]) ?? 0;
    const nextVal = Math.max(0, cur + delta);
    onChange(key, String(nextVal));
  };

  return (
    <div className="billing-inputs-grid">
      {RESOURCES.map((r) => {
        const hasError = Boolean(errors[r.key]);
        const currentUsed = overview?.[r.usage]?.used;
        const currentQuota = overview?.[r.quota];
        const unitPrice = overview?.currentPricing?.[r.price] ?? overview?.[r.price];

        return (
          <div key={r.key} className={`billing-input-card ${hasError ? 'billing-input-card--error' : ''}`}>
            <div className="billing-input-card__head">
              <label htmlFor={`quota-${r.key}`} className="billing-input-card__title">
                {getResourceIcon(r.key)}
                <span>{r.label}</span>
                <span className="req">*</span>
              </label>
              {currentUsed != null && (
                <span className="billing-input-card__used-tag">Đang dùng: {currentUsed}</span>
              )}
            </div>

            <div className="billing-stepper">
              <button
                type="button"
                className="billing-stepper__btn"
                onClick={() => handleStep(r.key, -1)}
                title="Giảm 1"
                aria-label={`Giảm ${r.label}`}
              >
                −
              </button>
              <input
                id={`quota-${r.key}`}
                type="number"
                min={0}
                step={1}
                className="billing-stepper__input"
                value={values[r.key]}
                onChange={(e) => onChange(r.key, e.target.value)}
              />
              <button
                type="button"
                className="billing-stepper__btn"
                onClick={() => handleStep(r.key, 1)}
                title="Tăng 1"
                aria-label={`Tăng ${r.label}`}
              >
                +
              </button>
            </div>

            {unitPrice != null && (
              <div className="billing-input-card__rate">
                Đơn giá: <b>{formatVnd(unitPrice)}</b> / {r.unit} / 30 ngày
              </div>
            )}

            {currentQuota != null && currentQuota !== currentUsed && (
              <div style={{ fontSize: '11.5px', color: '#64748b' }}>
                Hạn mức hiện tại: {currentQuota} {r.unit}
              </div>
            )}

            {hasError && <p className="billing-input-card__error">{errors[r.key]}</p>}
          </div>
        );
      })}
    </div>
  );
}

// ── Panel Mua gói (BuyPlanPanel) ──────────────────────────────────────────

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
    <div className="billing-card">
      <div className="billing-card__head">
        <div>
          <h2 className="billing-card__title">Mua gói dịch vụ</h2>
          <p className="billing-card__subtitle">
            Tự chọn số lượng khách sạn, nhân viên và phòng phù hợp với mô hình kinh doanh của bạn.
          </p>
        </div>
        <span className="billing-tag billing-tag--live">Chu kỳ 30 ngày</span>
      </div>

      <QuotaInputs
        values={values}
        onChange={(k, v) => setValues((p) => ({ ...p, [k]: v }))}
        errors={errors}
        overview={overview}
      />

      <div className="billing-calc-box">
        <div style={{ fontWeight: 700, fontSize: '13.5px', color: '#1e293b', marginBottom: 2 }}>
          Chi tiết bảng giá hiện hành
        </div>
        {RESOURCES.map((r) => {
          const qty = toNumber(values[r.key]) ?? 0;
          const price = overview.currentPricing[r.price];
          return (
            <div key={r.key} className="billing-calc-row">
              <span>
                {r.label} ({qty} × {formatVnd(price)})
              </span>
              <span>{formatVnd(qty * price)}</span>
            </div>
          );
        })}
        <div className="billing-calc-row billing-calc-row--highlight">
          <span>Tổng thanh toán chu kỳ 30 ngày</span>
          <span className="billing-calc-row__total">{valid ? formatVnd(total) : '—'}</span>
        </div>
      </div>

      <PaymentFeedback error={error} info={info} />

      <div style={{ marginTop: 20 }}>
        <button
          type="button"
          className="billing-btn-vnpay"
          disabled={!valid || submitting}
          onClick={() => pay(() => purchasePlan(toRequest(values)))}
        >
          <IconShieldCheck />
          {submitting ? 'Đang kết nối cổng VNPay…' : 'Thanh toán bảo mật qua VNPay'}
        </button>
      </div>
    </div>
  );
}

// ── Panel Mua thêm Quota giữa kỳ (BuyMorePanel) ────────────────────────────

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
  const blockedByPlan = plannedDiffers(overview);
  const valid = Object.keys(errors).length === 0 && increased && daysLeft > 0 && !belowMinimum && !blockedByPlan;

  return (
    <div className="billing-card">
      <div className="billing-card__head">
        <div>
          <h2 className="billing-card__title">Mua thêm hạn mức (Quota)</h2>
          <p className="billing-card__subtitle">
            Tăng số lượng ngay trong kỳ hiện tại. Bạn chỉ cần trả tiền chênh lệch cho{' '}
            <b>{daysLeft} ngày còn lại</b> của chu kỳ.
          </p>
        </div>
        <span className="billing-tag">Áp dụng tức thì</span>
      </div>

      {blockedByPlan && (
        <div className="billing-alert billing-alert--warn" role="status">
          <IconAlertCircle />
          <div>
            Bạn đã gia hạn trước cho kỳ bắt đầu từ <b>{formatDate(overview.plannedFrom)}</b> với cấu hình khác gói hiện
            tại, nên hệ thống sẽ mở lại mua thêm sau khi bước sang kỳ đó.
          </div>
        </div>
      )}

      <QuotaInputs
        values={values}
        onChange={(k, v) => setValues((p) => ({ ...p, [k]: v }))}
        errors={errors}
        overview={overview}
      />

      <div className="billing-calc-box">
        <div className="billing-calc-row">
          <span>Thời gian còn lại của kỳ hiện tại</span>
          <b>{daysLeft} / {CYCLE_DAYS} ngày</b>
        </div>
        <div className="billing-calc-row">
          <span>Tổng chênh lệch trọn kỳ 30 ngày</span>
          <span>{formatVnd(fullCycleDiff)}</span>
        </div>
        <div className="billing-calc-row billing-calc-row--highlight">
          <span>Phí chênh lệch cần thanh toán</span>
          <span className="billing-calc-row__total">{increased ? formatVnd(amount) : '—'}</span>
        </div>
      </div>

      {belowMinimum && (
        <div className="billing-alert billing-alert--warn">
          <IconAlertCircle />
          <div>
            Số tiền chênh lệch thấp hơn mức tối thiểu <b>{formatVnd(MIN_UPGRADE_AMOUNT)}</b> của cổng VNPay. Vui lòng tăng
            số lượng thêm hoặc thanh toán kèm trong kỳ gia hạn tiếp theo.
          </div>
        </div>
      )}

      <PaymentFeedback error={error} info={info} />

      <div style={{ marginTop: 20 }}>
        <button
          type="button"
          className="billing-btn-vnpay"
          disabled={!valid || submitting}
          onClick={() => pay(() => upgradePlan(toRequest(values)))}
        >
          <IconShieldCheck />
          {submitting ? 'Đang kết nối cổng VNPay…' : 'Thanh toán phần chênh lệch'}
        </button>
      </div>
    </div>
  );
}

// ── Panel Gia hạn gói (RenewPanel) ─────────────────────────────────────────

function RenewPanel({ overview, retry }) {
  const { pay, submitting, error, info } = usePayment('Không tạo được thanh toán gia hạn.');
  const { status, suspendReason, nextBillingDate: due } = overview;
  const locked = status === 'SUSPENDED' && suspendReason === 'PAYMENT_FAILED';
  const canRenew = daysUntil(due) <= MAX_PREPAY_DAYS;
  const nextStart = overview.nextPeriodStart ?? (locked ? todayIso() : due);
  const lockedDays = locked
    ? Math.max(0, Math.round((new Date(`${nextStart}T00:00:00`) - new Date(`${due}T00:00:00`)) / 86400000))
    : 0;
  const prepaying = daysUntil(nextStart) > 0;

  const keep = Object.fromEntries(RESOURCES.map((r) => [r.key, overview[r.planned] ?? overview[r.quota]]));
  const retryDiffers = retry && RESOURCES.some((r) => Number(retry[r.quota]) !== keep[r.key]);
  const [changing, setChanging] = useState(Boolean(retryDiffers));
  const [values, setValues] = useState(() =>
    Object.fromEntries(RESOURCES.map((r) => [r.key, String(retryDiffers ? retry[r.quota] : keep[r.key])])),
  );

  const errors = {};
  let fee = 0;
  let decreasedBelowCurrent = false;
  for (const r of RESOURCES) {
    const n = changing ? toNumber(values[r.key]) : keep[r.key];
    const used = overview[r.usage]?.used ?? 0;
    if (n == null) errors[r.key] = 'Nhập số nguyên không âm';
    else if (r.key === 'location' && n < 1) errors[r.key] = 'Cần ít nhất 1 khách sạn';
    else if (n < keep[r.key] && n < used) errors[r.key] = `Không được ít hơn số đang dùng (${used})`;
    else {
      fee += n * overview[r.price];
      if (n < overview[r.quota]) decreasedBelowCurrent = true;
    }
  }
  const valid = Object.keys(errors).length === 0;

  return (
    <div className="billing-card">
      <div className="billing-card__head">
        <div>
          <h2 className="billing-card__title">Gia hạn gói dịch vụ</h2>
          <p className="billing-card__subtitle">
            Gia hạn chu kỳ tiếp theo ({CYCLE_DAYS} ngày). Bạn có thể giữ nguyên cấu hình hiện tại hoặc linh hoạt tùy chỉnh.
          </p>
        </div>
        <span className="billing-tag billing-tag--tenant">
          Kỳ tới: {formatDate(nextStart)} → {formatDate(addDays(nextStart, CYCLE_DAYS - 1))}
        </span>
      </div>

      {(locked || status === 'PAYMENT_OVERDUE') && (
        <div className="billing-alert billing-alert--warn" role="status">
          <IconAlertCircle />
          <div>
            {locked
              ? 'Tài khoản đang bị tạm khóa do chưa hoàn tất gia hạn. Vui lòng thanh toán để mở khóa ngay lập tức.'
              : 'Gói dịch vụ đã quá hạn thanh toán. Hãy gia hạn sớm để tránh gián đoạn các tính năng của hệ thống.'}
          </div>
        </div>
      )}

      {locked && (
        <div className="billing-alert billing-alert--info" style={{ marginBottom: 16 }}>
          <IconCalendar className="billing-alert__icon" />
          <div>
            Tính tiếp từ ngày đến hạn <b>{formatDate(due)}</b> (ngày ân hạn vẫn được sử dụng bình thường)
            {lockedDays > 0 && `, cộng bù thêm ${lockedDays} ngày bị khóa`} → Ngày bắt đầu kỳ mới:{' '}
            <b>{formatDate(nextStart)}</b>.
          </div>
        </div>
      )}

      {overview.plannedFrom && (
        <div className="billing-alert billing-alert--info" style={{ marginBottom: 16 }}>
          <IconCheck className="billing-alert__icon" />
          <div>
            Đã thanh toán gia hạn trước từ <b>{formatDate(overview.plannedFrom)}</b>: {overview.plannedQuotaLocation} KS /{' '}
            {overview.plannedQuotaUser} NV / {overview.plannedQuotaRoom} Phòng.
          </div>
        </div>
      )}

      <div style={{ marginBottom: 8, fontSize: '13.5px', fontWeight: 700, color: '#1e293b' }}>
        Lựa chọn gói cho kỳ tới:
      </div>
      <div className="billing-mode-grid">
        <label className={`billing-mode-card ${!changing ? 'billing-mode-card--active' : ''}`}>
          <input type="radio" name="renew-mode" checked={!changing} onChange={() => setChanging(false)} />
          <div className="billing-mode-card__content">
            <span className="billing-mode-card__title">Giữ nguyên cấu hình hiện tại</span>
            <span className="billing-mode-card__desc">
              {keep.location} KS / {keep.user} NV / {keep.room} Phòng ({formatVnd(overview.periodFee)}/kỳ)
            </span>
          </div>
        </label>

        <label className={`billing-mode-card ${changing ? 'billing-mode-card--active' : ''}`}>
          <input type="radio" name="renew-mode" checked={changing} onChange={() => setChanging(true)} />
          <div className="billing-mode-card__content">
            <span className="billing-mode-card__title">Tùy chỉnh số lượng cho kỳ tới</span>
            <span className="billing-mode-card__desc">Tăng hoặc giảm tài nguyên áp dụng từ chu kỳ tiếp theo</span>
          </div>
        </label>
      </div>

      {changing && (
        <div style={{ marginTop: 16 }}>
          <QuotaInputs
            values={values}
            onChange={(k, v) => setValues((p) => ({ ...p, [k]: v }))}
            errors={errors}
            overview={overview}
          />
          <p style={{ fontSize: '12.5px', color: '#64748b', marginTop: 8 }}>
            ℹ️ {prepaying
              ? `Số lượng mới sẽ có hiệu lực từ ngày ${formatDate(nextStart)}; cho tới lúc đó bạn vẫn sử dụng gói hiện tại.`
              : 'Số lượng mới sẽ được kích hoạt ngay sau khi thanh toán thành công.'}{' '}
            Số lượng không được ít hơn số lượng thực tế đang sử dụng.
          </p>
        </div>
      )}

      {changing && prepaying && decreasedBelowCurrent && (
        <div className="billing-alert billing-alert--warn" style={{ marginTop: 12 }}>
          <IconAlertCircle />
          <div>
            Bạn đang gia hạn trước với số lượng ít hơn gói hiện tại. Từ khi thanh toán tới hết ngày{' '}
            <b>{formatDate(addDays(nextStart, -1))}</b>, bạn không thể tạo thêm vượt số lượng mới này.
          </div>
        </div>
      )}

      <div className="billing-calc-box">
        <div className="billing-calc-row">
          <span>Chu kỳ áp dụng</span>
          <b>{formatDate(nextStart)} → {formatDate(addDays(nextStart, CYCLE_DAYS - 1))}</b>
        </div>
        <div className="billing-calc-row billing-calc-row--highlight">
          <span>Số tiền thanh toán kỳ tới</span>
          <span className="billing-calc-row__total">{valid ? formatVnd(fee) : '—'}</span>
        </div>
        {valid && fee !== overview.periodFee && (
          <div style={{ fontSize: '12.5px', color: '#64748b', textAlign: 'right' }}>
            (Phí gói hiện tại: {formatVnd(overview.periodFee)})
          </div>
        )}
      </div>

      {!canRenew && (
        <div className="billing-alert billing-alert--info">
          <IconCalendar className="billing-alert__icon" />
          <div>
            Bạn đã thanh toán trả trước tối đa cho kỳ này. Bạn có thể gia hạn tiếp bắt đầu từ ngày{' '}
            <b>{formatDate(addDays(due, -MAX_PREPAY_DAYS))}</b>.
          </div>
        </div>
      )}

      <PaymentFeedback error={error} info={info} />

      <div style={{ marginTop: 20 }}>
        <button
          type="button"
          className="billing-btn-vnpay"
          disabled={!canRenew || !valid || submitting}
          onClick={() => pay(() => renewPlan(changing ? toRequest(values) : undefined))}
        >
          <IconShieldCheck />
          {submitting ? 'Đang kết nối cổng VNPay…' : 'Xác nhận gia hạn qua VNPay'}
        </button>
      </div>
    </div>
  );
}

// ── Onboarding Cho Khách Hàng Mới (ChoosePlanPanel) ─────────────────────────

function ChoosePlanPanel({ overview, retry }) {
  const { refresh } = useAuth();
  const navigate = useNavigate();
  const [buying, setBuying] = useState(false);
  const [confirmTrial, setConfirmTrial] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  async function handleStartTrial() {
    setConfirmTrial(false);
    setError('');
    setSubmitting(true);
    try {
      const { message, activated } = await startTrial();
      if (activated) {
        setInfo(message);
        const me = await refresh();
        setTimeout(() => navigate(homePathFor(me), { replace: true }), 1500);
        return;
      }
      setError(message);
    } catch (err) {
      setError(readErrorMessage(err, 'Không bắt đầu được dùng thử.'));
    }
    setSubmitting(false);
  }

  return (
    <div className="billing-card">
      <div className="billing-card__head">
        <div>
          <h2 className="billing-card__title">Chào mừng bạn đến với hệ thống</h2>
          <p className="billing-card__subtitle">
            Hãy lựa chọn trải nghiệm dùng thử miễn phí hoặc mua gói chính thức để bắt đầu vận hành chuỗi khách sạn.
          </p>
        </div>
      </div>

      <div className="billing-onboarding-grid">
        {/* Thẻ Dùng thử */}
        <div className="billing-tier-card">
          <div className="billing-tier-card__head">
            <span className="billing-tier-card__icon">🎁</span>
            <div>
              <h3 className="billing-tier-card__title">Dùng thử miễn phí</h3>
              <span className="billing-tag">Không cần thẻ tín dụng</span>
            </div>
          </div>

          <div className="billing-tier-card__price-box">
            <span className="billing-tier-card__price-val">{overview.trialDays}</span>
            <span className="billing-tier-card__price-unit">ngày trải nghiệm</span>
          </div>

          <ul className="billing-tier-card__features">
            <li className="billing-tier-card__feature-item">
              <IconCheck className="billing-tier-card__feature-icon" />
              <span>Tối đa <b>{overview.quotaLocation} khách sạn</b></span>
            </li>
            <li className="billing-tier-card__feature-item">
              <IconCheck className="billing-tier-card__feature-icon" />
              <span>Tối đa <b>{overview.quotaUser} nhân sự</b></span>
            </li>
            <li className="billing-tier-card__feature-item">
              <IconCheck className="billing-tier-card__feature-icon" />
              <span>Tối đa <b>{overview.quotaRoom} phòng</b></span>
            </li>
            <li className="billing-tier-card__feature-item">
              <IconCheck className="billing-tier-card__feature-icon" />
              <span>Có thể nâng cấp lên gói chính thức bất cứ lúc nào</span>
            </li>
          </ul>

          <button
            type="button"
            className="btn btn--ghost btn--block"
            disabled={submitting}
            onClick={() => setConfirmTrial(true)}
            style={{ marginTop: 'auto', padding: '12px 18px', fontWeight: 700 }}
          >
            {submitting ? 'Đang kích hoạt…' : 'Bắt đầu dùng thử ngay'}
          </button>
        </div>

        {/* Thẻ Mua gói chính thức */}
        <div className="billing-tier-card billing-tier-card--featured">
          <span className="billing-tier-card__ribbon">Đề xuất doanh nghiệp</span>
          <div className="billing-tier-card__head">
            <span className="billing-tier-card__icon">💳</span>
            <div>
              <h3 className="billing-tier-card__title">Gói Doanh nghiệp</h3>
              <span className="billing-tag billing-tag--live">Tự do tùy biến</span>
            </div>
          </div>

          <div className="billing-tier-card__price-box">
            <span className="billing-tier-card__price-val">{CYCLE_DAYS}</span>
            <span className="billing-tier-card__price-unit">ngày / chu kỳ</span>
          </div>

          <ul className="billing-tier-card__features">
            <li className="billing-tier-card__feature-item">
              <IconCheck className="billing-tier-card__feature-icon" />
              <span>Tự chọn số lượng khách sạn, nhân viên, phòng theo nhu cầu</span>
            </li>
            <li className="billing-tier-card__feature-item">
              <IconCheck className="billing-tier-card__feature-icon" />
              <span>Thanh toán tiện lợi, an toàn qua cổng VNPay</span>
            </li>
            <li className="billing-tier-card__feature-item">
              <IconCheck className="billing-tier-card__feature-icon" />
              <span>Hỗ trợ mua thêm quota và gia hạn chu kỳ bất kỳ lúc nào</span>
            </li>
            <li className="billing-tier-card__feature-item">
              <IconCheck className="billing-tier-card__feature-icon" />
              <span>Không giới hạn tính năng phân quyền & vận hành</span>
            </li>
          </ul>

          <button
            type="button"
            className="btn btn--primary btn--block"
            disabled={submitting}
            onClick={() => setBuying(true)}
            style={{ marginTop: 'auto', padding: '12px 18px', fontWeight: 700 }}
          >
            Cấu hình & Mua gói ngay
          </button>
        </div>
      </div>

      <div style={{ marginTop: 16 }}>
        <PaymentFeedback error={error} info={info} />
      </div>

      {confirmTrial && (
        <ConfirmDialog
          title="Bắt đầu dùng thử miễn phí?"
          message={
            <>
              Thời gian dùng thử <b>{overview.trialDays} ngày</b> bắt đầu tính từ hôm nay. Sau khi hết hạn nếu chưa mua
              gói chính thức, các tính năng quản lý sẽ tạm khóa cho tới khi thanh toán.
            </>
          }
          confirmLabel="Xác nhận dùng thử"
          confirmTone="primary"
          onCancel={() => setConfirmTrial(false)}
          onConfirm={handleStartTrial}
        />
      )}

      {buying && (
        <FormModal onClose={() => setBuying(false)} wide>
          <BuyPlanPanel overview={overview} retry={retry} />
        </FormModal>
      )}
    </div>
  );
}

// ── Trang Chính (BillingPage) ───────────────────────────────────────────────

export default function BillingPage() {
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const retryNotice = searchParams.get('msg');
  const retry = searchParams.get('ql')
    ? {
        quotaLocation: searchParams.get('ql'),
        quotaUser: searchParams.get('qu'),
        quotaRoom: searchParams.get('qr'),
      }
    : null;

  const [overview, setOverview] = useState(null);
  const [invoices, setInvoices] = useState([]);
  const [error, setError] = useState('');

  // Tab thao tác khi gói active (renew vs upgrade)
  const [activeTab, setActiveTab] = useState('renew');

  useEffect(() => {
    if (user?.role !== 'DIRECTOR') return undefined;
    let cancelled = false;
    Promise.all([fetchBillingOverview(), fetchInvoices()])
      .then(([ov, inv]) => {
        if (cancelled) return;
        setOverview(ov);
        setInvoices(inv);
        // Tự động chọn tab phù hợp nếu có thông tin retry hoặc đến hạn
        if (retry && ov) {
          const keepLoc = ov.plannedQuotaLocation ?? ov.quotaLocation;
          if (Number(retry.quotaLocation) > keepLoc) {
            setActiveTab('upgrade');
          }
        }
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
      <div className="billing-page">
        <div className="billing-alert billing-alert--danger">
          <IconAlertCircle />
          <div>{error}</div>
        </div>
      </div>
    );
  }

  if (!overview) {
    return (
      <div className="billing-page" style={{ paddingTop: 60, textAlign: 'center' }}>
        <p className="state">Đang tải thông tin gói dịch vụ…</p>
      </div>
    );
  }

  const { status, suspendReason, trial } = overview;
  const adminLocked = status === 'SUSPENDED' && suspendReason === 'ADMIN_LOCKED';
  const planNotChosen = trial && !overview.trialEndsAt;

  // Trường hợp Tenant mới chưa kích hoạt dùng thử hay mua gói
  if (planNotChosen) {
    return (
      <div className="billing-page">
        <div className="billing-header">
          <div className="billing-header__title-area">
            <div className="billing-header__badge-row">
              <span className="billing-tag billing-tag--tenant">Doanh nghiệp: {overview.tenantName}</span>
              <span className="billing-tag">Bắt đầu dịch vụ</span>
            </div>
            <h1>Gói dịch vụ hệ thống</h1>
          </div>
        </div>

        {retryNotice && (
          <div className="billing-alert billing-alert--warn" role="status">
            <IconAlertCircle />
            <div>{retryNotice}</div>
          </div>
        )}

        <ChoosePlanPanel overview={overview} retry={retry} />
      </div>
    );
  }

  const daysLeft = daysUntil(overview.nextBillingDate);
  const showOperations = !adminLocked;
  const canBothOperations = showOperations && !trial && status === 'ACTIVE';

  return (
    <div className="billing-page">
      {/* ── Tiêu đề & Thông tin Tenant ───────────────────────────────────── */}
      <div className="billing-header">
        <div className="billing-header__title-area">
          <div className="billing-header__badge-row">
            <span className="billing-tag billing-tag--tenant">Doanh nghiệp: {overview.tenantName}</span>
            <TenantStatusBadge status={status} />
            {trial ? (
              <span className="billing-tag billing-tag--live">Dùng thử</span>
            ) : (
              <span className="billing-tag billing-tag--live">Gói Doanh nghiệp</span>
            )}
          </div>
          <h1>Gói dịch vụ & Hạn mức sử dụng</h1>
        </div>
      </div>

      {/* Thông báo kết quả thanh toán / cảnh báo nếu có */}
      {retryNotice && (
        <div className="billing-alert billing-alert--warn" role="status">
          <IconAlertCircle />
          <div>{retryNotice}</div>
        </div>
      )}

      {status === 'SUSPENDED' && (
        <div className="billing-alert billing-alert--danger" role="status">
          <IconAlertCircle />
          <div>
            Tài khoản hiện đang bị khóa do <b>{SUSPEND_REASON_LABEL[suspendReason] ?? suspendReason ?? '—'}</b>.
            {suspendReason === 'TRIAL_EXPIRED' && ' Vui lòng mua gói dịch vụ bên dưới để mở lại quyền truy cập.'}
          </div>
        </div>
      )}

      {status === 'PAYMENT_OVERDUE' && (
        <div className="billing-alert billing-alert--warn" role="status">
          <IconAlertCircle />
          <div>
            Gói dịch vụ đã quá hạn thanh toán. Vui lòng gia hạn sớm để tránh tài khoản bị chuyển sang trạng thái tạm khóa.
          </div>
        </div>
      )}

      {/* ── Phần A: Thông tin Gói hiện tại & Tài nguyên ──────────────────── */}
      <section className="billing-card">
        <div className="billing-card__head">
          <div>
            <h2 className="billing-card__title">
              <IconShieldCheck />
              <span>Tổng quan gói đang sử dụng</span>
            </h2>
            <p className="billing-card__subtitle">
              Theo dõi hạn sử dụng, chi phí định kỳ và tỷ lệ sử dụng tài nguyên thực tế của toàn chuỗi khách sạn.
            </p>
          </div>
        </div>

        {/* Thông số chính của gói */}
        <div className="billing-plan-overview">
          {trial ? (
            <>
              <div className="billing-plan-stat">
                <span className="billing-plan-stat__label">
                  <IconCalendar /> Hạn dùng thử
                </span>
                <span className="billing-plan-stat__val">{formatDate(overview.trialEndsAt)}</span>
                <span className="billing-plan-stat__sub">
                  {overview.trialDaysLeft != null ? `Còn ${overview.trialDaysLeft} ngày trải nghiệm` : 'Đang dùng thử'}
                </span>
              </div>
              <div className="billing-plan-stat">
                <span className="billing-plan-stat__label">Đơn giá khi mua chính thức</span>
                <span className="billing-plan-stat__val">Theo bảng giá hiện hành</span>
                <span className="billing-plan-stat__sub">Chu kỳ 30 ngày từ ngày thanh toán</span>
              </div>
            </>
          ) : (
            <>
              <div className="billing-plan-stat">
                <span className="billing-plan-stat__label">
                  <IconCalendar /> Đã thanh toán đến
                </span>
                <span className="billing-plan-stat__val">{formatDate(addDays(overview.nextBillingDate, -1))}</span>
                <span className="billing-plan-stat__sub">
                  {daysLeft > 0 ? `Còn ${daysLeft} ngày khả dụng` : 'Hết hạn hôm nay'}
                </span>
              </div>

              <div className="billing-plan-stat">
                <span className="billing-plan-stat__label">
                  <IconCalendar /> Ngày đến hạn gia hạn
                </span>
                <span className="billing-plan-stat__val">{formatDate(overview.nextBillingDate)}</span>
                <span className="billing-plan-stat__sub">Chu kỳ 30 ngày tiếp theo</span>
              </div>

              <div className="billing-plan-stat">
                <span className="billing-plan-stat__label">Chi phí mỗi chu kỳ</span>
                <span className="billing-plan-stat__val" style={{ color: '#1e40af' }}>
                  {formatVnd(overview.periodFee)}
                </span>
                <span className="billing-plan-stat__sub">Áp dụng cho cấu hình hiện tại</span>
              </div>
            </>
          )}
        </div>

        {/* Thẻ hạn mức 3 loại tài nguyên */}
        <div style={{ marginBottom: 8, fontSize: '13.5px', fontWeight: 700, color: '#1e293b' }}>
          Tình trạng sử dụng tài nguyên:
        </div>
        <div className="billing-meters">
          {RESOURCES.map((r) => (
            <Meter key={r.key} resourceKey={r.key} label={r.label} metric={overview[r.usage]} />
          ))}
        </div>
      </section>

      {/* ── Phần B: Thao tác nâng cấp / Gia hạn gói ──────────────────────── */}
      {showOperations && (
        <section className="billing-tabs-container">
          {trial ? (
            <BuyPlanPanel overview={overview} retry={retry} />
          ) : canBothOperations ? (
            <div className="billing-tabs-wrapper">
              <div className="billing-tabs-nav">
                <button
                  type="button"
                  className={`billing-tab-btn ${activeTab === 'renew' ? 'billing-tab-btn--active' : ''}`}
                  onClick={() => setActiveTab('renew')}
                >
                  <IconCalendar />
                  <span>Gia hạn kỳ tiếp theo</span>
                  {daysLeft <= 7 && <span className="billing-tab-pill billing-tab-pill--warn">Đến hạn</span>}
                </button>
                <button
                  type="button"
                  className={`billing-tab-btn ${activeTab === 'upgrade' ? 'billing-tab-btn--active' : ''}`}
                  onClick={() => setActiveTab('upgrade')}
                >
                  <IconBuilding />
                  <span>Mua thêm hạn mức</span>
                </button>
              </div>

              <div style={{ marginTop: 16 }}>
                {activeTab === 'renew' && <RenewPanel overview={overview} retry={retry} />}
                {activeTab === 'upgrade' && <BuyMorePanel overview={overview} retry={retry} />}
              </div>
            </div>
          ) : (
            // Trạng thái SUSPENDED hoặc PAYMENT_OVERDUE: hiển thị trực tiếp Gia hạn
            <RenewPanel overview={overview} retry={retry} />
          )}
        </section>
      )}

      {/* ── Phần C: Lịch sử hóa đơn thanh toán ───────────────────────────── */}
      <section className="billing-card">
        <div className="billing-card__head">
          <div>
            <h2 className="billing-card__title">
              <IconCalendar />
              <span>Lịch sử hóa đơn & Giao dịch</span>
            </h2>
            <p className="billing-card__subtitle">
              Danh sách các hóa đơn đã thanh toán thành công qua cổng thanh toán VNPay.
            </p>
          </div>
          <span className="billing-tag">{invoices.length} hóa đơn</span>
        </div>

        {invoices.length === 0 ? (
          <div className="billing-empty-state">
            <svg
              className="billing-empty-state__icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <rect width="16" height="20" x="4" y="2" rx="2" />
              <line x1="8" x2="16" y1="6" y2="6" />
              <line x1="8" x2="16" y1="10" y2="10" />
              <line x1="8" x2="12" y1="14" y2="14" />
            </svg>
            <p style={{ fontWeight: 600 }}>Chưa có hóa đơn thanh toán nào</p>
            <p style={{ fontSize: '13px', margin: 0 }}>
              Các giao dịch mua gói, gia hạn và mua thêm quota sẽ được lưu trữ tự động tại đây.
            </p>
          </div>
        ) : (
          <div className="billing-table-card">
            <div className="table-wrap">
              <table className="billing-table">
                <thead>
                  <tr>
                    <th>Thời gian</th>
                    <th>Loại giao dịch</th>
                    <th>Kỳ áp dụng</th>
                    <th>Hạn mức tài nguyên</th>
                    <th style={{ textAlign: 'right' }}>Số tiền thanh toán</th>
                  </tr>
                </thead>
                <tbody>
                  {invoices.map((inv) => (
                    <tr key={inv.id}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{formatDateTime(inv.issuedAt)}</div>
                      </td>
                      <td>
                        <span className={`billing-badge-kind billing-badge-kind--${inv.kind}`}>
                          {INVOICE_KIND_LABEL[inv.kind] ?? inv.kind}
                        </span>
                      </td>
                      <td>
                        <div style={{ color: '#475569', fontSize: '13px' }}>
                          {formatDate(inv.periodStart)} → {formatDate(inv.periodEnd)}
                        </div>
                      </td>
                      <td>
                        <div className="billing-quota-pills">
                          <span className="billing-quota-pill">{inv.quotaLocation} Khách sạn</span>
                          <span className="billing-quota-pill">{inv.quotaUser} Nhân viên</span>
                          <span className="billing-quota-pill">{inv.quotaRoom} Phòng</span>
                        </div>
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <span style={{ fontWeight: 800, color: '#0f172a', fontSize: '14.5px' }}>
                          {formatVnd(inv.amount)}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
