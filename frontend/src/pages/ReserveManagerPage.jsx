import { useAuth } from '../context/AuthContext';
import Logo from '../components/Logo';

/**
 * Màn chờ của Quản lý dự bị: đã có tài khoản nhưng chưa phụ trách khách sạn nào. Backend chặn
 * mọi API quản lý với tài khoản này, nên thay vì để các màn nghiệp vụ báo lỗi 403 thì hiển thị
 * thẳng lý do. Được gán khách sạn thì tải lại trang là vào hệ thống bình thường.
 */
export default function ReserveManagerPage() {
  const { user, signOut } = useAuth();

  return (
    <div className="login-page">
      <div className="login-card">
        <header className="login-card__head">
          <Logo />
          <span className="version-chip">Quản lý dự bị</span>
        </header>

        <div className="login-card__body">
          <h1>Chưa được gán khách sạn</h1>
          <p className="muted">
            Tài khoản <b>{user?.email}</b> đang ở trạng thái <b>Quản lý dự bị</b>. Giám đốc sẽ gán khách
            sạn cho bạn, hoặc chọn bạn nhận bàn giao khi một Quản lý nghỉ việc. Khi đó các chức năng quản
            lý sẽ mở ra.
          </p>

          <button type="button" className="btn btn--primary btn--block" onClick={() => window.location.reload()}>
            Kiểm tra lại
          </button>
          <button type="button" className="btn btn--ghost btn--block" onClick={signOut}>
            Đăng xuất
          </button>
        </div>
      </div>
    </div>
  );
}
