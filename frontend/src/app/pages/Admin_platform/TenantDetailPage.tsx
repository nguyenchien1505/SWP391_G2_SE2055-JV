import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router";
import { BedDouble, Building, ChevronRight, Lock, LockOpen, MapPin, Phone, Star, Users } from "lucide-react";
import { toast } from "sonner";
import { api, errorMessage } from "../../lib/api";
import { formatDate, formatDateTime, formatVnd, LOCATION_STATUS_LABEL, SUSPEND_REASON_LABEL } from "../../lib/format";
import type { LocationItem, Page, TenantDetail, TenantUsage, UsageMetric } from "../../lib/types";
import { TenantAvatar, shortId } from "../../components/TenantAvatar";
import { TenantStatusBadge } from "../../components/TenantStatusBadge";
import { Button } from "../../components/ui/button";
import { Progress } from "../../components/ui/progress";

function Panel({ title, children, className = "" }: { title: string; children: ReactNode; className?: string }) {
  return (
    <section className={`rounded-xl border bg-white ${className}`}>
      <h2 className="border-b px-5 py-3 font-semibold">{title}</h2>
      <div className="p-5">{children}</div>
    </section>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className="mt-0.5 text-sm font-medium">{children}</div>
    </div>
  );
}

function ResourceCard({ label, icon, metric }: { label: string; icon: ReactNode; metric: UsageMetric }) {
  const percent = Math.min(metric.usedPercent ?? 0, 100);
  return (
    <div className="rounded-lg border p-4">
      <div className="flex items-center justify-between text-sm text-muted-foreground">
        {label}
        {icon}
      </div>
      <div className="mt-2 flex items-baseline gap-1">
        <span className={`text-3xl font-bold ${metric.overQuota ? "text-destructive" : ""}`}>{metric.used}</span>
        <span className="text-sm text-muted-foreground">/ {metric.quota ?? "—"}</span>
      </div>
      <Progress
        value={percent}
        className={`mt-3 ${metric.overQuota ? "bg-red-100 [&>div]:bg-destructive" : ""}`}
      />
      <div className={`mt-1.5 text-xs ${metric.overQuota ? "text-destructive" : "text-muted-foreground"}`}>
        {metric.overQuota
          ? `Vượt hạn mức ${Math.abs(metric.remaining ?? 0)} đơn vị`
          : metric.usedPercent != null
            ? `Đạt ${metric.usedPercent}% hạn mức`
            : "Chưa có hạn mức"}
      </div>
    </div>
  );
}

