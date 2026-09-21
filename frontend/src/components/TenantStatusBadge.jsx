// design.md mục 3: huy hiệu trạng thái luôn có CHỮ kèm màu, không chỉ dùng chấm tròn.
// Tách riêng khỏi StatusBadge.jsx (dành cho trạng thái Location) để không đụng vào file dùng chung.

/** Trạng thái hoạt động của Tenant — BR-SAAS-01. */
export const TENANT_STATUS_LABEL = {
  TRIAL: 'Dùng thử',
  ACTIVE: 'Đang hoạt động',
  PAYMENT_OVERDUE: 'Quá hạn thanh toán',
  SUSPENDED: 'Đã khóa',
};

/** Lý do Tenant bị khóa — BR-SAAS-16. */
export const SUSPEND_REASON_LABEL = {
  TRIAL_EXPIRED: 'Hết hạn dùng thử',
  PAYMENT_FAILED: 'Thanh toán thất bại',
  ADMIN_LOCKED: 'Admin khóa',
};

const TONE = {
  TRIAL: 'blue',
  ACTIVE: 'green',
  PAYMENT_OVERDUE: 'orange',
  SUSPENDED: 'grey',
};

export default function TenantStatusBadge({ status }) {
  const tone = TONE[status] ?? 'grey';
  return <span className={`badge badge--${tone}`}>{TENANT_STATUS_LABEL[status] ?? status}</span>;
}
