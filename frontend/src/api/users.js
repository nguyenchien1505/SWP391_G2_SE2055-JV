import { api } from './client';

/**
 * Nạp nhân sự của Tenant để suy ra hai cột mà API /locations không trả:
 * "Quản lý phụ trách" và số nhân sự của từng khách sạn.
 *
 * <p>Location không lưu manager — quan hệ đi theo chiều ngược lại: User có locationId
 * (BR-ORG-02). Số Location bị chặn bởi quota gói dịch vụ nên tập nhân sự đủ nhỏ để lấy
 * một lần rồi gộp ở phía client.
 */
export async function fetchStaffDirectory({ size = 200 } = {}) {
  const { data } = await api.get('/users', { params: { page: 0, size } });
  return data.content ?? [];
}

/**
 * Danh sách tài khoản theo vai trò. Số Manager bị chặn bởi số Location (mỗi Location 1
 * Manager) nên lấy trọn một lần rồi lọc / phân trang phía client.
 */
export async function fetchUsersByRole(role, { size = 200 } = {}) {
  const { data } = await api.get('/users', {
    params: { role, page: 0, size, sort: 'createdAt,desc' },
  });
  return data.content ?? [];
}

/**
 * BR-USER-03: tạo hồ sơ + tài khoản trong 1 bước. Response có `tempPassword` — chỉ hiển
 * thị ĐÚNG MỘT LẦN (BR-USER-07), không lấy lại được.
 */
export async function createUser(payload) {
  const { data } = await api.post('/users', payload);
  return data; // { user, tempPassword }
}

/** Sửa hồ sơ; trường để null thì giữ nguyên. `enabled` dùng để khóa / mở khóa tạm. */
export async function updateUser(id, payload) {
  const { data } = await api.put(`/users/${id}`, payload);
  return data;
}

/**
 * BR-USER-04: cho nghỉ việc là xóa mềm — tài khoản chuyển TERMINATED, không hoàn tác.
 * Chỉ dùng cho Staff và Manager dự bị; Manager đang phụ trách khách sạn dùng
 * `terminateWithHandover`.
 */
export async function terminateUser(id) {
  const { data } = await api.delete(`/users/${id}`);
  return data;
}

/**
 * Cho Manager đang phụ trách khách sạn nghỉ việc, bàn giao ngay cho người khác. `handover` có
 * đúng một trong hai: `{ replacementManagerId }` (Manager dự bị) hoặc `{ newManager }` (hồ sơ
 * Manager mới — khi đó response có `tempPassword`, hiển thị đúng một lần).
 */
export async function terminateWithHandover(id, handover) {
  const { data } = await api.post(`/users/${id}/terminate`, handover);
  return data; // { user, replacement, tempPassword }
}

/** Xóa vĩnh viễn Manager đã nghỉ việc; backend từ chối nếu tài khoản đã phát sinh dữ liệu. */
export async function deleteUserPermanently(id) {
  await api.delete(`/users/${id}/permanent`);
}

/** Cấp lại mật khẩu tạm — response có `tempPassword`, hiển thị đúng một lần. */
export async function resetUserPassword(id) {
  const { data } = await api.post(`/users/${id}/reset-password`);
  return data; // { user, tempPassword }
}

/** Gom nhân sự theo locationId: { [locationId]: { manager, staffCount } }. */
export function groupStaffByLocation(users) {
  const result = {};

  for (const user of users) {
    if (!user.locationId || user.status === 'TERMINATED') {
      continue;
    }
    const entry = (result[user.locationId] ??= { manager: null, staffCount: 0 });

    if (user.role === 'MANAGER') {
      entry.manager = user;
    } else if (user.role === 'STAFF') {
      entry.staffCount += 1;
    }
  }
  return result;
}
