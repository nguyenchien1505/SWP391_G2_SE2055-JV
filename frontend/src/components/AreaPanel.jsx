import { formatDateTime } from '../pages/rooms/format';
import AssetReportList from './damage-reports/AssetReportList';
import { TICKET_STATUS } from './damage-reports/damageReportLabels';
import ViewReportButton, { groupReportsBy } from './damage-reports/ViewReportButton';

/** Đếm tài sản theo tình trạng — chỉ 3 tình trạng vận hành, tài sản đã thanh lý không nằm trong danh sách. */
const STATUS_FACTS = [
  { status: 'Good', label: 'Tốt' },
  { status: 'Damaged', label: 'Hỏng' },
  { status: 'Repairing', label: 'Đang sửa' },
];

/**
 * Bảng chi tiết một khu vực ở màn Quản lý khu vực — cùng khung với bảng chi tiết phòng của sơ đồ
 * phòng (`RoomBoardPanel`, lớp `board-panel*` trong rooms.css): cột bên phải trên màn rộng, ngăn
 * kéo / trượt từ dưới trên màn hẹp. Trang cha quyết định cách bọc; component này chỉ vẽ nội dung.
 *
 * Đầu bảng có nút Thêm tài sản / Kiểm kê (Manager); thân bảng: thông tin khu vực → báo hỏng (chỉ
 * hiện khi khu vực có báo hỏng) → tài sản trong khu vực. Sửa tên / xóa khu vực nằm ở cột thao tác
 * của danh sách.
 *
 * @param assets  tài sản cố định đang vận hành của khu vực; `null` = đang tải
 * @param reports báo hỏng của tài sản trong khu vực, phiếu đang chờ trước; `null` = đang tải
 * @param onOpenReport bấm một phiếu → hộp thoại xử lý báo hỏng (chỉ Manager)
 */
export default function AreaPanel({
  area,
  assets,
  canManage = false,
  autoFocusClose = false,
  onClose,
  onAddAsset,
  onAudit,
  reports = null,
  onOpenReport,
}) {
  const count = assets?.length ?? 0;
  const pending = (reports ?? []).filter((report) => report.ticketStatus === 'New').length;

  // Nút "Xem báo hỏng" ở cột cuối dòng tài sản — chỉ Manager, chỉ tài sản còn báo hỏng đang chờ.
  const reportsByAsset = groupReportsBy(reports, 'assetId');
  const viewReportButton = (asset) =>
    canManage ? <ViewReportButton reports={reportsByAsset[asset.id]} onOpen={onOpenReport} /> : null;

  return (
    <aside className="board-panel area-panel" aria-labelledby="area-panel-title">
      <header className="board-panel__head room-tone--area">
        <span className="board-panel__tile" aria-hidden="true">
          <span className="material-symbols-outlined">apartment</span>
        </span>
        <div className="board-panel__title">
          <h2 id="area-panel-title">{area.name}</h2>
          <p className="muted">{assets === null ? 'Đang tải tài sản…' : `${count} tài sản cố định`}</p>
        </div>
        {canManage && (
          <div className="board-panel__head-actions">
            <button type="button" className="btn btn--primary" onClick={() => onAddAsset(area)}>
              <span className="material-symbols-outlined" aria-hidden="true">add_box</span>
              Thêm tài sản
            </button>
            <button type="button" className="btn btn--ghost" onClick={() => onAudit(area)}>
              <span className="material-symbols-outlined" aria-hidden="true">fact_check</span>
              Kiểm kê
            </button>
          </div>
        )}
        <button
          type="button"
          className="board-panel__close"
          onClick={onClose}
          aria-label="Đóng bảng chi tiết"
          autoFocus={autoFocusClose}
        >
          <span className="material-symbols-outlined" aria-hidden="true">close</span>
        </button>
      </header>

      <div className="board-panel__body">
        <section className="board-panel__section">
          <h3 className="board-panel__heading">Thông tin khu vực</h3>
          <dl className="board-panel__facts">
            <div>
              <dt>Tài sản</dt>
              <dd>{assets === null ? '…' : count}</dd>
            </div>
            {STATUS_FACTS.map(({ status, label }) => (
              <div key={status}>
                <dt>{label}</dt>
                <dd>{assets === null ? '…' : assets.filter((asset) => asset.status === status).length}</dd>
              </div>
            ))}
            <div>
              <dt>Ngày tạo</dt>
              <dd>{formatDateTime(area.createdAt)}</dd>
            </div>
            <div>
              <dt>Cập nhật</dt>
              <dd>{formatDateTime(area.updatedAt)}</dd>
            </div>
          </dl>
        </section>

        {reports?.length > 0 && (
          <section className="board-panel__section">
            <h3 className="board-panel__heading">
              Báo hỏng
              {pending > 0 && <span className="area-report__pending"> · {pending} đang chờ</span>}
            </h3>
            <ul className="area-report-list">
              {reports.map((report) => {
                const ticket = TICKET_STATUS[report.ticketStatus] ?? { label: report.ticketStatusLabel, badge: 'badge--grey' };
                const body = (
                  <>
                    <span className="area-report__head">
                      <b>{report.assetName}</b>
                      <span className={`badge ${ticket.badge}`}>{ticket.label}</span>
                    </span>
                    <span className="area-report__desc">{report.description}</span>
                    <small className="area-report__meta">
                      {report.assetCode} · {report.reportedBy} · {report.reportedTime}
                    </small>
                  </>
                );
                return (
                  <li key={report.id}>
                    {canManage ? (
                      <button type="button" className="area-report" onClick={() => onOpenReport(report)}>
                        {body}
                      </button>
                    ) : (
                      <div className="area-report">{body}</div>
                    )}
                  </li>
                );
              })}
            </ul>
          </section>
        )}

        <section className="board-panel__section">
          <h3 className="board-panel__heading">Tài sản trong khu vực</h3>
          {assets === null ? (
            <p className="muted">Đang tải tài sản…</p>
          ) : (
            <AssetReportList
              assets={assets}
              rowAction={viewReportButton}
              emptyText="Khu vực này chưa có tài sản cố định nào."
            />
          )}
        </section>
      </div>
    </aside>
  );
}
