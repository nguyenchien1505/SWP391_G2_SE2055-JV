import { api } from './client';
import { getDeviceId } from './deviceId';

// ── Đơn điều chỉnh công — chấm công M2 (chốt 01/11/2026) ─────────────────────────────────────────────
// Nhân viên gửi đơn sửa giờ vào / ra cho ca của chính mình; quản lý khách sạn duyệt thì giờ được ghi như quản lý
// nhập. Mỗi đơn: `{ id, shift, requesterName, requestedCheckInAt, requestedCheckOutAt, reason, status,
// reviewerName, decidedAt, reviewNote, createdAt }`.

/**
 * POST — `{ shiftId, checkInAt, checkOutAt, reason }`, giờ dạng "YYYY-MM-DDTHH:mm" (giờ khách sạn), giờ bỏ trống
 * là giữ giờ đang có. Giờ sai luật (ngoài khung ca, ở tương lai…) thì 400 kèm câu nêu rõ.
 */
export async function createAdjustment({ shiftId, checkInAt, checkOutAt, reason }) {
  const { data } = await api.post('/scheduling/attendance-adjustments',
    { shiftId, checkInAt: checkInAt || null, checkOutAt: checkOutAt || null, reason });
  return data;
}

/** GET /mine — Page đơn của chính mình, mới gửi trước. */
export async function fetchMyAdjustments({ page = 0, size = 20 } = {}) {
  const { data } = await api.get('/scheduling/attendance-adjustments/mine',
    { params: { page, size, sort: 'createdAt,desc' } });
  return data;
}

/** PATCH /{id}/cancel — người gửi hủy đơn còn chờ. */
export async function cancelAdjustment(id) {
  const { data } = await api.patch(`/scheduling/attendance-adjustments/${id}/cancel`);
  return data;
}

/** GET /review — đơn của khách sạn mình; `statuses` bỏ trống = đang chờ. */
export async function fetchAdjustmentReviewQueue({ statuses, page = 0, size = 20 } = {}) {
  const { data } = await api.get('/scheduling/attendance-adjustments/review', {
    params: { statuses: statuses?.join(',') || undefined, page, size, sort: 'createdAt,desc' },
  });
  return data;
}

/** PATCH /{id}/approve — ghi giờ trong đơn vào ca; kèm mã thiết bị như mọi lần ghi chấm công. */
export async function approveAdjustment(id, note) {
  const { data } = await api.patch(`/scheduling/attendance-adjustments/${id}/approve`, { note: note || null },
    { headers: { 'X-Device-Id': getDeviceId() } });
  return data;
}

/** PATCH /{id}/reject — lý do từ chối bắt buộc, nhân viên đọc được. */
export async function rejectAdjustment(id, note) {
  const { data } = await api.patch(`/scheduling/attendance-adjustments/${id}/reject`, { note });
  return data;
}
