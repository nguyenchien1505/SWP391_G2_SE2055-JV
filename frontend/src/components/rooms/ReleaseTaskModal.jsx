import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { unassignTask } from '../../api/housekeeping';
import { taskRoomLabel } from '../../pages/rooms/roomLabels';

/** Lựa chọn "gỡ cả nhóm" trong danh sách — không trùng được với id người nào. */
const WHOLE_TEAM = 'ALL';

/**
 * Gỡ người khỏi việc dọn đang làm — BR-PERM-03 "điều chỉnh lịch dọn". Một phòng có thể nhiều người
 * dọn, nên chọn gỡ MỘT người hay CẢ NHÓM; câu giải thích đổi theo lựa chọn vì hai kết cục khác hẳn:
 *   - còn người trong nhóm → việc tiếp tục, phòng vẫn «Đang dọn»;
 *   - không còn ai → việc quay lại hàng chờ, phòng về «Chờ dọn».
 *
 * @param onReleased nhận { task, removedName, emptied } sau khi gỡ để trang cha báo kết quả và tải lại
 */
export default function ReleaseTaskModal({ task, onClose, onReleased }) {
  const team = task.assignees ?? [];
  const [choice, setChoice] = useState(team.length === 1 ? team[0].staffId : '');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  const emptied = choice === WHOLE_TEAM || team.length === 1;
  const picked = team.find((member) => member.staffId === choice);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const updated = await unassignTask(task.id, choice === WHOLE_TEAM ? undefined : choice);
      onReleased({
        task: updated,
        removedName: choice === WHOLE_TEAM ? 'cả nhóm' : picked?.fullName ?? 'người này',
        emptied,
      });
    } catch (err) {
      setError(readErrorMessage(err, 'Không gỡ được người khỏi việc dọn.'));
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="release-task-title"
         onClick={() => !submitting && onClose()}>
      <form className="modal room-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit} noValidate>
        <h2 id="release-task-title">Gỡ người khỏi việc dọn phòng {taskRoomLabel(task)}</h2>

        <div className="modal__body">
          {team.length > 1 ? (
            <fieldset className="choice-list">
              <legend className="field__label">Gỡ ai?</legend>
              {team.map((member) => (
                <label key={member.staffId} className={`choice ${choice === member.staffId ? 'is-selected' : ''}`}>
                  <input
                    type="radio"
                    name="release-who"
                    checked={choice === member.staffId}
                    onChange={() => setChoice(member.staffId)}
                    disabled={submitting}
                  />
                  <span>
                    <b>{member.fullName ?? 'Nhân viên đã nghỉ'}</b>
                  </span>
                </label>
              ))}
              <label className={`choice ${choice === WHOLE_TEAM ? 'is-selected' : ''}`}>
                <input
                  type="radio"
                  name="release-who"
                  checked={choice === WHOLE_TEAM}
                  onChange={() => setChoice(WHOLE_TEAM)}
                  disabled={submitting}
                />
                <span>
                  <b>Cả nhóm ({team.length} người)</b>
                </span>
              </label>
            </fieldset>
          ) : (
            <p>
              Gỡ <b>{team[0]?.fullName ?? 'người dọn'}</b> khỏi việc dọn này.
            </p>
          )}

          {choice && (
            <div className="readonly-box">
              <p>
                {emptied
                  ? 'Không còn ai dọn: việc quay lại hàng chờ phân công và phòng quay về «Chờ dọn».'
                  : 'Những người còn lại tiếp tục dọn; phòng vẫn «Đang dọn».'}
              </p>
            </div>
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
          <button type="submit" className="btn btn--danger" disabled={submitting || !choice}>
            {submitting ? 'Đang gỡ…' : 'Gỡ người'}
          </button>
        </div>
      </form>
    </div>
  );
}
