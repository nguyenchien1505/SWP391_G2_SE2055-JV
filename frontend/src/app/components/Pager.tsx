import type { Page } from "../lib/types";
import { Button } from "./ui/button";

interface Props {
  data: Page<unknown> | null;
  page: number;
  onChange: (page: number) => void;
}

export function Pager({ data, page, onChange }: Props) {
  if (!data || data.totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-between pt-4 text-sm">
      <span className="text-muted-foreground">
        Trang {page + 1} / {data.totalPages} ({data.totalElements} bản ghi)
      </span>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
          Trước
        </Button>
        <Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => onChange(page + 1)}>
          Sau
        </Button>
      </div>
    </div>
  );
}
