/**
 * Nhãn trạng thái tài sản cố định (BR-ASSET-02) theo khóa UI mà assetApi trả về
 * (Good/Damaged/Repairing/Disposed), kèm class badge trong styles.css / damageReports.css.
 */
export const ASSET_STATUS = {
  Good: { label: 'Tốt', badge: 'badge--green' },
  Damaged: { label: 'Hỏng', badge: 'badge--red' },
  Repairing: { label: 'Đang sửa', badge: 'badge--orange' },
  Disposed: { label: 'Đã thanh lý', badge: 'badge--grey' },
};

export function assetStatusOf(status) {
  return ASSET_STATUS[status] ?? { label: status ?? '—', badge: 'badge--grey' };
}

/** Phiếu báo hỏng — đúng 2 trạng thái (BR-ASSET-11). */
export const TICKET_STATUS = {
  New: { label: 'Đang chờ xử lý', badge: 'badge--orange' },
  Processed: { label: 'Đã xử lý', badge: 'badge--green' },
};

/** Khớp @Size(max = 500) của CreateDamageReportRequest / ResolveDamageReportRequest. */
export const DAMAGE_TEXT_MAX = 500;
