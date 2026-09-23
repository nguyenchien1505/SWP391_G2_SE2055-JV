import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import {
  fetchTenant,
  fetchTenantUsage,
  reactivateTenant,
  suspendTenant,
} from '../../api/tenants';
import ConfirmDialog from '../../components/ConfirmDialog';
import TenantStatusBadge, { SUSPEND_REASON_LABEL } from '../../components/TenantStatusBadge';
import { formatDate, formatDateTime, formatVnd, initials, shortId } from './format';
import './admin.css';

/** Một chỉ số sử dụng so với hạn mức. `overQuota` khi đang vượt hạn mức (BR-SAAS-02, 03). */
function Meter({ label, icon, metric }) {
  const percent = Math.min(metric.usedPercent ?? 0, 100);
  return (
    <div className={`meter ${metric.overQuota ? 'meter--over' : ''}`}>
      <div className="meter__label">
        <span>{label}</span>
        <span aria-hidden="true">{icon}</span>
      </div>
      <p className="meter__value">
        {metric.used} <small>/ {metric.quota ?? '—'}</small>
      </p>
      <div
        className="meter__bar"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
      >
        <span className="meter__fill" style={{ width: `${percent}%` }} />
      </div>
      <p className="meter__note">
        {metric.overQuota
          ? `Vượt hạn mức ${Math.abs(metric.remaining ?? 0)} đơn vị`
          : metric.usedPercent != null
            ? `Đạt ${metric.usedPercent}% hạn mức`
            : 'Chưa có hạn mức'}
      </p>
    </div>
  );
}

/**
 * Chi tiết một Tenant — Admin Platform (BR-PERM-01): thông tin, mức sử dụng so với quota, gói
 * dịch vụ, và khóa / mở lại Tenant.
 *
 * <p>Nút mở lại chỉ dùng được khi Tenant bị khóa do ADMIN_LOCKED (BR-SAAS-12). Tenant bị khóa do
 * hết hạn dùng thử hoặc thanh toán thất bại chỉ thoát khi thanh toán thành công, Admin không có
 * quyền này — backend cũng chặn (400) nên giao diện vô hiệu hóa nút để khỏi gửi yêu cầu vô ích.
 */
