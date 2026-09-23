import { api } from './client';

/**
 * Danh mục Position của Tenant (kèm `departmentName` suy ra — BR-ORG-07). Giám đốc định nghĩa,
 * Manager chỉ chọn (BR-ORG-06).
 *
 * <p>`includeInactive = true` để tra tên cả Position đã ẩn (hồ sơ cũ vẫn trỏ vào); ô chọn chỉ
 * được hiện Position đang dùng (BR-ORG-14). Danh mục nhỏ nên lấy trọn một lần.
 */
export async function fetchPositions({ includeInactive = false } = {}) {
  const { data } = await api.get('/organization/positions', {
    params: { includeInactive, page: 0, size: 500, sort: 'name,asc' },
  });
  return data.content ?? [];
}
