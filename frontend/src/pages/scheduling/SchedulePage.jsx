import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { fetchLocation } from '../../api/locations';
import { fetchPositions } from '../../api/organization';
import { fetchPolicy, fetchShiftTemplates, fetchShifts } from '../../api/scheduling';
import { fetchStaffDirectory } from '../../api/users';
import CreateShiftsDialog from '../../components/scheduling/CreateShiftsDialog';
import EditShiftDialog from '../../components/scheduling/EditShiftDialog';
import PersonBoard from '../../components/scheduling/PersonBoard';
import ShiftBoard from '../../components/scheduling/ShiftBoard';
import { formatDate, todayIso } from '../rooms/format';
import {
  addDays,
  formatHours,
  isoWeekNumber,
  shiftTone,
  timeRange,
  weekDays,
  weekStartOf,
} from './scheduleFormat';
import { sortPeople } from './schedulePeople';
import './scheduling.css';

const VIEWS = [
  { value: 'SHIFT', label: 'Theo ca' },
  { value: 'PERSON', label: 'Theo nhân viên' },
];

/**
 * Bảng xếp lịch làm việc tuần — design.md màn 21 + 22; BR-SCH-02..05, BR-SCH-13..15, BR-SCH-24,
 * BR-PERM-03 (chỉ Quản lý chi nhánh — route bọc RequireBranchManager).
 *
 * Hai cách xem cùng một dữ liệu tuần:
 * - Theo ca (mặc định): hàng là mẫu ca + "Ca giờ khác", mỗi ô là những người làm ca đó — nhìn ra ngay
 *   ca nào thiếu người. Bấm + trong ô để giao ca đó cho nhiều người một lúc.
 * - Theo nhân viên: hàng là người, kèm tổng giờ tuần — để theo dõi ai sắp chạm giới hạn quy định.
 *
 * Trang KHÔNG tự kiểm tra quy định xếp ca: backend chặn cứng lúc lưu và trả câu nêu rõ vi phạm điều
 * nào (BR-SCH-02); hộp thoại hiện nguyên văn câu đó.
 */
