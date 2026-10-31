import { useCallback, useEffect, useState } from 'react';
import { fetchShifts } from '../api/scheduling';
import { todayIso } from '../pages/rooms/format';
import { isInCurrentWorkDay, yesterdayIso } from '../pages/rooms/workDay';

/**
 * Ca của chính nhân viên thuộc NGÀY LÀM HIỆN TẠI (kể cả ca đêm từ hôm qua còn đang làm — BR-SCH-03), cho khối
 * «Ca hôm nay» có nút Vào ca / Kết thúc ca (chấm công M2).
 *
 * @param enabled false thì không tải (người không có ca: quản lý, giám đốc)
 * @returns {{ shifts: Array|null, reload: () => Promise<void> }} `shifts` null = chưa tải / lỗi — ẩn khối, không
 *          chặn trang
 */
export function useWorkDayShifts(enabled = true) {
  const today = todayIso();
  const [shifts, setShifts] = useState(null);

  const reload = useCallback(async () => {
    if (!enabled) return;
    try {
      const list = await fetchShifts({ from: yesterdayIso(), to: today });
      setShifts(list.filter((shift) => isInCurrentWorkDay(shift)));
    } catch {
      setShifts(null);
    }
  }, [enabled, today]);

  useEffect(() => {
    reload();
  }, [reload]);

  return { shifts, reload, today };
}
