import { api } from './client';

/** Bỏ tham số rỗng: ô lọc để trống nghĩa là "không lọc". */
function withoutEmpty(params) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
}

/**
 * GET /housekeeping/tasks — Page các việc dọn phòng.
 *
 * Phạm vi do BACKEND quyết định theo vai trò, không phải tham số: Quản lý chi nhánh thấy việc
 * trong khách sạn của mình, nhân viên dọn chỉ thấy việc CỦA CHÍNH MÌNH (BR-PERM-05). Vì vậy màn
 * "Việc của tôi" gọi đúng endpoint này, không cần `/me`.
 */
export async function fetchTasks({ status, taskType, assignedDate, page = 0, size = 50, sort } = {}) {
  const { data } = await api.get('/housekeeping/tasks', {
    params: withoutEmpty({ status, taskType, assignedDate, page, size, sort }),
  });
  return data;
}

export async function fetchTask(id) {
  const { data } = await api.get(`/housekeeping/tasks/${id}`);
  return data;
}

/**
 * GET /housekeeping/tasks/assignable-staff?date= — nhân viên dọn phòng gán được việc trong ngày
 * đó: đang làm việc, cùng khách sạn, và CÓ CA ngày đó (BR-HK-03).
 *
 * Trả về MẢNG (không phân trang). Chỉ Quản lý chi nhánh gọi được.
 */
export async function fetchAssignableStaff(date) {
  const { data } = await api.get('/housekeeping/tasks/assignable-staff', { params: { date } });
  return data;
}

/** POST — chỉ tạo tay được việc dọn hằng ngày; việc dọn sau check-out do hệ thống tự sinh. */
export async function createStayoverTask(roomId) {
  const { data } = await api.post('/housekeeping/tasks', { roomId });
  return data;
}

/**
 * PATCH .../assign — gán người. Với việc dọn sau check-out, backend chuyển phòng sang «Đang dọn»
 * trong cùng transaction, nên response đã phản ánh trạng thái mới của cả hai.
 */
export async function assignTask(id, { staffId, assignedDate }) {
  const { data } = await api.patch(`/housekeeping/tasks/${id}/assign`, { staffId, assignedDate });
  return data;
}

/** PATCH .../unassign — gỡ người; việc quay lại hàng chờ và phòng quay về «Chờ dọn». */
export async function unassignTask(id) {
  const { data } = await api.patch(`/housekeeping/tasks/${id}/unassign`);
  return data;
}

/**
 * PATCH .../complete — chỉ người được phân công bấm được (BR-PERM-05). Việc dọn sau check-out
 * chuyển sang «Chờ kiểm tra» chứ chưa phải hoàn thành; dọn hằng ngày thì xong luôn.
 */
export async function completeTask(id) {
  const { data } = await api.patch(`/housekeeping/tasks/${id}/complete`);
  return data;
}
