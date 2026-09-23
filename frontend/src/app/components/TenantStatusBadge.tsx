import { TENANT_STATUS_LABEL } from "../lib/format";
import type { TenantStatus } from "../lib/types";

const STYLE: Record<TenantStatus, { pill: string; dot: string }> = {
  ACTIVE: { pill: "bg-emerald-50 text-emerald-700", dot: "bg-emerald-500" },
  TRIAL: { pill: "bg-blue-50 text-blue-700", dot: "bg-blue-500" },
  PAYMENT_OVERDUE: { pill: "bg-amber-50 text-amber-700", dot: "bg-amber-500" },
  SUSPENDED: { pill: "bg-slate-100 text-slate-600", dot: "bg-slate-400" },
};

export function TenantStatusBadge({ status }: { status: TenantStatus }) {
  const s = STYLE[status];
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${s.pill}`}>
      <span className={`size-1.5 rounded-full ${s.dot}`} />
      {TENANT_STATUS_LABEL[status]}
    </span>
  );
}
