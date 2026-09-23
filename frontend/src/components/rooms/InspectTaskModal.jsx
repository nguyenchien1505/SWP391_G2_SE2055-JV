import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { inspectTask } from '../../api/housekeeping';
import { formatDate } from '../../pages/rooms/format';
import { taskTypeLabel } from '../../pages/rooms/roomLabels';

/** Khớp @Size(max = 500) của InspectTaskRequest và cột inspection_records.reason. */
const REASON_MAX = 500;

/**
 * S-13 Kiểm tra phòng sau dọn — RM-19, BR-HK-06, BR-HK-08, BR-HK-12.
 *
 * Chỉ Quản lý chi nhánh mở được (nút "Kiểm tra" chỉ hiện ở cột «Chờ kiểm tra»); backend vẫn
 * kiểm tra lại quyền và trạng thái việc dọn.
 *
 * Hai lựa chọn là hai NÚT LỚN chứ không phải danh sách thả xuống: người dùng đang đứng trong
 * phòng vừa dọn, thao tác phải nhanh và khó bấm nhầm. Chọn "Không đạt" mới hiện ô lý do — bắt
 * buộc, text tự do, KHÔNG có danh mục lỗi (BR-HK-08).
 *
 * @param onInspected nhận BIÊN BẢN kiểm tra sau khi lưu (có `nextTaskId` khi không đạt), để
 *                    màn hình hiện đúng câu thông báo và tải lại bảng
 */
export default function InspectTaskModal({ task, staffName, onClose, onInspected }) {
  const [result, setResult] = useState('');       // '' = chưa chọn
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const failing = result === 'FAIL';

  // Esc để đóng, trừ lúc đang gửi — tránh đóng giữa chừng rồi không biết kết quả.
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  async function handleSubmit(e) {
    e.preventDefault();
    // Chặn sớm cho đỡ một lượt gọi API; backend vẫn kiểm tra lại (BR-HK-08).
    if (failing && reason.trim() === '') {
      setError('Kiểm tra không đạt thì phải nhập lý do.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      onInspected(await inspectTask(task.id, { result, reason: failing ? reason.trim() : null }));
    } catch (err) {
      setError(readErrorMessage(err, 'Không lưu được kết quả kiểm tra.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="inspect-task-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="inspect-task-title">Kiểm tra phòng {task.roomNumber}</h2>

        <div className="modal__body">
          <div className="readonly-box">
            <b>Tầng {task.floor ?? '—'} · {taskTypeLabel(task.taskType)}</b>
            <p>
              Người dọn: <b>{staffName ?? 'Không rõ'}</b>
              {task.assignedDate ? ` · Ngày làm ${formatDate(task.assignedDate)}` : ''}
            </p>
          </div>

          <fieldset className="inspect-choices">
            <legend className="field__label">
              Kết quả kiểm tra <span className="req">*</span>
            </legend>
            <ResultButton
              value="PASS"
              label="Đạt"
              hint="Phòng sạch, đón khách được ngay"
              selected={result}
              onSelect={setResult}
            />
            <ResultButton
              value="FAIL"
              label="Không đạt"
              hint="Phòng quay về Chờ dọn, tạo việc dọn lại"
              selected={result}
              onSelect={setResult}
            />
          </fieldset>

          {/* BR-HK-08: ô lý do chỉ có nghĩa khi không đạt, nên hiện đúng lúc đó. */}
          {failing && (
            <label className="field">
              <span className="field__label">
                Lý do không đạt <span className="req">*</span>
              </span>
              <textarea
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                maxLength={REASON_MAX}
                rows={3}
                autoFocus
                placeholder="Ví dụ: nhà tắm còn bẩn, chưa thay khăn"
              />
              <small className="field__help">
                Người dọn lại sẽ đọc được câu này. {reason.length}/{REASON_MAX}
              </small>
            </label>
          )}

          {error && (
            <div className="alert alert--error" role="alert">
              {error}
            </div>
          )}
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={submitting}>
            Hủy
          </button>
          <button type="submit" className="btn btn--primary" disabled={submitting || !result}>
            {submitting ? 'Đang lưu…' : 'Lưu kết quả'}
          </button>
        </div>
      </form>
    </div>
  );
}

/**
 * Một lựa chọn kết quả. Là `input[type=radio]` thật (bàn phím và trình đọc màn hình dùng được),
 * chỉ trình bày như nút lớn — vùng bấm ≥ 48px kể cả trên điện thoại (BR-ROOM-06).
 */
function ResultButton({ value, label, hint, selected, onSelect }) {
  return (
    <label className={`inspect-choice inspect-choice--${value.toLowerCase()} ${selected === value ? 'is-selected' : ''}`}>
      <input
        type="radio"
        name="inspection-result"
        value={value}
        checked={selected === value}
        onChange={() => onSelect(value)}
      />
      <span>
        <b>{label}</b>
        <small>{hint}</small>
      </span>
    </label>
  );
}
