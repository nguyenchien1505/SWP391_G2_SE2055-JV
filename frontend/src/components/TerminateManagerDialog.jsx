import { useState } from 'react';
import FormModal from './FormModal';
import ManagerForm from './ManagerForm';

/**
 * Cho Manager đang phụ trách khách sạn nghỉ việc. Bắt buộc bàn giao ngay để khách sạn không
 * lúc nào thiếu người quản lý: chọn một Quản lý dự bị, hoặc tạo tài khoản Quản lý mới.
 *
 * <p>`onConfirm(handover)` nhận `{ replacementManagerId }` hoặc `{ newManager }` và trả về chuỗi
 * lỗi (giữ hộp thoại mở để sửa) hoặc null khi thành công.
 */
export default function TerminateManagerDialog({ target, locationName, reserveManagers, onCancel, onConfirm }) {
  const [mode, setMode] = useState(reserveManagers.length > 0 ? 'reserve' : 'new');
  const [replacementId, setReplacementId] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function confirmReserve() {
    setError('');
    if (!replacementId) {
      setError('Chọn Quản lý dự bị nhận bàn giao.');
      return;
    }
    setSubmitting(true);
    const message = await onConfirm({ replacementManagerId: replacementId });
    setSubmitting(false);
    if (message) {
      setError(message);
    }
  }

  return (
    <FormModal onClose={onCancel}>
      <header className="form__head">
        <span className="form__icon form__icon--danger" aria-hidden="true">
          ⚠
        </span>
        <div>
          <h2>Cho quản lý nghỉ việc</h2>
          <p className="muted">
            <b>{target.fullName}</b> đang phụ trách <b>🏨 {locationName}</b>. Chọn người nhận bàn giao
            khách sạn trước khi cho nghỉ việc.
          </p>
        </div>
      </header>

      <div className="alert alert--warn">
        Không hoàn tác được. Tài khoản chuyển "Đã nghỉ việc", ca làm tương lai bị gỡ. Email này không
        dùng lại được, trừ khi sau đó tài khoản được xóa vĩnh viễn.
      </div>

      <div className="choice-group" role="radiogroup" aria-label="Người nhận bàn giao">
        <label className={`choice ${mode === 'reserve' ? 'choice--active' : ''}`}>
          <input
            type="radio"
            name="handover-mode"
            checked={mode === 'reserve'}
            onChange={() => {
              setMode('reserve');
              setError('');
            }}
          />
          <span>
            <b>Chọn Quản lý dự bị</b>
            <small>{reserveManagers.length} người đang chờ gán khách sạn</small>
          </span>
        </label>
        <label className={`choice ${mode === 'new' ? 'choice--active' : ''}`}>
          <input
            type="radio"
            name="handover-mode"
            checked={mode === 'new'}
            onChange={() => {
              setMode('new');
              setError('');
            }}
          />
          <span>
            <b>Tạo tài khoản Quản lý mới</b>
            <small>Cấp mật khẩu tạm cho người mới</small>
          </span>
        </label>
      </div>

      {mode === 'reserve' ? (
        <div className="form">
          <label className="field" htmlFor="handover-reserve">
            <span className="field__label">
              Quản lý dự bị nhận bàn giao <b className="req">*</b>
            </span>
            <select
              id="handover-reserve"
              value={replacementId}
              onChange={(e) => setReplacementId(e.target.value)}
              disabled={reserveManagers.length === 0}
            >
              <option value="">
                {reserveManagers.length === 0 ? '— Chưa có Quản lý dự bị nào —' : '— Chọn Quản lý dự bị —'}
              </option>
              {reserveManagers.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.fullName} — {m.email}
                </option>
              ))}
            </select>
            <span className="field__help">
              {reserveManagers.length === 0
                ? 'Chưa có ai để chọn — hãy tạo tài khoản Quản lý mới.'
                : 'Chỉ liệt kê Quản lý dự bị đang hoạt động (không bị tạm khóa).'}
            </span>
          </label>

          {error && (
            <div className="alert alert--error" role="alert">
              {error}
            </div>
          )}

          <div className="form__actions">
            <button type="button" className="btn btn--ghost" onClick={onCancel}>
              Hủy bỏ
            </button>
            <button
              type="button"
              className="btn btn--danger"
              onClick={confirmReserve}
              disabled={submitting || reserveManagers.length === 0}
            >
              {submitting ? 'Đang xử lý…' : 'Cho nghỉ việc & bàn giao'}
            </button>
          </div>
        </div>
      ) : (
        <ManagerForm
          freeLocations={[]}
          handoverLocationName={locationName}
          onCancel={onCancel}
          onSubmit={(values) => onConfirm({ newManager: values })}
        />
      )}
    </FormModal>
  );
}
