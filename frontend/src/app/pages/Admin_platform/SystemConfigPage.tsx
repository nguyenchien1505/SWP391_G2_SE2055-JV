import { useEffect, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { api, errorMessage } from "../../lib/api";
import { formatDateTime } from "../../lib/format";
import type { SystemConfig } from "../../lib/types";
import { Button } from "../../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card";
import { Input } from "../../components/ui/input";
import { Label } from "../../components/ui/label";

export function SystemConfigPage() {
  const [config, setConfig] = useState<SystemConfig | null>(null);
  const [trialDays, setTrialDays] = useState("");
  const [gracePeriodDays, setGracePeriodDays] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const apply = (c: SystemConfig) => {
    setConfig(c);
    setTrialDays(String(c.trialDays));
    setGracePeriodDays(String(c.gracePeriodDays));
  };

  useEffect(() => {
    api
      .get<SystemConfig>("/platform/system-config")
      .then((res) => apply(res.data))
      .catch((err) => toast.error(errorMessage(err, "Không tải được cấu hình")));
  }, []);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const res = await api.put<SystemConfig>("/platform/system-config", {
        trialDays: Number(trialDays),
        gracePeriodDays: Number(gracePeriodDays),
      });
      apply(res.data);
      toast.success("Đã lưu cấu hình");
    } catch (err) {
      toast.error(errorMessage(err, "Không lưu được cấu hình"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Cấu hình dùng thử &amp; ân hạn</h1>
      <Card className="max-w-xl">
        <CardHeader>
          <CardTitle className="text-base">
            {config?.updatedAt ? `Cập nhật lần cuối: ${formatDateTime(config.updatedAt)}` : "Chưa chỉnh sửa từ khi khởi tạo"}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-1">
              <Label htmlFor="trialDays">Số ngày dùng thử (1–365)</Label>
              <Input id="trialDays" type="number" min={1} max={365} required value={trialDays} onChange={(e) => setTrialDays(e.target.value)} />
              <p className="text-xs text-muted-foreground">Chỉ áp dụng cho Tenant đăng ký mới, không ảnh hưởng Tenant đang dùng thử.</p>
            </div>
            <div className="space-y-1">
              <Label htmlFor="grace">Số ngày ân hạn khi quá hạn thanh toán (0–90)</Label>
              <Input id="grace" type="number" min={0} max={90} required value={gracePeriodDays} onChange={(e) => setGracePeriodDays(e.target.value)} />
              <p className="text-xs text-muted-foreground">Sau số ngày này Tenant quá hạn thanh toán sẽ bị khóa.</p>
            </div>
            <Button type="submit" disabled={submitting || !config}>
              {submitting ? "Đang lưu…" : "Lưu cấu hình"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
