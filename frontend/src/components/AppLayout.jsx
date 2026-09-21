import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import Logo from './Logo';

const ROLE_LABEL = {
  PLATFORM_ADMIN: 'Quản trị nền tảng',
  DIRECTOR: 'Giám đốc điều hành',
  MANAGER: 'Quản lý chi nhánh',
  STAFF: 'Nhân viên',
};

/**
 * Khung màn hình sau đăng nhập. Các mục điều hướng chưa có màn hình tương ứng được để ở
 * trạng thái vô hiệu thay vì ẩn đi — giữ đúng bố cục thiết kế và cho thấy lộ trình còn lại.
 */
const NAV_GROUPS = [
  {
    title: 'Vận hành chuỗi',
    items: [
      { label: 'Dashboard tổng quan', ready: false },
      { label: 'Sơ đồ phòng', ready: false },
      { label: 'Danh sách phòng', ready: false },
      { label: 'Xếp lịch làm việc', ready: false },
      { label: 'Công việc dọn phòng', ready: false },
    ],
  },
  {
    title: 'Quản trị & hệ thống',
    items: [
      { label: 'Danh sách khách sạn', ready: true },
      { label: 'Manager & Nhân sự', ready: false },
      { label: 'Danh mục & Khu vực', ready: false },
      { label: 'Quy định & Mẫu ca', ready: false },
      { label: 'Quản lý tài sản', ready: false },
      { label: 'Cấu hình hệ thống', ready: false },
    ],
  },
  {
    title: 'Cá nhân',
    items: [{ label: 'Lịch cá nhân & Chấm công', ready: false }],
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
              {group.items.map((item) => (
                <button
                  key={item.label}
                  type="button"
                  className={`nav-item ${item.ready ? 'nav-item--active' : ''}`}
                  disabled={!item.ready}
                  title={item.ready ? undefined : 'Màn hình này chưa được phát triển'}
                >
                  {item.label}
                </button>
              ))}
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
