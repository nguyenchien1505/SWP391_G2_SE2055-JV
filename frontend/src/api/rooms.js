import { api } from './client';

/**
 * Bỏ các tham số rỗng trước khi gửi: ô lọc để trống nghĩa là "không lọc". Gửi `status=`
 * rỗng thì backend vẫn hiểu là null, nhưng URL gọn hơn và dễ đọc log hơn.
 */
function withoutEmpty(params) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
}

/**
 * GET /rooms — Page: { content, totalElements, totalPages, number, size }.
 * Lọc ở server (BR-ROOM-06). `locationId` chỉ có tác dụng với Giám đốc; Manager/Staff luôn bị
 * backend ép về Location của mình.
 */
export async function fetchRooms({ locationId, status, floor, roomTypeId, page = 0, size = 20 } = {}) {
  const { data } = await api.get('/rooms', {
    params: withoutEmpty({ locationId, status, floor, roomTypeId, page, size }),
  });
  return data;
}

/**
 * Toàn bộ phòng của một Location — cho sơ đồ phòng (S-06), cần thấy hết phòng cùng lúc.
 * Mỗi lần lấy tối đa 100 (giới hạn trang của backend); Location nhiều hơn thì tải tiếp.
 */
export async function fetchAllRooms({ locationId } = {}) {
  const rooms = [];
  let page = 0;
  let totalPages = 1;
  while (page < totalPages) {
    const data = await fetchRooms({ locationId, page, size: 100 });
    rooms.push(...data.content);
    totalPages = data.totalPages;
    page += 1;
  }
  return rooms;
}

export async function fetchRoom(id) {
  const { data } = await api.get(`/rooms/${id}`);
  return data;
}

/**
 * GET /rooms/status-summary — { locationId, total, counts: { AVAILABLE: n, … } }, luôn đủ
 * 7 trạng thái (BR-DASH-02, BR-DASH-03).
 */
export async function fetchRoomStatusSummary({ locationId } = {}) {
  const { data } = await api.get('/rooms/status-summary', { params: withoutEmpty({ locationId }) });
  return data;
}

/**
 * GET /organization/room-types — trả về MẢNG (không phân trang). Chỉ Giám đốc và Manager gọi
 * được; Staff nhận 403, nên màn hình của Staff không gọi hàm này.
 *
 * @param includeInactive true để lấy cả loại đã ẩn (BR-ORG-14) — cần cho ô lọc, vì phòng cũ
 *        vẫn có thể thuộc loại đã ẩn.
 */
export async function fetchRoomTypes({ includeInactive = false } = {}) {
  const { data } = await api.get('/organization/room-types', { params: { includeInactive } });
  return data;
}
