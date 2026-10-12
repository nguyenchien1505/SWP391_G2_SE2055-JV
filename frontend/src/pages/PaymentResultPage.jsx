import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { readErrorMessage } from '../api/client';
import { confirmVnpayReturn } from '../api/billing';
import './Admin_platform/admin.css';

/**
 * VNPay đưa Giám đốc về đây sau khi thanh toán (?vnp_...). Trang gửi nguyên kết quả lên backend
 * để kiểm tra chữ ký và cập nhật hóa đơn:
 *  - thành công        → báo thành công, tải lại quyền (hết chế độ chỉ đọc nếu đang bị khóa)
 *  - thất bại / hủy    → về trang Gói dịch vụ, điền sẵn số lượng cũ để thanh toán lại
 *  - không hợp lệ / cần kiểm tra → báo lỗi, KHÔNG mời thanh toán lại
 */
export default function PaymentResultPage() {
  const { search } = useLocation();
  const navigate = useNavigate();
  const { refresh } = useAuth();
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  // StrictMode chạy effect 2 lần khi dev — chỉ gửi kết quả một lần.
  const sent = useRef(false);

  useEffect(() => {
    if (sent.current) return;
    sent.current = true;

    if (!search.includes('vnp_')) {
      setError('Không có kết quả thanh toán.');
      return;
    }
    confirmVnpayReturn(search)
      .then((res) => {
        if (res.status === 'FAILED' || res.status === 'CANCELLED') {
          // Có số lượng (lần đầu nhận kết quả) thì điền lại form; tải lại trang thì hóa đơn đã bị
          // xóa nên chỉ còn thông báo.
          const params = new URLSearchParams({ msg: `${res.message} Bạn có thể thanh toán lại.` });
          if (res.quotaLocation != null) {
            params.set('ql', String(res.quotaLocation));
            params.set('qu', String(res.quotaUser));
            params.set('qr', String(res.quotaRoom));
          }
          navigate(`/goi-dich-vu?${params}`, { replace: true });
          return;
        }
        if (res.status === 'SUCCESS') refresh();
        setResult(res);
      })
      .catch((err) => setError(readErrorMessage(err, 'Không xác nhận được kết quả thanh toán.')));
  }, [search, navigate, refresh]);

  const success = result?.status === 'SUCCESS';

  return (
    <div className="page admin-page">
      <div className="page__head">
        <h1>Kết quả thanh toán</h1>
      </div>
      <section className="panel">
        {!result && !error && <p className="state">Đang xác nhận kết quả thanh toán…</p>}
        {error && (
          <div className="alert alert--error" role="alert">
            {error}
          </div>
        )}
        {result && (
          <div className={`alert ${success ? 'alert--success' : 'alert--error'}`} role="status">
            {result.message}
          </div>
        )}
        <Link to="/goi-dich-vu" className="btn btn--primary">
          Về Gói dịch vụ
        </Link>
      </section>
    </div>
  );
}
