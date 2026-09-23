import { api } from './client';

/**
 * API quản lý Tenant của Admin Platform (BR-PERM-01) — tất cả nằm dưới /platform/**.
 * Backend chỉ trả 200 cho PLATFORM_ADMIN; vai trò khác nhận 403.
 */

/**
 * GET /platform/tenants — trả về Page: { content, totalElements, totalPages, number, size }.
 * `status` và `keyword` bỏ trống thì không gửi (backend coi như không lọc).
 */
export async function fetchTenants({ status, keyword, page = 0, size = 10 } = {}) {
  const { data } = await api.get('/platform/tenants', {
    params: { status: status || undefined, keyword: keyword || undefined, page, size },
  });
  return data;
}

/** Đếm Tenant theo trạng thái (không truyền = tất cả) bằng cách lấy 1 dòng và đọc totalElements. */
export async function countTenants(status) {
  const { data } = await api.get('/platform/tenants', {
    params: { status: status || undefined, page: 0, size: 1 },
  });
  return data.totalElements ?? 0;
}

/** Chi tiết Tenant kèm tóm tắt gói dịch vụ (subscription). */
export async function fetchTenant(id) {
  const { data } = await api.get(`/platform/tenants/${id}`);
  return data;
}

/** Mức sử dụng (Location, Staff, Phòng) so với quota của một Tenant. */
export async function fetchTenantUsage(id) {
  const { data } = await api.get(`/platform/tenants/${id}/usage`);
  return data;
}

/** Khóa Tenant — lý do luôn là ADMIN_LOCKED (BR-SAAS-16). */
export async function suspendTenant(id) {
  const { data } = await api.post(`/platform/tenants/${id}/suspend`);
  return data;
}

/** Mở lại Tenant — backend chỉ cho phép khi Tenant đang bị khóa do ADMIN_LOCKED (BR-SAAS-12). */
export async function reactivateTenant(id) {
  const { data } = await api.post(`/platform/tenants/${id}/reactivate`);
  return data;
}

/** Danh sách cơ sở (Location) của một Tenant — dùng cho màn chi tiết Tenant. */
export async function fetchTenantLocations(id) {
  const { data } = await api.get(`/platform/tenants/${id}/locations`, { params: { size: 100 } });
  return data;
}
