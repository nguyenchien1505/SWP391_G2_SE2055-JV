import { useCallback, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { changeRoomStatus } from '../../api/rooms';
import { INSTANT } from './roomActions';

/**
 * Chạy một thao tác đổi trạng thái phòng — dùng chung cho S-15 Sơ đồ phòng và S-04 Chi tiết phòng.
 *
 * Hai màn hình đó gọi cùng một API với cùng cách xử lý lỗi, nên logic nằm ở đây; màn hình chỉ còn
 * lo phần hiển thị (cập nhật thẻ nào, hiện banner ở đâu).
 *
 * Ba kiểu tương tác, phân biệt bằng `action.flow` (xem `roomActions.js`):
 *   - `INSTANT`  — gọi API ngay khi bấm (đặt trước, check-in, khách đã đến).
 *   - `CONFIRM` / `REASON` — mở `RoomActionDialog` trước, người dùng xác nhận hoặc chọn lý do.
 *   - `LOCK`     — mở `LockRoomModal` (F2), hộp thoại đó tự gọi API của nó.
 *
 * Cả ba đều kết thúc ở `finish(phòng mới)` nên màn hình chỉ cần một chỗ xử lý thành công.
 *
 * @param onChanged (phòng mới, phòng trước đó) — cập nhật màn hình
 * @param onError   câu lỗi tiếng Việt của backend, chỉ dùng cho nhánh INSTANT (các nhánh khác
 *                  hiện lỗi ngay trong hộp thoại đang mở)
 */
export function useRoomAction({ onChanged, onError }) {
  // Thao tác đang chờ người dùng xác nhận. null = không có hộp thoại nào mở.
  const [pending, setPending] = useState(null); // { room, action }
  const [running, setRunning] = useState(false);

  const start = useCallback(
    async (room, action) => {
      if (action.flow !== INSTANT) {
        setPending({ room, action });
        return;
      }
      // Chặn bấm hai lần khi mạng chậm — nút INSTANT không có trạng thái "đang lưu" riêng.
      if (running) return;
      setRunning(true);
      try {
        onChanged(await changeRoomStatus(room.id, { targetStatus: action.targetStatus }), room);
      } catch (err) {
        onError(readErrorMessage(err, 'Không đổi được trạng thái phòng.'));
      } finally {
        setRunning(false);
      }
    },
    [running, onChanged, onError],
  );

  /** Hộp thoại báo đã đổi xong: phòng mới lấy thẳng từ response, không phải tải lại. */
  const finish = useCallback(
    (updated) => {
      onChanged(updated, pending?.room);
      setPending(null);
    },
    [onChanged, pending],
  );

  const cancel = useCallback(() => setPending(null), []);

  return { pending, running, start, finish, cancel };
}