export default function SchedulePage() {
  const { user } = useAuth();
  const today = todayIso();

  const [weekStart, setWeekStart] = useState(() => weekStartOf(today));
  const days = useMemo(() => weekDays(weekStart), [weekStart]);

  const [view, setView] = useState('SHIFT');
  const [lookups, setLookups] = useState(null); // { people, positionNames, templates, policy, locationName }
  const [lookupError, setLookupError] = useState('');
  const [shifts, setShifts] = useState([]);
  const [loadingShifts, setLoadingShifts] = useState(true);
  const [shiftError, setShiftError] = useState('');
  const [banner, setBanner] = useState(null); // { type, text }
  const [dialog, setDialog] = useState(null); // { kind: 'create', initial } | { kind: 'edit', shift }

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

  /** Người giao ca được: Manager và nhân viên chưa nghỉ việc của khách sạn (Giám đốc không có ca). */
  const schedulable = useMemo(
    () => sortPeople([...peopleById.values()].filter(
      (p) => (p.role === 'STAFF' || p.role === 'MANAGER') && p.status !== 'TERMINATED',
    )),
    [peopleById],
  );

  /**
   * Hàng của bảng theo nhân viên = người giao ca được + người KHÔNG còn giao được nhưng vẫn có ca trong
   * tuần (đã nghỉ việc, đã chuyển khách sạn) — ca của họ không được biến mất khỏi lịch.
   */
  const personRows = useMemo(() => {
    const listed = new Set(schedulable.map((p) => p.id));
    const others = [...new Set(shifts.map((s) => s.staffId).filter((id) => id && !listed.has(id)))]
      .map((id) => ({ ...(peopleById.get(id) ?? { id, fullName: 'Nhân viên không còn ở khách sạn này' }), readOnly: true }));
    return [...schedulable, ...others];
  }, [schedulable, shifts, peopleById]);

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

  function openCreate(initial) {
    setBanner(null);
    setDialog({ kind: 'create', initial });
  }

  function openEdit(shift) {
    setBanner(null);
    setDialog({ kind: 'edit', shift });
  }

  /** Tải lại lịch TRƯỚC khi báo thành công, để lưới đã đúng khi người dùng đọc thông báo. */
  async function handleSaved(text) {
    setDialog(null);
    await loadShifts();
    setBanner({ type: 'success', text });
  }

  /** Nút "+ Giao ca" trên đầu trang: mặc định hôm nay nếu đang xem tuần này, không thì Thứ Hai. */
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
            Bấm + trong ô để giao ca cho một hoặc nhiều người; bấm vào tên để đổi giờ, gỡ người hoặc xóa.
          </p>
        </div>
        <div className="page__actions">
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => openCreate({ date: defaultDate })}
            disabled={!lookups}
          >
            + Giao ca
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

        <div className="segmented segmented--flush" role="radiogroup" aria-label="Cách xem lịch">
          {VIEWS.map((option) => (
            <label key={option.value} className={view === option.value ? 'is-selected' : ''}>
              <input
                type="radio"
                name="schedule-view"
                value={option.value}
                checked={view === option.value}
                onChange={() => setView(option.value)}
              />
              {option.label}
            </label>
          ))}
        </div>

        <div className="sched-summary">
          <span className="chip">{shifts.length} ca trong tuần</span>
          <span className={`chip ${openCount > 0 ? 'chip--warn' : ''}`}>{openCount} chỗ chưa phân công</span>
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
        <div aria-busy={loadingShifts}>
          {view === 'SHIFT' ? (
            <ShiftBoard
              days={days}
              today={today}
              templates={activeTemplates}
              shifts={shifts}
              peopleById={peopleById}
              onAdd={({ date, templateId }) =>
                openCreate(templateId ? { date, templateId } : { date, mode: 'free' })}
              onEdit={openEdit}
            />
          ) : (
            <PersonBoard
              days={days}
              today={today}
              rows={personRows}
              shifts={shifts}
              templatesById={templatesById}
              weekLoad={weekLoad}
              maxWeekHours={maxWeekHours}
              onAddForPerson={(staffId, date) => openCreate({ date, staffIds: [staffId] })}
              onAddOpen={(date) => openCreate({ date, openSlots: 1 })}
              onEdit={openEdit}
            />
          )}

          {schedulable.length === 0 && (
            <div className="state state--empty">
              <p>Khách sạn chưa có nhân viên nào để xếp ca.</p>
              <p className="muted">Tạo nhân viên ở mục «Nhân viên chi nhánh», hoặc mở chỗ trống chưa phân công để giao sau.</p>
            </div>
          )}
        </div>
      )}

      <div className="note">
        <span aria-hidden="true">ⓘ</span>
        <div>
          <b>Cách hệ thống kiểm tra khi lưu ca</b>
          <p>
            Một ca có thể có nhiều người, cùng quyền hay khác quyền; mỗi người là một ca riêng và được kiểm
            tra riêng. Một người không được có hai ca chồng giờ nhau. Ca vi phạm bất kỳ quy định nào ở trên
            đều bị chặn, không có ngoại lệ; giao cho nhiều người mà có người vi phạm thì chưa lưu ai. Ca qua
            đêm thuộc về ngày bắt đầu: toàn bộ số giờ tính vào ngày đó và tuần chứa ngày đó. "Ngày làm liền"
            đếm số ngày có ca, hai ca trong một ngày vẫn là một ngày. Chỗ chưa phân công không bị kiểm tra cho
            tới khi giao người.
          </p>
        </div>
      </div>

      {dialog?.kind === 'create' && lookups && (
        <CreateShiftsDialog
          locationId={user?.locationId}
          days={days}
          shifts={shifts}
          people={schedulable}
          weekLoad={weekLoad}
          maxWeekHours={maxWeekHours}
          templates={activeTemplates}
          initial={dialog.initial}
          onClose={() => setDialog(null)}
          onSaved={handleSaved}
        />
      )}
      {dialog?.kind === 'edit' && lookups && (
        <EditShiftDialog
          shift={dialog.shift}
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
