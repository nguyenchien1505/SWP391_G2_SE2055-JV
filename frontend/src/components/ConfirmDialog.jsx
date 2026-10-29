/** design.md mục 5: mọi thao tác không hoàn tác được phải qua hộp thoại xác nhận. */
/** `confirmTone`: màu nút xác nhận — mặc định đỏ cho thao tác xóa/hủy, 'primary' cho việc thường. */
/** `cancelLabel`: đổi chữ nút thoát khi thao tác tự nó là "hủy" (ví dụ «Hủy yêu cầu» / «Giữ yêu cầu»). */
export default function ConfirmDialog({
  title, message, confirmLabel, onConfirm, onCancel, confirmTone = 'danger', cancelLabel = 'Hủy',
}) {
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        <div className="modal__body">{message}</div>
        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button type="button" className={`btn btn--${confirmTone}`} onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
