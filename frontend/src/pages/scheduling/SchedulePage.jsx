import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { fetchLocation } from '../../api/locations';
import { fetchPositions } from '../../api/organization';
import { fetchPolicy, fetchShiftTemplates, fetchShifts } from '../../api/scheduling';
import { fetchStaffDirectory } from '../../api/users';
import ShiftDialog from '../../components/scheduling/ShiftDialog';
import { formatDate, todayIso } from '../rooms/format';
import {
  UNASSIGNED_REASON_LABEL,
  WEEKDAY_SHORT,
  addDays,
  formatDayMonth,
  formatHours,
  hhmm,
  isoWeekNumber,
  shiftTone,
  timeRange,
  weekDays,
  weekStartOf,
} from './scheduleFormat';
import './scheduling.css';

/** Khóa hàng của ca chưa phân công trong bảng tra ô — không trùng được với id người dùng (UUID). */
const OPEN_ROW = 'OPEN';

/**
 * Bảng xếp lịch làm việc tuần — design.md màn 21 + 22; BR-SCH-02..05, BR-SCH-13..15, BR-SCH-24,
 * BR-PERM-03 (chỉ Quản lý chi nhánh — route bọc RequireBranchManager).
 *
 * Hàng là người, cột là 7 ngày Thứ Hai → Chủ Nhật (BR-SCH-13). Hàng đầu tiên gom các ca CHƯA PHÂN
 * CÔNG (DM-03) vì đó là việc Manager phải xử lý. Ca qua đêm nằm ở cột ngày BẮT ĐẦU (BR-SCH-03).
 *
 * Trang KHÔNG tự kiểm tra quy định xếp ca: backend chặn cứng lúc lưu và trả câu nêu rõ vi phạm điều
 * nào (BR-SCH-02); hộp thoại hiện nguyên văn câu đó. Trang chỉ hiển thị quy định đang áp dụng và số
 * giờ đã xếp mỗi người để Manager tự liệu trước.
 */
