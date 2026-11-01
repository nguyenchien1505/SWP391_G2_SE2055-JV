import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { updatePolicy } from '../../api/scheduling';
import { formatDateTime } from '../../pages/rooms/format';

/**
 * Các nhóm tham số của Schedule Policy. Mỗi nhóm có câu ghi chú riêng vì cách áp dụng khác nhau: luật xếp ca
 * chỉ áp cho ca xếp từ lúc lưu, còn quỹ phép tính lại ngay theo số mới.
 */
const GROUPS = [
  {
    key: 'schedule',
    title: 'Xếp ca',
    note: 'Ca vi phạm bất kỳ điều nào đều bị chặn, không có ngoại lệ. Số mới áp cho ca xếp từ lúc lưu; ca đã xếp giữ nguyên.',
  },
  {
    key: 'swap',
    title: 'Đổi ca',
    note: 'Áp cho yêu cầu gửi từ lúc lưu; yêu cầu đã gửi giữ hạn cũ.',
  },
  {
    key: 'leave',
    title: 'Nghỉ phép năm',
    note: 'Quỹ phép tính lại mỗi lần xem, nên đổi số ngày phép thì quỹ của mọi người đổi theo ngay.',
  },
  {
    key: 'attendance',
    title: 'Chấm công',
    note: 'Hệ thống chỉ đánh dấu đi muộn / về sớm, không tự phạt.',
  },
];

/**
 * BR-SCH-02 (5 luật chặn cứng khi xếp ca), BR-SCH-21 (hạn trả lời đổi ca), BR-SCH-27 và BR-SCH-07 bản sửa (phép
 * năm), BR-ATT-02 (ngưỡng đi muộn / về sớm). Giới hạn min/max khớp UpdateSchedulePolicyRequest ở backend (và cột /
 * CHECK của DB), để lỗi hiện ngay trên ô nhập thay vì đợi server trả 422.
 */
const FIELDS = [
  {
    group: 'schedule',
    key: 'maxHoursPerDay', label: 'Giờ làm tối đa mỗi ngày', unit: 'giờ', min: 0.5, max: 24, step: 0.5,
    help: 'Cộng mọi ca trong ngày; ca qua đêm tính vào ngày bắt đầu.',
  },
  {
    group: 'schedule',
    key: 'maxHoursPerWeek', label: 'Giờ làm tối đa mỗi tuần', unit: 'giờ', min: 0.5, max: 168, step: 0.5,
    help: 'Tuần từ Thứ Hai đến Chủ Nhật.',
  },
  {
    group: 'schedule',
    key: 'maxConsecutiveShifts', label: 'Số ngày làm liền tối đa', unit: 'ngày', min: 1, step: 1, integer: true,
    help: 'Đếm số ngày liên tiếp có ca, không đếm số ca.',
  },
  {
    group: 'schedule',
    key: 'minRestHoursBetweenShifts', label: 'Nghỉ tối thiểu giữa 2 ca', unit: 'giờ', min: 0, max: 99.99, step: 0.5,
    help: 'Từ lúc hết ca trước tới lúc vào ca sau.',
  },
  {
    group: 'schedule',
    key: 'minDaysOffPerWeek', label: 'Ngày nghỉ tối thiểu mỗi tuần', unit: 'ngày', min: 0, max: 7, step: 1, integer: true,
    help: 'Mỗi tuần có ca tối đa (7 − số này) ngày.',
  },
  {
    group: 'swap',
    key: 'swapResponseTimeoutHours', label: 'Thời gian chờ đồng nghiệp trả lời', unit: 'giờ', min: 1, max: 168, step: 1,
    integer: true,
    help: 'Quá hạn mà chưa đồng ý thì yêu cầu tự hủy.',
  },
  {
    group: 'leave',
    key: 'annualLeaveDaysPerYear', label: 'Số ngày phép mỗi năm', unit: 'ngày', min: 0, max: 60, step: 1, integer: true,
    help: 'Tích lũy theo tháng làm việc; không chuyển sang năm sau.',
  },
  {
    group: 'leave',
    key: 'annualLeaveNoticeDays', label: 'Báo trước khi xin phép năm', unit: 'ngày', min: 0, max: 30, step: 1, integer: true,
    help: 'Nghỉ không lương không cần báo trước.',
  },
  {
    group: 'attendance',
    key: 'lateEarlyThresholdMinutes', label: 'Ngưỡng đi muộn / về sớm', unit: 'phút', min: 0, max: 60, step: 1,
    integer: true,
    help: 'Trễ / sớm quá số phút này mới bị đánh dấu.',
  },
];

