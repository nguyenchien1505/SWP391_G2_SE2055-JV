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
 * trong khách sạn của mình, nhân viên dọn chỉ thấy việc CÓ MÌNH TRONG NHÓM DỌN (BR-PERM-05). Vì vậy
 * màn "Việc của tôi" gọi đúng endpoint này, không cần `/me`.
 *
 * Mỗi việc có `assignees` = [{ staffId, fullName }] (một phòng nhiều người dọn — chốt 05/10/2026),
 * `roomTypeName` để hiện "101 - Deluxe" và `roomStatus` (trạng thái hiện tại của phòng).
 *
 * `from` + `to` (đi cùng nhau, tính cả hai đầu) lọc theo ngày làm — lịch dọn theo tuần.
 *
 * `locationWide: true` — bảng điều phối: Giám sát vận hành xem cả lịch dọn của khách sạn mình
 * (BR-PERM-07) thay vì chỉ việc có mình trong nhóm. Vai trò khác backend bỏ qua cờ này.
 */
export async function fetchTasks({ status, taskType, assignedDate, from, to, locationWide, page = 0, size = 50, sort } = {}) {
  const { data } = await api.get('/housekeeping/tasks', {
    params: withoutEmpty({ status, taskType, assignedDate, from, to, locationWide, page, size, sort }),
  });
  return data;
}

export async function fetchTask(id) {
  const { data } = await api.get(`/housekeeping/tasks/${id}`);
  return data;
}

/**
 * GET /housekeeping/tasks/assignable-staff?date= — nhân viên dọn phòng có ca ngày đó: đang làm việc,
 * cùng khách sạn, có quyền Dọn dẹp, và CÓ CA ngày đó tại khách sạn này (BR-HK-03).
 *
 * `ongoingOnly: true` — hộp phân công: chỉ người gán được NGAY. Ngày hôm qua thì là người đang làm dở ca
 * qua đêm bắt đầu hôm qua (BR-SCH-03, chốt 28/10/2026); ngày khác hôm nay / hôm qua thì rỗng.
 *
 * Trả về MẢNG (không phân trang). Chỉ Quản lý chi nhánh và Giám sát vận hành gọi được.
 */
export async function fetchAssignableStaff(date, { ongoingOnly } = {}) {
  const { data } = await api.get('/housekeeping/tasks/assignable-staff', {
    params: withoutEmpty({ date, ongoingOnly }),
  });
  return data;
}

/**
 * PATCH .../assign — giao việc cho MỘT HOẶC NHIỀU người (tất cả hoặc không ai).
 *
 * - Việc đang chờ giao: cả nhóm bắt đầu dọn, backend chuyển phòng sang «Đang dọn» trong cùng
 *   transaction — response đã phản ánh trạng thái mới.
 * - Việc đang làm: thêm những người này vào nhóm; `assignedDate` phải đúng ngày của việc đó.
 */
export async function assignTask(id, { staffIds, assignedDate }) {
  const { data } = await api.patch(`/housekeeping/tasks/${id}/assign`, { staffIds, assignedDate });
  return data;
}

/**
 * PATCH .../unassign — gỡ người khỏi nhóm dọn. Có `staffId` thì gỡ đúng người đó, bỏ trống thì gỡ
 * cả nhóm. Gỡ đến người cuối cùng thì việc quay lại hàng chờ và phòng quay về «Chờ dọn».
 */
export async function unassignTask(id, staffId) {
  const { data } = await api.patch(`/housekeeping/tasks/${id}/unassign`, null, {
    params: staffId ? { staffId } : undefined,
  });
  return data;
}

/**
 * GET /housekeeping/tasks/open — việc đang chờ nhận của khách sạn mình (BR-HK-14), kèm luôn
 * `{ canClaim, blockedReason, workDay }`: BACKEND nói người đang đăng nhập có tự nhận được không và vì
 * sao (chưa có ca, chưa vào ca, đã kết thúc ca, đang dọn dở phòng khác). Màn hình chỉ hiện ra, không tự
 * suy luật. `workDay` là ngày làm của ca đang làm — ca đêm từ hôm qua thì là hôm qua.
 *
 * Chỉ nhân viên có quyền Dọn dẹp gọi được.
 */
export async function fetchOpenTasks() {
  const { data } = await api.get('/housekeeping/tasks/open');
  return data;
}

/**
 * PATCH .../claim — nhân viên Dọn dẹp tự nhận một việc chưa phân công. Nhận = giao: backend chuyển phòng
 * sang «Đang dọn» trong cùng transaction. Người khác vừa nhận trước thì trả 400 kèm câu báo — tải lại
 * danh sách sau mọi lần bấm, thành công hay không.
 */
export async function claimTask(id) {
  const { data } = await api.patch(`/housekeeping/tasks/${id}/claim`);
  return data;
}

/**
 * PATCH .../return — người ĐÃ TỰ NHẬN trả lại việc khi chưa bấm hoàn thành; lý do bắt buộc (BR-HK-17).
 * Dọn một mình thì việc về danh sách chờ nhận và phòng về «Chờ dọn»; nhóm còn người thì chỉ rút tên mình.
 * Việc do Quản lý / Giám sát giao thì backend trả 400 kèm câu báo.
 */
export async function returnTask(id, reason) {
  const { data } = await api.patch(`/housekeeping/tasks/${id}/return`, { reason });
  return data;
}

/**
 * GET .../returns — lịch sử trả lại của một việc, mới nhất trước: `[{ staffName, reason, returnedAt,
 * taskReleased }]`. `taskReleased` = phòng về «Chờ dọn»; `false` = nhóm còn người dọn tiếp.
 */
export async function fetchTaskReturns(id) {
  const { data } = await api.get(`/housekeeping/tasks/${id}/returns`);
  return data;
}

/**
 * PATCH .../complete — bất kỳ ai trong nhóm dọn bấm được, và là xong cho cả nhóm (BR-PERM-05, chốt
 * 05/10/2026). Việc sang «Chờ kiểm tra» chứ chưa phải hoàn thành — còn chờ Quản lý nghiệm thu.
 */
export async function completeTask(id) {
  const { data } = await api.patch(`/housekeeping/tasks/${id}/complete`);
  return data;
}

/**
 * POST .../inspection — Quản lý chi nhánh nghiệm thu phòng sau khi nhân viên báo dọn xong.
 *
 * Trả về BIÊN BẢN kiểm tra (không phải việc dọn). «Đạt» thì phòng sang «Trống / Sẵn sàng»;
 * «Không đạt» thì phòng quay về «Chờ dọn», backend tự sinh việc dọn lại và trả id của nó ở
 * `nextTaskId`. Lý do là BẮT BUỘC khi không đạt.
 */
export async function inspectTask(id, { result, reason }) {
  const { data } = await api.post(`/housekeeping/tasks/${id}/inspection`, { result, reason });
  return data;
}

/**
 * GET .../inspection — biên bản kiểm tra của một việc dọn. Dùng cho liên kết "xem lần kiểm tra
 * trước" trên thẻ việc dọn lại: truyền `parentTaskId`, không phải id của chính thẻ đó.
 *
 * Việc dọn chưa từng được kiểm tra thì trả 404 — nơi gọi tự hiện câu báo, không coi là lỗi hệ thống.
 */
export async function fetchInspection(taskId) {
  const { data } = await api.get(`/housekeeping/tasks/${taskId}/inspection`);
  return data;
}
