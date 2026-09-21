import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Logo from './Logo';
import '../pages/Admin_platform/admin.css';

/**
 * Khung màn hình khu Quản trị nền tảng (PLATFORM_ADMIN). Dùng cùng các lớp CSS với AppLayout
 * (shell, nav-item, topbar) để giao diện đồng nhất, nhưng mục điều hướng là liên kết thật
 * (NavLink) — AppLayout dùng chung chưa có điều hướng nên không sửa nó, dùng khung riêng này.
 *
 * <p>Là route bố cục: các trang con được vẽ vào {@code <Outlet />}.
 */
const NAV_GROUPS = [
  {
    title: 'Quản trị Tenant',
    items: [{ to: '/quan-tri/tenant', label: 'Danh sách Tenant' }],
  },
  {
    title: 'Cấu hình & Gói',
    items: [
      { to: '/quan-tri/bang-gia', label: 'Gói dịch vụ & Đơn giá' },
      { to: '/quan-tri/cau-hinh', label: 'Dùng thử & Ân hạn' },
    ],
  },
];

export default function AdminLayout() {
  const { user, signOut } = useAuth();
  const [navOpen, setNavOpen] = useState(false);

  return (
    <div className={`shell ${navOpen ? 'shell--nav-open' : ''}`}>
      <aside className="shell__nav">
        <div className="shell__brand">
          <Logo subtitle="SAAS PLATFORM" />
        </div>

        <nav>
          {NAV_GROUPS.map((group) => (
            <div className="nav-group" key={group.title}>
              <p className="nav-group__title">{group.title}</p>
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  onClick={() => setNavOpen(false)}
                  className={({ isActive }) => `nav-item ${isActive ? 'nav-item--active' : ''}`}
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        <div className="shell__nav-foot">
          <span className="dot dot--online" aria-hidden="true" /> SaaS đa Tenant
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
          <span className="spacer" />
          <div className="topbar__user">
            <div>
              <b>{user?.email}</b>
              <small>Quản trị nền tảng</small>
            </div>
            <button type="button" className="btn btn--ghost" onClick={signOut}>
              Đăng xuất
            </button>
          </div>
        </header>

        <main className="shell__content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
