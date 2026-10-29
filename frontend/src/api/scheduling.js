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
 * Danh mục mẫu ca (nhỏ, lấy trọn một lần). `includeInactive = true` để Giám đốc quản trị danh mục,
 * hoặc để tra tên mẫu của ca cũ; ô chọn khi xếp ca chỉ được hiện mẫu đang dùng.
 *
 * Bộ mẫu chung (`locationId` null) + bộ mẫu riêng từng chi nhánh; mỗi chi nhánh dùng đúng một bộ
 * (`location.ownShiftTemplates`). Manager luôn nhận bộ chi nhánh mình đang dùng (backend tự giới hạn);
 * Giám đốc nhận tất cả, hoặc truyền `locationId` để chỉ lấy bộ chi nhánh đó đang dùng. Nhân viên nhận
 * mẫu đang dùng của bộ chi nhánh mình — làm hàng ca cho lịch cá nhân / lịch dọn.
 */
export async function fetchShiftTemplates({ includeInactive = false, locationId } = {}) {
  const { data } = await api.get('/scheduling/shift-templates', {
    params: { includeInactive, locationId, page: 0, size: 200, sort: 'startTime,asc' },
  });
  return data.content ?? [];
}

/**
 * Một mẫu theo id — để hiện đúng tên mẫu của ca cũ khi mẫu đó không còn trong bộ chi nhánh đang dùng
 * (chi nhánh đã đổi bộ). Manager đọc được mẫu chung và mẫu riêng của chi nhánh mình.
 */
export async function fetchShiftTemplate(id) {
  const { data } = await api.get(`/scheduling/shift-templates/${id}`);
  return data;
}

/**
 * `locationId` null = thêm vào bộ mẫu chung; có giá trị = thêm vào bộ mẫu riêng của chi nhánh đó (không
 * đổi được về sau). Thêm vào bộ riêng KHÔNG tự bật bộ riêng cho chi nhánh.
 */
export async function createShiftTemplate(payload) {
  const { data } = await api.post('/scheduling/shift-templates', payload);
  return data;
}

/** Sửa giờ mẫu KHÔNG đổi các ca đã xếp từ mẫu này trước đó. */
export async function updateShiftTemplate(id, payload) {
  const { data } = await api.put(`/scheduling/shift-templates/${id}`, payload);
  return data;
}

/** BR-SCH-22: ngừng dùng mẫu thì tắt, không xóa. */
export async function setShiftTemplateActive(id, active) {
  const { data } = await api.patch(`/scheduling/shift-templates/${id}/active`, { active });
  return data;
}

/**
 * Xóa hẳn mẫu — backend chỉ cho với mẫu đã tắt và chưa ca nào dùng (`usedShiftCount` = 0, chốt
 * 09/10/2026); mẫu đã dùng thì giữ ở trạng thái tắt để ca cũ còn tra được tên mẫu.
 */
export async function deleteShiftTemplate(id) {
  await api.delete(`/scheduling/shift-templates/${id}`);
}

/**
 * Chi nhánh dùng bộ mẫu chung (`own = false`) hay bộ mẫu riêng của nó (`own = true`) — chỉ Giám đốc.
 * Bộ riêng chưa có mẫu nào đang dùng thì backend chặn. Trả { locationId, ownShiftTemplates, ownActiveTemplates }.
 */
export async function setLocationOwnTemplates(locationId, own) {
  const { data } = await api.patch(`/scheduling/shift-templates/locations/${locationId}/own-templates`, {
    ownShiftTemplates: own,
  });
  return data;
}

/** Sao chép các mẫu chung đang dùng sang bộ riêng của chi nhánh (bỏ qua tên đã có). Trả các mẫu vừa tạo. */
export async function copyCommonTemplates(locationId) {
  const { data } = await api.post(`/scheduling/shift-templates/locations/${locationId}/copy-common`);
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

/**
 * POST /scheduling/shifts/{id}/check-in — nhân viên vào ca của CHÍNH mình (chỉ ghi giờ — BR-DASH-01).
 * Không gửi body: trường xác thực có mặt của chấm công M2 (BR-ATT-03) để trống. Khung giờ được vào ca
 * do backend kiểm; câu lỗi hiện nguyên văn. Thêm cho màn "Việc của tôi" — tự nhận việc dọn đòi đã vào ca.
 */
export async function checkInShift(id) {
  const { data } = await api.post(`/scheduling/shifts/${id}/check-in`);
  return data;
}

/** POST /scheduling/shifts/{id}/check-out — kết thúc ca của chính mình; phải vào ca trước. */
export async function checkOutShift(id) {
  const { data } = await api.post(`/scheduling/shifts/${id}/check-out`);
  return data;
}

// ── Đổi ca giữa hai nhân viên — BR-SCH-08..12, 18, 19, 25 (Milestone 2) ──────────

/**
 * Ca của đồng nghiệp đổi được với ca `shiftId` của mình: cùng khách sạn, cùng vị trí và cùng quyền
 * nghiệp vụ, chưa bắt đầu, chưa check-in, chưa nằm trong yêu cầu đang chờ nào, trong 31 ngày tới.
 * Trả `[{ staffId, fullName, shift }]`. Schedule Policy chưa kiểm ở đây — kiểm khi gửi.
 */
export async function fetchSwapCandidates(shiftId) {
  const { data } = await api.get('/scheduling/shift-swap-requests/candidates', { params: { shiftId } });
  return data;
}

/** Gửi yêu cầu: đổi ca `requesterShiftId` của mình lấy ca `peerShiftId` của đồng nghiệp. */
export async function createSwapRequest({ requesterShiftId, peerShiftId }) {
  const { data } = await api.post('/scheduling/shift-swap-requests', { requesterShiftId, peerShiftId });
  return data;
}

/**
 * `box`: `'SENT'` (tôi đã gửi), `'RECEIVED'` (gửi cho tôi) hoặc `'LOCATION'` (mọi yêu cầu của khách
 * sạn — chỉ Manager). Backend so đúng chữ hoa. `statuses` bỏ trống = mọi trạng thái. Mới nhất trước.
 */
export async function fetchSwapRequests({ box, statuses, page = 0, size = 50 }) {
  const { data } = await api.get('/scheduling/shift-swap-requests', {
    params: { box, status: statuses, page, size },
    // Spring đọc danh sách dạng status=a&status=b, không phải status[]=a.
    paramsSerializer: { indexes: null },
  });
  return data;
}

/** Đồng nghiệp đồng ý — backend kiểm lại quy định xếp ca cho cả hai người (BR-SCH-10). */
export async function acceptSwap(id) {
  const { data } = await api.patch(`/scheduling/shift-swap-requests/${id}/accept`);
  return data;
}

export async function declineSwap(id) {
  const { data } = await api.patch(`/scheduling/shift-swap-requests/${id}/decline`);
  return data;
}

/** Người gửi tự hủy khi yêu cầu còn chờ. */
export async function cancelSwap(id) {
  const { data } = await api.patch(`/scheduling/shift-swap-requests/${id}/cancel`);
  return data;
}

/** Manager duyệt: hai ca hoán đổi người, việc dọn đi theo người nhận ca (BR-SCH-18, 26). */
export async function approveSwap(id) {
  const { data } = await api.patch(`/scheduling/shift-swap-requests/${id}/approve`);
  return data;
}

export async function rejectSwap(id) {
  const { data } = await api.patch(`/scheduling/shift-swap-requests/${id}/reject`);
  return data;
}
