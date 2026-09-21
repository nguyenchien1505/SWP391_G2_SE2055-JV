import { api } from './client';

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

export async function fetchCurrentUser() {
  const { data } = await api.get('/auth/me');
  return data;
}

export async function logout() {
  await api.post('/auth/logout');
}
