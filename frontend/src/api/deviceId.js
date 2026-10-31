/**
 * Mã thiết bị gửi kèm mỗi lần vào / ra ca (header `X-Device-Id`, chấm công M2) — để quản lý đối soát ai
 * hay chấm công hộ nhau.
 *
 * Trình duyệt không cho đọc mã máy thật, nên tự sinh một mã ngẫu nhiên và giữ trong localStorage: cùng
 * trình duyệt trên cùng máy thì cùng mã. Chỉ để tham khảo — xóa dữ liệu trình duyệt là ra mã mới, ai cũng
 * sửa được. Chế độ ẩn danh / chặn lưu trữ thì dùng một mã tạm cho tới khi đóng tab.
 */
const STORAGE_KEY = 'attendance.deviceId';

let sessionDeviceId = null;

export function getDeviceId() {
  try {
    let id = window.localStorage.getItem(STORAGE_KEY);
    if (!id) {
      id = newDeviceId();
      window.localStorage.setItem(STORAGE_KEY, id);
    }
    return id;
  } catch {
    sessionDeviceId ??= newDeviceId();
    return sessionDeviceId;
  }
}

/** `crypto.randomUUID` chỉ có trên https / localhost — mở bằng IP mạng nội bộ qua http thì không có. */
function newDeviceId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
