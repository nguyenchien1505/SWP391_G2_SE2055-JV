import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { fetchInspection } from '../../api/housekeeping';
import { formatDateTime } from '../../pages/rooms/format';
import { inspectionResultMeta } from '../../pages/rooms/roomLabels';

/**
 * Lần kiểm tra trước của một việc dọn lại — BR-HK-12.
 *
 * Thẻ "Dọn lại" chỉ nói việc này sinh ra vì kiểm tra không đạt; người nhận việc cần biết KHÔNG
 * ĐẠT Ở CHỖ NÀO. Biên bản nằm ở task CHA, nên tra theo `task.parentTaskId` chứ không phải id
 * của thẻ đang xem.
 *
 * Chỉ đọc, không có nút thao tác — vì vậy là hộp thoại thường chứ không phải `form`.
 */
export default function PreviousInspectionModal({ task, onClose }) {
  const [record, setRecord] = useState(null);   // null = đang tải
  const [error, setError] = useState('');

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  useEffect(() => {
    let cancelled = false;
    fetchInspection(task.parentTaskId)
      .then((data) => !cancelled && setRecord(data))
      .catch((err) => !cancelled
        && setError(readErrorMessage(err, 'Không tìm thấy biên bản của lần kiểm tra trước.')));
    return () => {
      cancelled = true;
    };
  }, [task.parentTaskId]);

  const tone = record ? inspectionResultMeta(record.result).tone : 'unavailable';

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true"
         aria-labelledby="prev-inspection-title" onClick={onClose}>
      <div className="modal room-modal" onClick={(e) => e.stopPropagation()}>
        <h2 id="prev-inspection-title">Lần kiểm tra trước — phòng {task.roomNumber}</h2>

        <div className="modal__body">
          {!record && !error && <p className="state">Đang tải biên bản…</p>}

          {error && (
            <div className="alert alert--error" role="alert">
              {error}
            </div>
          )}

          {record && (
            <>
              <p className="inspect-verdict">
                <span className={`badge badge--room room-tone--${tone}`}>
                  {inspectionResultMeta(record.result).label}
                </span>
                <span className="muted">{formatDateTime(record.inspectedAt)}</span>
              </p>

              <div className="readonly-box">
                <b>Lý do</b>
                <p>{record.reason ?? 'Không ghi lý do.'}</p>
              </div>
            </>
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
