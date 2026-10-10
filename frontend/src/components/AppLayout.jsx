import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { STAFF_PERMISSIONS, canReportDamage, hasPermission } from '../permissions';
import Logo from './Logo';

const ROLE_LABEL = {
  PLATFORM_ADMIN: 'Quản trị nền tảng',
  DIRECTOR: 'Giám đốc điều hành',
  MANAGER: 'Quản lý chi nhánh',
  STAFF: 'Nhân viên',
};

/** Giám đốc / Manager — Staff gọi các API quản trị (vd. GET /locations) sẽ nhận 403. */
const isManagement = (user) => user?.role === 'DIRECTOR' || user?.role === 'MANAGER';

/**
 * Lịch dọn phòng chỉ dành cho Quản lý chi nhánh. Giám đốc không có thao tác nào ở đây
 * (BR-HK-02, BR-HK-05, BR-HK-06, BR-HK-09 đều ✖ với DIRECTOR), và danh sách của Giám đốc
 * trộn phòng của MọI khách sạn nên đọc dễ nhầm — hai khách sạn đều có "phòng 102".
 */
const isBranchManager = (user) => user?.role === 'MANAGER';

/**
 * Chỉ nhân viên được Manager tick quyền Dọn dẹp mới có việc dọn của riêng mình — BR-PERM-05. Tính
 * cả nhân viên đa nhiệm (Lễ tân kiêm Dọn dẹp).
 */
const isHousekeeper = (user) => hasPermission(user, 'HOUSEKEEPING');

/** Được tick quyền Lễ tân — BR-PERM-04. */
const isReception = (user) => hasPermission(user, 'RECEPTION');

const isStaff = (user) => user?.role === 'STAFF';

/** Chỉ Giám đốc quản lý tài khoản Manager — BR-PERM-02. */
const isDirector = (user) => user?.role === 'DIRECTOR';

/** Manager CRUD tài khoản nhân viên trong khách sạn của mình — BR-PERM-03. */
const isManager = (user) => user?.role === 'MANAGER';

/**
 * Có ca làm việc: check-in/out, xem lịch cá nhân, xin nghỉ — Manager và mọi Staff (BR-PERM-03..06).
 * Giám đốc không có ca nên không có mục này.
 */
const hasShifts = (user) => user?.role === 'MANAGER' || user?.role === 'STAFF';

/** Manager và Staff thuộc đúng một khách sạn — hiện tên khách sạn trên thanh tiêu đề. */
const worksAtLocation = (user) => user?.role === 'MANAGER' || user?.role === 'STAFF';

/**
 * Khung màn hình sau đăng nhập. Các mục điều hướng chưa có màn hình tương ứng được để ở
 * trạng thái vô hiệu thay vì ẩn đi — giữ đúng bố cục thiết kế và cho thấy lộ trình còn lại.
 *
 * Nhóm điều hướng theo 5 mục tính năng chính: Dịch vụ SaaS (2.1), Quản lý chung (2.2), Lịch làm
 * việc & dọn dẹp (2.3), Quản lý phòng (2.4), Tài sản vật chất (2.5) — cộng nhóm Tổng quan ở đầu.
 * Nhân viên không có nhóm riêng theo nghiệp vụ: mục của Lễ tân / Dọn dẹp nằm trong đúng nhóm tính
 * năng tương ứng.
 *
 * Mục đã có màn hình khai báo `to` (đường dẫn). Mỗi mục khai báo `visible(user)` theo bảng phân
 * quyền BR-PERM-02..06, kể cả mục đang vô hiệu — không mục nào hiện cho vai trò không có quyền
 * đó. Chỉ để dễ dùng: quyền thật do backend quyết định. Admin Platform dùng khung riêng
 * (AdminLayout), nên "Cấu hình hệ thống" không nằm ở đây.
 */
