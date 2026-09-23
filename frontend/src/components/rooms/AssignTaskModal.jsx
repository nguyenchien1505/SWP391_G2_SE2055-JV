import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { assignTask, fetchAssignableStaff } from '../../api/housekeeping';
import { formatDate, todayIso } from '../../pages/rooms/format';
import { taskTypeLabel } from '../../pages/rooms/roomLabels';

/**
 * S-10 Phân công việc dọn — RM-14, BR-HK-02, BR-HK-03.
 *
 * Danh sách người nhận việc do BACKEND lọc (`/assignable-staff?date=`) với đúng bộ điều kiện mà
 * lệnh gán sẽ kiểm lại: đang làm việc, cùng khách sạn, Position loại Dọn dẹp, và có ca ngày đó.
 * Nhờ vậy ai hiện ra là gán được — Quản lý không bấm rồi mới nhận lỗi.
 *
 * Đổi ngày là tải lại danh sách, vì "có ca" phụ thuộc ngày. KHÔNG hiện giới hạn số việc mỗi
 * người: BR-HK-02 nói rõ là không có giới hạn.
 *
 * @param onAssigned nhận việc SAU KHI gán (kèm trạng thái mới) để màn hình cập nhật ngay
 */
export default function AssignTaskModal({ task, onClose, onAssigned }) {
  // Việc dọn sau trả phòng chỉ gán được cho hôm nay (phòng sang «Đang dọn» ngay khi gán), nên
  // khóa luôn ô ngày cho khỏi chọn nhầm rồi nhận lỗi.
  const todayOnly = task.taskType === 'CHECKOUT';

  const [date, setDate] = useState(todayIso());
  const [staff, setStaff] = useState(null); // null = đang tải
  const [staffId, setStaffId] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  // Đổi ngày → danh sách người có ca đổi theo. Cờ `cancelled` bỏ qua phản hồi của lần gọi cũ
  // nếu người dùng bấm ngày khác nhanh hơn tốc độ mạng.
  useEffect(() => {
    let cancelled = false;
    setStaff(null);
    setStaffId('');
    fetchAssignableStaff(date)
      .then((data) => !cancelled && setStaff(data ?? []))
      .catch((err) => {
        if (cancelled) return;
        setStaff([]);
        setError(readErrorMessage(err, 'Không tải được danh sách nhân viên.'));
      });
    return () => {
      cancelled = true;
    };
  }, [date]);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      onAssigned(await assignTask(task.id, { staffId, assignedDate: date }));
    } catch (err) {
      setError(readErrorMessage(err, 'Không phân công được việc dọn.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="assign-task-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="assign-task-title">Phân công phòng {task.roomNumber}</h2>

        <div className="modal__body">
          <div className="readonly-box">
            <b>{taskTypeLabel(task.taskType)}</b>
            <p>
              {todayOnly
                ? 'Phòng sẽ chuyển sang «Đang dọn» ngay khi phân công.'
                : 'Khách vẫn đang ở, phòng giữ nguyên «Đang sử dụng».'}
            </p>
          </div>

          <label className="field" htmlFor="assign-date">
            <span className="field__label">Ngày làm</span>
            <input
              id="assign-date"
              type="date"
              value={date}
              min={todayIso()}
              max={todayOnly ? todayIso() : undefined}
              onChange={(e) => setDate(e.target.value)}
              disabled={todayOnly}
            />
            <span className="field__help">
              {todayOnly
                ? 'Việc dọn sau trả phòng chỉ giao được trong ngày.'
                : 'Chỉ hiện nhân viên dọn phòng có ca ngày này.'}
            </span>
          </label>

          {staff === null && <p className="state">Đang tải danh sách nhân viên…</p>}

          {staff?.length === 0 && (
            <div className="alert alert--info">
              Không có nhân viên dọn phòng nào có ca ngày {formatDate(date)}. Cần xếp ca trước khi
              giao việc.
            </div>
          )}

          {staff?.length > 0 && (
            <fieldset className="choice-list">
              <legend className="field__label">Giao cho</legend>
              {staff.map((person, index) => (
                <label key={person.id} className={`choice ${staffId === person.id ? 'is-selected' : ''}`}>
                  <input
                    type="radio"
                    name="assign-staff"
                    value={person.id}
                    checked={staffId === person.id}
                    onChange={() => setStaffId(person.id)}
                    autoFocus={index === 0}
                  />
                  <span>
                    <b>{person.fullName}</b>
                    <small>{person.phone}</small>
                  </span>
                </label>
              ))}
            </fieldset>
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
          <button type="submit" className="btn btn--primary" disabled={submitting || !staffId}>
            {submitting ? 'Đang lưu…' : 'Giao việc'}
          </button>
        </div>
      </form>
    </div>
  );
}
