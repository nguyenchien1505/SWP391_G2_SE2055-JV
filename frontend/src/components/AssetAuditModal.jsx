import { useEffect, useMemo, useState } from 'react';
import { assetService } from '../services/assetApi';
import { ASSET_STATUS } from './damage-reports/damageReportLabels';

/** Thứ tự lựa chọn tình trạng; "Đã thanh lý" cuối cùng vì không hoàn tác được (BR-ASSET-14). */
const STATUS_OPTIONS = ['Good', 'Damaged', 'Repairing', 'Disposed'];

/**
 * Kiểm kê tài sản cố định tại một vị trí (phòng hoặc khu vực) — Manager đối chiếu từng tài sản
 * và cập nhật tình
 * trạng thực tế. Chỉ gửi những tài sản có tình trạng thay đổi, mỗi tài sản một lần
 * `PATCH /assets/fixed-assets/{id}/status`. Milestone 1 chưa có bản ghi "đợt kiểm kê" riêng cho
 * tài sản cố định, nên kết quả kiểm kê chính là tình trạng mới của từng tài sản.
 *
 * Tài sản đã thanh lý không được liệt kê (BR-ASSET-14). Một vài tài sản lưu lỗi thì hộp thoại vẫn
 * mở, giữ lại các thay đổi chưa lưu để Manager thử lại.
 *
 * @param scope    { roomId } hoặc { areaId } — vị trí cần kiểm kê
 * @param title    tiêu đề hộp thoại, ví dụ "Kiểm kê tài sản phòng 101"
 * @param emptyText câu hiện khi vị trí chưa có tài sản nào
 * @param onSaved  gọi với số tài sản đã đổi tình trạng
 */
export default function AssetAuditModal({ scope, title, emptyText, onClose, onSaved }) {
  const { roomId, areaId } = scope;
  const [assets, setAssets] = useState(null); // null = đang tải
  const [loadError, setLoadError] = useState('');
  const [draft, setDraft] = useState({}); // assetId → tình trạng mới
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    assetService
      .getReportableAssets({ roomId, areaId })
      .then((rows) => !cancelled && setAssets(rows))
      .catch((err) => !cancelled && setLoadError(err?.message || 'Không tải được danh sách tài sản.'));
    return () => {
      cancelled = true;
    };
  }, [roomId, areaId]);

  // Esc để đóng, trừ lúc đang gửi — tránh đóng giữa chừng rồi không biết kết quả.
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  const changes = useMemo(
    () => (assets ?? []).filter((asset) => draft[asset.id] && draft[asset.id] !== asset.status),
    [assets, draft],
  );
  const disposing = changes.filter((asset) => draft[asset.id] === 'Disposed');

  async function handleSubmit(e) {
    e.preventDefault();
    if (changes.length === 0) {
      onClose();
      return;
    }
    setSubmitting(true);
    setError('');

    const failed = [];
    const saved = {};
    for (const asset of changes) {
      try {
        await assetService.updateAssetStatus(asset.id, draft[asset.id]);
        saved[asset.id] = draft[asset.id];
      } catch (err) {
        failed.push(`${asset.code}: ${err?.message || 'lỗi không rõ'}`);
      }
    }

    if (failed.length === 0) {
      onSaved(changes.length);
      return;
    }
    // Ghi nhận phần đã lưu để lần thử lại chỉ gửi phần còn lỗi.
    setAssets((rows) => rows.map((row) => (saved[row.id] ? { ...row, status: saved[row.id] } : row)));
    setError(`Không lưu được ${failed.length}/${changes.length} tài sản — ${failed.join('; ')}`);
    setSubmitting(false);
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="asset-audit-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal asset-audit" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="asset-audit-title">{title}</h2>

        <div className="modal__body">
          {loadError && (
            <div className="alert alert--error" role="alert">
              {loadError}
            </div>
          )}
          {!loadError && assets === null && <p className="state">Đang tải tài sản…</p>}
          {assets?.length === 0 && (
            <div className="state state--empty">
              <p>{emptyText ?? 'Chưa có tài sản cố định nào.'}</p>
            </div>
          )}

          {assets?.length > 0 && (
            <>
              <p className="muted">Đối chiếu từng tài sản và chọn tình trạng thực tế. Chỉ dòng thay đổi được lưu.</p>
              <ul className="asset-audit__list">
                {assets.map((asset) => {
                  const value = draft[asset.id] ?? asset.status;
                  const changed = value !== asset.status;
                  return (
                    <li key={asset.id} className={`asset-audit__row ${changed ? 'is-changed' : ''}`}>
                      <div className="asset-audit__info">
                        <b>{asset.name}</b>
                        <small>
                          {asset.code} · hiện tại: {ASSET_STATUS[asset.status]?.label ?? asset.status}
                        </small>
                      </div>
                      <select
                        value={value}
                        onChange={(e) => setDraft((d) => ({ ...d, [asset.id]: e.target.value }))}
                        disabled={submitting}
                        aria-label={`Tình trạng ${asset.name} (${asset.code})`}
                      >
                        {STATUS_OPTIONS.map((status) => (
                          <option key={status} value={status}>
                            {ASSET_STATUS[status].label}
                          </option>
                        ))}
                      </select>
                    </li>
                  );
                })}
              </ul>
            </>
          )}

          {disposing.length > 0 && (
            <div className="alert alert--warn" role="note">
              {disposing.length} tài sản sẽ chuyển sang <b>Đã thanh lý</b>. Thao tác này không hoàn tác được và
              tự đóng các báo hỏng đang mở của tài sản đó.
            </div>
          )}

          {error && (
            <div className="alert alert--error" role="alert">
              {error}
            </div>
          )}
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={submitting}>
            Hủy
          </button>
          <button type="submit" className="btn btn--primary" disabled={submitting || !assets?.length}>
            {submitting
              ? 'Đang lưu…'
              : changes.length > 0
                ? `Lưu kiểm kê (${changes.length})`
                : 'Xác nhận không thay đổi'}
          </button>
        </div>
      </form>
    </div>
  );
}
