import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { Link } from "react-router";
import { AlertTriangle, Building2, Mail, Phone, RefreshCw, Search } from "lucide-react";
import { api } from "../../lib/api";
import { formatDate, SUSPEND_REASON_LABEL, TENANT_STATUS_LABEL } from "../../lib/format";
import type { Page, SuspendReason, Tenant, TenantStatus } from "../../lib/types";
import { usePagedList } from "../../lib/usePagedList";
import { Pager } from "../../components/Pager";
import { TenantAvatar, shortId } from "../../components/TenantAvatar";
import { TenantStatusBadge } from "../../components/TenantStatusBadge";
import { Button } from "../../components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../../components/ui/table";

const STATUSES = Object.keys(TENANT_STATUS_LABEL) as TenantStatus[];

type Counts = Record<TenantStatus, number> & { total: number };

function StatCard({
  label,
  value,
  unit,
  icon,
  tone = "blue",
  children,
}: {
  label: string;
  value: number | string;
  unit: string;
  icon: ReactNode;
  tone?: "blue" | "amber";
  children?: ReactNode;
}) {
  const iconStyle = tone === "amber" ? "bg-amber-50 text-amber-600" : "bg-blue-50 text-blue-600";
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="flex items-start justify-between">
        <div>
          <div className={`text-xs font-semibold tracking-wide ${tone === "amber" ? "text-amber-600" : "text-muted-foreground"}`}>
            {label}
          </div>
          <div className="mt-1 flex items-baseline gap-2">
            <span className="text-3xl font-bold">{value}</span>
            <span className="text-sm text-muted-foreground">{unit}</span>
          </div>
        </div>
        <div className={`flex size-9 items-center justify-center rounded-lg ${iconStyle}`}>{icon}</div>
      </div>
      {children && <div className="mt-3 text-xs text-muted-foreground">{children}</div>}
    </div>
  );
}

export function TenantsPage() {
  const [status, setStatus] = useState<TenantStatus | "">("");
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [counts, setCounts] = useState<Counts | null>(null);
  const { data, loading, page, setPage, reload } = usePagedList<Tenant>("/platform/tenants", {
    status: status || undefined,
    keyword: keyword || undefined,
  });

  useEffect(() => {
    const count = (s?: TenantStatus) =>
      api
        .get<Page<Tenant>>("/platform/tenants", { params: { status: s, size: 1 } })
        .then((r) => r.data.totalElements)
        .catch(() => 0);
    Promise.all([count(), ...STATUSES.map((s) => count(s))]).then(([total, ...rest]) => {
      const byStatus = Object.fromEntries(STATUSES.map((s, i) => [s, rest[i]])) as Record<TenantStatus, number>;
      setCounts({ total, ...byStatus });
    });
  }, []);

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    setPage(0);
    setKeyword(keywordInput.trim());
  };

  const from = data && data.totalElements > 0 ? data.number * data.size + 1 : 0;
  const to = data ? data.number * data.size + data.content.length : 0;

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold">Quản lý Danh sách Tenant</h1>
        <p className="text-sm text-muted-foreground">
          Tổng quan các tổ chức, chuỗi khách sạn và doanh nghiệp lưu trú đang vận hành trên nền tảng SaaS Multi-tenant.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <StatCard label="TỔNG SỐ TENANT" value={counts?.total ?? "—"} unit="Tổ chức" icon={<Building2 className="size-5" />}>
          <span className="text-emerald-600">{counts?.ACTIVE ?? 0} Đang hoạt động</span>
          {" · "}
          <span className="text-blue-600">{counts?.TRIAL ?? 0} Thử nghiệm</span>
          {" · "}
          <span>{counts?.SUSPENDED ?? 0} Đã khóa</span>
        </StatCard>
        <StatCard
          label="CẢNH BÁO THUÊ BAO"
          value={counts?.PAYMENT_OVERDUE ?? "—"}
          unit="Quá hạn thanh toán"
          tone="amber"
          icon={<AlertTriangle className="size-5" />}
        >
          {counts?.SUSPENDED ?? 0} tenant đã bị khóa
        </StatCard>
      </div>

      <div className="rounded-xl border bg-white p-4">
        <form onSubmit={onSearch} className="flex flex-wrap gap-2">
          <div className="relative min-w-64 flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <input
              className="h-9 w-full rounded-md border bg-slate-50 pl-9 pr-3 text-sm outline-none focus:ring-2 focus:ring-primary/30"
              placeholder="Tìm theo tên tenant hoặc email liên hệ…"
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
            />
          </div>
          <select
            className="h-9 rounded-md border bg-white px-3 text-sm"
            value={status}
            onChange={(e) => {
              setPage(0);
              setStatus(e.target.value as TenantStatus | "");
            }}
          >
            <option value="">Tất cả trạng thái{counts ? ` (${counts.total})` : ""}</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {TENANT_STATUS_LABEL[s]}
                {counts ? ` (${counts[s]})` : ""}
              </option>
            ))}
          </select>
          <Button type="submit">Tìm kiếm</Button>
          <Button type="button" variant="outline" size="icon" onClick={reload} aria-label="Tải lại">
            <RefreshCw className="size-4" />
          </Button>
        </form>
      </div>

      <div className="rounded-xl border bg-white">
        <div className="border-b px-4 py-3 font-semibold">Danh sách Tenant Đang Quản Trị</div>
        <div className="px-4">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="text-xs">MÃ &amp; TÊN TENANT</TableHead>
                <TableHead className="text-xs">LIÊN HỆ</TableHead>
                <TableHead className="text-xs">NGÀY ĐĂNG KÝ</TableHead>
                <TableHead className="text-xs">TRẠNG THÁI</TableHead>
                <TableHead className="text-right text-xs">THAO TÁC</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data?.content.map((t) => (
                <TableRow key={t.id}>
                  <TableCell>
                    <div className="flex items-center gap-3">
                      <TenantAvatar id={t.id} name={t.name} />
                      <div>
                        <div className="font-semibold">{t.name}</div>
                        <div className="text-xs text-muted-foreground">TNT-{shortId(t.id)}</div>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1.5 text-sm">
                      <Mail className="size-3.5 text-muted-foreground" /> {t.contactEmail}
                    </div>
                    <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                      <Phone className="size-3.5" /> {t.contactPhone}
                    </div>
                  </TableCell>
                  <TableCell className="text-sm">{formatDate(t.createdAt)}</TableCell>
                  <TableCell>
                    <TenantStatusBadge status={t.status} />
                    {t.suspendReason && (
                      <div className="mt-1 text-xs text-muted-foreground">
                        {SUSPEND_REASON_LABEL[t.suspendReason as SuspendReason] ?? t.suspendReason}
                      </div>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <Button asChild size="sm" variant="outline">
                      <Link to={`/tenants/${t.id}`}>Chi tiết</Link>
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {!loading && data?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="py-10 text-center text-muted-foreground">
                    Không có tenant nào khớp điều kiện
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
        <div className="border-t px-4 pb-4">
          {data && data.totalElements > 0 && (
            <div className="pt-3 text-xs text-muted-foreground">
              Hiển thị {from} – {to} trên tổng số {data.totalElements} Tenants
            </div>
          )}
          <Pager data={data} page={page} onChange={setPage} />
        </div>
      </div>
    </div>
  );
}
