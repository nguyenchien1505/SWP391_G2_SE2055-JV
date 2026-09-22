import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
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
 * Khung màn hình sau đăng nhập. Các mục điều hướng chưa có màn hình tương ứng được để ở
 * trạng thái vô hiệu thay vì ẩn đi — giữ đúng bố cục thiết kế và cho thấy lộ trình còn lại.
 *
 * Mục đã có màn hình khai báo `to` (đường dẫn). `visible(user)` (tùy chọn) ẩn mục với vai trò
 * không dùng được — chỉ để dễ dùng, quyền thật do backend quyết định.
 */
const NAV_GROUPS = [
  {
    title: 'Vận hành chuỗi',
    items: [
      { label: 'Dashboard tổng quan' },
      { label: 'Sơ đồ phòng', to: '/so-do-phong' },
      { label: 'Danh sách phòng', to: '/phong', visible: isManagement },
      { label: 'Xếp lịch làm việc' },
      { label: 'Công việc dọn phòng' },
    ],
  },
  {
    title: 'Quản trị & hệ thống',
    items: [
      { label: 'Danh sách khách sạn', to: '/khach-san', visible: isManagement },
      { label: 'Manager & Nhân sự' },
      { label: 'Danh mục & Khu vực' },
      { label: 'Quy định & Mẫu ca' },
      { label: 'Quản lý tài sản' },
      { label: 'Cấu hình hệ thống' },
    ],
  },
  {
    title: 'Cá nhân',
    items: [{ label: 'Lịch cá nhân & Chấm công' }],
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

  return (
    <div className={`shell ${navOpen ? 'shell--nav-open' : ''}`}>
      <aside className="shell__nav">
        <div className="shell__brand">
          <Logo subtitle="PMS MULTI-TENANT" />
        </div>

        <nav>
          {NAV_GROUPS.map((group) => (
            <div className="nav-group" key={group.title}>
              <p className="nav-group__title">{group.title}</p>
              {group.items
                .filter((item) => !item.visible || item.visible(user))
                .map((item) =>
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

        <main className="shell__content">{children}</main>
      </div>
    </div>
  );
}
