import { useEffect, useMemo, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { assignTask, fetchAssignableStaff } from '../../api/housekeeping';
import { formatDate, todayIso } from '../../pages/rooms/format';
import { taskRoomLabel, teamNames } from '../../pages/rooms/roomLabels';

/**
 * S-10 Phân công việc dọn — RM-14, BR-HK-02, BR-HK-03. Một phòng được NHIỀU người cùng dọn (chốt
 * 05/10/2026): tick bao nhiêu người cũng được, tất cả hoặc không ai — một người không đủ điều kiện thì
 * không giao cho ai.
 *
 * Hai chế độ theo trạng thái của việc:
 *   - **Chờ giao** — giao lần đầu: cả nhóm bắt đầu dọn, phòng sang «Đang dọn» ngay.
 *   - **Đang làm** — thêm người vào nhóm: phòng đứng yên, người mới dọn cùng ngày với nhóm.
 *
 * Danh sách người nhận do BACKEND lọc (`/assignable-staff?date=`) với đúng bộ điều kiện mà lệnh gán
 * sẽ kiểm lại: đang làm việc, cùng khách sạn, có quyền Dọn dẹp, và có ca ngày đó — ai hiện ra là gán
 * được. KHÔNG hiện giới hạn số việc mỗi người: BR-HK-02 nói rõ là không có giới hạn.
 *
 * Việc dọn chỉ giao trong ngày (phòng sang «Đang dọn» ngay khi giao — Q5), nên ngày không chọn được.
 *
 * @param onAssigned nhận việc SAU KHI gán (kèm trạng thái và nhóm mới) để màn hình cập nhật ngay
 */
export default function AssignTaskModal({ task, onClose, onAssigned }) {
  const adding = task.status === 'IN_PROGRESS';
  // Thêm người: dọn cùng ngày với nhóm. Giao lần đầu: hôm nay.
  const date = adding ? task.assignedDate : todayIso();
  const team = useMemo(() => new Set(task.assignedStaffIds ?? []), [task.assignedStaffIds]);

  const [staff, setStaff] = useState(null); // null = đang tải
  const [selected, setSelected] = useState(() => new Set());
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  useEffect(() => {
    let cancelled = false;
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

  // Người đã trong nhóm thì không chọn lại.
  const candidates = (staff ?? []).filter((person) => !team.has(person.id));

  function toggle(id) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      onAssigned(await assignTask(task.id, { staffIds: [...selected], assignedDate: date }));
    } catch (err) {
      setError(readErrorMessage(err, adding ? 'Không thêm được người vào nhóm dọn.' : 'Không phân công được việc dọn.'));
      setSubmitting(false);
    }
  }

  const room = taskRoomLabel(task);

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="assign-task-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="assign-task-title">{adding ? `Thêm người dọn phòng ${room}` : `Phân công phòng ${room}`}</h2>

        <div className="modal__body">
          <div className="readonly-box">
            <b>Ngày làm {formatDate(date)}</b>
            <p>
              {adding
                ? `Đang dọn: ${teamNames(task) || '—'}. Người thêm vào dọn cùng nhóm; phòng vẫn «Đang dọn».`
                : 'Phòng sẽ chuyển sang «Đang dọn» ngay khi phân công. Việc dọn chỉ giao được trong ngày.'}
            </p>
          </div>

          {staff === null && <p className="state">Đang tải danh sách nhân viên…</p>}

          {staff !== null && candidates.length === 0 && !error && (
            <div className="alert alert--info">
              {staff.length === 0
                ? `Không có nhân viên dọn phòng nào có ca ngày ${formatDate(date)}. Cần xếp ca trước khi giao việc.`
                : 'Mọi người có ca hôm nay đều đã ở trong nhóm dọn phòng này.'}
            </div>
          )}

          {candidates.length > 0 && (
            <fieldset className="choice-list">
              <legend className="field__label">{adding ? 'Thêm vào nhóm' : 'Giao cho'}</legend>
              {candidates.map((person, index) => (
                <label key={person.id} className={`choice ${selected.has(person.id) ? 'is-selected' : ''}`}>
                  <input
                    type="checkbox"
                    value={person.id}
                    checked={selected.has(person.id)}
                    onChange={() => toggle(person.id)}
                    disabled={submitting}
                    autoFocus={index === 0}
                  />
                  <span>
                    <b>{person.fullName}</b>
                    <small>{person.phone}</small>
                  </span>
                </label>
              ))}
              <p className="field__help">
                Chọn được nhiều người cùng dọn một phòng. Một người trong nhóm bấm «Hoàn thành» là xong
                cho cả nhóm.
              </p>
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
          <button type="submit" className="btn btn--primary" disabled={submitting || selected.size === 0}>
            {submitting
              ? 'Đang lưu…'
              : `${adding ? 'Thêm' : 'Giao việc'}${selected.size > 1 ? ` cho ${selected.size} người` : ''}`}
          </button>
        </div>
      </form>
    </div>
  );
}
