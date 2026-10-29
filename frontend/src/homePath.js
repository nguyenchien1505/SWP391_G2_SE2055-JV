import { hasPermission, isSupervisor } from './permissions';

/**
 * Trang đầu tiên sau đăng nhập. Đặt riêng một file để cả App.jsx và LoginPage.jsx dùng chung mà
 * không import vòng tròn.
 *
 * - Admin Platform → khu quản trị nền tảng.
 * - Staff được tick quyền Giám sát → bảng Công việc dọn phòng: giao việc và kiểm tra phòng là việc
 *   chính của Giám sát vận hành (BR-PERM-07), kể cả khi kiêm Lễ tân / Dọn dẹp.
 * - Staff chỉ có quyền Dọn dẹp → thẳng vào việc dọn của mình: đó là toàn bộ công việc của họ
 *   (BR-PERM-05).
 * - Staff còn lại (có quyền Lễ tân, kể cả kiêm Dọn dẹp, hoặc chỉ quyền chung) → sơ đồ phòng của
 *   khách sạn mình: Staff KHÔNG gọi được GET /locations (403) nên không thể vào /khach-san.
 * - Giám đốc của Tenant mới chưa chọn Dùng thử / Mua gói → Gói dịch vụ (trang duy nhất mở được).
 * - Giám đốc, Manager → danh sách khách sạn.
 */
export function homePathFor(user) {
  if (user?.role === 'PLATFORM_ADMIN') return '/quan-tri/tenant';
  if (user?.planRequired) return '/goi-dich-vu';
  if (user?.role === 'STAFF') {
    if (isSupervisor(user)) return '/don-phong';
    const housekeepingOnly = hasPermission(user, 'HOUSEKEEPING') && !hasPermission(user, 'RECEPTION');
    return housekeepingOnly ? '/don-phong/cua-toi' : '/so-do-phong';
  }
  return '/khach-san';
}
