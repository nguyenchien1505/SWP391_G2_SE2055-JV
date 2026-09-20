import { api } from './client';

/** GET /locations — trả về Page: { content, totalElements, totalPages, number, size }. */
export async function fetchLocations({ page = 0, size = 10, sort = 'name,asc' } = {}) {
  const { data } = await api.get('/locations', { params: { page, size, sort } });
  return data;
}

export async function createLocation(payload) {
  const { data } = await api.post('/locations', payload);
  return data;
}

/** PUT: sửa toàn bộ thông tin — chỉ Giám đốc (BR-ORG-03). */
export async function updateLocation(id, payload) {
  const { data } = await api.put(`/locations/${id}`, payload);
  return data;
}

/** PATCH: chỉ địa chỉ + số điện thoại — phần Manager được sửa (BR-ORG-03). */
export async function updateLocationContact(id, payload) {
  const { data } = await api.patch(`/locations/${id}`, payload);
  return data;
}

/** BR-ORG-05: backend chặn cứng nếu còn nhân sự / phòng / khu vực trực thuộc. */
export async function deleteLocation(id) {
  await api.delete(`/locations/${id}`);
}
