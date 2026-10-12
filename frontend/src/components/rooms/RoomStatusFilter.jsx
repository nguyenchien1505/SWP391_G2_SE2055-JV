import { ROOM_STATUS_ORDER, roomStatusMeta } from '../../pages/rooms/roomLabels';

/**
 * Số phòng theo 7 trạng thái, BẤM để lọc — design.md mục 5 ("Thẻ số liệu … bấm được để lọc").
 * Bấm lại trạng thái đang chọn thì bỏ lọc.
 *
 * Dải thẻ có biểu tượng + tỉ lệ, ô "Tất cả" đứng đầu; trên điện thoại cuộn ngang thành MỘT hàng để
 * nhường chỗ cho lưới phòng. Dùng ở sơ đồ phòng (S-06, cả dạng Danh sách — S-02 cũ) và khối "Phòng
 * toàn chuỗi" của Giám đốc. Kiểu thẻ lớn "cards" của màn Danh sách phòng cũ đã bỏ cùng màn đó.
 *
 * @param counts   { AVAILABLE: 3, DIRTY: 2, … } — trạng thái thiếu coi như 0
 * @param selected mã trạng thái đang lọc, hoặc '' nếu không lọc
 * @param onSelect nhận mã trạng thái mới, hoặc '' để bỏ lọc
 * @param groupLabel nhãn đọc cho trình đọc màn hình — đổi khi bấm không phải để lọc (dashboard Giám
 *                   đốc dùng để XẾP khách sạn)
 */
export default function RoomStatusFilter({
  counts = {},
  total = 0,
  selected,
  onSelect,
  groupLabel = 'Lọc theo trạng thái',
}) {
  const toggle = (status) => onSelect(selected === status ? '' : status);

  return (
    <div className="status-tiles" role="group" aria-label={groupLabel}>
      <button
        type="button"
        className={`status-tile status-tile--all ${selected ? '' : 'is-selected'}`}
        onClick={() => onSelect('')}
        aria-pressed={!selected}
      >
        <span className="status-tile__label">
          <span className="material-symbols-outlined" aria-hidden="true">apps</span>
          Tất cả
        </span>
        <span className="status-tile__value">{total}</span>
      </button>
      {ROOM_STATUS_ORDER.map((status) => {
        const meta = roomStatusMeta(status);
        const count = counts[status] ?? 0;
        return (
          <button
            key={status}
            type="button"
            className={`status-tile room-tone--${meta.tone} ${selected === status ? 'is-selected' : ''}`}
            onClick={() => toggle(status)}
            aria-pressed={selected === status}
          >
            <span className="status-tile__label">
              <span className="material-symbols-outlined" aria-hidden="true">{meta.icon}</span>
              {meta.label}
            </span>
            <span className="status-tile__value">
              {count}
              <small>{total > 0 ? `${Math.round((count / total) * 100)}%` : '0%'}</small>
            </span>
          </button>
        );
      })}
    </div>
  );
}
