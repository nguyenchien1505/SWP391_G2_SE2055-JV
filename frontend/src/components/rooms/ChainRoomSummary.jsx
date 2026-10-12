import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { fetchLocations } from '../../api/locations';
import { fetchRoomStatusSummary } from '../../api/rooms';
import { ROOM_STATUS_ORDER, roomStatusMeta } from '../../pages/rooms/roomLabels';
import RoomStatusFilter from './RoomStatusFilter';
import '../../pages/rooms/rooms.css';

/**
 * Phòng toàn chuỗi — phần dashboard Giám đốc theo BR-DASH-02: tổng phòng toàn Tenant theo 7 trạng
 * thái và so sánh nhanh giữa các khách sạn. Cùng chỗ với khối "Vận hành hôm nay" của Manager
 * (`HotelOpsSummary`) ở đầu /tong-quan.
 *
 * Sơ đồ phòng vẫn là của MỘT khách sạn (hai khách sạn đều có "phòng 102", trộn chung dễ nhầm); bấm
 * tên khách sạn ở đây là mở sơ đồ của khách sạn đó (`/so-do-phong?locationId=…`).
 *
 * Dòng số liệu đầu gọi `GET /rooms/status-summary` không truyền khách sạn (backend tính cả Tenant);
 * mỗi khách sạn gọi thêm một lần có `locationId`. Số khách sạn bị chặn bởi hạn mức gói dịch vụ nên
 * chỉ vài lời gọi chạy song song — chuỗi lớn hơn thì mới cần API đếm gộp theo khách sạn.
 */
export default function ChainRoomSummary() {
  const [data, setData] = useState(null); // { chain: { total, counts }, hotels: [{ id, name, total, counts }] }
  const [error, setError] = useState('');
  // Trạng thái đang chọn trên dải số liệu: xếp khách sạn có nhiều phòng ở trạng thái đó lên đầu.
  const [sortStatus, setSortStatus] = useState('');

  useEffect(() => {
    let cancelled = false;
    // Số Location bị chặn bởi quota gói dịch vụ nên một trang 100 là đủ.
    fetchLocations({ page: 0, size: 100 })
      .then(async (page) => {
        const locations = page.content ?? [];
        const [chain, ...perHotel] = await Promise.all([
          fetchRoomStatusSummary(),
          ...locations.map((location) => fetchRoomStatusSummary({ locationId: location.id })),
        ]);
        if (cancelled) return;
        setData({
          chain,
          hotels: locations.map((location, i) => ({
            id: location.id,
            name: location.name,
            total: perHotel[i].total ?? 0,
            counts: perHotel[i].counts ?? {},
          })),
        });
      })
      .catch((err) => !cancelled && setError(readErrorMessage(err, 'Không tải được số liệu phòng toàn chuỗi.')));
    return () => {
      cancelled = true;
    };
  }, []);

  // Không chọn trạng thái: giữ thứ tự tên khách sạn như backend trả. Có chọn: nhiều phòng nhất lên đầu.
  const hotels = useMemo(() => {
    if (!data) return [];
    if (!sortStatus) return data.hotels;
    const count = (hotel) => hotel.counts[sortStatus] ?? 0;
    return [...data.hotels].sort((a, b) => count(b) - count(a) || a.name.localeCompare(b.name, 'vi'));
  }, [data, sortStatus]);

  return (
    <section className="ops-summary chain-rooms" aria-labelledby="chain-rooms-title">
      <div className="ops-summary__head">
        <h2 id="chain-rooms-title">Phòng toàn chuỗi</h2>
        {data && (
          <span className="muted">
            {data.chain.total ?? 0} phòng · {data.hotels.length} khách sạn
          </span>
        )}
      </div>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}
      {!data && !error && <p className="state">Đang tải số liệu phòng…</p>}

      {data && (
        <>
          <RoomStatusFilter
            counts={data.chain.counts}
            total={data.chain.total ?? 0}
            selected={sortStatus}
            onSelect={setSortStatus}
            groupLabel="Xếp khách sạn theo trạng thái phòng"
          />
          {data.hotels.length === 0 ? (
            <p className="state">Chuỗi chưa có khách sạn nào.</p>
          ) : (
            <>
              <p className="muted chain-rooms__hint">
                {sortStatus
                  ? `Khách sạn nhiều phòng «${roomStatusMeta(sortStatus).label}» nhất xếp trên cùng.`
                  : 'Bấm một trạng thái để xếp khách sạn có nhiều phòng ở trạng thái đó lên trên cùng.'}
              </p>
              <ul className="chain-rooms__list">
                {hotels.map((hotel) => (
                  <ChainHotelRow key={hotel.id} hotel={hotel} highlight={sortStatus} />
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </section>
  );
}

/**
 * Một khách sạn: thanh tỉ lệ 7 trạng thái + số phòng từng trạng thái. Chỉ hiện trạng thái có phòng,
 * riêng trạng thái đang được chọn để xếp thì hiện cả khi bằng 0 để so sánh được.
 */
function ChainHotelRow({ hotel, highlight }) {
  const mapLink = `/so-do-phong?locationId=${hotel.id}`;
  const shown = ROOM_STATUS_ORDER.filter((status) => (hotel.counts[status] ?? 0) > 0 || status === highlight);
  const summary = ROOM_STATUS_ORDER.filter((status) => hotel.counts[status] > 0)
    .map((status) => `${roomStatusMeta(status).label} ${hotel.counts[status]}`)
    .join(', ');

  return (
    <li className="chain-hotel">
      <div className="chain-hotel__head">
        <Link to={mapLink} className="chain-hotel__name">
          {hotel.name}
        </Link>
        <span className="muted">{hotel.total} phòng</span>
        <Link to={mapLink} className="link-btn">
          Xem sơ đồ ›
        </Link>
      </div>

      {hotel.total === 0 ? (
        <p className="muted chain-hotel__empty">Chưa có phòng.</p>
      ) : (
        <>
          <div className="chain-hotel__bar" role="img" aria-label={`Tỉ lệ trạng thái phòng: ${summary}`}>
            {ROOM_STATUS_ORDER.filter((status) => hotel.counts[status] > 0).map((status) => (
              <span
                key={status}
                className={`chain-hotel__seg room-tone--${roomStatusMeta(status).tone}`}
                style={{ flexGrow: hotel.counts[status] }}
              />
            ))}
          </div>
          <ul className="chain-hotel__counts">
            {shown.map((status) => {
              const meta = roomStatusMeta(status);
              return (
                <li
                  key={status}
                  className={`chain-hotel__count room-tone--${meta.tone} ${status === highlight ? 'is-selected' : ''}`}
                >
                  {meta.label} <b>{hotel.counts[status] ?? 0}</b>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </li>
  );
}
