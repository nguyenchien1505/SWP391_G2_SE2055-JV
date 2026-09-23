import { Link, useNavigate } from "react-router";
import { useAuth } from "../../context/AuthContext";
import { Button } from "../../components/ui/button";

export function UnauthorizedPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const onLogout = async () => {
    await logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 p-6 text-center">
      <h1 className="text-2xl font-semibold">Không có quyền truy cập</h1>
      <p className="max-w-md text-muted-foreground">
        Giao diện này chỉ dành cho Admin nền tảng. Tài khoản của bạn chưa được đăng ký hoặc không có quyền vào trang này.
      </p>
      {user ? (
        <Button onClick={onLogout}>Đăng xuất</Button>
      ) : (
        <Button asChild>
          <Link to="/login">Về trang đăng nhập</Link>
        </Button>
      )}
    </div>
  );
}
