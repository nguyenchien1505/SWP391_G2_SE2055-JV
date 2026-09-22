import { useEffect, useState } from 'react';
import { fetchLocations } from '../../api/locations';
import { fetchRoomTypes } from '../../api/rooms';

/**
 * Danh sách khách sạn của Tenant — chỉ GIÁM ĐỐC cần: để chọn Location cần xem và để hiện tên
 * khách sạn thay cho id. Manager/Staff đã bị backend ép về Location của mình nên không gọi.
 *
 * Lỗi tải danh mục không chặn màn hình chính: trả mảng rỗng, trang vẫn hiện phòng (chỉ thiếu
 * tên khách sạn).
 *
 * @param enabled false thì không gọi API (dùng cho vai trò không cần) và trả ngay mảng rỗng
 * @returns {Array|null} null nghĩa là ĐANG TẢI — phân biệt với "Tenant chưa có khách sạn nào" ([])
 */
export function useTenantLocations(enabled) {
  const [locations, setLocations] = useState(null);

  useEffect(() => {
    if (!enabled) return undefined;
    let cancelled = false;
    // Số Location bị chặn bởi quota gói dịch vụ nên một trang 100 là đủ.
    fetchLocations({ page: 0, size: 100 })
      .then((data) => !cancelled && setLocations(data.content ?? []))
      .catch(() => !cancelled && setLocations([]));
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  return enabled ? locations : NONE;
}

/** Hằng rỗng dùng chung — trả cùng một mảng mỗi lần render để không kích hoạt effect thừa. */
const NONE = [];

/**
 * Loại phòng cho ô lọc — lấy CẢ loại đã ẩn (BR-ORG-14) vì phòng cũ vẫn có thể thuộc loại đó.
 * Chỉ Giám đốc / Manager gọi được API này (Staff nhận 403).
 */
export function useRoomTypes(enabled) {
  const [roomTypes, setRoomTypes] = useState([]);

  useEffect(() => {
    if (!enabled) return undefined;
    let cancelled = false;
    fetchRoomTypes({ includeInactive: true })
      .then((data) => !cancelled && setRoomTypes(data ?? []))
      .catch(() => !cancelled && setRoomTypes([]));
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  return roomTypes;
}
