import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { countPendingAdjustments, countPendingLeave, countPendingSwaps } from '../api/pendingCounts';
import { useAuth } from './AuthContext';

/**
 * Bộ đếm đơn chờ duyệt dùng chung cả app: số cạnh các mục duyệt trên menu trái, viên số trên tab «Chờ duyệt» của 3
 * màn duyệt và thẻ «Đơn chờ duyệt» trên dashboard Manager đều đọc từ đây, nên luôn ra cùng một số. Duyệt / từ chối ở
 * màn nào thì gọi `refresh()`; mỗi lần đổi trang cũng tải lại để thấy cả đơn mới vừa có người gửi.
 *
 * Chỉ người có quyền duyệt mới gọi API: Manager đếm cả 3 loại, Giám đốc chỉ đơn nghỉ (đơn của các Manager —
 * BR-SCH-08). Nhân viên và vai trò khác không gọi gì.
 */

const COUNTERS = {
  leave: countPendingLeave,
  swap: countPendingSwaps,
  adjustment: countPendingAdjustments,
};

function kindsFor(user) {
  if (!user || user.planRequired) return [];
  if (user.role === 'MANAGER') return ['leave', 'swap', 'adjustment'];
  if (user.role === 'DIRECTOR') return ['leave'];
  return [];
}

// Số gần nhất của người đang đăng nhập — trang bọc trong layout có thể dựng lại khi chuyển trang, giữ số cũ để viên
// số không nháy mất trong lúc chờ tải lại.
const lastKnown = { userId: null, counts: {} };

const PendingCountsContext = createContext(null);

export function PendingCountsProvider({ children }) {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const userId = user?.id ?? null;
  const kindsKey = kindsFor(user).join(',');

  const [counts, setCounts] = useState(() => (lastKnown.userId === userId ? lastKnown.counts : {}));
  // Bấm nhanh / chuyển trang liên tục: chỉ nhận kết quả của lần tải mới nhất.
  const latest = useRef(0);

  const refresh = useCallback(async () => {
    const kinds = kindsKey ? kindsKey.split(',') : [];
    if (kinds.length === 0) return;
    const requestId = ++latest.current;
    const results = await Promise.all(kinds.map((kind) => COUNTERS[kind]().then(
      (n) => [kind, n],
      () => null, // Lỗi mạng: giữ số cũ của loại này — viên số chỉ để nhắc việc, không được chặn màn.
    )));
    if (requestId !== latest.current) return;
    setCounts((prev) => {
      const next = { ...prev, ...Object.fromEntries(results.filter(Boolean)) };
      lastKnown.userId = userId;
      lastKnown.counts = next;
      return next;
    });
  }, [kindsKey, userId]);

  // Đổi người đăng nhập thì bỏ số của người trước.
  useEffect(() => {
    if (lastKnown.userId !== userId) {
      lastKnown.userId = userId;
      lastKnown.counts = {};
      setCounts({});
    }
  }, [userId]);

  useEffect(() => {
    refresh();
  }, [refresh, pathname]);

  const value = useMemo(() => ({ counts, refresh }), [counts, refresh]);
  return <PendingCountsContext.Provider value={value}>{children}</PendingCountsContext.Provider>;
}

const NO_COUNTS = { counts: {}, refresh: () => {} };

/** `{ counts: { leave, swap, adjustment }, refresh }` — loại chưa tải xong là undefined. */
export function usePendingCounts() {
  return useContext(PendingCountsContext) ?? NO_COUNTS;
}

/** "3", "99+"; chưa có số hoặc không có đơn chờ thì null để không hiện viên số. */
export function pendingBadge(count) {
  if (!count) return null;
  return count > 99 ? '99+' : String(count);
}
