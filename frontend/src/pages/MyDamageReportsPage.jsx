import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import AssetReportList from '../components/damage-reports/AssetReportList';
import DamageReportDialog from '../components/damage-reports/DamageReportDialog';
import { TICKET_STATUS } from '../components/damage-reports/damageReportLabels';
import { assetService } from '../services/assetApi';
import '../components/damage-reports/damageReports.css';
import './rooms/rooms.css';

const PAGE_SIZE = 10;

/** `status` gửi lên backend: không truyền = chỉ NEW, rỗng = tất cả (DM-16). */
const TABS = [
  { key: 'NEW', label: 'Đang chờ' },
  { key: 'RESOLVED', label: 'Đã xử lý' },
  { key: '', label: 'Tất cả' },
];

/**
 * Báo hỏng của tôi — nhân viên Lễ tân / Dọn dẹp (BR-ASSET-05).
 *
 * Hai phần:
 *   - **Tài sản khu vực** — báo hỏng tài sản gắn Khu vực (Sảnh, Hành lang, Kho…), vì những tài
 *     sản này không có trang chi tiết phòng. Tài sản trong phòng báo ở tab "Tài sản trong phòng"
 *     của chi tiết phòng.
 *   - **Phiếu đã gửi** — theo dõi quản lý đã xử lý chưa và xử lý thế nào. Backend chỉ trả phiếu
 *     do chính người đang đăng nhập gửi.
 *
 * Mobile-first như "Việc của tôi": không có bảng, mỗi dòng một nút to.
 */
export default function MyDamageReportsPage() {
  const [banner, setBanner] = useState(null); // { type, text }
  const [reporting, setReporting] = useState(null);
  // Tăng sau mỗi lần gửi → danh sách phiếu nạp lại từ đầu.
  const [reportsVersion, setReportsVersion] = useState(0);

  function handleSubmitted(report) {
    setReporting(null);
    setBanner({ type: 'success', text: `Đã gửi báo hỏng ${report.assetName} (${report.assetCode}) cho quản lý.` });
    setReportsVersion((v) => v + 1);
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Cá nhân › Báo hỏng tài sản</p>
          <h1>Báo hỏng của tôi</h1>
        </div>
      </div>

      {banner && (
        <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="status">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">
            ×
          </button>
        </div>
      )}

      <AreaAssetsSection onReport={(asset) => { setBanner(null); setReporting(asset); }} />

      <MyReportsSection key={reportsVersion} />

      {reporting && (
        <DamageReportDialog asset={reporting} onClose={() => setReporting(null)} onSubmitted={handleSubmitted} />
      )}
    </div>
  );
}

/** Chọn khu vực → danh sách tài sản của khu vực đó, mỗi tài sản có nút "Báo hỏng". */
function AreaAssetsSection({ onReport }) {
  const [areas, setAreas] = useState([]);
  const [areaId, setAreaId] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  useEffect(() => {
    let cancelled = false;
    assetService
      .getReportableAreas()
      .then((list) => {
        if (cancelled) return;
        setAreas(list);
        setAreaId(list[0]?.id ?? '');
      })
      .catch((err) => !cancelled && setLoadError(err?.message || 'Không tải được danh sách khu vực.'))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  const area = areas.find((a) => a.id === areaId);

  return (
    <section className="panel">
      <div className="panel__head">
        <h2>Tài sản khu vực</h2>
      </div>
      <p className="muted">
        Tài sản ở Sảnh, Hành lang, Kho… Tài sản trong phòng thì mở phòng trên{' '}
        <Link className="inline-link" to="/so-do-phong">Sơ đồ phòng</Link> rồi chọn tab “Tài sản trong phòng”.
      </p>

      {loadError && (
        <div className="alert alert--error" role="alert">
          {loadError}
        </div>
      )}
      {loading && <p className="state">Đang tải dữ liệu…</p>}

      {!loading && !loadError && areas.length === 0 && (
        <div className="state state--empty">
          <p>Khách sạn chưa có tài sản nào gắn ở khu vực.</p>
        </div>
      )}

      {areas.length > 0 && (
        <>
          <label className="field area-picker">
            <span className="field__label">Khu vực</span>
            <select value={areaId} onChange={(e) => setAreaId(e.target.value)}>
              {areas.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name} ({a.assets.length})
                </option>
              ))}
            </select>
          </label>
          {area && <AssetReportList assets={area.assets} onReport={onReport} />}
        </>
      )}
    </section>
  );
}

/** Phiếu do chính mình gửi, theo tab trạng thái; "Xem thêm" để nạp trang kế tiếp. */
function MyReportsSection() {
  const [status, setStatus] = useState('NEW');
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const load = useCallback(async (nextPage) => {
    setLoading(true);
    setLoadError('');
    try {
      const res = await assetService.getDamageReports({ status, page: nextPage, limit: PAGE_SIZE });
      setItems((prev) => (nextPage === 1 ? res.items : [...prev, ...res.items]));
      setPage(nextPage);
      setTotalPages(res.totalPages);
      setTotal(res.total);
    } catch (err) {
      setLoadError(err?.message || 'Không tải được danh sách báo hỏng.');
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    load(1);
  }, [load]);

  return (
    <section className="panel">
      <div className="panel__head">
        <h2>Phiếu đã gửi</h2>
        {!loading && <span className="chip">{total} phiếu</span>}
      </div>

      <div className="room-tabs" role="tablist" aria-label="Lọc theo trạng thái">
        {TABS.map((item) => (
          <button
            key={item.key || 'all'}
            type="button"
            role="tab"
            aria-selected={status === item.key}
            className={`room-tab ${status === item.key ? 'is-active' : ''}`}
            onClick={() => setStatus(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {loadError && (
        <div className="alert alert--error" role="alert">
          {loadError}
        </div>
      )}

      {!loading && !loadError && items.length === 0 && (
        <div className="state state--empty">
          <p>Không có phiếu báo hỏng nào.</p>
        </div>
      )}

      <ul className="asset-report-list">
        {items.map((report) => {
          const ticket = TICKET_STATUS[report.ticketStatus];
          return (
            <li key={report.id} className="damage-ticket">
              <div className="damage-ticket__head">
                <span className="damage-ticket__title">
                  {report.assetName} · {report.room}
                </span>
                <span className={`badge ${ticket.badge}`}>{ticket.label}</span>
              </div>
              <p className="damage-ticket__desc">{report.description}</p>
              <span className="damage-ticket__meta">
                {report.assetCode} · Gửi lúc {report.reportedTime}
              </span>
              {report.ticketStatus === 'Processed' && (
                <>
                  <span className="damage-ticket__meta">
                    Xử lý bởi {report.resolvedBy || 'quản lý'} lúc {report.resolvedTime}
                  </span>
                  {report.resolutionNote && (
                    <p className="damage-ticket__resolution">{report.resolutionNote}</p>
                  )}
                </>
              )}
            </li>
          );
        })}
      </ul>

      {loading && <p className="state">Đang tải dữ liệu…</p>}
      {!loading && page < totalPages && (
        <button type="button" className="btn btn--ghost" onClick={() => load(page + 1)}>
          Xem thêm
        </button>
      )}
    </section>
  );
}
