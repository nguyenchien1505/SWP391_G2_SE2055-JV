import { useState } from 'react';
import { POSITION_TYPE_LABEL } from '../StaffForm';

/** Tiêu đề / biểu tượng / ví dụ của từng loại danh mục. */
export const CATALOG_KINDS = {
  roomType: { title: 'Loại phòng', icon: '🛏', placeholder: 'Ví dụ: Phòng đôi tiêu chuẩn' },
  department: { title: 'Phòng ban', icon: '🏢', placeholder: 'Ví dụ: Buồng phòng' },
  position: { title: 'Vị trí công việc', icon: '🪪', placeholder: 'Ví dụ: Nhân viên lễ tân' },
};

const POSITION_TYPES = ['RECEPTION', 'HOUSEKEEPING', 'OTHER'];

/**
 * Thêm / sửa một mục danh mục (Loại phòng, Phòng ban, Vị trí công việc) — đặt trong FormModal.
 *
 * <p>Vị trí: phòng ban chọn lúc tạo và KHÔNG đổi được sau đó (BR-ORG-07 — "thuộc đúng 1 Department
 * cố định"), chỉ chọn trong các phòng ban đang dùng (BR-ORG-14). Loại vị trí chỉ để tick sẵn quyền
 * nghiệp vụ khi Manager tạo nhân viên; quyền thật Manager tick cho từng người.
 *
 * <p>`onSubmit(values)` trả về chuỗi lỗi (giữ pop-up để sửa) hoặc null khi thành công.
 */
export default function CatalogItemForm({ kind, editing, departments = [], onSubmit, onCancel }) {
  const meta = CATALOG_KINDS[kind];
  const isPosition = kind === 'position';
  const activeDepartments = departments.filter((d) => d.active);

  const [name, setName] = useState(editing?.name ?? '');
  const [departmentId, setDepartmentId] = useState(editing?.departmentId ?? '');
  const [positionType, setPositionType] = useState(editing?.positionType ?? '');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    if (!name.trim()) {
      setError(`Tên ${meta.title.toLowerCase()} là bắt buộc.`);
      return;
    }
    if (isPosition && !editing && !departmentId) {
      setError('Chọn phòng ban cho vị trí này.');
      return;
    }
    if (isPosition && !positionType) {
      setError('Chọn loại vị trí.');
      return;
    }

    setSubmitting(true);
    const values = isPosition
      ? { name: name.trim(), positionType, ...(editing ? {} : { departmentId }) }
      : { name: name.trim() };
    const message = await onSubmit(values);
    setSubmitting(false);
    if (message) {
      setError(message);
    }
  }

  const departmentName = departments.find((d) => d.id === editing?.departmentId)?.name;

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <header className="form__head">
        <span className="form__icon" aria-hidden="true">
          {meta.icon}
        </span>
        <div>
          <h2>{editing ? `Sửa ${meta.title.toLowerCase()}` : `Thêm ${meta.title.toLowerCase()}`}</h2>
          <p className="muted">Dùng chung cho mọi khách sạn trong chuỗi. Tên không được trùng trong chuỗi.</p>
        </div>
      </header>

      <label className="field" htmlFor="catalog-name">
        <span className="field__label">
          Tên {meta.title.toLowerCase()} <b className="req">*</b>
        </span>
        <input
          id="catalog-name"
          value={name}
          maxLength={100}
          onChange={(e) => setName(e.target.value)}
          placeholder={meta.placeholder}
        />
      </label>

      {isPosition &&
        (editing ? (
          <div className="field">
            <span className="field__label">Phòng ban</span>
            <div className="readonly-box">
              {departmentName ?? '—'}
              <small className="muted">Phòng ban của vị trí cố định sau khi tạo.</small>
            </div>
          </div>
        ) : (
          <label className="field" htmlFor="catalog-department">
            <span className="field__label">
              Phòng ban <b className="req">*</b>
            </span>
            <select
              id="catalog-department"
              value={departmentId}
              onChange={(e) => setDepartmentId(e.target.value)}
              disabled={activeDepartments.length === 0}
            >
              <option value="">
                {activeDepartments.length === 0 ? '— Chưa có phòng ban nào đang dùng —' : '— Chọn phòng ban —'}
              </option>
              {activeDepartments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
            <span className="field__help">
              {activeDepartments.length === 0
                ? 'Tạo phòng ban ở tab "Phòng ban" trước.'
                : 'Không đổi được phòng ban sau khi tạo. Phòng ban đang ẩn không nhận thêm vị trí.'}
            </span>
          </label>
        ))}

      {isPosition && (
        <label className="field" htmlFor="catalog-type">
          <span className="field__label">
            Loại vị trí <b className="req">*</b>
          </span>
          <select id="catalog-type" value={positionType} onChange={(e) => setPositionType(e.target.value)}>
            <option value="">— Chọn loại —</option>
            {POSITION_TYPES.map((type) => (
              <option key={type} value={type}>
                {POSITION_TYPE_LABEL[type]}
              </option>
            ))}
          </select>
          <span className="field__help">
            Chỉ dùng để tick sẵn quyền nghiệp vụ khi Manager tạo nhân viên ở vị trí này. Quyền thật của từng
            người do Manager tick, nên đổi loại không làm thay đổi quyền của người đang giữ vị trí.
          </span>
        </label>
      )}

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      <div className="form__actions">
        <button type="button" className="btn btn--ghost" onClick={onCancel}>
          Hủy bỏ
        </button>
        <button type="submit" className="btn btn--primary" disabled={submitting}>
          {submitting ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : '✓ Thêm vào danh mục'}
        </button>
      </div>
    </form>
  );
}
