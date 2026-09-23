import { useState } from 'react';

/**
 * Hiển thị mật khẩu tạm vừa cấp — thiết kế "Cấp tài khoản Quản lý thành công" trong
 * docs/FE_Lâm_Dũng.
 *
 * <p>BR-USER-03 / BR-USER-07: mật khẩu tạm chỉ hiển thị ĐÚNG MỘT LẦN, không xem lại được;
 * người cấp tự thông báo cho nhân sự. Vì vậy hộp thoại chỉ đóng bằng nút xác nhận, không
 * đóng khi bấm ra ngoài.
 */
export default function TempPasswordDialog({ title, description, user, locationName, tempPassword, onClose }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(tempPassword);
      setCopied(true);
    } catch {
      // Trình duyệt chặn clipboard (ví dụ trang không phải https / localhost): để người dùng
      // tự bôi đen và chép tay.
      setCopied(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className="modal modal--wide">
        <div className="pw-dialog__head">
          <span className="pw-dialog__icon" aria-hidden="true">
            ✓
          </span>
          <div>
            <span className="chip chip--green">Hệ thống Sao Mai PMS</span>
            <h2>{title}</h2>
            <p className="muted">{description}</p>
          </div>
        </div>

        <dl className="pw-dialog__info">
          <dt>Người nhận tài khoản</dt>
          <dd>
            <b>{user.fullName}</b>
          </dd>
          <dt>Email đăng nhập</dt>
          <dd>{user.email}</dd>
          {locationName && (
            <>
              <dt>Cơ sở bổ nhiệm</dt>
              <dd>🏨 {locationName}</dd>
            </>
          )}
        </dl>

        <p className="pw-dialog__label">Mật khẩu tạm thời được cấp</p>
        <div className="pw-dialog__secret">
          <code>{tempPassword}</code>
          <button type="button" className="btn btn--primary btn--sm" onClick={copy}>
            {copied ? 'Đã sao chép ✓' : 'Sao chép mật khẩu'}
          </button>
        </div>

        <div className="pw-dialog__warn">
          <b>⚠ LƯU Ý QUAN TRỌNG: CHỈ HIỂN THỊ ĐÚNG 1 LẦN!</b>
          <p>
            Mật khẩu tạm này <b>sẽ không được hiển thị lại</b> sau khi bạn đóng hộp thoại. Hãy sao
            chép và gửi trực tiếp cho người nhận qua kênh liên lạc bảo mật.
            <br />
            Hệ thống sẽ <b>bắt buộc đổi mật khẩu mới</b> ngay ở lần đăng nhập đầu tiên (BR-USER-07).
          </p>
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--dark" onClick={onClose}>
            ✓ Đã sao chép &amp; Đóng hộp thoại
          </button>
        </div>
      </div>
    </div>
  );
}
