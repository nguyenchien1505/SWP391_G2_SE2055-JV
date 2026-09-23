import { useState } from 'react';
import { roomStatusMeta } from '../../pages/rooms/roomLabels';

/** Khớp @Size / @Positive của CreateRoomRequest và UpdateRoomRequest. */
const MAX = { roomNumber: 20, floor: 10, note: 500 };

const EMPTY = { locationId: '', roomNumber: '', floor: '', roomTypeId: '', capacity: '2', note: '' };

/**
 * S-03 Form tạo / sửa phòng — RM-02, RM-03.
 *
 * Chỉ Giám đốc thấy form này; ẩn nút với vai trò khác chỉ để dễ dùng, quyền thật do backend
 * quyết định (gọi thẳng API vẫn nhận 403).
 *
 * Theo khuôn `LocationForm.jsx`: `onSubmit(payload)` trả về CÂU LỖI để hiện trong form, hoặc
 * `null` khi thành công. Trang cha quyết định gọi API tạo hay sửa.
 *
 * @param editing   phòng đang sửa; `null` = đang tạo mới
 * @param locations danh sách khách sạn (chỉ cần khi tạo) — `null` nghĩa là đang tải
 * @param roomTypes danh mục loại phòng, gồm cả loại đã ẩn
 */
export default function RoomForm({ editing, locations, roomTypes, onSubmit, onCancel }) {
  const [values, setValues] = useState(() => initialValues(editing));
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const typeOptions = selectableRoomTypes(roomTypes, editing);
  const locationName = (locations ?? []).find((item) => item.id === editing?.locationId)?.name;

  function setField(field, value) {
    setValues((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);

    const message = await onSubmit(toPayload(values, editing));
    setSubmitting(false);

    if (message) {
      setError(message);
    } else if (!editing) {
      // Tạo xong thì dọn form để nhập tiếp phòng kế bên, giữ nguyên khách sạn đang chọn.
      setValues({ ...EMPTY, locationId: values.locationId });
    }
  }

  return (
    <form className="form room-form" onSubmit={handleSubmit} noValidate>
      <header className="form__head">
        <span className="form__icon" aria-hidden="true">
          🛏
        </span>
        <div>
          <h2>{editing ? `Sửa phòng ${editing.roomNumber}` : 'Thêm phòng mới'}</h2>
          <p className="muted">
            {editing
              ? 'Cập nhật thông tin phòng. Trạng thái và khách sạn không đổi ở đây.'
              : 'Khai báo một phòng cho khách sạn trong chuỗi'}
          </p>
        </div>
      </header>

      {/* Chuyển phòng sang khách sạn khác không phải nghiệp vụ có thật, nên khi sửa thì bỏ ô này. */}
      {!editing && (
        <label className="field" htmlFor="room-location">
          <span className="field__label">
            Khách sạn <b className="req">*</b>
          </span>
          <select
            id="room-location"
            value={values.locationId}
            onChange={(e) => setField('locationId', e.target.value)}
            required
          >
            <option value="">{locations === null ? 'Đang tải…' : '— Chọn khách sạn —'}</option>
            {(locations ?? []).map((location) => (
              <option key={location.id} value={location.id}>
                {location.name}
              </option>
            ))}
          </select>
        </label>
      )}

      <div className="field-row">
        <label className="field" htmlFor="room-number">
          <span className="field__label">
            Số phòng <b className="req">*</b>
          </span>
          <input
            id="room-number"
            value={values.roomNumber}
            onChange={(e) => setField('roomNumber', e.target.value)}
            maxLength={MAX.roomNumber}
            placeholder="301"
            required
          />
        </label>

        <label className="field" htmlFor="room-floor">
          <span className="field__label">
            Tầng <b className="req">*</b>
          </span>
          <input
            id="room-floor"
            value={values.floor}
            onChange={(e) => setField('floor', e.target.value)}
            maxLength={MAX.floor}
            placeholder="3"
            required
          />
          <span className="field__help">Chấp nhận chữ: G, M, B1.</span>
        </label>
      </div>

      <div className="field-row">
        <label className="field" htmlFor="room-type">
          <span className="field__label">
            Loại phòng <b className="req">*</b>
          </span>
          <select
            id="room-type"
            value={values.roomTypeId}
            onChange={(e) => setField('roomTypeId', e.target.value)}
            required
          >
            <option value="">— Chọn loại phòng —</option>
            {typeOptions.map((type) => (
              <option key={type.id} value={type.id}>
                {type.name}
                {type.active ? '' : ' (đã ngừng dùng)'}
              </option>
            ))}
          </select>
        </label>

        <label className="field" htmlFor="room-capacity">
          <span className="field__label">
            Sức chứa <b className="req">*</b>
          </span>
          <input
            id="room-capacity"
            type="number"
            min="1"
            value={values.capacity}
            onChange={(e) => setField('capacity', e.target.value)}
            required
          />
          <span className="field__help">Số khách tối đa, tính bằng người.</span>
        </label>
      </div>

      <label className="field" htmlFor="room-note">
        <span className="field__label">Ghi chú</span>
        <textarea
          id="room-note"
          value={values.note}
          onChange={(e) => setField('note', e.target.value)}
          maxLength={MAX.note}
          rows={3}
          placeholder="Phòng góc, view hồ…"
        />
        <span className="field__help">
          {values.note.length}/{MAX.note} ký tự.
        </span>
      </label>

      <div className="readonly-box">
        {editing ? (
          <>
            <b>Trạng thái hiện tại</b>
            <p>
              {roomStatusMeta(editing.status).label}
              {locationName ? ` · ${locationName}` : ''}
            </p>
            <small className="muted">
              Trạng thái chỉ đổi qua thao tác vận hành: nhận / trả phòng, khóa phòng, dọn phòng.
            </small>
          </>
        ) : (
          <>
            <b>Phòng mới tạo</b>
            <p>
              Luôn ở trạng thái <b>Chờ dọn</b> và tự sinh một việc dọn phòng chưa phân công.
            </p>
            <small className="muted">
              Phòng đã xóa vẫn tính vào hạn mức phòng của gói dịch vụ.
            </small>
          </>
        )}
      </div>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      <div className="form__actions">
        <button type="button" className="btn btn--ghost" onClick={onCancel}>
          {editing ? 'Hủy bỏ' : 'Đóng'}
        </button>
        <button type="submit" className="btn btn--primary" disabled={submitting}>
          {submitting ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Lưu phòng'}
        </button>
      </div>
    </form>
  );
}

function initialValues(editing) {
  if (!editing) return EMPTY;
  return {
    locationId: editing.locationId ?? '',
    roomNumber: editing.roomNumber ?? '',
    floor: editing.floor ?? '',
    roomTypeId: editing.roomTypeId ?? '',
    capacity: String(editing.capacity ?? ''),
    note: editing.note ?? '',
  };
}

/**
 * Chỉ liệt kê loại phòng đang dùng. Ngoại lệ: phòng đang sửa vốn thuộc một loại đã ẩn thì loại
 * đó vẫn phải xuất hiện, nếu không Giám đốc buộc phải đổi loại chỉ để sửa được ghi chú.
 */
function selectableRoomTypes(roomTypes, editing) {
  const active = (roomTypes ?? []).filter((type) => type.active);
  const current = (roomTypes ?? []).find((type) => type.id === editing?.roomTypeId);
  return current && !current.active ? [current, ...active] : active;
}

/** Ô số để trống gửi lên `null` để backend báo "bắt buộc", không phải "phải lớn hơn 0". */
function toPayload(values, editing) {
  const payload = {
    roomNumber: values.roomNumber.trim(),
    floor: values.floor.trim(),
    roomTypeId: values.roomTypeId,
    capacity: values.capacity === '' ? null : Number(values.capacity),
    note: values.note.trim(),
  };
  return editing ? payload : { ...payload, locationId: values.locationId };
}
