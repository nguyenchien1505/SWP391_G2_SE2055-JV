import { Link } from 'react-router-dom';
import { assetStatusOf } from './damageReportLabels';
import './damageReports.css';

/**
 * Danh sách tài sản cố định, mỗi tài sản một dòng — dùng chung cho tab "Tài sản" của chi tiết
 * phòng, bảng chi tiết phòng trên sơ đồ và khối "Tài sản khu vực" của nhân viên.
 *
 * @param onReport  có giá trị thì báo hỏng được (chỉ nhân viên Lễ tân/Dọn dẹp — BR-ASSET-05); để
 *                  trống là danh sách chỉ xem (Giám đốc/Manager)
 * @param reportOnRowClick bật thì CẢ DÒNG là nút báo hỏng — bảng chi tiết phòng trên sơ đồ, bấm thẳng
 *                  vào tài sản, vùng bấm to cho điện thoại. Tắt thì có nút "Báo hỏng" riêng ở cuối
 *                  dòng. Dòng bấm được không vẽ `nameAddon` / `rowAction` (không lồng nút trong nút).
 * @param nameAddon (asset) => nội dung đặt ngay cạnh tên tài sản, ví dụ nút "Xem báo hỏng"
 * @param rowAction (asset) => nội dung ở cột cuối dòng (bên phải), ví dụ nút "Xem báo hỏng"
 * @param compact   một dòng mỗi tài sản: mã tài sản đứng trước tên, trạng thái nằm cạnh tên — cho
 *                  bảng chi tiết phòng trên sơ đồ, nơi chỉ cần nhìn nhanh tình trạng
 * @param linkToDetail bấm tên tài sản → màn Chi tiết tài sản (chỉ Giám đốc/Manager mở được)
 */
export default function AssetReportList({
  assets,
  onReport,
  reportOnRowClick = false,
  nameAddon,
  rowAction,
  compact = false,
  linkToDetail = false,
  emptyText = 'Chưa có tài sản cố định nào.',
}) {
  if (assets.length === 0) {
    return (
      <div className="state state--empty">
        <p>{emptyText}</p>
      </div>
    );
  }

  /** Tên + trạng thái (+ mã khi không compact). Toàn `span` để đặt được bên trong một nút. */
  const renderInfo = (asset, status, addon) => (
    <span className="asset-report-row__info">
      <span className="asset-report-row__title">
        <span className="asset-report-row__name">{asset.name}</span>
        {compact && <span className={`badge ${status.badge}`}>{status.label}</span>}
        {addon}
      </span>
      {!compact && (
        <span className="asset-report-row__meta">
          <span>{asset.code}</span>
          <span className={`badge ${status.badge}`}>{status.label}</span>
        </span>
      )}
    </span>
  );

  return (
    <ul className="asset-report-list">
      {assets.map((asset) => {
        const status = assetStatusOf(asset.status);

        if (onReport && reportOnRowClick) {
          return (
            <li key={asset.id} className="asset-report-row asset-report-row--clickable">
              <button type="button" className="asset-report-row__hit" onClick={() => onReport(asset)}>
                {renderInfo(asset, status)}
                <span className="asset-report-row__cta">
                  <span className="material-symbols-outlined" aria-hidden="true">report_problem</span>
                  Báo hỏng
                </span>
              </button>
            </li>
          );
        }

        return (
          <li key={asset.id} className="asset-report-row">
            {renderInfo(asset, status, nameAddon?.(asset))}
            {rowAction && <div className="asset-report-row__extra">{rowAction(asset)}</div>}
            {onReport && (
              <button
                type="button"
                className="btn btn--danger asset-report-row__action"
                onClick={() => onReport(asset)}
              >
                Báo hỏng
              </button>
            )}
          </li>
        );
      })}
    </ul>
  );
}
