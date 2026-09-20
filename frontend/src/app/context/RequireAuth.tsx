import { Navigate, Outlet, useLocation } from "react-router";
import { useAuth } from "./AuthContext";
import type { Role } from "../lib/types";

interface Props {
  roles?: Role[];
}

// BR-USER-07: còn mật khẩu tạm thì chỉ được vào /change-password.
export function RequireAuth({ roles }: Props) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <div className="flex h-full items-center justify-center text-muted-foreground">Đang tải…</div>;
  }
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to="/unauthorized" replace />;
  return <Outlet />;
}
