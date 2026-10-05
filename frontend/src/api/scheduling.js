import { api } from './client';

// ── Quy định xếp ca — BR-SCH-01, DM-18: mỗi Tenant đúng MỘT bản, sửa đè ─────────

/** GET /scheduling/policy — Giám đốc và Manager đọc được; Tenant chưa có thì backend tự sinh bản mặc định (BR-SCH-20). */
export async function fetchPolicy() {
  const { data } = await api.get('/scheduling/policy');
  return data;
}

/** PUT toàn phần — chỉ Giám đốc. Giá trị mới chỉ áp cho ca xếp từ lúc lưu trở đi. */
export async function updatePolicy(payload) {
  const { data } = await api.put('/scheduling/policy', payload);
  return data;
}

// ── Mẫu ca — BR-SCH-04, BR-SCH-22 ───────────────────────────────────────────────

/**
 * Danh mục mẫu ca của Tenant (nhỏ, lấy trọn một lần). `includeInactive = true` để Giám đốc quản trị
 * danh mục, hoặc để tra tên mẫu của ca cũ; ô chọn khi xếp ca chỉ được hiện mẫu đang dùng.
 */
export async function fetchShiftTemplates({ includeInactive = false } = {}) {
  const { data } = await api.get('/scheduling/shift-templates', {
    params: { includeInactive, page: 0, size: 200, sort: 'startTime,asc' },
  });
  return data.content ?? [];
}

export async function createShiftTemplate(payload) {
  const { data } = await api.post('/scheduling/shift-templates', payload);
  return data;
}

/** Sửa giờ mẫu KHÔNG đổi các ca đã xếp từ mẫu này trước đó. */
export async function updateShiftTemplate(id, payload) {
  const { data } = await api.put(`/scheduling/shift-templates/${id}`, payload);
  return data;
}

/** BR-SCH-22: không xóa mẫu — chỉ bật/tắt. */
export async function setShiftTemplateActive(id, active) {
  const { data } = await api.patch(`/scheduling/shift-templates/${id}/active`, { active });
  return data;
}

// ── Ca làm việc ─────────────────────────────────────────────────────────────────

/**
 * GET /scheduling/shifts?from=&to= — mọi ca có NGÀY BẮT ĐẦU trong khoảng (tính cả hai đầu); ca qua
 * đêm thuộc về ngày bắt đầu (BR-SCH-03). Phạm vi do backend quyết định theo vai trò: Manager nhận ca
 * của khách sạn mình. Một tuần của một khách sạn 2–3 sao chỉ vài trăm ca nên lấy trọn một trang.
 */
export async function fetchShifts({ from, to }) {
  const { data } = await api.get('/scheduling/shifts', {
    params: { from, to, page: 0, size: 1000, sort: ['shiftDate,asc', 'startTime,asc'] },
    // Spring đọc nhiều tham số sort dạng sort=a&sort=b, không phải sort[]=a.
    paramsSerializer: { indexes: null },
  });
  return data.content ?? [];
}

/**
 * POST /scheduling/shifts/batch — giao CÙNG MỘT ca cho một hoặc nhiều người: mỗi người một ca riêng
 * (DM-03), kiểm tra quy định từng người (BR-SCH-02). Giờ chọn ĐÚNG MỘT trong hai cách (BR-SCH-04):
 * `sourceTemplateId` hoặc `startTime` + `endTime`. `openSlots` = số chỗ trống chưa giao người mở thêm.
 *
 * Có người vi phạm thì KHÔNG lưu ca nào: 400 với `violations: [{ staffId, fullName, message }]`.
 * Trả về mảng các ca vừa tạo.
 */
export async function createShifts(payload) {
  const { data } = await api.post('/scheduling/shifts/batch', payload);
  return data;
}

/** Sửa ngày / giờ — trường nào không gửi thì giữ nguyên. Đổi người đi qua assign / unassign. */
export async function updateShift(id, payload) {
  const { data } = await api.put(`/scheduling/shifts/${id}`, payload);
  return data;
}

/** Gán người cho ca chưa phân công — chạy lại toàn bộ kiểm tra quy định xếp ca cho người đó. */
export async function assignShift(id, staffId) {
  const { data } = await api.patch(`/scheduling/shifts/${id}/assign`, { staffId });
  return data;
}

/** Gỡ người — ca quay về "chưa phân công" với lý do «Quản lý gỡ» (BR-SCH-24), không mất bản ghi. */
export async function unassignShift(id) {
  const { data } = await api.patch(`/scheduling/shifts/${id}/unassign`);
  return data;
}

export async function deleteShift(id) {
  await api.delete(`/scheduling/shifts/${id}`);
}
