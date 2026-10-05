import { useEffect, useMemo, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { assignTask } from '../../api/housekeeping';
import { formatDate } from '../../pages/rooms/format';
import { canAssignOn, compareQueue, isRedo } from '../../pages/rooms/cleaningBoard';
import { taskTypeLabel } from '../../pages/rooms/roomLabels';

/**
 * Giao NHIỀU việc trong hàng chờ cho MỘT người — chiều ngược lại của AssignTaskModal, mở từ nút
 * "+ Giao việc" trên một hàng của bảng theo nhân viên. BR-HK-02: giao tay, không giới hạn số việc.
 *
 * Mỗi việc gọi API gán riêng: một việc lỗi (ví dụ vừa có người khác giao mất) không kéo cả loạt
 * thất bại. Chạy hết mới báo kết quả cho trang cha qua `onDone`.
 *
 * @param person  { id, fullName, shiftLabel } — người nhận, đã có ca ngày `date`
 * @param date    ngày giao (ISO)
 * @param today   hôm nay (ISO) — việc dọn sau trả phòng chỉ giao được cho hôm nay (Q5)
 * @param tasks   các việc trong hàng chờ
 * @param onDone  nhận { assigned: task[], failed: [{ task, message }] }
 */
export default function AssignToStaffModal({ person, date, today, tasks, onClose, onDone }) {
  const [selected, setSelected] = useState(() => new Set());
  const [submitting, setSubmitting] = useState(false);
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  const options = useMemo(() => [...tasks].sort(compareQueue), [tasks]);
  const lockedCheckout = options.some((task) => !canAssignOn(task, date, today));

  function toggle(taskId) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(taskId)) next.delete(taskId);
      else next.add(taskId);
      return next;
    });
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    const assigned = [];
    const failed = [];
    for (const task of options.filter((t) => selected.has(t.id))) {
      try {
        assigned.push(await assignTask(task.id, { staffId: person.id, assignedDate: date }));
      } catch (err) {
        failed.push({ task, message: readErrorMessage(err, 'Không giao được việc này.') });
      }
      setProgress((count) => count + 1);
    }
    onDone({ assigned, failed });
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="assign-person-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="assign-person-title">Giao việc cho {person.fullName}</h2>

        <div className="modal__body">
          <div className="readonly-box">
            <b>Ngày {formatDate(date)}</b>
            <p>{person.shiftLabel ? `Ca ${person.shiftLabel}.` : 'Có ca trong ngày.'} Chọn các phòng muốn giao.</p>
          </div>

          {options.length === 0 && (
            <div className="alert alert--info">Hàng chờ không còn việc nào.</div>
          )}

          {options.length > 0 && (
            <fieldset className="choice-list">
              <legend className="field__label">Việc trong hàng chờ</legend>
              {options.map((task) => {
                const allowed = canAssignOn(task, date, today);
                return (
                  <label
                    key={task.id}
                    className={`choice ${selected.has(task.id) ? 'is-selected' : ''} ${allowed ? '' : 'is-disabled'}`}
                  >
                    <input
                      type="checkbox"
                      checked={selected.has(task.id)}
                      disabled={!allowed || submitting}
                      onChange={() => toggle(task.id)}
                    />
                    <span>
                      <b>Phòng {task.roomNumber ?? '—'}</b>
                      <small>
                        Tầng {task.floor ?? '—'} · {taskTypeLabel(task.taskType)}
                        {isRedo(task) ? ' · Dọn lại' : ''}
                      </small>
                    </span>
                  </label>
                );
              })}
            </fieldset>
          )}

          {lockedCheckout && (
            <p className="field__help">
              Việc dọn sau trả phòng chỉ giao được trong ngày, vì phòng chuyển sang «Đang dọn» ngay khi giao.
            </p>
          )}
        </div>

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={submitting}>
            Hủy
          </button>
          <button type="submit" className="btn btn--primary" disabled={submitting || selected.size === 0}>
            {submitting
              ? `Đang giao ${progress}/${selected.size}…`
              : selected.size > 0 ? `Giao ${selected.size} việc` : 'Giao việc'}
          </button>
        </div>
      </form>
    </div>
  );
}