export default function SchedulePage() {
  const { user } = useAuth();
  const today = todayIso();

  const [weekStart, setWeekStart] = useState(() => weekStartOf(today));
  const days = useMemo(() => weekDays(weekStart), [weekStart]);

  const [lookups, setLookups] = useState(null); // { people, positionNames, templates, policy, locationName }
  const [lookupError, setLookupError] = useState('');
  const [shifts, setShifts] = useState([]);
  const [loadingShifts, setLoadingShifts] = useState(true);
  const [shiftError, setShiftError] = useState('');
  const [banner, setBanner] = useState(null); // { type, text }
  const [dialog, setDialog] = useState(null); // { shift } để sửa | { staffId, date } để thêm

  // Danh bạ nhân sự, vị trí, mẫu ca, quy định: không đổi theo tuần nên chỉ tải một lần.
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      fetchStaffDirectory({ size: 500 }),
      fetchPositions({ includeInactive: true }),
      // Lấy cả mẫu đã tắt để ca cũ vẫn hiện đúng tên mẫu; ô chọn chỉ lấy mẫu đang dùng.
      fetchShiftTemplates({ includeInactive: true }),
      fetchPolicy(),
      user?.locationId ? fetchLocation(user.locationId).catch(() => null) : null,
    ])
      .then(([people, positions, templates, policy, location]) => {
        if (cancelled) return;
        setLookups({
          people,
          positionNames: Object.fromEntries(positions.map((p) => [p.id, p.name])),
          templates,
          policy,
          locationName: location?.name ?? '',
        });
      })
      .catch((err) => !cancelled && setLookupError(readErrorMessage(err, 'Không tải được danh sách nhân viên và mẫu ca.')));
    return () => {
      cancelled = true;
    };
  }, [user?.locationId]);

  // Bấm chuyển tuần nhanh hơn tốc độ mạng: chỉ nhận kết quả của lần gọi mới nhất.
  const latestRequest = useRef(0);
  const loadShifts = useCallback(async () => {
    const requestId = ++latestRequest.current;
    setLoadingShifts(true);
    setShiftError('');
    try {
      const data = await fetchShifts({ from: days[0], to: days[6] });
      if (requestId === latestRequest.current) setShifts(data);
    } catch (err) {
      if (requestId === latestRequest.current) setShiftError(readErrorMessage(err, 'Không tải được lịch tuần này.'));
    } finally {
      if (requestId === latestRequest.current) setLoadingShifts(false);
    }
  }, [days]);

  useEffect(() => {
    loadShifts();
  }, [loadShifts]);

  // ── Dữ liệu dẫn xuất ───────────────────────────────────────────────────────

  const peopleById = useMemo(() => {
    const map = new Map();
    for (const person of lookups?.people ?? []) {
      map.set(person.id, { ...person, positionName: lookups.positionNames[person.positionId] });
    }
    return map;
  }, [lookups]);

  /** Người xếp ca được: Manager và nhân viên chưa nghỉ việc của khách sạn (Giám đốc không có ca). */
  const schedulable = useMemo(
    () => sortPeople([...peopleById.values()].filter(
      (p) => (p.role === 'STAFF' || p.role === 'MANAGER') && p.status !== 'TERMINATED',
    )),
    [peopleById],
  );

  /**
   * Hàng của bảng = người xếp ca được + người KHÔNG còn xếp được nhưng vẫn có ca trong tuần (đã nghỉ
   * việc, đã chuyển khách sạn) — ca của họ vẫn phải hiện ra chứ không được biến mất khỏi lịch.
   */
  const rows = useMemo(() => {
    const listed = new Set(schedulable.map((p) => p.id));
    const others = [...new Set(shifts.map((s) => s.staffId).filter((id) => id && !listed.has(id)))]
      .map((id) => ({ ...(peopleById.get(id) ?? { id, fullName: 'Nhân viên không còn ở khách sạn này' }), readOnly: true }));
    return [...schedulable, ...others];
  }, [schedulable, shifts, peopleById]);

  const cells = useMemo(() => {
    const map = new Map();
    for (const shift of shifts) {
      const key = `${shift.staffId ?? OPEN_ROW}|${shift.shiftDate}`;
      if (!map.has(key)) map.set(key, []);
      map.get(key).push(shift);
    }
    for (const list of map.values()) list.sort((a, b) => hhmm(a.startTime).localeCompare(hhmm(b.startTime)));
    return map;
  }, [shifts]);

  /** Tổng giờ và số ngày có ca của mỗi người trong tuần — để so với quy định trước khi xếp thêm. */
  const weekLoad = useMemo(() => {
    const map = new Map();
    for (const shift of shifts) {
      if (!shift.staffId) continue;
      const entry = map.get(shift.staffId) ?? { hours: 0, days: new Set() };
      entry.hours += Number(shift.durationHours) || 0;
      entry.days.add(shift.shiftDate);
      map.set(shift.staffId, entry);
    }
    return map;
  }, [shifts]);

  const templatesById = useMemo(() => new Map((lookups?.templates ?? []).map((t) => [t.id, t])), [lookups]);
  const activeTemplates = useMemo(() => (lookups?.templates ?? []).filter((t) => t.active), [lookups]);
  const openCount = shifts.filter((s) => !s.staffId).length;
  const staffWithShifts = schedulable.filter((p) => weekLoad.has(p.id)).length;
  const policy = lookups?.policy;
  const maxWeekHours = Number(policy?.maxHoursPerWeek);

  // ── Thao tác ───────────────────────────────────────────────────────────────

  function openCreate(staffId, date) {
    setBanner(null);
    setDialog({ staffId, date });
  }

  function openEdit(shift) {
    setBanner(null);
    setDialog({ shift });
  }

  /** Tải lại lịch TRƯỚC khi báo thành công, để lưới đã đúng khi người dùng đọc thông báo. */
  async function handleSaved(text) {
    setDialog(null);
    await loadShifts();
    setBanner({ type: 'success', text });
  }

  /** Nút "+ Thêm ca" trên đầu trang: mặc định hôm nay nếu đang xem tuần này, không thì Thứ Hai. */
  const defaultDate = days.includes(today) ? today : days[0];

  // ── Hiển thị ───────────────────────────────────────────────────────────────

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Vận hành › Xếp lịch làm việc</p>
          <h1>Xếp lịch làm việc tuần</h1>
          <p className="muted">
            {lookups?.locationName ? `${lookups.locationName} · ` : ''}
            Bấm ô trống để thêm ca, bấm vào ca để giao người, đổi giờ hoặc xóa.
          </p>
        </div>
        <div className="page__actions">
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => openCreate('', defaultDate)}
            disabled={!lookups}
          >
            + Thêm ca
          </button>
        </div>
      </div>

      <div className="sched-toolbar">
        <div className="week-nav" role="group" aria-label="Chọn tuần">
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setWeekStart(addDays(weekStart, -7))}>
            ‹ Tuần trước
          </button>
          <div className="week-nav__label" aria-live="polite">
            Tuần {isoWeekNumber(weekStart)}
            <small>
              {formatDate(days[0])} – {formatDate(days[6])}
            </small>
          </div>
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setWeekStart(addDays(weekStart, 7))}>
            Tuần sau ›
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setWeekStart(weekStartOf(today))}
            disabled={days.includes(today)}
          >
            Tuần này
          </button>
        </div>

        <div className="sched-summary">
          <span className="chip">{shifts.length} ca trong tuần</span>
          <span className={`chip ${openCount > 0 ? 'chip--warn' : ''}`}>{openCount} ca chưa phân công</span>
          <span className="chip">
            {staffWithShifts}/{schedulable.length} người có ca
          </span>
        </div>
      </div>

      {policy && (
        <div className="sched-info">
          <span>
            <b>Quy định xếp ca:</b> tối đa <b>{formatHours(policy.maxHoursPerDay)}</b>/ngày ·{' '}
            <b>{formatHours(policy.maxHoursPerWeek)}</b>/tuần · <b>{policy.maxConsecutiveShifts}</b> ngày làm liền ·
            nghỉ ít nhất <b>{formatHours(policy.minRestHoursBetweenShifts)}</b> giữa 2 ca · ít nhất{' '}
            <b>{policy.minDaysOffPerWeek}</b> ngày nghỉ/tuần
          </span>
          {activeTemplates.length > 0 && (
            <span>
              <b>Mẫu ca:</b>{' '}
              {activeTemplates.map((template, index) => (
                <span key={template.id}>
                  {index > 0 && ' · '}
                  <span className={`legend shift-tone--${shiftTone(template.startTime)}`}>
                    {template.name} {timeRange(template.startTime, template.endTime)}
                  </span>
                </span>
              ))}
            </span>
          )}
        </div>
      )}

      {banner && (
        <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="status">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">
            ×
          </button>
        </div>
      )}
      {lookupError && <div className="alert alert--error" role="alert">{lookupError}</div>}
      {shiftError && <div className="alert alert--error" role="alert">{shiftError}</div>}
      {(!lookups && !lookupError) && <p className="state">Đang tải dữ liệu…</p>}

      {lookups && (
        <div className="sched-scroll">
          <table className="sched-grid" aria-busy={loadingShifts}>
            <thead>
              <tr>
                <th scope="col" className="sched-grid__person">Nhân viên</th>
                {days.map((day, index) => (
                  <th key={day} scope="col" className={dayClass(day, today)}>
                    {WEEKDAY_SHORT[index]}
                    <b>{formatDayMonth(day)}</b>
                    {day === today && <span className="today-chip">Hôm nay</span>}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <tr className="sched-row--open">
                <th scope="row" className="sched-grid__person person-cell">
                  <b>Ca chưa phân công</b>
                  <small>{openCount > 0 ? `${openCount} ca cần giao người` : 'Không còn ca trống'}</small>
                </th>
                {days.map((day) => (
                  <td key={day} className={dayClass(day, today)}>
                    {(cells.get(`${OPEN_ROW}|${day}`) ?? []).map((shift) => (
                      <ShiftChip key={shift.id} shift={shift} template={templatesById.get(shift.sourceTemplateId)} onClick={openEdit} />
                    ))}
                    <AddButton label={`Thêm ca chưa phân công ngày ${formatDate(day)}`} onClick={() => openCreate('', day)} />
                  </td>
                ))}
              </tr>

              {rows.map((person) => {
                const load = weekLoad.get(person.id);
                const over = load && Number.isFinite(maxWeekHours) && load.hours > maxWeekHours;
                return (
                  <tr key={person.id} className={person.readOnly ? 'sched-row--readonly' : ''}>
                    <th scope="row" className="sched-grid__person person-cell">
                      <b>{person.fullName}</b>
                      <small>{personSubtitle(person)}</small>
                      <span className={`person-cell__hours ${over ? 'is-over' : ''}`}>
                        {formatHours(load?.hours ?? 0)}
                        {Number.isFinite(maxWeekHours) ? ` / ${formatHours(maxWeekHours)}` : ''}
                        {load ? ` · ${load.days.size} ngày` : ''}
                      </span>
                    </th>
                    {days.map((day) => (
                      <td key={day} className={dayClass(day, today)}>
                        {(cells.get(`${person.id}|${day}`) ?? []).map((shift) => (
                          <ShiftChip key={shift.id} shift={shift} template={templatesById.get(shift.sourceTemplateId)} onClick={openEdit} />
                        ))}
                        {!person.readOnly && (
                          <AddButton
                            label={`Thêm ca cho ${person.fullName} ngày ${formatDate(day)}`}
                            onClick={() => openCreate(person.id, day)}
                          />
                        )}
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>

          {schedulable.length === 0 && (
            <div className="state state--empty">
              <p>Khách sạn chưa có nhân viên nào để xếp ca.</p>
              <p className="muted">Tạo nhân viên ở mục «Nhân viên chi nhánh», hoặc thêm ca chưa phân công để giao sau.</p>
            </div>
          )}
        </div>
      )}

      <div className="note">
        <span aria-hidden="true">ⓘ</span>
        <div>
          <b>Cách hệ thống kiểm tra khi lưu ca</b>
          <p>
            Ca vi phạm bất kỳ quy định nào ở trên đều bị chặn, không có ngoại lệ. Ca qua đêm thuộc về ngày
            bắt đầu: toàn bộ số giờ tính vào ngày đó và tuần chứa ngày đó. "Ngày làm liền" đếm số ngày có
            ca, hai ca trong một ngày vẫn là một ngày. Trùng giờ chỉ xét các ca trong khách sạn này. Ca chưa
            phân công không bị kiểm tra cho tới khi giao người.
          </p>
        </div>
      </div>

      {dialog && lookups && (
        <ShiftDialog
          shift={dialog.shift}
          initialStaffId={dialog.staffId}
          initialDate={dialog.date}
          locationId={user?.locationId}
          people={schedulable}
          peopleById={peopleById}
          templates={activeTemplates}
          templatesById={templatesById}
          onClose={() => setDialog(null)}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
}

// ── Thành phần con ──────────────────────────────────────────────────────────────

function ShiftChip({ shift, template, onClick }) {
  const open = !shift.staffId;
  const name = template?.name ?? 'Ca tự do';
  const meta = [
    formatHours(shift.durationHours),
    shift.overnight ? 'qua đêm' : null,
    shift.checkInAt ? (shift.checkOutAt ? 'đã ra ca' : 'đã vào ca') : null,
    open && shift.unassignedReason ? UNASSIGNED_REASON_LABEL[shift.unassignedReason] : null,
  ].filter(Boolean);

  return (
    <button
      type="button"
      className={`shift-chip shift-tone--${shiftTone(shift.startTime)} ${open ? 'shift-chip--open' : ''}`}
      onClick={() => onClick(shift)}
      title={`${name} · ${timeRange(shift.startTime, shift.endTime)}${shift.overnight ? ' (kết thúc sáng hôm sau)' : ''}`}
    >
      <span className="shift-chip__name">{name}</span>
      <span className="shift-chip__time">{timeRange(shift.startTime, shift.endTime)}</span>
      <span className="shift-chip__meta">{meta.join(' · ')}</span>
    </button>
  );
}

function AddButton({ label, onClick }) {
  return (
    <button type="button" className="cell-add" onClick={onClick} aria-label={label} title={label}>
      +
    </button>
  );
}

// ── Tiện ích ────────────────────────────────────────────────────────────────────

function dayClass(day, today) {
  if (day === today) return 'is-today';
  return day < today ? 'is-past' : '';
}

function personSubtitle(person) {
  if (person.role === 'MANAGER') return 'Quản lý khách sạn';
  if (person.status === 'TERMINATED') return 'Đã nghỉ việc';
  if (person.readOnly) return 'Không còn thuộc khách sạn này';
  const position = person.positionName ?? 'Nhân viên';
  return person.status === 'INACTIVE' ? `${position} · đang tạm khóa` : position;
}

/** Quản lý lên đầu, rồi theo vị trí, rồi theo tên — so sánh theo tiếng Việt. */
function sortPeople(people) {
  const rank = (p) => (p.role === 'MANAGER' ? 0 : 1);
  return [...people].sort(
    (a, b) =>
      rank(a) - rank(b)
      || (a.positionName ?? '').localeCompare(b.positionName ?? '', 'vi')
      || (a.fullName ?? '').localeCompare(b.fullName ?? '', 'vi'),
  );
}
