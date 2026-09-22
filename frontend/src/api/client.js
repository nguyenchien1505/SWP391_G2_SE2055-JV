import axios from 'axios';

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

/**
 * Backend xác thực bằng SESSION COOKIE (JSESSIONID), không phải JWT — xem SecurityConfig.
 * Vì vậy `withCredentials` là bắt buộc ở mọi request, kể cả request đăng nhập; thiếu nó
 * thì login trả 200 nhưng request sau đó vẫn 401 vì cookie không được gửi kèm.
 */
export const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
});

/** Tên lỗi tiếng Việt do backend trả về (ApiError.message), đã nêu rõ vi phạm rule nào. */
export function readErrorMessage(error, fallback = 'Đã xảy ra lỗi, vui lòng thử lại.') {
  if (!error?.response) {
    return 'Không kết nối được máy chủ. Kiểm tra backend đã chạy ở ' + API_BASE_URL + ' chưa.';
  }

  const { status, data } = error.response;

  // 422: lỗi validate từng trường — gộp lại cho người dùng thấy đủ.
  if (Array.isArray(data?.fieldErrors) && data.fieldErrors.length > 0) {
    return data.fieldErrors.map((fe) => fe.message).join(' · ');
  }
  if (data?.message) {
    return data.message;
  }
  if (status === 401) {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  }
  if (status === 403) {
    return 'Bạn không có quyền thực hiện thao tác này.';
  }
  return fallback;
}