export function TenantDetailPage() {
  const { id } = useParams();
  const [tenant, setTenant] = useState<TenantDetail | null>(null);
  const [usage, setUsage] = useState<TenantUsage | null>(null);
  const [locations, setLocations] = useState<LocationItem[] | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [d, u, l] = await Promise.all([
        api.get<TenantDetail>(`/platform/tenants/${id}`),
        api.get<TenantUsage>(`/platform/tenants/${id}/usage`),
        api.get<Page<LocationItem>>(`/platform/tenants/${id}/locations`, { params: { size: 100 } }),
      ]);
      setTenant(d.data);
      setUsage(u.data);
      setLocations(l.data.content);
    } catch (err) {
      toast.error(errorMessage(err, "Không tải được thông tin tenant"));
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const act = async (action: "suspend" | "reactivate") => {
    if (!tenant) return;
    const verb = action === "suspend" ? "khóa" : "mở khóa";
    if (!window.confirm(`Xác nhận ${verb} "${tenant.name}"?`)) return;
    setBusy(true);
    try {
      await api.post(`/platform/tenants/${tenant.id}/${action}`);
      toast.success(`Đã ${verb} ${tenant.name}`);
      await load();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  if (!tenant) return <p className="text-muted-foreground">Đang tải…</p>;

  const sub = tenant.subscription;
  const estimate = sub
    ? sub.quotaLocation * sub.pricePerLocation + sub.quotaUser * sub.pricePerUser + sub.quotaRoom * sub.pricePerRoom
    : null;
  // Backend chỉ cho Admin mở lại khi Tenant bị khóa thủ công (ADMIN_LOCKED).
  const canReactivate = tenant.status === "SUSPENDED" && tenant.suspendReason === "ADMIN_LOCKED";

  return (
    <div className="space-y-5">
      <nav className="flex items-center gap-1 text-sm text-muted-foreground">
        <span>Quản trị Tenant</span>
        <ChevronRight className="size-4" />
        <Link to="/tenants" className="hover:text-foreground">
          Danh sách Tenant
        </Link>
        <ChevronRight className="size-4" />
        <span className="font-medium text-foreground">Chi tiết Tenant: {tenant.name}</span>
      </nav>

      <div className="flex flex-wrap items-start justify-between gap-4 rounded-xl border bg-white p-5">
        <div className="flex items-center gap-4">
          <TenantAvatar id={tenant.id} name={tenant.name} size="lg" />
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-bold">{tenant.name}</h1>
              <TenantStatusBadge status={tenant.status} />
            </div>
            <div className="text-sm text-muted-foreground">
              Mã định danh: TNT-{shortId(tenant.id)} · Ngày đăng ký: {formatDate(tenant.createdAt)}
            </div>
          </div>
        </div>
        <div className="space-y-2 text-right">
          {tenant.status === "SUSPENDED" ? (
            <Button disabled={busy || !canReactivate} onClick={() => act("reactivate")}>
              <LockOpen className="mr-2 size-4" /> Mở khóa tài khoản
            </Button>
          ) : (
            <Button variant="destructive" disabled={busy} onClick={() => act("suspend")}>
              <Lock className="mr-2 size-4" /> Khóa tài khoản
            </Button>
          )}
        </div>
      </div>

      {tenant.status === "SUSPENDED" && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          Tenant đang bị khóa do <b>{tenant.suspendReason ? SUSPEND_REASON_LABEL[tenant.suspendReason] : "—"}</b>
          {tenant.suspendedAt && <> từ {formatDateTime(tenant.suspendedAt)}</>}.
          {!canReactivate && " Chỉ được kích hoạt lại khi thanh toán thành công, Admin không có quyền mở khóa."}
        </div>
      )}

      <div className="grid gap-5 xl:grid-cols-3">
        <div className="space-y-5 xl:col-span-2">
          <Panel title="Thông tin pháp nhân & đại diện">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Tên tổ chức">{tenant.name}</Field>
              <Field label="Email liên hệ">{tenant.contactEmail}</Field>
              <Field label="Số điện thoại">{tenant.contactPhone}</Field>
              <Field label="Cập nhật lần cuối">{formatDateTime(tenant.updatedAt)}</Field>
              <Field label="Khóa lần gần nhất">{formatDateTime(tenant.suspendedAt)}</Field>
              <Field label="Mở khóa lần gần nhất">{formatDateTime(tenant.reactivatedAt)}</Field>
            </div>
          </Panel>

          <Panel title="Giám sát tài nguyên & hạn mức hợp đồng">
            {usage ? (
              <>
                <div className="grid gap-4 sm:grid-cols-3">
                  <ResourceCard label="Cơ sở / Location" icon={<Building className="size-4" />} metric={usage.locations} />
                  <ResourceCard label="Phòng nghỉ" icon={<BedDouble className="size-4" />} metric={usage.rooms} />
                  <ResourceCard label="Tài khoản nhân viên" icon={<Users className="size-4" />} metric={usage.staff} />
                </div>

              </>
            ) : (
              <p className="text-sm text-muted-foreground">Đang tải…</p>
            )}
          </Panel>

          <Panel title="Danh sách cơ sở">
            {locations ? (
              locations.length > 0 ? (
                <div className="grid gap-3 sm:grid-cols-2">
                  {locations.map((loc) => (
                    <div key={loc.id} className="rounded-lg border p-4">
                      <div className="flex items-start justify-between gap-2">
                        <div className="font-medium">{loc.name}</div>
                        <span
                          className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${
                            loc.status === "OPERATIONAL"
                              ? "bg-emerald-50 text-emerald-700"
                              : "bg-slate-100 text-slate-600"
                          }`}
                        >
                          {LOCATION_STATUS_LABEL[loc.status]}
                        </span>
                      </div>
                      <div className="mt-2 space-y-1 text-sm text-muted-foreground">
                        <div className="flex items-center gap-1.5">
                          <MapPin className="size-3.5 shrink-0" /> {loc.address}
                        </div>
                        <div className="flex items-center gap-1.5">
                          <Phone className="size-3.5 shrink-0" /> {loc.phone}
                        </div>
                        <div className="flex items-center gap-3">
                          {loc.starRating != null && (
                            <span className="flex items-center gap-1">
                              <Star className="size-3.5" /> {loc.starRating}
                            </span>
                          )}
                          <span className="flex items-center gap-1">
                            <BedDouble className="size-3.5" /> {loc.totalRooms} phòng
                          </span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">Tenant chưa có cơ sở nào.</p>
              )
            ) : (
              <p className="text-sm text-muted-foreground">Đang tải…</p>
            )}
          </Panel>
        </div>

        <div className="space-y-5">
          <section className="rounded-xl bg-blue-900 p-5 text-white">
            <div className="text-xs font-semibold tracking-wider text-blue-200">
              {sub?.trial ? "GÓI DÙNG THỬ HIỆN TẠI" : "GÓI THUÊ BAO HIỆN TẠI"}
            </div>
            {sub ? (
              <>
                <div className="mt-2 text-3xl font-bold">{formatVnd(estimate)}</div>
                <div className="text-sm text-blue-200">tạm tính / chu kỳ 30 ngày</div>
                <dl className="mt-4 space-y-2 border-t border-blue-700 pt-4 text-sm">
                  <div className="flex justify-between">
                    <dt className="text-blue-200">Location</dt>
                    <dd>{sub.quotaLocation} × {formatVnd(sub.pricePerLocation)}</dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-blue-200">Staff</dt>
                    <dd>{sub.quotaUser} × {formatVnd(sub.pricePerUser)}</dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-blue-200">Phòng</dt>
                    <dd>{sub.quotaRoom} × {formatVnd(sub.pricePerRoom)}</dd>
                  </div>
                </dl>
                <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-blue-700 pt-4 text-sm">
                  {sub.trial ? (
                    <div className="col-span-2">
                      <dt className="text-xs text-blue-200">Hết hạn dùng thử</dt>
                      <dd className="font-medium">{formatDate(sub.trialEndsAt)}</dd>
                    </div>
                  ) : (
                    <>
                      <div>
                        <dt className="text-xs text-blue-200">Bắt đầu chu kỳ</dt>
                        <dd className="font-medium">{formatDate(sub.currentPeriodStart)}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-blue-200">Kỳ thanh toán tới</dt>
                        <dd className="font-medium">{formatDate(sub.nextBillingDate)}</dd>
                      </div>
                    </>
                  )}
                </dl>
              </>
            ) : (
              <p className="mt-3 text-sm text-blue-200">Tenant chưa có gói dịch vụ.</p>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
