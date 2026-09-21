import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { api, errorMessage } from "./api";
import type { Page } from "./types";

export function usePagedList<T>(url: string, params: Record<string, unknown> = {}, size = 20) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<T> | null>(null);
  const [loading, setLoading] = useState(true);
  const paramsKey = JSON.stringify(params);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<Page<T>>(url, { params: { ...JSON.parse(paramsKey), page, size } });
      setData(res.data);
    } catch (err) {
      toast.error(errorMessage(err, "Không tải được dữ liệu"));
    } finally {
      setLoading(false);
    }
  }, [url, paramsKey, page, size]);

  useEffect(() => {
    reload();
  }, [reload]);

  return { data, loading, page, setPage, reload };
}
