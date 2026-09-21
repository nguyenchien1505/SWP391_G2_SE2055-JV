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
