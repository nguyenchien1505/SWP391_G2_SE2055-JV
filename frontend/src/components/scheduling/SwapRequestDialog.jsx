import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { createSwapRequest, fetchSwapCandidates } from '../../api/scheduling';
import { formatShortDateTime } from '../../pages/rooms/format';
import { formatHours, formatLongDate } from '../../pages/scheduling/scheduleFormat';
import { swapShiftLabel } from '../../pages/scheduling/swapRequests';
import { BlockedAlert, useCloseOnEscape } from './shiftFields';

/**
 * Gửi yêu cầu đổi ca — BR-SCH-09, 12, 19, 25 (Milestone 2): chọn MỘT ca của đồng nghiệp để đổi lấy ca
 * `shift` của mình. Backend lọc sẵn danh sách: cùng khách sạn, cùng vị trí và cùng quyền nghiệp vụ, ca
 * chưa bắt đầu, chưa nằm trong yêu cầu đang chờ nào, trong 31 ngày tới. Quy định xếp ca kiểm khi gửi
 * (BR-SCH-10); bị chặn thì hộp thoại hiện nguyên văn lý do, có ghi ai vi phạm.
 *
 * @param onSent nhận câu thông báo sau khi gửi xong; trang tự đóng hộp thoại
 */
export default function SwapRequestDialog({ shift, onClose, onSent }) {
  const [candidates, setCandidates] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [personId, setPersonId] = useState('');
  const [peerShiftId, setPeerShiftId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useCloseOnEscape(onClose, busy);

  useEffect(() => {
    let cancelled = false;
    fetchSwapCandidates(shift.id)
      .then((list) => !cancelled && setCandidates(list))
      .catch((err) => !cancelled && setLoadError(readErrorMessage(err, 'Không tải được danh sách ca để đổi.')));
    return () => {
      cancelled = true;
    };
  }, [shift.id]);

  // Lọc theo đồng nghiệp khi danh sách dài (vài người × 31 ngày).
  const people = useMemo(() => {
    const names = new Map((candidates ?? []).map((c) => [c.staffId, c.fullName]));
    return [...names]
      .map(([id, fullName]) => ({ id, fullName }))
      .sort((a, b) => a.fullName.localeCompare(b.fullName, 'vi'));
  }, [candidates]);
  const shown = (candidates ?? []).filter((c) => !personId || c.staffId === personId);
  const chosen = (candidates ?? []).find((c) => c.shift.id === peerShiftId);

  async function send() {
    setBusy(true);
    setError('');
    try {
      const request = await createSwapRequest({ requesterShiftId: shift.id, peerShiftId });
      onSent(`Đã gửi yêu cầu đổi ca cho ${chosen.fullName} — hạn trả lời ${formatShortDateTime(request.expiresAt)}. `
        + 'Đồng nghiệp đồng ý xong còn chờ quản lý duyệt.');
    } catch (err) {
      setError(readErrorMessage(err, 'Không gửi được yêu cầu đổi ca.'));
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="swap-dialog-title">
      <div className="modal modal--form form">
        <button type="button" className="modal__close" onClick={onClose} aria-label="Đóng" disabled={busy}>
          ✕
        </button>
        <header className="form__head">
          <span className="form__icon" aria-hidden="true">⇄</span>
          <div>
            <h2 id="swap-dialog-title">Đổi ca</h2>
            <p className="muted">Đồng nghiệp đồng ý, quản lý duyệt xong thì hai người đổi ca cho nhau.</p>
          </div>
        </header>

        <div className="readonly-box">
          <small>Ca của bạn</small>
          <b>
            {swapShiftLabel(shift)} · {formatHours(shift.durationHours)}
          </b>
          <small>{formatLongDate(shift.shiftDate)}</small>
        </div>

        {loadError && (
          <div className="alert alert--error" role="alert">
            {loadError}{' '}
            <Link className="inline-link" to="/doi-ca">
              Xem yêu cầu đổi ca
            </Link>
          </div>
        )}
        {!candidates && !loadError && <p className="state">Đang tìm ca của đồng nghiệp…</p>}

        {candidates?.length === 0 && (
          <div className="state state--empty">
            <p>Chưa có ca nào để đổi.</p>
            <p className="muted">
              Chỉ đổi được với đồng nghiệp cùng vị trí và cùng quyền (Lễ tân / Dọn dẹp), ca chưa bắt đầu trong
              31 ngày tới và chưa nằm trong yêu cầu đổi ca khác.
            </p>
          </div>
        )}

        {candidates?.length > 0 && (
          <section className="dialog-section" aria-labelledby="swap-pick-title">
            <h3 id="swap-pick-title">Ca muốn nhận</h3>
            {people.length > 1 && (
              <label className="field" htmlFor="swap-person">
                <span className="field__label">Đồng nghiệp</span>
                <select
                  id="swap-person"
                  value={personId}
                  onChange={(e) => {
                    setPersonId(e.target.value);
                    setPeerShiftId('');
                  }}
                >
                  <option value="">Tất cả ({candidates.length} ca)</option>
                  {people.map((person) => (
                    <option key={person.id} value={person.id}>
                      {person.fullName}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <div className="swap-options" role="radiogroup" aria-labelledby="swap-pick-title">
              {shown.map((candidate) => (
                <label
                  key={candidate.shift.id}
                  className={`swap-option ${peerShiftId === candidate.shift.id ? 'is-selected' : ''}`}
                >
                  <input
                    type="radio"
                    name="swap-peer-shift"
                    value={candidate.shift.id}
                    checked={peerShiftId === candidate.shift.id}
                    onChange={() => setPeerShiftId(candidate.shift.id)}
                  />
                  <span>
                    <b>{swapShiftLabel(candidate.shift)}</b>
                    <small>
                      {candidate.fullName} · {formatHours(candidate.shift.durationHours)}
                    </small>
                  </span>
                </label>
              ))}
            </div>
          </section>
        )}

        <BlockedAlert message={error} title="Chưa gửi được yêu cầu đổi ca" />

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={busy}>
            Đóng
          </button>
          <button type="button" className="btn btn--primary" onClick={send} disabled={!chosen || busy}>
            {busy ? 'Đang kiểm tra quy định…' : 'Gửi yêu cầu'}
          </button>
        </div>
      </div>
    </div>
  );
}
