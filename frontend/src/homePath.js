/**
 * Trang đầu tiên sau đăng nhập. Đặt riêng một file để cả App.jsx và LoginPage.jsx dùng chung mà
 * không import vòng tròn.
 *
 * - Admin Platform → khu quản trị nền tảng.
 * - Staff Dọn dẹp → thẳng vào việc dọn của mình: đó là toàn bộ công việc của họ (BR-PERM-05).
 * - Staff còn lại (Lễ tân, Khác) → sơ đồ phòng của khách sạn mình: Staff KHÔNG gọi được
 *   GET /locations (403) nên không thể vào /khach-san (BR-PERM-04).
 * - Giám đốc, Manager → danh sách khách sạn.
 */
export function homePathFor(user) {
  if (user?.role === 'PLATFORM_ADMIN') return '/quan-tri/tenant';
  if (user?.role === 'STAFF') {
    return user.positionType === 'HOUSEKEEPING' ? '/don-phong/cua-toi' : '/so-do-phong';
  }
  return '/khach-san';
}
