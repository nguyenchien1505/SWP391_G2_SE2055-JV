import { formatDateTime } from '../../pages/rooms/format';
import {
  UNASSIGNED_REASON_LABEL,
  formatHours,
  formatLongDate,
  shiftTone,
  timeRange,
} from '../../pages/scheduling/scheduleFormat';
import { OTHER_ROW, cellKey, groupByCell, lacksReceptionist } from '../../pages/scheduling/scheduleCells';
import { coverageSummary, isReceptionist, personSubtitle } from '../../pages/scheduling/schedulePeople';
import { useCloseOnEscape } from './shiftFields';

/**
 * Chi tiết MỘT ô của bảng xếp lịch — một mẫu ca (hoặc "Ca giờ khác") trong một ngày (chốt 05/10/2026:
 * ô lịch là khối tóm tắt, bấm vào mới xem chi tiết).
 *
 * Liệt kê từng người và từng chỗ trống của ca; mỗi dòng có nút mở hộp thoại sửa ca đó (đổi giờ, gỡ
 * người, giao người cho chỗ trống, xóa). Lưu xong trang quay lại đúng hộp thoại này với dữ liệu mới.
 * Ca theo mẫu đã có người mà chưa có lễ tân thì báo ngay trên đầu — backend chặn mọi thao tác thêm
 * người không kèm lễ tân vào ca như vậy.
 *
 * Mẫu đã tắt hoặc không còn thuộc bộ mẫu chi nhánh đang dùng thì chỉ sửa, gỡ, xóa được ca cũ — không
 * thêm người (backend không cho xếp ca mới theo mẫu đó).
 *
 * @param cell          { date, templateId } — templateId null là hàng "Ca giờ khác"
 * @param templates     mẫu dùng được để xếp ca mới
 * @param templatesById tra mọi mẫu đã biết, kể cả mẫu của ca cũ
 * @param shifts        mọi ca của tuần đang xem
 * @param onEdit     (shift) — mở hộp thoại sửa ca đó
 * @param onAdd      () — mở hộp thoại giao thêm người vào ca này
 */
export default function ShiftCellDialog({
  cell, templates, templatesById, shifts, peopleById, onEdit, onAdd, onClose,
}) {
  useCloseOnEscape(onClose, false);

  const rowId = cell.templateId ?? OTHER_ROW;
  const template = cell.templateId ? templatesById.get(cell.templateId) ?? null : null;
  // Hàng "Ca giờ khác" luôn thêm được (giờ tự do); hàng mẫu chỉ khi mẫu còn dùng được.
  const canAdd = !cell.templateId || templates.some((t) => t.id === cell.templateId);
  const list = groupByCell(shifts, peopleById).get(cellKey(rowId, cell.date)) ?? [];

  const staffed = list.filter((shift) => shift.staffId);
  const open = list.length - staffed.length;
  const coverage = coverageSummary(staffed.map((shift) => peopleById.get(shift.staffId)));
  const missingReception = lacksReceptionist(rowId, list, peopleById);
  const title = cell.templateId ? template?.name ?? 'Mẫu ca khác' : 'Ca giờ khác';

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="cell-dialog-title">
      <div className="modal modal--form modal--assign form">
        <button type="button" className="modal__close" onClick={onClose} aria-label="Đóng">
          ✕
        </button>
        <header className="form__head">
          <span className={`form__icon cell-dialog__icon shift-tone--${shiftTone((template ?? list[0] ?? {}).startTime)}`} aria-hidden="true">
            🗓
          </span>
          <div>
            <h2 id="cell-dialog-title">
              {title} · {formatLongDate(cell.date)}
            </h2>
            <p className="muted">
              {template
                ? `${timeRange(template.startTime, template.endTime)}${template.overnight ? ' (qua đêm)' : ''} · ${formatHours(template.durationHours)}`
                : 'Ca tự nhập giờ — mỗi ca một khung giờ riêng, không bắt buộc lễ tân.'}
            </p>
          </div>
        </header>

        <div className="cell-dialog__summary">
          <span className="chip">{staffed.length} người</span>
          {open > 0 && <span className="chip chip--warn">{open} chỗ trống</span>}
          {coverage && <span className="chip">{coverage}</span>}
        </div>

        {!canAdd && (
          <div className="alert alert--info">
            {template && !template.active ? 'Mẫu này đã tắt' : 'Mẫu này không thuộc bộ mẫu chi nhánh đang dùng'} — không
            xếp thêm ca mới theo mẫu này; các ca bên dưới vẫn sửa, gỡ người hoặc xóa được.
          </div>
        )}

        {missingReception && (
          <div className="alert alert--error" role="alert">
            <b className="alert__title">Ca này chưa có lễ tân</b>
            Mỗi ca theo mẫu đã có người phải có ít nhất 1 lễ tân (người có quyền Lễ tân).{' '}
            {canAdd
              ? 'Bấm «+ Thêm người vào ca» và chọn một lễ tân — chưa có lễ tân thì không thêm được người khác vào ca này.'
              : 'Giao một lễ tân vào chỗ trống của ca, hoặc chỉ bớt người — không thêm được người khác vào ca này.'}
          </div>
        )}

        {list.length === 0 ? (
          <p className="state state--empty">Ca này chưa có ai.</p>
        ) : (
          <ul className="cell-dialog__list">
            {list.map((shift) => (
              <ShiftRow
                key={shift.id}
                shift={shift}
                person={shift.staffId ? peopleById.get(shift.staffId) : null}
                showTime={!template}
                onEdit={onEdit}
              />
            ))}
          </ul>
        )}

        <div className="modal__actions modal__actions--split">
          {canAdd ? (
            <button type="button" className="btn btn--primary" onClick={onAdd}>
              + Thêm người vào ca
            </button>
          ) : (
            <span />
          )}
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}

/** Một người (hoặc một chỗ trống) trong ca, kèm nút mở hộp thoại sửa đúng ca đó. */
function ShiftRow({ shift, person, showTime, onEdit }) {
  const open = !shift.staffId;
  const name = open ? 'Chỗ trống' : (person?.fullName ?? 'Nhân viên không còn ở khách sạn này');
  const details = [
    showTime ? `${timeRange(shift.startTime, shift.endTime)}${shift.overnight ? ' (qua đêm)' : ''}` : null,
    shift.checkInAt ? `Vào ca ${formatDateTime(shift.checkInAt)}` : null,
    shift.checkOutAt ? `ra ca ${formatDateTime(shift.checkOutAt)}` : null,
    open && shift.unassignedReason ? UNASSIGNED_REASON_LABEL[shift.unassignedReason] : null,
  ].filter(Boolean);

  return (
    <li className={`cell-dialog__row ${open ? 'is-open' : ''}`}>
      <span className="cell-dialog__who">
        <b>
          {name}
          {isReceptionist(person) && <span className="badge badge--blue cell-dialog__tag">Lễ tân</span>}
        </b>
        {person && <small>{personSubtitle(person)}</small>}
        {details.length > 0 && <small>{details.join(' · ')}</small>}
      </span>
      <button type="button" className="btn btn--ghost btn--sm" onClick={() => onEdit(shift)}>
        {open ? 'Giao người' : 'Sửa / gỡ'}
      </button>
    </li>
  );
}