const NAV_GROUPS = [
  {
    title: 'Tổng quan',
    items: [{ label: 'Dashboard tổng quan', to: '/tong-quan', visible: isManagement }],
  },
  {
    title: 'Dịch vụ SaaS',
    items: [
      // Gói đang dùng, hạn dùng, mua gói / mua thêm / gia hạn — BR-SAAS-02..10.
      { label: 'Gói dịch vụ', to: '/goi-dich-vu', visible: isDirector },
    ],
  },
  {
    title: 'Quản lý chung',
    items: [
      // Cùng một màn: Giám đốc CRUD cả chuỗi, Manager chỉ có khách sạn của mình.
      { label: 'Danh sách khách sạn', to: '/khach-san', visible: isDirector },
      { label: 'Thông tin khách sạn', to: '/khach-san', visible: isManager },
      // Phòng ban, Vị trí, Loại phòng — Giám đốc (BR-ORG-06, BR-ORG-11).
      { label: 'Phòng ban, Vị trí & Loại phòng', to: '/danh-muc', visible: isDirector },
      // Khu vực tạo ở cấp khách sạn, Manager CRUD (BR-ORG-12).
      { label: 'Khu vực', to: '/khu-vuc', visible: isManager },
      { label: 'Manager & Nhân sự', to: '/quan-ly', visible: isDirector },
      { label: 'Nhân viên chi nhánh', to: '/nhan-vien', visible: isManager },
    ],
  },
  {
    title: 'Lịch làm việc & dọn dẹp',
    items: [
      // Schedule Policy và Shift Template — Giám đốc (BR-SCH-01, BR-SCH-22).
      { label: 'Quy định & Mẫu ca', visible: isDirector },
      { label: 'Xếp lịch làm việc', visible: isBranchManager },
      { label: 'Công việc dọn phòng', to: '/don-phong', visible: isBranchManager },
      { label: 'Việc dọn của tôi', to: '/don-phong/cua-toi', visible: (user) => isStaff(user) && isHousekeeper(user) },
      { label: 'Lịch cá nhân & Chấm công', visible: hasShifts },
    ],
  },
  {
    title: 'Quản lý phòng',
    items: [
      { label: 'Sơ đồ phòng', to: '/so-do-phong', visible: isManagement },
      // Sơ đồ phòng có nút đặt / hủy đặt phòng, check-in, check-out cho người có quyền Lễ tân (BR-PERM-04).
      { label: 'Nhận / trả phòng', to: '/so-do-phong', visible: (user) => isStaff(user) && isReception(user) },
      // Không có quyền Lễ tân vẫn xem được tình trạng phòng, chỉ không có nút thao tác.
      { label: 'Sơ đồ phòng (xem)', to: '/so-do-phong', visible: (user) => isStaff(user) && !isReception(user) },
    ],
  },
  {
    title: 'Tài sản vật chất',
    items: [
      { label: 'Quản lý tài sản', to: '/tai-san', visible: isManagement },
      { label: 'Vật tư tiêu hao', to: '/vat-tu', visible: isManagement },
      // Màn xử lý báo hỏng của Manager (Giám đốc xem); nhân viên gửi báo hỏng ở mục riêng bên dưới.
      { label: 'Báo hỏng', to: '/bao-hong', visible: isManagement },
      // Lễ tân / Dọn dẹp — BR-ASSET-05. Tài sản trong phòng báo ở chi tiết phòng; trang này có
      // tài sản khu vực và các phiếu đã gửi.
      { label: 'Báo hỏng của tôi', to: '/bao-hong/cua-toi', visible: canReportDamage },
      // Danh mục tài sản cố định + tiêu hao — Giám đốc CRUD, Manager chỉ xem (BR-ASSET-08, BR-ASSET-09).
      { label: 'Danh mục tài sản', to: '/danh-muc-tai-san', visible: isManagement },
    ],
  },
];

/** "Nhân viên · Lễ tân + Dọn dẹp" — cho nhân viên thấy ngay mình đang có những quyền nào. */
function roleLabelOf(user) {
  const role = ROLE_LABEL[user?.role] ?? user?.role;
  if (!isStaff(user)) return role;
  const permissions = STAFF_PERMISSIONS.filter((p) => hasPermission(user, p.value)).map((p) => p.label);
  return `${role} · ${permissions.length > 0 ? permissions.join(' + ') : 'quyền chung'}`;
}

function formatToday() {
  const now = new Date();
  const weekday = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'][
    now.getDay()
  ];
  const date = now.toLocaleDateString('vi-VN');
  const time = now.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  return `${weekday}, ${date} - ${time}`;
}

export default function AppLayout({ children }) {
  const { user, signOut } = useAuth();
  const [navOpen, setNavOpen] = useState(false);

  // Bỏ cả nhóm khi vai trò này không còn mục nào — không để tiêu đề nhóm trơ trọi.
  const visibleGroups = NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.visible || item.visible(user)),
  })).filter((group) => group.items.length > 0);

  return (
    <div className={`shell ${navOpen ? 'shell--nav-open' : ''}`}>
      <aside className="shell__nav">
        <div className="shell__brand">
          <Logo subtitle="PMS MULTI-TENANT" />
        </div>

        <nav>
          {visibleGroups.map((group) => (
            <div className="nav-group" key={group.title}>
              <p className="nav-group__title">{group.title}</p>
              {group.items.map((item) =>
                item.to ? (
                  <NavLink
                    key={item.label}
                    to={item.to}
                    onClick={() => setNavOpen(false)}
                    className={({ isActive }) => `nav-item ${isActive ? 'nav-item--active' : ''}`}
                  >
                    {item.label}
                  </NavLink>
                ) : (
                  <button
                    key={item.label}
                    type="button"
                    className="nav-item"
                    disabled
                    title="Màn hình này chưa được phát triển"
                  >
                    {item.label}
                  </button>
                ),
              )}
            </div>
          ))}
        </nav>
      </aside>

      <div className="shell__main">
        <header className="topbar">
          <button
            type="button"
            className="topbar__burger"
            onClick={() => setNavOpen((v) => !v)}
            aria-label="Mở menu"
          >
            ☰
          </button>
          <span className="topbar__clock">🕘 {formatToday()}</span>
          {worksAtLocation(user) && user.locationName && (
            <span className="topbar__location" title="Khách sạn đang làm việc">
              🏨 {user.locationName}
            </span>
          )}
          {/* Email Quản lý của khách sạn — cho nhân viên biết liên hệ ai. Manager chính là người đó
              (email đã hiện ở góc phải) nên không lặp lại. */}
          {user?.role === 'STAFF' && user.locationManagerEmail && (
            <a
              className="topbar__manager"
              href={`mailto:${user.locationManagerEmail}`}
              title="Email quản lý khách sạn"
            >
              👤 Quản lý: {user.locationManagerEmail}
            </a>
          )}
          <span className="spacer" />
          <div className="topbar__user">
            <div>
              <b>{user?.email}</b>
              <small>{roleLabelOf(user)}</small>
            </div>
            <button type="button" className="btn btn--ghost" onClick={signOut}>
              Đăng xuất
            </button>
          </div>
        </header>

        <main className="shell__content">{children || <Outlet />}</main>
      </div>
    </div>
  );
}
