import { api } from './client';

/** Gói đang dùng, hạn dùng, mức sử dụng và bảng giá hiện hành — chỉ Giám đốc. */
export async function fetchBillingOverview() {
  const { data } = await api.get('/billing/overview');
  return data;
}

/** Lịch sử hóa đơn của Tenant, mới nhất trước. */
export async function fetchInvoices() {
  const { data } = await api.get('/billing/invoices');
  return data;
}

/** Tenant mới chọn Dùng thử — trả { message, activated } (activated = true khi đã bắt đầu dùng thử). */
export async function startTrial() {
  const { data } = await api.post('/billing/start-trial');
  return data;
}

/** Mua gói lần đầu — trả { paymentUrl } để chuyển sang VNPay, hoặc { message, activated }. */
export async function purchasePlan({ quotaLocation, quotaUser, quotaRoom }) {
  const { data } = await api.post('/billing/purchase', { quotaLocation, quotaUser, quotaRoom });
  return data;
}

/** Mua thêm quota giữa kỳ — số lượng MỚI (không phải phần tăng). Trả { paymentUrl } hoặc { message, activated }. */
export async function upgradePlan({ quotaLocation, quotaUser, quotaRoom }) {
  const { data } = await api.post('/billing/upgrade', { quotaLocation, quotaUser, quotaRoom });
  return data;
}

/** Gia hạn 1 kỳ 30 ngày theo quota hiện tại — trả { paymentUrl } hoặc { message, activated }. */
export async function renewPlan() {
  const { data } = await api.post('/billing/renew');
  return data;
}

/**
 * Gửi nguyên chuỗi tham số VNPay gắn vào trang kết quả (`?vnp_...`) lên backend để kiểm tra chữ
 * ký và cập nhật hóa đơn. Trả { status, message, quotaLocation, quotaUser, quotaRoom, ... }.
 */
export async function confirmVnpayReturn(search) {
  const { data } = await api.get(`/billing/vnpay-return${search}`);
  return data;
}
