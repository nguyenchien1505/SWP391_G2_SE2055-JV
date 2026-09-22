import { api, API_BASE_URL } from './client';

/**
 * Backend dùng formLogin của Spring Security: body phải là
 * `application/x-www-form-urlencoded` với đúng hai tham số `username` / `password`.
 * Gửi JSON sẽ bị trả 401 dù thông tin đăng nhập đúng.
 */
export async function login(email, password) {
  const body = new URLSearchParams();
  body.append('username', email);
  body.append('password', password);

  await api.post('/auth/login', body, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });
}

/**
 * Đăng nhập Google phải là ĐIỀU HƯỚNG CẢ TRANG, không gọi bằng axios: luồng OAuth2 đi
 * qua chuỗi redirect sang accounts.google.com rồi quay về backend, XHR không theo được.
 *
 * <p>Backend KHÔNG tự tạo tài khoản từ Google — email phải được Manager/Giám đốc cấp
 * trước (BR-USER-03). Email lạ sẽ bị đẩy về `/dang-nhap?error=oauth_unauthorized`.
 */
export function startGoogleLogin() {
  window.location.href = `${API_BASE_URL}/auth/login/google`;
}

export async function fetchCurrentUser() {
  const { data } = await api.get('/auth/me');
  return data;
}

/** BR-USER-07: đổi mật khẩu tạm ở lần đăng nhập đầu tiên. */
export async function changePassword(currentPassword, newPassword) {
  await api.post('/auth/change-password', { currentPassword, newPassword });
}

export async function logout() {
  await api.post('/auth/logout');
}
