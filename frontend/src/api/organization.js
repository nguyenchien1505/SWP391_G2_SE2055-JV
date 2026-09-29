import { api } from './client';

/**
 * Danh mục cấp Tenant do Giám đốc định nghĩa, dùng chung mọi khách sạn trong chuỗi — BR-ORG-06,
 * BR-ORG-11. Manager chỉ đọc để chọn (ví dụ chọn Vị trí khi tạo nhân viên); mọi thao tác ghi chỉ
 * Giám đốc (backend trả 403 với vai trò khác). Mỗi danh mục nhỏ nên lấy trọn một lần.
 *
 * <p>Không xóa được mục đang được dùng (BR-ORG-10) — backend trả lỗi nêu rõ lý do; muốn ngừng
 * dùng thì ẩn (BR-ORG-14).
 */

// ── Vị trí công việc (Position) ─────────────────────────────────────────────

/**
 * Danh mục Position của Tenant (kèm `departmentName`, `departmentActive` — BR-ORG-07).
 *
 * <p>`includeInactive = true` để tra tên cả Position đã ẩn (hồ sơ cũ vẫn trỏ vào). Mặc định
 * (`false`) là danh sách CHỌN: bỏ cả Position đã ẩn lẫn Position thuộc phòng ban đã ẩn (BR-ORG-14).
 */
export async function fetchPositions({ includeInactive = false } = {}) {
  const { data } = await api.get('/organization/positions', {
    params: { includeInactive, page: 0, size: 500, sort: 'name,asc' },
  });
  return data.content ?? [];
}

/** body: { departmentId, name, positionType } — phòng ban không đổi được sau khi tạo (BR-ORG-07). */
export async function createPosition(body) {
  const { data } = await api.post('/organization/positions', body);
  return data;
}

/** body: { name, positionType } */
export async function updatePosition(id, body) {
  const { data } = await api.put(`/organization/positions/${id}`, body);
  return data;
}

export async function setPositionActive(id, active) {
  const { data } = await api.patch(`/organization/positions/${id}/active`, { active });
  return data;
}

export async function deletePosition(id) {
  await api.delete(`/organization/positions/${id}`);
}

// ── Phòng ban (Department) ──────────────────────────────────────────────────

export async function fetchDepartments({ includeInactive = false } = {}) {
  const { data } = await api.get('/organization/departments', {
    params: { includeInactive, page: 0, size: 500, sort: 'name,asc' },
  });
  return data.content ?? [];
}

export async function createDepartment(name) {
  const { data } = await api.post('/organization/departments', { name });
  return data;
}

export async function updateDepartment(id, name) {
  const { data } = await api.put(`/organization/departments/${id}`, { name });
  return data;
}

export async function setDepartmentActive(id, active) {
  const { data } = await api.patch(`/organization/departments/${id}/active`, { active });
  return data;
}

export async function deleteDepartment(id) {
  await api.delete(`/organization/departments/${id}`);
}

// ── Loại phòng (RoomType) — xem thêm `fetchRoomTypes` ở api/rooms.js ────────

export async function createRoomType(name) {
  const { data } = await api.post('/organization/room-types', { name });
  return data;
}

export async function updateRoomType(id, name) {
  const { data } = await api.put(`/organization/room-types/${id}`, { name });
  return data;
}

export async function setRoomTypeActive(id, active) {
  const { data } = await api.patch(`/organization/room-types/${id}/active`, { active });
  return data;
}

export async function deleteRoomType(id) {
  await api.delete(`/organization/room-types/${id}`);
}

// ── Đang dùng — thứ chặn việc xóa một mục (chỉ Giám đốc) ───────────────────

/** { rooms: [{ id, roomNumber, floor, locationName, status, active }] } — kể cả phòng đã xóa. */
export async function fetchRoomTypeUsage(id) {
  const { data } = await api.get(`/organization/room-types/${id}/usage`);
  return data;
}

/** { positions: [{ id, name, positionType, active, workingStaff, terminatedStaff }] } */
export async function fetchDepartmentUsage(id) {
  const { data } = await api.get(`/organization/departments/${id}/usage`);
  return data;
}

/** { staff: [{ id, fullName, email, locationName, status, startWorkDate }] } — kể cả người đã nghỉ việc. */
export async function fetchPositionUsage(id) {
  const { data } = await api.get(`/organization/positions/${id}/usage`);
  return data;
}
