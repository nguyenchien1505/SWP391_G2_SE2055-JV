import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { hasPermission } from '../permissions';
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

/** Chỉ Giám đốc quản lý tài khoản Manager — BR-PERM-02. */
const isDirector = (user) => user?.role === 'DIRECTOR';

/** Manager CRUD tài khoản nhân viên trong khách sạn của mình — BR-PERM-03. */
const isManager = (user) => user?.role === 'MANAGER';

/** Lễ tân và Dọn dẹp được báo hỏng tài sản; Position loại Khác thì không — BR-ASSET-05, BR-PERM-06. */
const canReportDamage = (user) => user?.role === 'STAFF' && (isReception(user) || isHousekeeper(user));

/**
 * Có ca làm việc: check-in/out, xem lịch cá nhân, xin nghỉ — Manager và mọi Staff (BR-PERM-03..06).
 * Giám đốc không có ca nên không có mục này.
 */
const hasShifts = (user) => user?.role === 'MANAGER' || user?.role === 'STAFF';

/**
 * Khung màn hình sau đăng nhập. Các mục điều hướng chưa có màn hình tương ứng được để ở
 * trạng thái vô hiệu thay vì ẩn đi — giữ đúng bố cục thiết kế và cho thấy lộ trình còn lại.
 *
 * Mục đã có màn hình khai báo `to` (đường dẫn). Mỗi mục khai báo `visible(user)` theo bảng phân
 * quyền BR-PERM-02..06, kể cả mục đang vô hiệu — không mục nào hiện cho vai trò không có quyền
 * đó. Chỉ để dễ dùng: quyền thật do backend quyết định. Admin Platform dùng khung riêng
 * (AdminLayout), nên "Cấu hình hệ thống" không nằm ở đây.
 */
const NAV_GROUPS = [
  {
    title: 'Vận hành chuỗi',
    items: [
      { label: 'Dashboard tổng quan', to: '/tong-quan', visible: isManagement },
      // Mọi vai trò xem được sơ đồ phòng; chỉ Lễ tân và Manager có nút đổi trạng thái.
      { label: 'Sơ đồ phòng', to: '/so-do-phong' },
      { label: 'Danh sách phòng', to: '/phong', visible: isManagement },
      { label: 'Xếp lịch làm việc', visible: isBranchManager },
      { label: 'Công việc dọn phòng', to: '/don-phong', visible: isBranchManager },
      { label: 'Quản lý tài sản', to: '/tai-san', visible: isManagement },
      { label: 'Vật tư tiêu hao', to: '/vat-tu', visible: isManagement },
      // Màn xử lý báo hỏng của Manager (Giám đốc xem); nhân viên gửi báo hỏng ở mục riêng bên dưới.
      { label: 'Báo hỏng', to: '/bao-hong', visible: isManagement },
    ],
  },
  {
    title: 'Quản trị & hệ thống',
    items: [
      { label: 'Danh sách khách sạn', to: '/khach-san', visible: isManagement },
      { label: 'Manager & Nhân sự', to: '/quan-ly', visible: isDirector },
      { label: 'Nhân viên chi nhánh', to: '/nhan-vien', visible: isManager },
      // Phòng ban, Vị trí, Loại phòng, Danh mục tài sản — Giám đốc (BR-ORG-06, BR-ORG-11, BR-ASSET-09).
      { label: 'Danh mục', visible: isDirector },
      // Khu vực tạo ở cấp khách sạn, Manager CRUD (BR-ORG-12).
      { label: 'Khu vực', visible: isManager },
      // Schedule Policy và Shift Template — Giám đốc (BR-SCH-01, BR-SCH-22).
      { label: 'Quy định & Mẫu ca', visible: isDirector },
    ],
  },
  {
    title: 'Cá nhân',
    items: [
      { label: 'Việc dọn của tôi', to: '/don-phong/cua-toi', visible: isHousekeeper },
      { label: 'Gửi báo hỏng tài sản', visible: canReportDamage },
      { label: 'Lịch cá nhân & Chấm công', visible: hasShifts },
    ],
  },
];

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

        <div className="shell__nav-foot">
          <span className="dot dot--online" aria-hidden="true" /> Máy chủ hoạt động
          <span className="version-chip">v2.4.1</span>
        </div>
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
          <span className="spacer" />
          <div className="topbar__user">
            <div>
              <b>{user?.email}</b>
              <small>{ROLE_LABEL[user?.role] ?? user?.role}</small>
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
