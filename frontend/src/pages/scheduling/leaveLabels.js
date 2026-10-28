// Nhãn tiếng Việt + tông màu của đơn xin nghỉ — backend chỉ trả mã enum (cùng cách với roomLabels.js).
// Tông màu là hậu tố của lớp `badge--<tone>`.

/** BR-SCH-06 bản sửa M2: hai loại nghỉ. */
export const LEAVE_TYPE = {
  ANNUAL: { label: 'Phép năm', tone: 'blue' },
  UNPAID: { label: 'Không lương', tone: 'grey' },
};

/** BR-SCH-23: bốn trạng thái của đơn. */
export const LEAVE_STATUS = {
  PENDING: { label: 'Chờ duyệt', tone: 'orange' },
  APPROVED: { label: 'Đã duyệt', tone: 'green' },
  REJECTED: { label: 'Từ chối', tone: 'red' },
  CANCELLED: { label: 'Đã hủy', tone: 'grey' },
};

/** Mã lạ (backend thêm giá trị mà FE chưa biết) vẫn hiện được, không làm vỡ màn hình. */
export const leaveTypeMeta = (type) => LEAVE_TYPE[type] ?? { label: type ?? '—', tone: 'grey' };
export const leaveStatusMeta = (status) => LEAVE_STATUS[status] ?? { label: status ?? '—', tone: 'grey' };

/** Số ngày nghỉ, tính cả hai đầu (DM-07); 0 khi chưa đủ hai ngày hoặc ngày kết thúc trước ngày bắt đầu. */
export function daysInclusive(fromIso, toIso) {
  if (!fromIso || !toIso || toIso < fromIso) return 0;
  const [fy, fm, fd] = fromIso.split('-').map(Number);
  const [ty, tm, td] = toIso.split('-').map(Number);
  return Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(fy, fm - 1, fd)) / 86_400_000) + 1;
}

/** "28/10/2026", "28/10 – 30/10/2026" hoặc "30/12/2026 – 02/01/2027". */
export function formatLeaveRange(fromIso, toIso) {
  if (!fromIso) return '—';
  const [fy, fm, fd] = fromIso.split('-');
  if (!toIso || toIso === fromIso) return `${fd}/${fm}/${fy}`;
  const [ty, tm, td] = toIso.split('-');
  return fy === ty ? `${fd}/${fm} – ${td}/${tm}/${ty}` : `${fd}/${fm}/${fy} – ${td}/${tm}/${ty}`;
}
