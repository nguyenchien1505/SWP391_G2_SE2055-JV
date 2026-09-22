import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { countTenants, fetchTenants } from '../../api/tenants';
import StatCard from '../../components/StatCard';
import TenantStatusBadge, {
  SUSPEND_REASON_LABEL,
  TENANT_STATUS_LABEL,
} from '../../components/TenantStatusBadge';
import { formatDate, initials, shortId } from './format';
import './admin.css';

const PAGE_SIZE = 10;
const STATUSES = Object.keys(TENANT_STATUS_LABEL);

/**
 * Danh sách Tenant — Admin Platform (BR-PERM-01).
 *
 * <p>Tìm kiếm, lọc trạng thái và phân trang đều làm ở BACKEND (GET /platform/tenants), khác với
 * trang khách sạn lọc phía client: số Tenant trên nền tảng có thể rất lớn nên không nạp hết.
 */
export default function TenantsPage() {
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');

  // Số Tenant theo từng trạng thái — dùng cho thẻ thống kê và ô chọn trạng thái.
  const [counts, setCounts] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      setPageData(await fetchTenants({ status, keyword, page, size: PAGE_SIZE }));
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được danh sách Tenant.'));
    } finally {
      setLoading(false);
    }
  }, [status, keyword, page]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    let cancelled = false;
    const safeCount = (s) => countTenants(s).catch(() => 0);

    Promise.all([safeCount(), ...STATUSES.map((s) => safeCount(s))]).then(([total, ...byStatus]) => {
      if (cancelled) return;
      setCounts({
        total,
        ...Object.fromEntries(STATUSES.map((s, i) => [s, byStatus[i]])),
      });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  function handleSearch(event) {
    event.preventDefault();
    setPage(0);
    setKeyword(keywordInput.trim());
  }

  function handleStatusChange(value) {
    setPage(0);
    setStatus(value);
  }

  const rows = pageData?.content ?? [];
  const total = pageData?.totalElements ?? 0;
  const from = total > 0 ? (pageData.number * pageData.size) + 1 : 0;
  const to = total > 0 ? (pageData.number * pageData.size) + rows.length : 0;

  return (
    <div className="page admin-page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Quản trị nền tảng › Danh sách Tenant</p>
          <h1>Quản lý Danh sách Tenant</h1>
          <p className="muted">
            Các tổ chức, chuỗi khách sạn đang vận hành trên nền tảng SaaS đa Tenant.
          </p>
        </div>
      </div>

      <div className="stat-grid">
        <StatCard
          label="Tổng số Tenant"
          value={counts?.total ?? '—'}
          note={
            counts
              ? `${counts.ACTIVE} đang hoạt động · ${counts.TRIAL} dùng thử · ${counts.SUSPENDED} đã khóa`
              : undefined
          }
          icon="🏢"
          tone="blue"
        />
        <StatCard
          label="Cảnh báo thuê bao"
          value={counts?.PAYMENT_OVERDUE ?? '—'}
          note={`Quá hạn thanh toán · ${counts?.SUSPENDED ?? 0} Tenant đã bị khóa`}
          icon="⚠"
          tone="amber"
        />
      </div>

      <section className="panel">
        <form className="toolbar" onSubmit={handleSearch}>
          <input
            className="toolbar__search"
            type="search"
            placeholder="Tìm theo tên Tenant hoặc email liên hệ…"
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
          />
          <select value={status} onChange={(e) => handleStatusChange(e.target.value)}>
            <option value="">
              Trạng thái: Tất cả{counts ? ` (${counts.total})` : ''}
            </option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {TENANT_STATUS_LABEL[s]}
                {counts ? ` (${counts[s]})` : ''}
              </option>
            ))}
          </select>
          <button type="submit" className="btn btn--primary">
            Tìm kiếm
          </button>
          <button type="button" className="btn btn--ghost" onClick={load} disabled={loading}>
            Tải lại
          </button>
        </form>

        <div className="panel__head">
          <h2>
            <span className="dot dot--online" aria-hidden="true" /> Tenant đang quản trị
          </h2>
          <span className="chip">
            {total > 0 ? `Hiển thị ${from} – ${to} / ${total}` : 'Không có kết quả'}
          </span>
        </div>

        {loading && <p className="state">Đang tải dữ liệu…</p>}
        {loadError && <div className="alert alert--error">{loadError}</div>}

        {!loading && !loadError && rows.length === 0 && (
          <div className="state state--empty">
            <p>Không có Tenant nào khớp điều kiện.</p>
          </div>
        )}

        {!loading && rows.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Mã &amp; tên Tenant</th>
                  <th>Liên hệ</th>
                  <th>Ngày đăng ký</th>
                  <th>Trạng thái</th>
                  <th aria-label="Thao tác" />
                </tr>
              </thead>
              <tbody>
                {rows.map((tenant) => (
                  <tr key={tenant.id}>
                    <td>
                      <div className="cell-hotel">
                        <span className="avatar" aria-hidden="true">
                          {initials(tenant.name)}
                        </span>
                        <div>
                          <b>{tenant.name}</b>
                          <small>TNT-{shortId(tenant.id)}</small>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="cell-manager">
                        <span>✉ {tenant.contactEmail}</span>
                        <small>☎ {tenant.contactPhone}</small>
                      </div>
                    </td>
                    <td>{formatDate(tenant.createdAt)}</td>
                    <td>
                      <div className="cell-manager">
                        <TenantStatusBadge status={tenant.status} />
                        {tenant.suspendReason && (
                          <small>
                            {SUSPEND_REASON_LABEL[tenant.suspendReason] ?? tenant.suspendReason}
                          </small>
                        )}
                      </div>
                    </td>
                    <td className="cell-actions">
                      <Link
                        className="btn btn--ghost btn--sm"
                        to={`/quan-tri/tenant/${tenant.id}`}
                      >
                        Chi tiết
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="panel__foot">
          <span className="muted">Tổng cộng {total} Tenant trên nền tảng</span>
          <div className="pager">
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              disabled={(pageData?.number ?? 0) === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ‹
            </button>
            <span>
              Trang {(pageData?.number ?? 0) + 1} / {Math.max(1, pageData?.totalPages ?? 1)}
            </span>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              disabled={(pageData?.number ?? 0) + 1 >= (pageData?.totalPages ?? 1)}
              onClick={() => setPage((p) => p + 1)}
            >
              ›
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}
