import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { updatePolicy } from '../../api/scheduling';
import { formatDateTime } from '../../pages/rooms/format';

/**
 * 5 tham số chặn cứng khi xếp ca — BR-SCH-02. Giới hạn min/max khớp UpdateSchedulePolicyRequest ở
 * backend (và cột DECIMAL của DB), để lỗi hiện ngay trên ô nhập thay vì đợi server trả 422.
 */
const FIELDS = [
  {
    key: 'maxHoursPerDay', label: 'Giờ làm tối đa mỗi ngày', unit: 'giờ', min: 0.5, max: 24, step: 0.5,
    help: 'Cộng mọi ca trong ngày. Ca qua đêm tính trọn số giờ vào ngày bắt đầu ca.',
  },
  {
    key: 'maxHoursPerWeek', label: 'Giờ làm tối đa mỗi tuần', unit: 'giờ', min: 0.5, max: 168, step: 0.5,
    help: 'Tuần tính từ Thứ Hai đến Chủ Nhật, theo ngày bắt đầu ca.',
  },
  {
    key: 'maxConsecutiveShifts', label: 'Số ngày làm liền tối đa', unit: 'ngày', min: 1, step: 1, integer: true,
    help: 'Đếm số ngày liên tiếp có ca, không đếm số ca — hai ca trong một ngày vẫn là một ngày.',
  },
  {
    key: 'minRestHoursBetweenShifts', label: 'Nghỉ tối thiểu giữa 2 ca', unit: 'giờ', min: 0, max: 99.99, step: 0.5,
    help: 'Tính từ lúc kết thúc ca trước đến lúc bắt đầu ca sau, kể cả khi hai ca khác ngày.',
  },
  {
    key: 'minDaysOffPerWeek', label: 'Ngày nghỉ tối thiểu mỗi tuần', unit: 'ngày', min: 0, max: 7, step: 1, integer: true,
    help: 'Mỗi tuần một người có ca tối đa (7 − số này) ngày khác nhau.',
  },
];

/** BR-SCH-20 — bộ mặc định hệ thống sinh sẵn cho Tenant mới. */
const DEFAULTS = {
  maxHoursPerDay: '8',
  maxHoursPerWeek: '48',
  maxConsecutiveShifts: '6',
  minRestHoursBetweenShifts: '12',
  minDaysOffPerWeek: '1',
};

function toValues(policy) {
  return Object.fromEntries(FIELDS.map(({ key }) => [key, policy ? String(Number(policy[key])) : '']));
}

function fieldError(field, raw) {
  if (raw === '' || raw === null) return 'Bắt buộc nhập.';
  const value = Number(raw);
  if (!Number.isFinite(value)) return 'Phải là một số.';
  if (field.integer && !Number.isInteger(value)) return 'Phải là số nguyên.';
  if (value < field.min) return `Không nhỏ hơn ${String(field.min).replace('.', ',')} ${field.unit}.`;
  if (field.max !== undefined && value > field.max) return `Không lớn hơn ${String(field.max).replace('.', ',')} ${field.unit}.`;
  return '';
}

/**
 * Quy định xếp ca của cả chuỗi — BR-SCH-01: một bộ cấp Tenant, chỉ Giám đốc sửa, dùng chung mọi
 * khách sạn. DM-18: sửa đè, không lưu lịch sử.
 *
 * Thời gian chờ phản hồi đổi ca cũng nằm trong bản ghi policy nhưng thuộc nghiệp vụ đổi ca (BR-SCH-21,
 * làm sau) nên không hiện ở đây — gửi lại nguyên giá trị đang có.
 *
 * @param onSaved nhận policy mới sau khi lưu
 */
export default function SchedulePolicyForm({ policy, onSaved }) {
  const [values, setValues] = useState(() => toValues(policy));
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Policy tải xong (hoặc vừa lưu) thì đồng bộ lại ô nhập.
  useEffect(() => {
    setValues(toValues(policy));
  }, [policy]);

  const errors = Object.fromEntries(FIELDS.map((field) => [field.key, fieldError(field, values[field.key])]));
  const invalid = Object.values(errors).some(Boolean);
  const saved = toValues(policy);
  const dirty = FIELDS.some(({ key }) => Number(values[key]) !== Number(saved[key]) || values[key] === '');
  const isDefault = FIELDS.every(({ key }) => Number(values[key]) === Number(DEFAULTS[key]));

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const updated = await updatePolicy({
        ...Object.fromEntries(FIELDS.map(({ key }) => [key, Number(values[key])])),
        swapResponseTimeoutHours: policy.swapResponseTimeoutHours,
      });
      onSaved(updated);
    } catch (err) {
      setError(readErrorMessage(err, 'Không lưu được quy định xếp ca.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="panel panel--form form" onSubmit={handleSubmit} noValidate>
      <header className="form__head">
        <span className="form__icon" aria-hidden="true">⚖</span>
        <div>
          <h2>Quy định xếp ca</h2>
          <p className="muted">Áp dụng chung cho mọi khách sạn. Ca vi phạm bất kỳ điều nào đều bị chặn, không có ngoại lệ.</p>
        </div>
      </header>

      {!policy && <p className="state">Đang tải quy định…</p>}

      {policy &&
        FIELDS.map((field) => {
          const id = `policy-${field.key}`;
          return (
            <label key={field.key} className="field" htmlFor={id}>
              <span className="field__label">
                {field.label} <span className="req">*</span>
              </span>
              <span className="input-unit">
                <input
                  id={id}
                  type="number"
                  inputMode="decimal"
                  min={field.min}
                  max={field.max}
                  step={field.step}
                  value={values[field.key]}
                  onChange={(e) => setValues((prev) => ({ ...prev, [field.key]: e.target.value }))}
                  aria-invalid={Boolean(errors[field.key])}
                  aria-describedby={`${id}-help`}
                />
                <span className="input-unit__suffix">{field.unit}</span>
              </span>
              <span id={`${id}-help`} className={`field__help ${errors[field.key] ? 'field__help--error' : ''}`}>
                {errors[field.key] || field.help}
              </span>
            </label>
          );
        })}

      {policy && (
        <p className="field__help">
          Giá trị mới chỉ áp cho ca xếp từ lúc lưu; ca đã xếp trước đó giữ nguyên.
          {policy.updatedAt ? ` Lần sửa gần nhất: ${formatDateTime(policy.updatedAt)}.` : ' Đang dùng bộ mặc định của hệ thống.'}
        </p>
      )}

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      <div className="form__actions">
        <button
          type="button"
          className="btn btn--ghost"
          onClick={() => setValues(DEFAULTS)}
          disabled={!policy || submitting || isDefault}
          title="Điền lại 8 giờ/ngày, 48 giờ/tuần, 6 ngày liền, nghỉ 12 giờ, 1 ngày nghỉ/tuần — chưa lưu cho tới khi bấm Lưu"
        >
          Khôi phục mặc định
        </button>
        <button type="submit" className="btn btn--primary" disabled={!policy || submitting || invalid || !dirty}>
          {submitting ? 'Đang lưu…' : 'Lưu quy định'}
        </button>
      </div>
    </form>
  );
}
