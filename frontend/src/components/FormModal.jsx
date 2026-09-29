/**
 * Khung pop-up cho form tạo / sửa. Form bên trong tự lo tiêu đề và các nút.
 *
 * <p>KHÔNG đóng khi bấm ra ngoài: người dùng đang nhập dở dễ lỡ tay bấm trượt và mất hết
 * dữ liệu. Chỉ đóng bằng nút ✕ hoặc nút "Hủy bỏ" của form.
 *
 * <p>`wide`: rộng hơn cho nội dung dạng bảng (ví dụ danh sách "đang dùng" của màn Danh mục).
 */
export default function FormModal({ onClose, children, wide = false }) {
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className={`modal modal--form ${wide ? 'modal--form-wide' : ''}`}>
        <button type="button" className="modal__close" onClick={onClose} aria-label="Đóng">
          ✕
        </button>
        {children}
      </div>
    </div>
  );
}
