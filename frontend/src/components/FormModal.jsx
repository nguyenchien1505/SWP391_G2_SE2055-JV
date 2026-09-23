/**
 * Khung pop-up cho form tạo / sửa. Form bên trong tự lo tiêu đề và các nút.
 *
 * <p>KHÔNG đóng khi bấm ra ngoài: người dùng đang nhập dở dễ lỡ tay bấm trượt và mất hết
 * dữ liệu. Chỉ đóng bằng nút ✕ hoặc nút "Hủy bỏ" của form.
 */
export default function FormModal({ onClose, children }) {
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className="modal modal--form">
        <button type="button" className="modal__close" onClick={onClose} aria-label="Đóng">
          ✕
        </button>
        {children}
      </div>
    </div>
  );
}
