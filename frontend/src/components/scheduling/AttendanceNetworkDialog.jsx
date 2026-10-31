import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import {
  addAttendanceNetwork,
  fetchAttendanceNetwork,
  removeAttendanceNetwork,
  setAttendanceIpMode,
} from '../../api/scheduling';

/** Ba chế độ kiểm IP — gợi ý chạy «Cảnh báo» trước để biết IP thật của khách sạn. */
const MODES = [
  { value: 'OFF', label: 'Tắt', hint: 'Không kiểm IP — vào ca ở đâu cũng được.' },
  {
    value: 'WARN',
    label: 'Cảnh báo',
    hint: 'Vẫn cho vào ca, nhưng ca chấm ngoài mạng khách sạn bị đánh dấu. Nên chạy 1–2 tuần trước khi chặn.',
  },
  { value: 'ENFORCE', label: 'Bắt buộc', hint: 'Phải dùng wifi khách sạn mới vào / kết thúc ca được.' },
];

/** Khớp @Size của AttendanceNetworkRequest. */
const CIDR_MAX = 50;
const LABEL_MAX = 100;

/**
 * Mạng chấm công của khách sạn (chấm công M2, chốt 01/11/2026) — Manager khai IP / dải IP mạng khách sạn và
 * chọn chế độ kiểm khi nhân viên tự vào / ra ca. Quản lý nhập giờ không bị kiểm.
 *
 * Không ai biết IP mạng nhà mình là gì, nên có nút «Thêm IP mạng hiện tại» — đứng ở khách sạn, dùng wifi
 * khách sạn rồi bấm. Nhà mạng cấp cả IPv4 lẫn IPv6 thì nên mở trang này từ vài máy để thêm đủ cả hai.
 *
 * @param onChanged () — đổi cài đặt xong, trang cha tải lại bảng (cờ «ngoài mạng»)
 */
export default function AttendanceNetworkDialog({ onClose, onChanged }) {
  const [settings, setSettings] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [cidr, setCidr] = useState('');
  const [label, setLabel] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchAttendanceNetwork()
      .then((data) => !cancelled && setSettings(data))
      .catch((err) => !cancelled && setLoadError(readErrorMessage(err, 'Không tải được cài đặt mạng chấm công.')));
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !busy && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [busy, onClose]);

  async function run(action, fallback) {
    setBusy(true);
    setError('');
    try {
      setSettings(await action());
      onChanged();
      return true;
    } catch (err) {
      setError(readErrorMessage(err, fallback));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function handleAdd(e) {
    e.preventDefault();
    if (cidr.trim() === '') return;
    if (await run(() => addAttendanceNetwork({ cidr: cidr.trim(), label: label.trim() }), 'Không thêm được IP.')) {
      setCidr('');
      setLabel('');
    }
  }

  const noNetworks = settings && settings.networks.length === 0;

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="att-network-title"
         onClick={() => !busy && onClose()}>
      <div className="modal room-modal" onClick={(e) => e.stopPropagation()}>
        <h2 id="att-network-title">Mạng chấm công của khách sạn</h2>

        <div className="modal__body">
          {loadError && <div className="alert alert--error" role="alert">{loadError}</div>}
          {!settings && !loadError && <p className="muted">Đang tải…</p>}

          {settings && (
            <>
              <fieldset className="att-network__modes" disabled={busy}>
                <legend className="field__label">Kiểm IP khi nhân viên tự vào / kết thúc ca</legend>
                {MODES.map((mode) => (
                  <label key={mode.value} className={`att-network__mode ${settings.mode === mode.value ? 'is-active' : ''}`}>
                    <input
                      type="radio"
                      name="att-ip-mode"
                      value={mode.value}
                      checked={settings.mode === mode.value}
                      disabled={mode.value !== 'OFF' && noNetworks}
                      onChange={() => run(() => setAttendanceIpMode(mode.value), 'Không đổi được chế độ.')}
                    />
                    <span>
                      <b>{mode.label}</b>
                      <small>{mode.hint}</small>
                    </span>
                  </label>
                ))}
                {noNetworks && <small className="field__help">Thêm ít nhất một IP trước khi bật kiểm.</small>}
              </fieldset>

              <h3 className="attendance-edit__history-title">IP / dải IP mạng khách sạn</h3>
              {settings.networks.length === 0 ? (
                <p className="muted">Chưa có IP nào.</p>
              ) : (
                <ul className="attendance-events">
                  {settings.networks.map((network) => (
                    <li key={network.id} className="att-network__row">
                      <span>
                        <b>{network.cidr}</b>
                        {network.label && <small>{network.label}</small>}
                      </span>
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        disabled={busy}
                        onClick={() => run(() => removeAttendanceNetwork(network.id), 'Không xóa được IP.')}
                      >
                        Xóa
                      </button>
                    </li>
                  ))}
                </ul>
              )}

              <div className={`alert ${settings.currentIpAllowed ? 'alert--success' : 'alert--info'}`}>
                IP máy bạn đang dùng: <b>{settings.currentIp ?? 'không xác định'}</b>
                {settings.currentIpAllowed ? ' — đã nằm trong danh sách.' : ' — chưa có trong danh sách.'}
                {/* IPv6 thêm cả dải /64 (backend gợi ý): địa chỉ IPv6 của từng máy tự đổi định kỳ. */}
                {!settings.currentIpAllowed && settings.suggestedCidr && (
                  <>
                    {' '}
                    <button
                      type="button"
                      className="btn btn--primary btn--sm"
                      disabled={busy}
                      onClick={() => run(
                        () => addAttendanceNetwork({ cidr: settings.suggestedCidr, label: 'Thêm từ máy quản lý' }),
                        'Không thêm được IP.',
                      )}
                    >
                      Thêm IP mạng hiện tại
                    </button>
                    {settings.suggestedCidr !== settings.currentIp && (
                      <small className="field__help">
                        Máy bạn đang dùng IPv6 — sẽ thêm cả dải {settings.suggestedCidr} của đường mạng này, vì địa chỉ
                        IPv6 của từng máy tự đổi theo thời gian.
                      </small>
                    )}
                  </>
                )}
              </div>

              <form className="att-network__add" onSubmit={handleAdd} noValidate>
                <label className="field">
                  <span className="field__label">Thêm IP / dải IP</span>
                  <input
                    value={cidr}
                    onChange={(e) => setCidr(e.target.value)}
                    maxLength={CIDR_MAX}
                    placeholder="113.160.92.202 hoặc 113.160.92.0/24 hoặc 2001:ee0:4f1::/64"
                  />
                </label>
                <label className="field">
                  <span className="field__label">Ghi chú</span>
                  <input
                    value={label}
                    onChange={(e) => setLabel(e.target.value)}
                    maxLength={LABEL_MAX}
                    placeholder="Ví dụ: Wifi tầng 1"
                  />
                </label>
                <button type="submit" className="btn btn--ghost" disabled={busy || cidr.trim() === ''}>
                  Thêm
                </button>
              </form>
              <small className="field__help">
                Nhân viên dùng 4G sẽ không qua được kiểm IP — cần kết nối wifi khách sạn. IP mạng gia đình có thể
                đổi khi khởi động lại modem; dùng chế độ «Cảnh báo» để phát hiện sớm.
              </small>
            </>
          )}

          {error && <div className="alert alert--error" role="alert">{error}</div>}
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={busy}>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
