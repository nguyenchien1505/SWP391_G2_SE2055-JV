import { NavLink, Outlet, useNavigate } from "react-router";
import { BadgeDollarSign, Building2, Layers, LogOut, Settings } from "lucide-react";
import type { ComponentType } from "react";
import { useAuth } from "../context/AuthContext";
import { ROLE_LABEL } from "../lib/format";

interface NavItem {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
}

const SECTIONS: { title: string; items: NavItem[] }[] = [
  {
    title: "QUẢN TRỊ TENANT",
    items: [{ to: "/tenants", label: "Danh sách Tenant / Khách hàng", icon: Building2 }],
  },
  {
    title: "CẤU HÌNH & GÓI",
    items: [
      { to: "/pricing", label: "Gói dịch vụ & Đơn giá", icon: BadgeDollarSign },
      { to: "/system-config", label: "Dùng thử & Ân hạn", icon: Settings },
    ],
  },
];

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  if (!user) return null;

  const onLogout = async () => {
    await logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="flex h-full bg-slate-50">
      <aside className="flex w-64 shrink-0 flex-col border-r bg-white">
        <div className="flex items-center gap-3 border-b px-5 py-4">
          <div className="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Layers className="size-5" />
          </div>
          <div className="leading-tight">
            <div className="font-semibold">SaaS Platform</div>
            <div className="text-xs text-muted-foreground">Hệ thống Quản trị Nền tảng</div>
          </div>
        </div>
        <nav className="flex-1 space-y-5 overflow-y-auto px-3 py-5">
          {SECTIONS.map((section) => (
            <div key={section.title} className="space-y-1">
              <div className="px-3 pb-1 text-[11px] font-semibold tracking-wider text-muted-foreground">
                {section.title}
              </div>
              {section.items.map(({ to, label, icon: Icon }) => (
                <NavLink
                  key={to}
                  to={to}
                  className={({ isActive }) =>
                    `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
                      isActive
                        ? "bg-primary/10 font-medium text-primary"
                        : "text-slate-600 hover:bg-slate-100"
                    }`
                  }
                >
                  <Icon className="size-4" />
                  {label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="border-t px-5 py-3 text-xs text-muted-foreground">SaaS Multi-tenant</div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center justify-end gap-4 border-b bg-white px-6">
          <div className="text-right leading-tight">
            <div className="text-sm font-medium">{ROLE_LABEL[user.role]}</div>
            <div className="text-xs text-muted-foreground">{user.email}</div>
          </div>
          <button
            onClick={onLogout}
            className="flex items-center gap-1 rounded-md px-2 py-1 text-sm text-slate-600 hover:bg-slate-100"
          >
            <LogOut className="size-4" /> Đăng xuất
          </button>
        </header>
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
