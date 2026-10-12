/** design.md mục 5: mọi thao tác không hoàn tác được phải qua hộp thoại xác nhận. */
/** `confirmTone`: màu nút xác nhận — mặc định đỏ cho thao tác xóa/hủy, 'primary' cho việc thường. */
export default function ConfirmDialog({ title, message, confirmLabel, onConfirm, onCancel, confirmTone = 'danger' }) {
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        <div className="modal__body">{message}</div>
        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCancel}>
            Hủy
          </button>
          <button type="button" className={`btn btn--${confirmTone}`} onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
