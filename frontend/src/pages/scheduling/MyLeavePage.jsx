import { useCallback, useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { cancelLeaveRequest, fetchMyLeaveBalance, fetchMyLeaveRequests } from '../../api/leaveRequests';
import ConfirmDialog from '../../components/ConfirmDialog';
import FormModal from '../../components/FormModal';
import StatCard from '../../components/StatCard';
import LeaveRequestForm from '../../components/scheduling/LeaveRequestForm';
import LeaveTicket from '../../components/scheduling/LeaveTicket';
import { useAuth } from '../../context/AuthContext';
import { formatDate } from '../rooms/format';
import { formatLeaveRange } from './leaveLabels';
import '../rooms/rooms.css';
import './scheduling.css';
import './leave.css';

const PAGE_SIZE = 20;

/** `status` gửi lên backend; rỗng = mọi trạng thái. */
const TABS = [
  { key: '', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ duyệt' },
  { key: 'APPROVED', label: 'Đã duyệt' },
  { key: 'REJECTED', label: 'Từ chối' },
  { key: 'CANCELLED', label: 'Đã hủy' },
];

/**
 * Đơn xin nghỉ của tôi — nhân viên và quản lý khách sạn (BR-PERM-06; BR-SCH-06..08, BR-SCH-16, BR-SCH-27..31).
 *
 * Đầu trang là quỹ phép năm: phép tích lũy theo tháng làm việc, đơn đang chờ duyệt đã tính vào phần "còn lại"
 * (BR-SCH-28). Đơn của nhân viên do quản lý khách sạn duyệt, đơn của quản lý do giám đốc duyệt (BR-SCH-08).
 * Chỉ đơn còn chờ duyệt mới tự hủy được (BR-SCH-16).
 */
export default function MyLeavePage() {
  const { user } = useAuth();
  const approverLabel = user?.role === 'MANAGER' ? 'Giám đốc' : 'Quản lý khách sạn';

  const [balance, setBalance] = useState(null);
  const [balanceError, setBalanceError] = useState('');
  const [status, setStatus] = useState('');
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [creating, setCreating] = useState(false);
  const [cancelling, setCancelling] = useState(null); // đơn đang chờ xác nhận hủy
  const [banner, setBanner] = useState(null); // { type, text }

  const loadBalance = useCallback(() => {
    setBalanceError('');
    fetchMyLeaveBalance()
      .then(setBalance)
      .catch((err) => setBalanceError(readErrorMessage(err, 'Không tải được quỹ phép năm.')));
  }, []);

  const load = useCallback(async (nextPage) => {
    setLoading(true);
    setLoadError('');
    try {
      const data = await fetchMyLeaveRequests({ status, page: nextPage, size: PAGE_SIZE });
      setItems((prev) => (nextPage === 0 ? data.content ?? [] : [...prev, ...(data.content ?? [])]));
      setPage(nextPage);
      setTotalPages(data.totalPages ?? 0);
      setTotal(data.totalElements ?? 0);
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được danh sách đơn nghỉ.'));
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    loadBalance();
  }, [loadBalance]);

  useEffect(() => {
    load(0);
  }, [load]);

  function finishCreate(created) {
    setCreating(false);
    setBanner({
      type: 'success',
      text: `Đã gửi đơn nghỉ ${formatLeaveRange(created.fromDate, created.toDate)} — chờ ${approverLabel.toLowerCase()} duyệt.`,
    });
    loadBalance();
    load(0);
  }

  async function confirmCancel() {
    const target = cancelling;
    setCancelling(null);
    try {
      await cancelLeaveRequest(target.id);
      setBanner({ type: 'success', text: `Đã hủy đơn nghỉ ${formatLeaveRange(target.fromDate, target.toDate)}.` });
      loadBalance();
      load(0);
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không hủy được đơn nghỉ.') });
    }
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Lịch làm việc › Đơn xin nghỉ</p>
          <h1>Đơn xin nghỉ của tôi</h1>
          <p className="muted">
            Đơn do {approverLabel.toLowerCase()} duyệt. Duyệt xong, các ca từ ngày mai trong khoảng nghỉ tự gỡ khỏi
            lịch của bạn; ca của chính hôm nay quản lý sẽ xếp lại.
          </p>
        </div>
        <button type="button" className="btn btn--primary" onClick={() => setCreating(true)}>
          + Tạo đơn nghỉ
        </button>
      </div>

      {banner && (
        <div className={`alert alert--${banner.type}`} role="status">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">
            ×
          </button>
        </div>
      )}

      {balanceError && (
        <div className="alert alert--error" role="alert">
          {balanceError}
        </div>
      )}
      {balance && (
        <div className="stat-grid">
          <StatCard
            label={`Phép năm ${balance.year} còn lại`}
            value={`${balance.remainingDays} ngày`}
            note={`Đã tích lũy ${balance.entitledDays}/${balance.daysPerYear} · đã dùng ${balance.usedDays} · chờ duyệt ${balance.pendingDays}`}
            icon="🌴"
            tone="green"
          />
          <StatCard
            label="Phép năm sớm nhất từ"
            value={formatDate(balance.earliestAnnualStart)}
            note={`Phép năm gửi trước ít nhất ${balance.noticeDays} ngày; nghỉ không lương gửi được cả hôm nay`}
            icon="🗓"
            tone="blue"
          />
        </div>
      )}
      {balance && !balance.startWorkDate && (
        <div className="alert alert--info">
          Hồ sơ của bạn chưa có ngày bắt đầu làm việc nên chưa tính được quỹ phép năm — nhờ quản lý cập nhật hồ sơ.
          Trong lúc đó bạn vẫn gửi được đơn nghỉ không lương.
        </div>
      )}

      <section className="panel">
        <div className="panel__head">
          <h2>Đơn đã gửi</h2>
          {!loading && <span className="chip">{total} đơn</span>}
        </div>

        <div className="room-tabs" role="tablist" aria-label="Lọc theo trạng thái">
          {TABS.map((tab) => (
            <button
              key={tab.key || 'all'}
              type="button"
              role="tab"
              aria-selected={status === tab.key}
              className={`room-tab ${status === tab.key ? 'is-active' : ''}`}
              onClick={() => setStatus(tab.key)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {loadError && (
          <div className="alert alert--error" role="alert">
            {loadError}
          </div>
        )}
        {!loading && !loadError && items.length === 0 && (
          <div className="state state--empty">
            <p>Chưa có đơn nghỉ nào.</p>
          </div>
        )}

        <ul className="leave-list">
          {items.map((leave) => (
            <LeaveTicket
              key={leave.id}
              leave={leave}
              actions={leave.status === 'PENDING'
                ? [{ key: 'cancel', label: 'Hủy đơn', tone: 'ghost', onClick: () => setCancelling(leave) }]
                : []}
            />
          ))}
        </ul>

        {loading && <p className="state">Đang tải dữ liệu…</p>}
        {!loading && page + 1 < totalPages && (
          <button type="button" className="btn btn--ghost" onClick={() => load(page + 1)}>
            Xem thêm
          </button>
        )}
      </section>

      {creating && (
        <FormModal onClose={() => setCreating(false)}>
          <LeaveRequestForm
            balance={balance}
            approverLabel={approverLabel}
            onCreated={finishCreate}
            onCancel={() => setCreating(false)}
          />
        </FormModal>
      )}

      {cancelling && (
        <ConfirmDialog
          title="Hủy đơn nghỉ?"
          message={`Đơn nghỉ ${formatLeaveRange(cancelling.fromDate, cancelling.toDate)} sẽ chuyển sang «Đã hủy». Muốn nghỉ lại thì gửi đơn mới.`}
          confirmLabel="Hủy đơn"
          onConfirm={confirmCancel}
          onCancel={() => setCancelling(null)}
        />
      )}
    </div>
  );
}
