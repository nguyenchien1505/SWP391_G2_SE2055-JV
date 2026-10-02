import { assetStatusOf } from './damageReportLabels';
import './damageReports.css';

/**
 * Danh sách tài sản cố định, mỗi tài sản một dòng — dùng chung cho tab "Tài sản" của chi tiết
 * phòng và khối "Tài sản khu vực" của nhân viên.
 *
 * @param onReport có giá trị thì hiện nút "Báo hỏng" (chỉ nhân viên Lễ tân/Dọn dẹp —
 *                 BR-ASSET-05); để trống là danh sách chỉ xem (Giám đốc/Manager)
 */
export default function AssetReportList({ assets, onReport, emptyText = 'Chưa có tài sản cố định nào.' }) {
  if (assets.length === 0) {
    return (
      <div className="state state--empty">
        <p>{emptyText}</p>
      </div>
    );
  }

  return (
    <ul className="asset-report-list">
      {assets.map((asset) => {
        const status = assetStatusOf(asset.status);
        return (
          <li key={asset.id} className="asset-report-row">
            <div className="asset-report-row__info">
              <span className="asset-report-row__name">{asset.name}</span>
              <span className="asset-report-row__meta">
                <span>{asset.code}</span>
                <span className={`badge ${status.badge}`}>{status.label}</span>
              </span>
            </div>
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
