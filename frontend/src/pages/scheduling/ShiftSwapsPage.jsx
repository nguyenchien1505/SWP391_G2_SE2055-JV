import { useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { acceptSwap, cancelSwap, declineSwap } from '../../api/scheduling';
import ConfirmDialog from '../../components/ConfirmDialog';
import SwapRequestList, { SwapPair } from '../../components/scheduling/SwapRequestList';
import { CLOSED_SWAP_STATUSES, PENDING_SWAP_STATUSES, swapShiftLabel } from './swapRequests';
import '../rooms/rooms.css';
import './scheduling.css';

const TABS = [
  { box: 'RECEIVED', label: 'Gửi cho tôi' },
  { box: 'SENT', label: 'Tôi đã gửi' },
];

const HISTORY_PAGE_SIZE = 10;

/** Mỗi thao tác một lệnh gọi API; đều không hoàn tác được nên phải xác nhận (design.md mục 5). */
const ACTIONS = {
  accept: {
    call: acceptSwap,
    title: 'Đồng ý đổi ca?',
    confirmLabel: 'Đồng ý',
    tone: 'primary',
    message: (r) => (
      <>
        Bạn sẽ làm <b>{swapShiftLabel(r.requester.shift)}</b>, còn {r.requester.fullName} làm{' '}
        <b>{swapShiftLabel(r.peer.shift)}</b> của bạn. Hệ thống kiểm lại quy định xếp ca cho cả hai người;
        qua thì yêu cầu chờ quản lý duyệt.
      </>
    ),
    done: (r) => `Đã đồng ý đổi ca với ${r.requester.fullName}. Yêu cầu đang chờ quản lý duyệt.`,
  },
  decline: {
    call: declineSwap,
    title: 'Từ chối đổi ca?',
    confirmLabel: 'Từ chối',
    tone: 'danger',
    message: (r) => <>{r.requester.fullName} sẽ thấy yêu cầu bị từ chối và có thể hỏi người khác.</>,
    done: (r) => `Đã từ chối yêu cầu đổi ca của ${r.requester.fullName}.`,
  },
  cancel: {
    call: cancelSwap,
    title: 'Hủy yêu cầu đổi ca?',
    confirmLabel: 'Hủy yêu cầu',
    cancelLabel: 'Giữ yêu cầu',
    tone: 'danger',
    message: (r) => <>Yêu cầu gửi {r.peer.fullName} bị hủy, ca của bạn giữ nguyên.</>,
    done: (r) => `Đã hủy yêu cầu đổi ca với ${r.peer.fullName}.`,
  },
};

/**
 * Đổi ca của tôi — BR-SCH-09..11, 19, 23 (Milestone 2). Hai hộp: «Gửi cho tôi» (đồng nghiệp đề nghị
 * đổi ca — đồng ý hoặc từ chối) và «Tôi đã gửi» (theo dõi, hủy khi còn chờ). Yêu cầu mới gửi bằng nút
 * «Đổi ca» trên Lịch cá nhân. Mỗi hộp chia phần đang chờ và phần đã xử lý (xem thêm theo trang).
 */
export default function ShiftSwapsPage() {
  const [box, setBox] = useState('RECEIVED');
  const [banner, setBanner] = useState(null); // { type, text }
  const [confirming, setConfirming] = useState(null); // { request, action }
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState(0);

  async function run({ request, action }) {
    setConfirming(null);
    setBanner(null);
    setBusy(true);
    try {
      await ACTIONS[action].call(request.id);
      setBanner({ type: 'success', text: ACTIONS[action].done(request) });
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không thực hiện được thao tác.') });
    } finally {
      setBusy(false);
      setVersion((v) => v + 1);
    }
  }

  function renderItem(request) {
    const received = box === 'RECEIVED';
    const other = received ? request.requester : request.peer;
    const give = received ? request.peer.shift : request.requester.shift;
    const take = received ? request.requester.shift : request.peer.shift;
    const ask = (action) => () => setConfirming({ request, action });
    let actions = null;
    if (received && request.status === 'PENDING_PEER') {
      actions = (
        <>
          <button type="button" className="btn btn--ghost" disabled={busy} onClick={ask('decline')}>
            Từ chối
          </button>
          <button type="button" className="btn btn--primary" disabled={busy} onClick={ask('accept')}>
            Đồng ý
          </button>
        </>
      );
    } else if (!received && PENDING_SWAP_STATUSES.includes(request.status)) {
      actions = (
        <button type="button" className="btn btn--danger-ghost" disabled={busy} onClick={ask('cancel')}>
          Hủy yêu cầu
        </button>
      );
    }
    return {
      title: received ? `${other.fullName ?? 'Đồng nghiệp'} đề nghị đổi ca` : `Gửi ${other.fullName ?? 'đồng nghiệp'}`,
      body: (
        <SwapPair
          rows={[
            { label: 'Bạn đưa', value: swapShiftLabel(give) },
            { label: 'Bạn nhận', value: swapShiftLabel(take) },
          ]}
        />
      ),
      actions,
    };
  }

  const current = ACTIONS[confirming?.action];

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Lịch làm việc › Đổi ca</p>
          <h1>Đổi ca</h1>
          <p className="muted">
            Muốn đổi ca thì bấm «Đổi ca» trên ca chưa bắt đầu ở{' '}
            <Link className="inline-link" to="/lich-ca-nhan">Lịch cá nhân</Link>. Đồng nghiệp đồng ý rồi quản lý
            duyệt thì hai người đổi ca cho nhau.
          </p>
        </div>
      </div>

      {banner && (
        <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="status">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">
            ×
          </button>
        </div>
      )}

      <div className="room-tabs" role="tablist" aria-label="Hộp yêu cầu đổi ca">
        {TABS.map((tab) => (
          <button
            key={tab.box}
            type="button"
            role="tab"
            aria-selected={box === tab.box}
            className={`room-tab ${box === tab.box ? 'is-active' : ''}`}
            onClick={() => setBox(tab.box)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <section className="panel swap-section" key={`${box}-pending`}>
        <div className="panel__head">
          <h2>Đang chờ</h2>
        </div>
        <SwapRequestList
          box={box}
          statuses={PENDING_SWAP_STATUSES}
          version={version}
          emptyText={box === 'RECEIVED' ? 'Không có yêu cầu nào đang chờ bạn trả lời.' : 'Bạn không có yêu cầu nào đang chờ.'}
          renderItem={renderItem}
        />
      </section>

      <section className="panel swap-section" key={`${box}-closed`}>
        <div className="panel__head">
          <h2>Đã xử lý</h2>
        </div>
        <SwapRequestList
          box={box}
          statuses={CLOSED_SWAP_STATUSES}
          pageSize={HISTORY_PAGE_SIZE}
          version={version}
          emptyText="Chưa có yêu cầu nào đã xử lý."
          renderItem={renderItem}
        />
      </section>

      {current && (
        <ConfirmDialog
          title={current.title}
          message={current.message(confirming.request)}
          confirmLabel={current.confirmLabel}
          cancelLabel={current.cancelLabel}
          confirmTone={current.tone}
          onCancel={() => setConfirming(null)}
          onConfirm={() => run(confirming)}
        />
      )}
    </div>
  );
}
