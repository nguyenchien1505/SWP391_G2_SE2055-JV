import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { toast } from "sonner";
import { api, errorMessage } from "../../lib/api";
import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { Label } from "../../components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card";

const FIELDS = [
  { key: "companyName", label: "Tên công ty / chuỗi khách sạn", type: "text" },
  { key: "representativeName", label: "Họ tên người đại diện", type: "text" },
  { key: "email", label: "Email (dùng để đăng nhập)", type: "email" },
  { key: "phone", label: "Số điện thoại", type: "tel" },
  { key: "password", label: "Mật khẩu (8–72 ký tự)", type: "password" },
] as const;

type FormState = Record<(typeof FIELDS)[number]["key"], string>;

export function RegisterTenantPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState<FormState>({
    companyName: "",
    representativeName: "",
    email: "",
    phone: "",
    password: "",
  });
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post("/auth/register-tenant", form);
      toast.success("Đăng ký thành công, hãy đăng nhập bằng email vừa đăng ký");
      navigate("/login", { replace: true });
    } catch (err) {
      toast.error(errorMessage(err, "Không đăng ký được"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-full items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-xl">Đăng ký doanh nghiệp</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <form onSubmit={onSubmit} className="space-y-4">
            {FIELDS.map(({ key, label, type }) => (
              <div key={key} className="space-y-2">
                <Label htmlFor={key}>{label}</Label>
                <Input
                  id={key}
                  type={type}
                  required
                  value={form[key]}
                  onChange={(e) => setForm({ ...form, [key]: e.target.value })}
                />
              </div>
            ))}
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? "Đang đăng ký…" : "Đăng ký"}
            </Button>
          </form>
          <p className="text-center text-sm text-muted-foreground">
            Đã có tài khoản?{" "}
            <Link to="/login" className="text-primary underline">
              Đăng nhập
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
