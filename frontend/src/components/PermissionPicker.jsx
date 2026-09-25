import { useEffect, useRef, useState } from 'react';
import { STAFF_PERMISSIONS } from '../permissions';

/**
 * Ô thả xuống có ô tick để Manager cấp quyền nghiệp vụ cho nhân viên — tick bao nhiêu ô cũng được,
 * không tick ô nào nghĩa là chỉ có quyền chung. `value` là mảng giá trị quyền (`RECEPTION`…).
 */
export default function PermissionPicker({ id, value, onChange }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);

  // Bấm ra ngoài hoặc nhấn Esc thì đóng danh sách.
  useEffect(() => {
    if (!open) return undefined;
    function handlePointer(event) {
      if (!rootRef.current?.contains(event.target)) setOpen(false);
    }
    function handleKey(event) {
      if (event.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', handlePointer);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handlePointer);
      document.removeEventListener('keydown', handleKey);
    };
  }, [open]);

  function toggle(permission) {
    onChange(value.includes(permission) ? value.filter((p) => p !== permission) : [...value, permission]);
  }

  const chosen = STAFF_PERMISSIONS.filter((p) => value.includes(p.value));

  return (
    <div className="multi-select" ref={rootRef}>
      <button
        type="button"
        id={id}
        className="multi-select__toggle"
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <span className={chosen.length === 0 ? 'muted' : undefined}>
          {chosen.length === 0 ? 'Không có quyền nghiệp vụ (chỉ quyền chung)' : chosen.map((p) => p.label).join(', ')}
        </span>
        <span aria-hidden="true">▾</span>
      </button>

      {open && (
        <div className="multi-select__menu" role="listbox" aria-multiselectable="true">
          {STAFF_PERMISSIONS.map((p) => (
            <label key={p.value} className="multi-select__option">
              <input type="checkbox" checked={value.includes(p.value)} onChange={() => toggle(p.value)} />
              <span>
                <b>{p.label}</b>
                <small>{p.hint}</small>
              </span>
            </label>
          ))}
        </div>
      )}
    </div>
  );
}
