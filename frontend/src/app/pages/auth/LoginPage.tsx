import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router";
import { toast } from "sonner";
import { useAuth } from "../../context/AuthContext";
import { errorMessage } from "../../lib/api";
import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { Label } from "../../components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card";

export function LoginPage() {
  const { user, loading, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (!loading && user) {
    return <Navigate to={user.mustChangePassword ? "/change-password" : "/tenants"} replace />;
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const me = await login(email, password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(me.mustChangePassword ? "/change-password" : (from ?? "/tenants"), { replace: true });
    } catch (err) {
      toast.error(errorMessage(err, "Email hoặc mật khẩu không đúng"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex h-full items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-xl">Đăng nhập Hotel Workforce</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Mật khẩu</Label>
              <Input id="password" type="password" required value={password} onChange={(e) => setPassword(e.target.value)} />
            </div>
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? "Đang đăng nhập…" : "Đăng nhập"}
            </Button>
          </form>
          <Button variant="outline" className="w-full" asChild>
            <a href="/api/auth/login/google">Đăng nhập bằng Google</a>
          </Button>
          <p className="text-center text-sm text-muted-foreground">
            Doanh nghiệp mới?{" "}
            <Link to="/register" className="text-primary underline">
              Đăng ký dùng thử
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
