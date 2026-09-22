import { useCallback, useEffect, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { api, errorMessage } from "../../lib/api";
import { formatDate, formatVnd } from "../../lib/format";
import type { PricingConfig } from "../../lib/types";
import { Badge } from "../../components/ui/badge";
import { Button } from "../../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card";
import { Input } from "../../components/ui/input";
import { Label } from "../../components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../../components/ui/table";

const EMPTY = { pricePerLocation: "", pricePerUser: "", pricePerRoom: "", effectiveFrom: "" };

export function PricingPage() {
  const [list, setList] = useState<PricingConfig[]>([]);
  const [current, setCurrent] = useState<PricingConfig | null>(null);
  const [form, setForm] = useState(EMPTY);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    try {
      const [all, cur] = await Promise.all([
        api.get<PricingConfig[]>("/platform/pricing"),
        api.get<PricingConfig>("/platform/pricing/current").catch(() => null),
      ]);
      setList(all.data);
      setCurrent(cur?.data ?? null);
    } catch (err) {
      toast.error(errorMessage(err, "Không tải được bảng giá"));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post("/platform/pricing", {
        pricePerLocation: Number(form.pricePerLocation),
        pricePerUser: Number(form.pricePerUser),
        pricePerRoom: Number(form.pricePerRoom),
        effectiveFrom: form.effectiveFrom,
      });
      toast.success("Đã thêm bảng giá mới");
      setForm(EMPTY);
      await load();
    } catch (err) {
      toast.error(errorMessage(err, "Không thêm được bảng giá"));
    } finally {
      setSubmitting(false);
    }
  };

  const fields = [
    { key: "pricePerLocation", label: "Đơn giá / Location (₫)" },
    { key: "pricePerUser", label: "Đơn giá / Staff (₫)" },
    { key: "pricePerRoom", label: "Đơn giá / Phòng (₫)" },
  ] as const;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Gói dịch vụ &amp; bảng giá</h1>
        <p className="text-sm text-muted-foreground">
          Giá tính theo Location, Staff và Phòng cho mỗi chu kỳ 30 ngày. Bảng giá chỉ thêm mới, không sửa hay xóa;
          gói đã mua giữ nguyên giá lúc chốt.
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Bảng giá đang hiệu lực</CardTitle>
          </CardHeader>
          <CardContent>
            {current ? (
              <dl className="divide-y text-sm">
                <div className="flex justify-between py-2">
                  <dt className="text-muted-foreground">Mỗi Location</dt>
                  <dd className="font-medium">{formatVnd(current.pricePerLocation)}</dd>
                </div>
                <div className="flex justify-between py-2">
                  <dt className="text-muted-foreground">Mỗi Staff</dt>
                  <dd className="font-medium">{formatVnd(current.pricePerUser)}</dd>
                </div>
                <div className="flex justify-between py-2">
                  <dt className="text-muted-foreground">Mỗi Phòng</dt>
                  <dd className="font-medium">{formatVnd(current.pricePerRoom)}</dd>
                </div>
                <div className="flex justify-between py-2">
                  <dt className="text-muted-foreground">Hiệu lực từ</dt>
                  <dd className="font-medium">{formatDate(current.effectiveFrom)}</dd>
                </div>
              </dl>
            ) : (
              <p className="text-sm text-muted-foreground">Chưa có bảng giá nào đang hiệu lực.</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Thêm bảng giá mới</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={onSubmit} className="space-y-3">
              {fields.map(({ key, label }) => (
                <div key={key} className="space-y-1">
                  <Label htmlFor={key}>{label}</Label>
                  <Input
                    id={key}
                    type="number"
                    min={0}
                    max={1_000_000_000}
                    required
                    value={form[key]}
                    onChange={(e) => setForm({ ...form, [key]: e.target.value })}
                  />
                </div>
              ))}
              <div className="space-y-1">
                <Label htmlFor="effectiveFrom">Ngày bắt đầu hiệu lực</Label>
                <Input
                  id="effectiveFrom"
                  type="date"
                  required
                  value={form.effectiveFrom}
                  onChange={(e) => setForm({ ...form, effectiveFrom: e.target.value })}
                />
              </div>
              <Button type="submit" disabled={submitting}>
                {submitting ? "Đang lưu…" : "Thêm bảng giá"}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Lịch sử bảng giá</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Hiệu lực từ</TableHead>
                <TableHead>Mỗi Location</TableHead>
                <TableHead>Mỗi Staff</TableHead>
                <TableHead>Mỗi Phòng</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.map((p) => (
                <TableRow key={p.id}>
                  <TableCell>{formatDate(p.effectiveFrom)}</TableCell>
                  <TableCell>{formatVnd(p.pricePerLocation)}</TableCell>
                  <TableCell>{formatVnd(p.pricePerUser)}</TableCell>
                  <TableCell>{formatVnd(p.pricePerRoom)}</TableCell>
                  <TableCell>{current?.id === p.id && <Badge>Đang áp dụng</Badge>}</TableCell>
                </TableRow>
              ))}
              {list.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="text-center text-muted-foreground">
                    Chưa có bảng giá
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
