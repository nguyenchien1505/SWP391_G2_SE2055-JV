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
