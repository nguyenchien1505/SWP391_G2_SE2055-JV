import { useEffect, useState } from 'react';
import { assetService } from '../../services/assetApi';
import { DAMAGE_TEXT_MAX, assetStatusOf } from './damageReportLabels';

/**
 * Nhân viên Lễ tân / Dọn dẹp gửi báo hỏng một tài sản cố định — BR-ASSET-05.
 *
 * Báo hỏng chỉ tạo phiếu cho Manager, KHÔNG đổi trạng thái tài sản hay phòng (BR-ASSET-06) —
 * nói rõ trên hộp thoại để nhân viên không tưởng phòng đã bị khóa. Tài sản đang Hỏng/Đang sửa
 * vẫn cho gửi (có thể là hỏng thêm chỗ khác), chỉ nhắc để tránh báo trùng.
 *
 * Thành công thì trả phiếu MỚI qua `onSubmitted`.
 */
export default function DamageReportDialog({ asset, onClose, onSubmitted }) {
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Esc để đóng, trừ lúc đang gửi — tránh đóng giữa chừng rồi không biết kết quả.
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!description.trim()) {
      setError('Vui lòng mô tả tình trạng hỏng để quản lý biết cần xử lý gì.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      onSubmitted(await assetService.createDamageReport({ fixedAssetId: asset.id, description: description.trim() }));
    } catch (err) {
      setError(err?.message || 'Không gửi được báo hỏng.');
      setSubmitting(false);
    }
  }

  const status = assetStatusOf(asset.status);
  const alreadyReported = asset.status === 'Damaged' || asset.status === 'Repairing';

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="damage-report-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="damage-report-title">Báo hỏng tài sản</h2>

        <div className="modal__body">
          <div className="damage-asset-card">
            <span className="damage-asset-card__name">{asset.name}</span>
            <span className="damage-asset-card__meta">
              {asset.code} · {asset.location}
            </span>
            <span>
              <span className={`badge ${status.badge}`}>{status.label}</span>
            </span>
          </div>

          {alreadyReported && (
            <div className="alert alert--warn" role="note">
              Tài sản này đang ở trạng thái “{status.label}” — quản lý có thể đã biết. Vẫn gửi nếu bạn
              thấy hỏng thêm chỗ khác.
            </div>
          )}

          <label className="field">
            <span className="field__label">Mô tả tình trạng hỏng *</span>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={DAMAGE_TEXT_MAX}
              rows={4}
              autoFocus
              placeholder="Ví dụ: tivi không lên hình, đã thử đổi nguồn"
            />
            <small className="field__help">
              Báo hỏng chỉ gửi cho quản lý, không tự khóa phòng. {description.length}/{DAMAGE_TEXT_MAX}
            </small>
          </label>

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
          <button type="submit" className="btn btn--primary" disabled={submitting}>
            {submitting ? 'Đang gửi…' : 'Gửi báo hỏng'}
          </button>
        </div>
      </form>
    </div>
  );
}
