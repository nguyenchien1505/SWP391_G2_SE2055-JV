/**
 * Trang đầu tiên sau đăng nhập: Admin Platform vào khu quản trị, các vai trò khác vào /khach-san.
 * Đặt riêng một file để cả App.jsx và LoginPage.jsx dùng chung mà không import vòng tròn.
 */
export function homePathFor(user) {
  return user?.role === 'PLATFORM_ADMIN' ? '/quan-tri/tenant' : '/khach-san';
}
