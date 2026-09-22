import { useState, type FormEvent } from "react";
import { Navigate, useNavigate } from "react-router";
import { toast } from "sonner";
import { useAuth } from "../../context/AuthContext";
import { api, errorMessage } from "../../lib/api";
import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { Label } from "../../components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card";

export function ChangePasswordPage() {
  const { user, loading, refresh } = useAuth();
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (loading) return null;
  if (!user) return <Navigate to="/login" replace />;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (newPassword.length < 8) return toast.error("Mật khẩu mới phải có ít nhất 8 ký tự");
    if (newPassword !== confirm) return toast.error("Mật khẩu xác nhận không khớp");
    setSubmitting(true);
    try {
      await api.post("/auth/change-password", { currentPassword, newPassword });
      await refresh();
      toast.success("Đổi mật khẩu thành công");
      navigate("/tenants", { replace: true });
    } catch (err) {
      toast.error(errorMessage(err, "Không đổi được mật khẩu"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex h-full items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-xl">Đổi mật khẩu</CardTitle>
          {user.mustChangePassword && (
            <p className="text-sm text-muted-foreground">Bạn đang dùng mật khẩu tạm, cần đổi trước khi sử dụng hệ thống.</p>
          )}
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="current">Mật khẩu hiện tại</Label>
              <Input id="current" type="password" required value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="new">Mật khẩu mới</Label>
              <Input id="new" type="password" required value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirm">Nhập lại mật khẩu mới</Label>
              <Input id="confirm" type="password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} />
            </div>
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? "Đang lưu…" : "Đổi mật khẩu"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