/** BR-SCH-20 và các mặc định M2 — bộ hệ thống sinh sẵn cho Tenant mới. */
const DEFAULTS = {
  maxHoursPerDay: '8',
  maxHoursPerWeek: '48',
  maxConsecutiveShifts: '6',
  minRestHoursBetweenShifts: '12',
  minDaysOffPerWeek: '1',
  swapResponseTimeoutHours: '24',
  annualLeaveDaysPerYear: '12',
  annualLeaveNoticeDays: '3',
  lateEarlyThresholdMinutes: '5',
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
 * Quy định chung của cả chuỗi — BR-SCH-01: một bộ cấp Tenant, chỉ Giám đốc sửa, dùng chung mọi khách sạn.
 * DM-18: sửa đè, không lưu lịch sử. PUT toàn phần nên luôn gửi đủ mọi trường.
 *
 * Mỗi dòng: nhãn và gợi ý bên trái, ô nhập hẹp bên phải (điện thoại xếp dọc). Mỗi nhóm có «Dùng mặc định» riêng,
 * để trả một nhóm về mặc định không kéo theo các nhóm khác.
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
  const fieldsOf = (groupKey) => FIELDS.filter((field) => field.group === groupKey);
  const isGroupDefault = (groupKey) =>
    fieldsOf(groupKey).every(({ key }) => values[key] !== '' && Number(values[key]) === Number(DEFAULTS[key]));

  /** Chỉ điền lại ô của MỘT nhóm — chưa lưu cho tới khi bấm Lưu. */
  function resetGroup(groupKey) {
    setValues((prev) => ({
      ...prev,
      ...Object.fromEntries(fieldsOf(groupKey).map(({ key }) => [key, DEFAULTS[key]])),
    }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const updated = await updatePolicy(Object.fromEntries(FIELDS.map(({ key }) => [key, Number(values[key])])));
      onSaved(updated);
    } catch (err) {
      setError(readErrorMessage(err, 'Không lưu được quy định.'));
    } finally {
      setSubmitting(false);
    }
  }

  function renderField(field) {
    const id = `policy-${field.key}`;
    return (
      <div key={field.key} className="field policy-row">
        <div className="policy-row__text">
          <label className="field__label" htmlFor={id}>
            {field.label} <span className="req">*</span>
          </label>
          <p id={`${id}-help`} className={`field__help ${errors[field.key] ? 'field__help--error' : ''}`}>
            {errors[field.key] || field.help}
          </p>
        </div>
        <span className="input-unit policy-row__input">
          <input
            id={id}
            type="number"
            inputMode={field.integer ? 'numeric' : 'decimal'}
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
      </div>
    );
  }

  return (
    <form className="panel form policy-form" onSubmit={handleSubmit} noValidate>
      <header className="form__head">
        <span className="form__icon" aria-hidden="true">⚖</span>
        <div>
          <h2>Quy định chung</h2>
          <p className="muted">Áp dụng cho mọi khách sạn trong chuỗi.</p>
        </div>
      </header>

      {!policy && <p className="state">Đang tải quy định…</p>}

      {policy &&
        GROUPS.map((group) => (
          <section key={group.key} className="policy-group" aria-labelledby={`policy-group-${group.key}`}>
            <div className="policy-group__head">
              <h3 id={`policy-group-${group.key}`}>{group.title}</h3>
              <button
                type="button"
                className="link-button"
                onClick={() => resetGroup(group.key)}
                disabled={submitting || isGroupDefault(group.key)}
                title="Điền lại giá trị mặc định cho nhóm này — chưa lưu cho tới khi bấm Lưu"
              >
                Dùng mặc định
              </button>
            </div>
            <p className="policy-group__note">{group.note}</p>
            {fieldsOf(group.key).map(renderField)}
          </section>
        ))}

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      <div className="form__actions">
        <span className="field__help policy-form__updated">
          {policy?.updatedAt ? `Lần sửa gần nhất: ${formatDateTime(policy.updatedAt)}` : policy ? 'Đang dùng bộ mặc định của hệ thống.' : ''}
        </span>
        <span className="policy-form__buttons">
          <button
            type="button"
            className="btn btn--ghost"
            onClick={() => setValues(toValues(policy))}
            disabled={!policy || submitting || !dirty}
          >
            Bỏ thay đổi
          </button>
          <button type="submit" className="btn btn--primary" disabled={!policy || submitting || invalid || !dirty}>
            {submitting ? 'Đang lưu…' : 'Lưu quy định'}
          </button>
        </span>
      </div>
    </form>
  );
}
