import { useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import { updatePolicy } from '../../api/scheduling';
import { formatDateTime } from '../../pages/rooms/format';

/**
 * Bốn card của Schedule Policy (chốt bố cục 04/11/2026): «Quy định xếp ca» rộng hết hàng; hàng dưới bên trái là «Yêu
 * cầu đổi ca» rồi «Quy chuẩn chấm công», bên phải là «Nghỉ phép năm». Điện thoại xếp dọc theo đúng thứ tự mảng này.
 *
 * `warn`: cảnh báo nổi bật (chỉ card xếp ca — ràng buộc cứng). `note`: một dòng chữ nhỏ cho điều ảnh hưởng tới quyết
 * định của Giám đốc. Mã BR chỉ ghi trong comment, không hiện trên giao diện.
 */
const GROUPS = [
  {
    // BR-SCH-02, BR-SCH-03, BR-SCH-13..15.
    key: 'schedule',
    title: 'Quy định xếp ca',
    warn: 'Ràng buộc cứng: vi phạm sẽ chặn lưu ca trên toàn hệ thống. Số mới chỉ áp cho ca xếp từ lúc lưu.',
  },
  {
    // BR-SCH-11, BR-SCH-21.
    key: 'swap',
    title: 'Yêu cầu đổi ca',
  },
  {
    // BR-SCH-27, BR-SCH-07 bản sửa.
    key: 'leave',
    title: 'Nghỉ phép năm',
    note: 'Đổi số ngày phép thì quỹ phép của mọi người tính lại ngay.',
  },
  {
    // BR-ATT-02.
    key: 'attendance',
    title: 'Quy chuẩn chấm công',
    note: 'Hệ thống chỉ đánh dấu đi muộn / về sớm, không tự phạt.',
  },
];

/**
 * Giới hạn min/max khớp UpdateSchedulePolicyRequest ở backend (và cột / CHECK của DB), để lỗi hiện ngay dưới ô nhập
 * thay vì đợi server trả 422. `help` hiện trong ô ⓘ cạnh nhãn.
 */
const FIELDS = [
  {
    group: 'schedule',
    key: 'maxHoursPerDay', label: 'Giờ làm tối đa / ngày', unit: 'giờ', min: 0.5, max: 24, step: 0.5,
    help: 'Cộng mọi ca trong ngày; ca qua đêm tính trọn vào ngày bắt đầu ca.',
  },
  {
    group: 'schedule',
    key: 'maxHoursPerWeek', label: 'Giờ làm tối đa / tuần', unit: 'giờ', min: 0.5, max: 168, step: 0.5,
    help: 'Tuần tính từ Thứ Hai đến Chủ Nhật, theo ngày bắt đầu ca.',
  },
  {
    group: 'schedule',
    key: 'maxConsecutiveShifts', label: 'Số ngày làm liền tối đa', unit: 'ngày', min: 1, step: 1, integer: true,
    help: 'Đếm số ngày liên tiếp có ca, không đếm số ca — hai ca trong một ngày vẫn là một ngày.',
  },
  {
    group: 'schedule',
    key: 'minRestHoursBetweenShifts', label: 'Nghỉ tối thiểu giữa 2 ca', unit: 'giờ', min: 0, max: 99.99, step: 0.5,
    help: 'Tính từ lúc kết thúc ca trước tới lúc bắt đầu ca sau, kể cả khi hai ca khác ngày.',
  },
  {
    group: 'schedule',
    key: 'minDaysOffPerWeek', label: 'Ngày nghỉ tối thiểu / tuần', unit: 'ngày', min: 0, max: 7, step: 1, integer: true,
    help: 'Mỗi tuần một người có ca tối đa (7 − số này) ngày khác nhau.',
  },
  {
    group: 'swap',
    key: 'swapResponseTimeoutHours', label: 'Thời gian chờ đồng nghiệp trả lời', unit: 'giờ', min: 1, max: 168, step: 1,
    integer: true,
    help: 'Quá thời gian này mà đồng nghiệp chưa đồng ý thì yêu cầu đổi ca tự hủy. Yêu cầu đã gửi giữ hạn cũ.',
  },
  {
    group: 'leave',
    key: 'annualLeaveDaysPerYear', label: 'Số ngày phép mỗi năm', unit: 'ngày', min: 0, max: 60, step: 1, integer: true,
    help: 'Tích lũy theo tháng làm việc tính từ ngày bắt đầu làm; phép còn dư không chuyển sang năm sau.',
  },
  {
    group: 'leave',
    key: 'annualLeaveNoticeDays', label: 'Báo trước khi xin nghỉ phép', unit: 'ngày', min: 0, max: 30, step: 1, integer: true,
    help: 'Đơn phép năm phải gửi trước ít nhất số ngày này. Nghỉ không lương không cần báo trước.',
  },
  {
    group: 'attendance',
    key: 'lateEarlyThresholdMinutes', label: 'Ngưỡng đi muộn / về sớm', unit: 'phút', min: 0, max: 60, step: 1,
    integer: true,
    help: 'Vào ca trễ hoặc ra ca sớm QUÁ số phút này mới bị đánh dấu đi muộn / về sớm.',
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
 * Mỗi card có «Dùng mặc định» riêng, để trả một nhóm về mặc định không kéo theo nhóm khác. Thanh «Lần sửa gần nhất /
 * Bỏ thay đổi / Lưu quy định» dính đáy màn hình để bấm Lưu được ngay khi đang cuộn.
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
    const message = errors[field.key];
    return (
      <div key={field.key} className="field policy-field">
        <div className="policy-field__row">
          <span className="policy-field__name">
            <label htmlFor={id}>{field.label}</label>
            {/* Chạm (điện thoại) hoặc rê chuột / Tab tới thì hiện giải thích. */}
            <button type="button" className="policy-tip" aria-label={`Giải thích: ${field.help}`}>
              <span aria-hidden="true">ⓘ</span>
              <span role="tooltip" id={`${id}-tip`} className="policy-tip__bubble">
                {field.help}
              </span>
            </button>
          </span>
          <span className="input-unit policy-field__input">
            <input
              id={id}
              type="number"
              inputMode={field.integer ? 'numeric' : 'decimal'}
              min={field.min}
              max={field.max}
              step={field.step}
              value={values[field.key]}
              onChange={(e) => setValues((prev) => ({ ...prev, [field.key]: e.target.value }))}
              aria-invalid={Boolean(message)}
              aria-describedby={message ? `${id}-error` : `${id}-tip`}
            />
            <span className="input-unit__suffix">{field.unit}</span>
          </span>
        </div>
        {message && (
          <p id={`${id}-error`} className="field__help field__help--error policy-field__error">
            {message}
          </p>
        )}
      </div>
    );
  }

  return (
    <form className="form policy-form" onSubmit={handleSubmit} noValidate>
      {!policy && <p className="state">Đang tải quy định…</p>}

      {policy && (
        <div className="policy-cards">
          {GROUPS.map((group) => (
            <section
              key={group.key}
              className={`policy-card policy-card--${group.key}`}
              aria-labelledby={`policy-group-${group.key}`}
            >
              <div className="policy-card__head">
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
              {group.warn && (
                <p className="policy-card__warn">
                  <span aria-hidden="true">⚠</span> {group.warn}
                </p>
              )}
              {group.note && <p className="policy-card__note">{group.note}</p>}
              <div className="policy-card__fields">{fieldsOf(group.key).map(renderField)}</div>
            </section>
          ))}
        </div>
      )}

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