export default function TenantDetailPage() {
  const { id } = useParams();

  const [tenant, setTenant] = useState(null);
  const [usage, setUsage] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [banner, setBanner] = useState(null); // { type, text }
  const [pendingAction, setPendingAction] = useState(null); // 'suspend' | 'reactivate'
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoadError('');
    try {
      const [detail, usageData] = await Promise.all([fetchTenant(id), fetchTenantUsage(id)]);
      setTenant(detail);
      setUsage(usageData);
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được thông tin Tenant.'));
    }
  }, [id]);

  useEffect(() => {
    setTenant(null);
    setUsage(null);
    setBanner(null);
    load();
  }, [load]);

  async function handleConfirm() {
    const action = pendingAction;
    setPendingAction(null);
    setBusy(true);
    try {
      if (action === 'suspend') {
        await suspendTenant(id);
        setBanner({ type: 'success', text: `Đã khóa Tenant "${tenant.name}".` });
      } else {
        await reactivateTenant(id);
        setBanner({ type: 'success', text: `Đã mở khóa Tenant "${tenant.name}".` });
      }
      await load();
    } catch (err) {
      setBanner({
        type: 'error',
        text: readErrorMessage(
          err,
          action === 'suspend' ? 'Không khóa được Tenant.' : 'Không mở khóa được Tenant.',
        ),
      });
    } finally {
      setBusy(false);
    }
  }

  if (loadError) {
    return (
      <div className="page admin-page">
        <p className="breadcrumb">
          <Link to="/quan-tri/tenant">Danh sách Tenant</Link> › Chi tiết
        </p>
        <div className="alert alert--error">{loadError}</div>
      </div>
    );
  }
  if (!tenant) {
    return <p className="state">Đang tải…</p>;
  }

  const sub = tenant.subscription;
  // Phí mỗi kỳ tính theo QUOTA đã mua, không theo số đang dùng (BR-SAAS-05).
  const estimate = sub
    ? sub.quotaLocation * sub.pricePerLocation +
      sub.quotaUser * sub.pricePerUser +
      sub.quotaRoom * sub.pricePerRoom
    : null;

  const isSuspended = tenant.status === 'SUSPENDED';
  // Khớp backend: mở lại được khi ADMIN_LOCKED, hoặc khi thiếu lý do (dữ liệu lệch).
  const canReactivate =
    isSuspended && (tenant.suspendReason === 'ADMIN_LOCKED' || tenant.suspendReason == null);

  return (
    <div className="page admin-page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">
            <Link to="/quan-tri/tenant">Danh sách Tenant</Link> › Chi tiết Tenant
          </p>
          <div className="head-title">
            <span className="avatar" aria-hidden="true">
              {initials(tenant.name)}
            </span>
            <h1>{tenant.name}</h1>
            <TenantStatusBadge status={tenant.status} />
          </div>
          <p className="muted">
            Mã định danh: TNT-{shortId(tenant.id)} · Ngày đăng ký: {formatDate(tenant.createdAt)}
          </p>
        </div>

        {isSuspended ? (
          <button
            type="button"
            className="btn btn--primary"
            disabled={busy || !canReactivate}
            onClick={() => setPendingAction('reactivate')}
          >
            Mở khóa Tenant
          </button>
        ) : (
          <button
            type="button"
            className="btn btn--danger"
            disabled={busy}
            onClick={() => setPendingAction('suspend')}
          >
            Khóa Tenant
          </button>
        )}
      </div>

      {banner && (
        <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="alert">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)}>
            ✕
          </button>
        </div>
      )}

      {isSuspended && (
        <div className="alert alert--warn" role="status">
          Tenant đang bị khóa do{' '}
          <b>
            {tenant.suspendReason
              ? (SUSPEND_REASON_LABEL[tenant.suspendReason] ?? tenant.suspendReason)
              : '—'}
          </b>
          {tenant.suspendedAt && <> từ {formatDateTime(tenant.suspendedAt)}</>}.
          {!canReactivate &&
            ' Chỉ được kích hoạt lại khi thanh toán thành công, Admin không có quyền mở khóa.'}
        </div>
      )}

      <div className="split">
        <div className="stack">
          <section className="panel">
            <div className="panel__head">
              <h2>Thông tin pháp nhân &amp; đại diện</h2>
            </div>
            <dl className="kv">
              <div>
                <dt>Tên tổ chức</dt>
                <dd>{tenant.name}</dd>
              </div>
              <div>
                <dt>Email liên hệ (đăng nhập Giám đốc)</dt>
                <dd>{tenant.contactEmail}</dd>
              </div>
              <div>
                <dt>Số điện thoại</dt>
                <dd>{tenant.contactPhone}</dd>
              </div>
              <div>
                <dt>Cập nhật lần cuối</dt>
                <dd>{formatDateTime(tenant.updatedAt)}</dd>
              </div>
              <div>
                <dt>Khóa lần gần nhất</dt>
                <dd>{formatDateTime(tenant.suspendedAt)}</dd>
              </div>
              <div>
                <dt>Mở khóa lần gần nhất</dt>
                <dd>{formatDateTime(tenant.reactivatedAt)}</dd>
              </div>
            </dl>
          </section>

          <section className="panel">
            <div className="panel__head">
              <h2>Giám sát tài nguyên &amp; hạn mức</h2>
            </div>
            {usage ? (
              <>
                <div className="meter-grid">
                  <Meter label="Cơ sở / Location" icon="🏢" metric={usage.locations} />
                  <Meter label="Phòng nghỉ" icon="🛏" metric={usage.rooms} />
                  <Meter label="Tài khoản nhân viên" icon="👥" metric={usage.staff} />
                </div>
              
              </>
            ) : (
              <p className="state">Đang tải…</p>
            )}
          </section>
        </div>

        <aside className="stack">
          <section className="plan">
            <p className="plan__label">{sub?.trial ? 'Gói dùng thử hiện tại' : 'Gói thuê bao hiện tại'}</p>
            {sub ? (
              <>
                <p className="plan__price">{formatVnd(estimate)}</p>
                <p className="plan__note">tạm tính / chu kỳ 30 ngày</p>
                <dl className="plan__rows">
                  <div>
                    <dt>Location</dt>
                    <dd>
                      {sub.quotaLocation} × {formatVnd(sub.pricePerLocation)}
                    </dd>
                  </div>
                  <div>
                    <dt>Staff</dt>
                    <dd>
                      {sub.quotaUser} × {formatVnd(sub.pricePerUser)}
                    </dd>
                  </div>
                  <div>
                    <dt>Phòng</dt>
                    <dd>
                      {sub.quotaRoom} × {formatVnd(sub.pricePerRoom)}
                    </dd>
                  </div>
                  {sub.trial ? (
                    <div>
                      <dt>Hết hạn dùng thử</dt>
                      <dd>{formatDate(sub.trialEndsAt)}</dd>
                    </div>
                  ) : (
                    <>
                      <div>
                        <dt>Bắt đầu chu kỳ</dt>
                        <dd>{formatDate(sub.currentPeriodStart)}</dd>
                      </div>
                      <div>
                        <dt>Kỳ thanh toán tới</dt>
                        <dd>{formatDate(sub.nextBillingDate)}</dd>
                      </div>
                    </>
                  )}
                </dl>
              </>
            ) : (
              <p className="plan__note">Tenant chưa có gói dịch vụ.</p>
            )}
          </section>
        </aside>
      </div>

      {pendingAction && (
        <ConfirmDialog
          title={pendingAction === 'suspend' ? 'Khóa Tenant?' : 'Mở khóa Tenant?'}
          message={
            pendingAction === 'suspend' ? (
              <>
                Bạn sắp khóa <b>{tenant.name}</b>. Toàn bộ người dùng của Tenant sẽ bị chặn đăng
                nhập và phiên đang mở bị ngắt ngay.
              </>
            ) : (
              <>
                Bạn sắp mở khóa <b>{tenant.name}</b>. Tenant đang dùng thử sẽ quay về trạng thái{' '}
                <b>Dùng thử</b>, Tenant đã trả phí về <b>Đang hoạt động</b>.
              </>
            )
          }
          confirmLabel={pendingAction === 'suspend' ? 'Khóa Tenant' : 'Mở khóa'}
          onCancel={() => setPendingAction(null)}
          onConfirm={handleConfirm}
        />
      )}
    </div>
  );
}
