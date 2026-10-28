import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { fetchTaskReturns } from '../../api/housekeeping';
import { formatDateTime } from '../../pages/rooms/format';
import { taskRoomLabel } from '../../pages/rooms/roomLabels';

/**
 * Lịch sử trả lại của một việc dọn — BR-HK-17. Mở từ liên kết "Lịch sử trả lại (N)" trên thẻ việc của bảng
 * điều phối (Quản lý chi nhánh, Giám sát vận hành).
 *
 * Khi nhóm còn người, lần trả không đổi trạng thái phòng nên KHÔNG có trong lịch sử phòng — đây là nơi duy
 * nhất đọc được lý do của những lần đó. Mới nhất trước.
 *
 * Chỉ đọc, khuôn như `PreviousInspectionModal`.
 */
export default function TaskReturnsModal({ task, onClose }) {
  const [returns, setReturns] = useState(null);   // null = đang tải
  const [error, setError] = useState('');

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  useEffect(() => {
    let cancelled = false;
    fetchTaskReturns(task.id)
      .then((data) => !cancelled && setReturns(data))
      .catch((err) => !cancelled && setError(readErrorMessage(err, 'Không tải được lịch sử trả lại.')));
    return () => {
      cancelled = true;
    };
  }, [task.id]);

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true"
         aria-labelledby="task-returns-title" onClick={onClose}>
      <div className="modal room-modal" onClick={(e) => e.stopPropagation()}>
        <h2 id="task-returns-title">Lịch sử trả lại — phòng {taskRoomLabel(task)}</h2>

        <div className="modal__body">
          {!returns && !error && <p className="state">Đang tải…</p>}

          {error && (
            <div className="alert alert--error" role="alert">
              {error}
            </div>
          )}

          {returns?.length === 0 && <p className="state state--empty">Việc này chưa bị trả lại lần nào.</p>}

          {returns?.length > 0 && (
            <ol className="task-returns">
              {returns.map((item) => (
                <li key={item.id} className="readonly-box">
                  <b>
                    {item.staffName ?? 'Nhân viên đã nghỉ'}
                    <span className="muted"> · {formatDateTime(item.returnedAt)}</span>
                  </b>
                  <p>{item.reason}</p>
                  <small className="muted">
                    {item.taskReleased ? 'Phòng về «Chờ dọn»' : 'Nhóm vẫn dọn tiếp'}
                  </small>
                </li>
              ))}
            </ol>
          )}
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--primary" onClick={onClose} autoFocus>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
