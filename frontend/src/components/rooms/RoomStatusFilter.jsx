import { ROOM_STATUS_ORDER, roomStatusMeta } from '../../pages/rooms/roomLabels';

/**
 * Số phòng theo 7 trạng thái, BẤM để lọc — design.md mục 5 ("Thẻ số liệu … bấm được để lọc").
 * Bấm lại trạng thái đang chọn thì bỏ lọc.
 *
 * Hai kiểu hiển thị, cùng một hành vi:
 *   - "cards": thẻ số liệu lớn cho trang danh sách (S-02)
 *   - "tiles": dải thẻ có biểu tượng + tỉ lệ cho sơ đồ phòng (S-06); thêm ô "Tất cả" đứng đầu,
 *     trên điện thoại cuộn ngang thành MỘT hàng để nhường chỗ cho lưới phòng
 *
 * @param counts   { AVAILABLE: 3, DIRTY: 2, … } — trạng thái thiếu coi như 0
 * @param selected mã trạng thái đang lọc, hoặc '' nếu không lọc
 * @param onSelect nhận mã trạng thái mới, hoặc '' để bỏ lọc
 */
export default function RoomStatusFilter({ counts = {}, total = 0, selected, onSelect, variant = 'cards' }) {
  const toggle = (status) => onSelect(selected === status ? '' : status);

  if (variant === 'tiles') {
    return (
      <div className="status-tiles" role="group" aria-label="Lọc theo trạng thái">
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

  return (
    <div className="room-stat-grid" role="group" aria-label="Lọc theo trạng thái">
      {ROOM_STATUS_ORDER.map((status) => {
        const meta = roomStatusMeta(status);
        return (
          <button
            key={status}
            type="button"
            className={`stat-card room-stat room-tone--${meta.tone} ${selected === status ? 'is-selected' : ''}`}
            onClick={() => toggle(status)}
            aria-pressed={selected === status}
          >
            <span className="stat-card__label">{meta.label}</span>
            <span className="stat-card__value">{counts[status] ?? 0}</span>
          </button>
        );
      })}
    </div>
  );
}
