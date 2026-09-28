import { api } from './client';

/**
 * Danh mục tài sản cấp Tenant — BR-ASSET-08, BR-ASSET-09. GET mở cho Giám đốc và Manager
 * (Manager chỉ để chọn khi tạo tài sản); mọi thao tác ghi chỉ Giám đốc.
 *
 * <p>Backend chưa hỗ trợ tìm theo tên, và danh mục của một Tenant nhỏ, nên lấy trọn một lần
 * (kể cả danh mục đang ẩn — Giám đốc phải thấy thì mới bật lại được, BR-ORG-14) rồi lọc ở
 * client.
 */
export async function fetchAssetCategories() {
  const { data } = await api.get('/organization/asset-categories', {
    params: { page: 0, size: 500, sort: 'name,asc' },
  });
  return data.content ?? [];
}

/** body: { name, assetKind, purpose, unit } — `unit` bắt buộc khi CONSUMABLE, bỏ trống khi FIXED. */
export async function createAssetCategory(body) {
  const { data } = await api.post('/organization/asset-categories', body);
  return data;
}

/** body: { name, purpose, unit } — `assetKind` bất biến sau khi tạo. */
export async function updateAssetCategory(id, body) {
  const { data } = await api.put(`/organization/asset-categories/${id}`, body);
  return data;
}

/** Ẩn / hiện lại danh mục thay cho xóa — BR-ORG-14. */
export async function setAssetCategoryActive(id, value) {
  const { data } = await api.patch(`/organization/asset-categories/${id}/active`, null, {
    params: { value },
  });
  return data;
}
