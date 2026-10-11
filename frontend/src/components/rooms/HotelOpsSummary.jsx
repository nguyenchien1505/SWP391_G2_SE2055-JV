import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { fetchTasks } from '../../api/housekeeping';
import { fetchRoomStatusSummary } from '../../api/rooms';
import { fetchShifts } from '../../api/scheduling';
import { fetchStaffDirectory } from '../../api/users';
import { formatDate, todayIso } from '../../pages/rooms/format';
import StatCard from '../StatCard';
import '../../pages/rooms/rooms.css';

/**
 * Số liệu vận hành hôm nay của MỘT khách sạn — phần dashboard Manager theo BR-DASH-03: phòng theo
 * trạng thái, nhân viên có ca hôm nay / tổng nhân viên, việc dọn theo trạng thái, đơn chờ duyệt.
 *
 * Đặt ở đầu /tong-quan, trên dashboard tài sản, chỉ cho Manager. Phạm vi do backend ép về khách sạn
 * của người đăng nhập. Đơn nghỉ phép / đổi ca chưa có module nên ô đó ghi "—" thay vì số 0 giả.
 */
export default function HotelOpsSummary() {
  const today = todayIso();
  const [stats, setStats] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    // Chỉ cần tổng số việc của mỗi trạng thái: lấy trang 1 phần tử và đọc totalElements.
    const countTasks = (params) => fetchTasks({ ...params, size: 1 }).then((page) => page.totalElements ?? 0);
    Promise.all([
      fetchRoomStatusSummary(),
      fetchShifts({ from: today, to: today }),
      fetchStaffDirectory({ size: 500 }),
      countTasks({ status: 'UNASSIGNED' }),
      countTasks({ status: 'IN_PROGRESS' }),
      countTasks({ status: 'PENDING_INSPECTION' }),
      countTasks({ status: 'COMPLETED', assignedDate: today }),
    ])
      .then(([rooms, shifts, people, unassigned, inProgress, pending, doneToday]) => {
        if (cancelled) return;
        // BR-DASH-03 đếm Staff. Quản lý không có ca (chốt 05/10/2026) — vẫn lọc theo role cho ca cũ.
        const staffIds = new Set(
          people.filter((p) => p.role === 'STAFF' && p.status === 'ACTIVE').map((p) => p.id),
        );
        const onShift = new Set(shifts.map((s) => s.staffId).filter((id) => staffIds.has(id)));
        setStats({
          rooms,
          staffTotal: staffIds.size,
          staffOnShift: onShift.size,
          unassigned,
          inProgress,
          pending,
          doneToday,
        });
      })
      .catch((err) => !cancelled && setError(readErrorMessage(err, 'Không tải được số liệu vận hành.')));
    return () => {
      cancelled = true;
    };
  }, [today]);

  const count = (status) => stats?.rooms?.counts?.[status] ?? 0;

  return (
    <section className="ops-summary" aria-labelledby="ops-summary-title">
      <div className="ops-summary__head">
        <h2 id="ops-summary-title">Vận hành hôm nay</h2>
        <span className="muted">{formatDate(today)}</span>
      </div>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}
      {!stats && !error && <p className="state">Đang tải số liệu vận hành…</p>}

      {stats && (
        <div className="stat-grid">
          <Link to="/so-do-phong" className="ops-card-link">
            <StatCard
              label="Phòng sẵn sàng"
              value={`${count('AVAILABLE')}/${stats.rooms.total ?? 0}`}
              note={`Có khách ${count('OCCUPIED')} · Chờ dọn ${count('DIRTY')} · Đang dọn ${count('CLEANING')} · Chờ kiểm tra ${count('INSPECTION')}`}
              icon="🛏"
              tone="blue"
            />
          </Link>
          <Link to="/xep-lich" className="ops-card-link">
            <StatCard
              label="Nhân viên có ca hôm nay"
              value={`${stats.staffOnShift}/${stats.staffTotal}`}
              note="Xếp ca ở màn Xếp lịch làm việc"
              icon="🗓"
              tone="indigo"
            />
          </Link>
          <Link to="/don-phong" className="ops-card-link">
            <StatCard
              label="Việc dọn chờ giao"
              value={stats.unassigned}
              note={`Đang làm ${stats.inProgress} · Chờ kiểm tra ${stats.pending} · Xong hôm nay ${stats.doneToday}`}
              icon="🧹"
              tone="amber"
            />
          </Link>
          <StatCard
            label="Đơn chờ duyệt"
            value="—"
            note="Chưa có module nghỉ phép / đổi ca"
            icon="📨"
            tone="green"
          />
        </div>
      )}
    </section>
  );
}
