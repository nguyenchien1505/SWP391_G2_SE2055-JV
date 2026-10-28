import { api } from './client';

// ── Đơn xin nghỉ — BR-SCH-06..08, BR-SCH-16, BR-SCH-27..31 (BR Milestone 2) ──────────
// Người gửi (nhân viên, quản lý khách sạn) chỉ làm việc với đơn của chính mình. Người duyệt theo cấp: quản lý
// khách sạn duyệt đơn nhân viên khách sạn mình, giám đốc duyệt đơn của quản lý (BR-SCH-08).

/**
 * POST — gửi đơn `{ leaveType: 'ANNUAL' | 'UNPAID', fromDate, toDate, reason }`. Luật theo ngày (báo trước, quỹ
 * phép, trùng đơn, ngày đã qua) lỗi thì 400 kèm câu nêu rõ lý do.
 */
export async function createLeaveRequest(payload) {
  const { data } = await api.post('/scheduling/leave-requests', payload);
  return data;
}

/** GET /mine — Page đơn của chính mình, mới gửi trước. `status` bỏ trống = mọi trạng thái. */
export async function fetchMyLeaveRequests({ status, page = 0, size = 20 } = {}) {
  const { data } = await api.get('/scheduling/leave-requests/mine', {
    params: { status: status || undefined, page, size, sort: 'createdAt,desc' },
  });
  return data;
}

/**
 * GET /balance — quỹ phép năm của chính mình trong năm nay: `entitledDays` đã tích lũy, `usedDays` đã duyệt,
 * `pendingDays` đang chờ duyệt, `remainingDays`, `earliestAnnualStart` (ngày sớm nhất xin phép năm được),
 * `startWorkDate` (null = chưa tính được quỹ).
 */
export async function fetchMyLeaveBalance() {
  const { data } = await api.get('/scheduling/leave-requests/balance');
  return data;
}

/** PATCH /{id}/cancel — tự hủy đơn còn chờ duyệt (BR-SCH-16). */
export async function cancelLeaveRequest(id) {
  const { data } = await api.patch(`/scheduling/leave-requests/${id}/cancel`);
  return data;
}

/**
 * GET /review — hàng đơn của người duyệt (backend tự giới hạn phạm vi theo vai trò). Đơn chờ duyệt xếp theo
 * ngày nghỉ gần nhất trước — đơn nào sắp tới phải xử lý trước; các tab khác xếp đơn mới gửi trước.
 */
export async function fetchLeaveReviewQueue({ status, page = 0, size = 20 } = {}) {
  const { data } = await api.get('/scheduling/leave-requests/review', {
    params: {
      status: status || undefined,
      page,
      size,
      sort: status === 'PENDING' ? 'fromDate,asc' : 'createdAt,desc',
    },
  });
  return data;
}

/**
 * GET /{id}/approval-preview — hệ quả nếu duyệt: `releasedShifts` (ca sẽ gỡ), `keptShifts` (ca hôm nay / đã
 * check-in, kèm `note`), `receptionWarnings` (ca sẽ mất lễ tân duy nhất — BR-SCH-29) và `balance` (phép năm).
 */
export async function fetchLeaveApprovalPreview(id) {
  const { data } = await api.get(`/scheduling/leave-requests/${id}/approval-preview`);
  return data;
}

/** PATCH /{id}/approve — duyệt; trả cùng cấu trúc với bản xem trước, lúc này là các ca ĐÃ gỡ. */
export async function approveLeaveRequest(id) {
  const { data } = await api.patch(`/scheduling/leave-requests/${id}/approve`);
  return data;
}

export async function rejectLeaveRequest(id) {
  const { data } = await api.patch(`/scheduling/leave-requests/${id}/reject`);
  return data;
}
