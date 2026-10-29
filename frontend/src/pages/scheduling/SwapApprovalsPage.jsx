import { useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { approveSwap, rejectSwap } from '../../api/scheduling';
import ConfirmDialog from '../../components/ConfirmDialog';
import SwapRequestList, { SwapPair } from '../../components/scheduling/SwapRequestList';
import { CLOSED_SWAP_STATUSES, swapShiftLabel } from './swapRequests';
import '../rooms/rooms.css';
import './scheduling.css';

const TABS = [
  { key: 'manager', label: 'Chờ duyệt', statuses: ['PENDING_MANAGER'], empty: 'Không có yêu cầu nào chờ bạn duyệt.' },
  {
    key: 'peer',
    label: 'Chờ đồng nghiệp',
    statuses: ['PENDING_PEER'],
    empty: 'Không có yêu cầu nào đang chờ đồng nghiệp trả lời.',
  },
  { key: 'closed', label: 'Đã xử lý', statuses: CLOSED_SWAP_STATUSES, empty: 'Chưa có yêu cầu nào đã xử lý.', paged: true },
];

const HISTORY_PAGE_SIZE = 20;

const nameOf = (party) => party.fullName ?? 'Nhân viên';

/**
 * Duyệt đổi ca — Quản lý chi nhánh, yêu cầu của khách sạn mình (BR-SCH-09, 18, 23, 26 — Milestone 2).
 * Yêu cầu chỉ tới tay quản lý sau khi đồng nghiệp đồng ý. Duyệt thì backend khóa hai ca, kiểm lại quy
 * định xếp ca rồi hoán đổi người; bị chặn hay yêu cầu vừa tự hủy thì hiện nguyên văn lý do.
 */
export default function SwapApprovalsPage() {
  const [tab, setTab] = useState('manager');
  const [banner, setBanner] = useState(null); // { type, text }
  const [confirming, setConfirming] = useState(null); // { request, action }
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState(0);

  const current = TABS.find((t) => t.key === tab);

  async function run({ request, action }) {
    setConfirming(null);
    setBanner(null);
    setBusy(true);
    const pair = `${nameOf(request.requester)} và ${nameOf(request.peer)}`;
    try {
      if (action === 'approve') {
        await approveSwap(request.id);
        setBanner({ type: 'success', text: `Đã duyệt: ${pair} đã đổi ca cho nhau trên lịch.` });
      } else {
        await rejectSwap(request.id);
        setBanner({ type: 'success', text: `Đã từ chối yêu cầu đổi ca giữa ${pair}.` });
      }
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không thực hiện được thao tác.') });
    } finally {
      setBusy(false);
      setVersion((v) => v + 1);
    }
  }

  function renderItem(request) {
    const ask = (action) => () => setConfirming({ request, action });
    return {
      title: `${nameOf(request.requester)} ⇄ ${nameOf(request.peer)}`,
      body: (
        <SwapPair
          rows={[
            { label: `${nameOf(request.requester)} (người gửi) đưa`, value: swapShiftLabel(request.requester.shift) },
            { label: `${nameOf(request.peer)} đưa`, value: swapShiftLabel(request.peer.shift) },
          ]}
        />
      ),
      actions: request.status === 'PENDING_MANAGER' && (
        <>
          <button type="button" className="btn btn--ghost" disabled={busy} onClick={ask('reject')}>
            Từ chối
          </button>
          <button type="button" className="btn btn--primary" disabled={busy} onClick={ask('approve')}>
            Duyệt
          </button>
        </>
      ),
    };
  }

  const request = confirming?.request;

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Lịch làm việc › Duyệt đổi ca</p>
          <h1>Duyệt đổi ca</h1>
          <p className="muted">
            Nhân viên tự thỏa thuận đổi ca với đồng nghiệp cùng vị trí; đồng nghiệp đồng ý thì yêu cầu tới đây.
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

      <div className="room-tabs" role="tablist" aria-label="Lọc yêu cầu đổi ca">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            aria-selected={tab === item.key}
            className={`room-tab ${tab === item.key ? 'is-active' : ''}`}
            onClick={() => setTab(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <section className="panel" key={tab}>
        <SwapRequestList
          box="LOCATION"
          statuses={current.statuses}
          pageSize={current.paged ? HISTORY_PAGE_SIZE : 100}
          version={version}
          emptyText={current.empty}
          renderItem={renderItem}
        />
      </section>

      {confirming?.action === 'approve' && (
        <ConfirmDialog
          title="Duyệt đổi ca?"
          message={
            <>
              <b>{nameOf(request.requester)}</b> sẽ làm {swapShiftLabel(request.peer.shift)}, còn{' '}
              <b>{nameOf(request.peer)}</b> làm {swapShiftLabel(request.requester.shift)}. Hệ thống kiểm lại quy định
              xếp ca trước khi đổi; việc dọn đang làm của ngày đó đi theo người nhận ca.
            </>
          }
          confirmLabel="Duyệt"
          confirmTone="primary"
          onCancel={() => setConfirming(null)}
          onConfirm={() => run(confirming)}
        />
      )}
      {confirming?.action === 'reject' && (
        <ConfirmDialog
          title="Từ chối đổi ca?"
          message={<>Hai ca giữ nguyên người làm. {nameOf(request.requester)} và {nameOf(request.peer)} sẽ thấy yêu cầu bị từ chối.</>}
          confirmLabel="Từ chối"
          onCancel={() => setConfirming(null)}
          onConfirm={() => run(confirming)}
        />
      )}
    </div>
  );
}
