import { api } from './client';

/**
 * Cấu hình cấp nền tảng do Admin Platform quản lý — đơn giá (BR-SAAS-02) và số ngày dùng thử /
 * ân hạn (BR-SAAS-08, BR-SAAS-10). Tất cả nằm dưới /platform/**.
 */

/** Lịch sử bảng giá, mới nhất trước. */
export async function fetchPricingHistory() {
  const { data } = await api.get('/platform/pricing');
  return data;
}

/** Bảng giá đang hiệu lực hôm nay; backend trả 400 nếu chưa có bảng giá nào → coi như null. */
export async function fetchCurrentPricing() {
  try {
    const { data } = await api.get('/platform/pricing/current');
    return data;
  } catch (err) {
    if (err?.response?.status === 400) {
      return null;
    }
    throw err;
  }
}

/** Bảng giá chỉ THÊM MỚI, không sửa hay xóa (gói đã bán giữ snapshot giá — BR-SAAS-05). */
export async function createPricing(payload) {
  const { data } = await api.post('/platform/pricing', payload);
  return data;
}

export async function fetchSystemConfig() {
  const { data } = await api.get('/platform/system-config');
  return data;
}

export async function updateSystemConfig(payload) {
  const { data } = await api.put('/platform/system-config', payload);
  return data;
}
