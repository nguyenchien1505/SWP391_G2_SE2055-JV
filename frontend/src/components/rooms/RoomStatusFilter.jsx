import { ROOM_STATUS_ORDER, roomStatusMeta } from '../../pages/rooms/roomLabels';

/**
 * Số phòng theo 7 trạng thái, BẤM để lọc — design.md mục 5 ("Thẻ số liệu … bấm được để lọc").
 * Bấm lại trạng thái đang chọn thì bỏ lọc.
 *
 * Hai kiểu hiển thị, cùng một hành vi:
 *   - "cards": thẻ số liệu lớn cho trang danh sách (S-02)
 *   - "chips": chip gọn cho sơ đồ phòng (S-06), nhường chỗ cho lưới phòng
 *
 * @param counts   { AVAILABLE: 3, DIRTY: 2, … } — trạng thái thiếu coi như 0
 * @param selected mã trạng thái đang lọc, hoặc '' nếu không lọc
 * @param onSelect nhận mã trạng thái mới, hoặc '' để bỏ lọc
 */
export default function RoomStatusFilter({ counts = {}, total = 0, selected, onSelect, variant = 'cards' }) {
  const toggle = (status) => onSelect(selected === status ? '' : status);

  if (variant === 'chips') {
    return (
      <div className="status-chips" role="group" aria-label="Lọc theo trạng thái">
        <button
          type="button"
          className={`status-chip status-chip--all ${selected ? '' : 'is-selected'}`}
          onClick={() => onSelect('')}
          aria-pressed={!selected}
        >
          Tất cả <b>{total}</b>
        </button>
        {ROOM_STATUS_ORDER.map((status) => {
          const meta = roomStatusMeta(status);
          return (
            <button
              key={status}
              type="button"
              className={`status-chip room-tone--${meta.tone} ${selected === status ? 'is-selected' : ''}`}
              onClick={() => toggle(status)}
              aria-pressed={selected === status}
            >
              {meta.label} <b>{counts[status] ?? 0}</b>
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
