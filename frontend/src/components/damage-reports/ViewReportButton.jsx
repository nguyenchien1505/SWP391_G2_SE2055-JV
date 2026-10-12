/**
 * Chia danh sách báo hỏng theo một trường (`roomId`, `areaId`, `assetId`…). Trong mỗi nhóm, phiếu
 * đang chờ lên trước; cùng nhóm thì giữ thứ tự mới nhất trước của backend — nên phiếu đầu tiên
 * luôn là phiếu nên mở.
 */
export function groupReportsBy(reports, key) {
  const grouped = {};
  for (const report of reports ?? []) {
    const id = report[key];
    if (id) (grouped[id] ??= []).push(report);
  }
  for (const list of Object.values(grouped)) {
    list.sort((a, b) => (a.ticketStatus === 'New' ? 0 : 1) - (b.ticketStatus === 'New' ? 0 : 1));
  }
  return grouped;
}

/**
 * Nút "Xem báo hỏng" ở cột cuối dòng tài sản (bảng chi tiết phòng / khu vực) — CHỈ khi tài sản còn
 * báo hỏng đang chờ xử lý; phiếu đã xử lý xong thì không hiện. Bấm mở phiếu đang chờ mới nhất (hộp
 * thoại xử lý có sẵn danh sách các phiếu khác của cùng tài sản).
 *
 * @param reports báo hỏng của MỘT tài sản, đã xếp bằng {@link groupReportsBy}
 */
export default function ViewReportButton({ reports, onOpen }) {
  const waiting = (reports ?? []).filter((report) => report.ticketStatus === 'New');
  if (waiting.length === 0) return null;
  return (
    <button
      type="button"
      className="view-report-btn is-pending"
      onClick={() => onOpen(waiting[0])}
      title={`${waiting.length} báo hỏng đang chờ xử lý`}
    >
      <span className="material-symbols-outlined" aria-hidden="true">report</span>
      Xem báo hỏng{waiting.length > 1 ? ` (${waiting.length})` : ''}
    </button>
  );
}
