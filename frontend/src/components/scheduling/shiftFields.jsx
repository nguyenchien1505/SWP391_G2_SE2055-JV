import { useEffect } from 'react';
import {
  durationHours,
  formatDayMonth,
  formatHours,
  formatLongDate,
  hhmm,
  isOvernight,
  timeRange,
} from '../../pages/scheduling/scheduleFormat';

/**
 * Các phần dùng chung của hộp thoại giao ca và sửa ca.
 *
 * Giá trị giờ ca là `{ mode: 'template' | 'free', templateId, start, end }` — BR-SCH-04 cho đúng hai
 * cách tạo ca: theo mẫu (giờ lấy từ mẫu) hoặc tự nhập giờ.
 */

/** Chọn giờ ca: theo mẫu đang dùng (BR-SCH-22) hoặc giờ tự do. */
export function ShiftTimeFields({ idPrefix, templates, value, onChange, date }) {
  const set = (patch) => onChange({ ...value, ...patch });
  const hours = resolveHours(value, templates);

  return (
    <>
      <div className="segmented" role="radiogroup" aria-label="Cách tạo ca">
        {[
          ['template', 'Theo mẫu ca'],
          ['free', 'Giờ tự do'],
        ].map(([mode, label]) => (
          <label key={mode} className={value.mode === mode ? 'is-selected' : ''}>
            <input
              type="radio"
              name={`${idPrefix}-mode`}
              value={mode}
              checked={value.mode === mode}
              onChange={() => set({ mode })}
            />
            {label}
          </label>
        ))}
      </div>

      {value.mode === 'template' &&
        (templates.length === 0 ? (
          <div className="alert alert--info">
            Chưa có mẫu ca nào đang dùng. Giám đốc tạo mẫu ở mục «Quy định & Mẫu ca»; trong lúc chờ có thể
            nhập giờ tự do.
          </div>
        ) : (
          <fieldset className="choice-group choice-fieldset">
            <legend className="field__label">Mẫu ca</legend>
            {templates.map((template) => (
              <label key={template.id} className={`choice ${value.templateId === template.id ? 'choice--active' : ''}`}>
                <input
                  type="radio"
                  name={`${idPrefix}-template`}
                  checked={value.templateId === template.id}
                  onChange={() => set({ templateId: template.id })}
                />
                <span>
                  <b>{template.name}</b>
                  <small>
                    {timeRange(template.startTime, template.endTime)}
                    {template.overnight ? ' (qua đêm)' : ''} · {formatHours(template.durationHours)}
                  </small>
                </span>
              </label>
            ))}
          </fieldset>
        ))}

      {value.mode === 'free' && (
        <div className="field-row">
          <label className="field" htmlFor={`${idPrefix}-start`}>
            <span className="field__label">Giờ bắt đầu <span className="req">*</span></span>
            <input id={`${idPrefix}-start`} type="time" value={value.start} onChange={(e) => set({ start: e.target.value })} />
          </label>
          <label className="field" htmlFor={`${idPrefix}-end`}>
            <span className="field__label">Giờ kết thúc <span className="req">*</span></span>
            <input id={`${idPrefix}-end`} type="time" value={value.end} onChange={(e) => set({ end: e.target.value })} />
          </label>
        </div>
      )}

      {hours && <ShiftPreview start={hours.start} end={hours.end} date={date} />}
    </>
  );
}

/** BR-SCH-03 — nói rõ ca qua đêm thuộc về ngày nào trước khi Manager bấm lưu. */
function ShiftPreview({ start, end, date }) {
  const overnight = isOvernight(start, end);
  const total = durationHours(start, end);
  return (
    <p className={`shift-preview ${total >= 24 ? 'shift-preview--warn' : ''}`}>
      Ca <b>{timeRange(start, end)}</b> · <b>{formatHours(total)}</b>
      {overnight && date && <> — qua đêm, toàn bộ giờ tính vào ngày {formatDayMonth(date)}</>}
      {total >= 24 && <> — giờ bắt đầu trùng giờ kết thúc nên thành ca 24 giờ</>}.
    </p>
  );
}

/**
 * Ô date hiển thị theo ngôn ngữ của HỆ ĐIỀU HÀNH (máy tiếng Anh hiện 10/05/2026 = tháng/ngày), nên
 * nhắc lại ngày đang chọn theo quy ước dd/MM/yyyy của dự án cho khỏi nhầm.
 */
export function DateHelp({ date }) {
  return <span className="field__help">{date ? formatLongDate(date) : 'Chọn ngày bắt đầu ca.'}</span>;
}

/** design.md mục 5 — thông báo chặn: nền đỏ nhạt, nêu rõ vi phạm điều nào, không cho lưu. */
export function BlockedAlert({ message, title = 'Không lưu được — ca chưa được thay đổi', children }) {
  if (!message) return null;
  return (
    <div className="alert alert--error" role="alert">
      <b className="alert__title">{title}</b>
      {message}
      {children}
    </div>
  );
}

/** Giờ bắt đầu / kết thúc đang chọn, lấy từ mẫu hoặc ô nhập; null khi chưa đủ. */
export function resolveHours(time, templates) {
  if (time.mode === 'template') {
    const template = templates.find((t) => t.id === time.templateId);
    return template ? { start: hhmm(template.startTime), end: hhmm(template.endTime) } : null;
  }
  return time.start && time.end ? { start: time.start, end: time.end } : null;
}

export function useCloseOnEscape(onClose, blocked) {
  useEffect(() => {
    const onKey = (event) => event.key === 'Escape' && !blocked && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose, blocked]);
}
