import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { readErrorMessage } from '../../api/client';
import { fetchLocation } from '../../api/locations';
import { fetchPositions } from '../../api/organization';
import { fetchPolicy, fetchShiftTemplate, fetchShiftTemplates, fetchShifts } from '../../api/scheduling';
import { fetchStaffDirectory } from '../../api/users';
import CreateShiftsDialog from '../../components/scheduling/CreateShiftsDialog';
import EditShiftDialog from '../../components/scheduling/EditShiftDialog';
import ShiftBoard from '../../components/scheduling/ShiftBoard';
import ShiftCellDialog from '../../components/scheduling/ShiftCellDialog';
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
import { groupByCell, lacksReceptionist } from './scheduleCells';
import { isSchedulable, sortPeople } from './schedulePeople';
import './scheduling.css';

/**
 * Bảng xếp lịch làm việc tuần — design.md màn 21 + 22; BR-SCH-02..05, BR-SCH-13..15, BR-SCH-24,
 * BR-PERM-03 (chỉ Quản lý chi nhánh — route bọc RequireBranchManager).
 *
 * Xem theo ca: hàng là mẫu ca + "Ca giờ khác", mỗi ô là một khối tóm tắt những người làm ca đó — nhìn
 * ra ngay ca nào thiếu người, ca nào thiếu lễ tân. Bấm khối để xem chi tiết và thao tác; ô trống bấm +
 * để giao ca cho nhiều người một lúc (chốt 05/10/2026 — đã bỏ cách xem theo nhân viên).
 *
 * Chỉ xếp ca cho nhân viên; quản lý khách sạn không có ca. Mỗi ca theo mẫu đã có người phải có ít
 * nhất 1 lễ tân (chốt 05/10/2026).
 *
 * Trang KHÔNG tự kiểm tra quy định xếp ca: backend chặn cứng lúc lưu và trả câu nêu rõ vi phạm điều
 * nào (BR-SCH-02); hộp thoại hiện nguyên văn câu đó.
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
  // { kind: 'cell', cell } | { kind: 'create', initial, returnTo? } | { kind: 'edit', shift, returnTo? }
  // returnTo = ô đang xem chi tiết: đóng / lưu xong hộp thoại con thì quay lại đúng ô đó.
  const [dialog, setDialog] = useState(null);

  // Danh bạ nhân sự, vị trí, mẫu ca, quy định: không đổi theo tuần nên chỉ tải một lần.
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      fetchStaffDirectory({ size: 500 }),
      fetchPositions({ includeInactive: true }),
      // Mẫu chung + mẫu riêng của khách sạn mình (backend tự giới hạn). Lấy cả mẫu đã tắt để ca cũ vẫn
      // hiện đúng tên mẫu; ô chọn chỉ lấy mẫu đang dùng.
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

  /** Người giao ca được: nhân viên chưa nghỉ việc của khách sạn (quản lý và Giám đốc không có ca). */
  const schedulable = useMemo(() => sortPeople([...peopleById.values()].filter(isSchedulable)), [peopleById]);

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

  // Mẫu của ca cũ không có trong bộ chi nhánh đang dùng (chi nhánh đã đổi bộ mẫu): tra từng mẫu theo id
  // để hàng của nó hiện đúng tên. Lỗi tra thì hàng hiện "Mẫu ca khác" — không chặn bảng.
  const [extraTemplates, setExtraTemplates] = useState(() => new Map());
  const triedTemplateIds = useRef(new Set()); // mỗi id chỉ tra một lần, kể cả khi tra lỗi
  useEffect(() => {
    if (!lookups) return;
    const known = new Set(lookups.templates.map((t) => t.id));
    const missing = [...new Set(shifts.map((s) => s.sourceTemplateId))]
      .filter((id) => id && !known.has(id) && !triedTemplateIds.current.has(id));
    if (missing.length === 0) return;
    missing.forEach((id) => triedTemplateIds.current.add(id));
    Promise.all(missing.map((id) => fetchShiftTemplate(id).catch(() => null))).then((found) => {
      const loaded = found.filter(Boolean);
      if (loaded.length > 0) {
        setExtraTemplates((prev) => new Map([...prev, ...loaded.map((t) => [t.id, t])]));
      }
    });
  }, [shifts, lookups]);

  const templatesById = useMemo(
    () => new Map([...extraTemplates, ...(lookups?.templates ?? []).map((t) => [t.id, t])]),
    [lookups, extraTemplates],
  );
  // Mẫu dùng được để xếp ca mới: đang dùng và thuộc bộ chi nhánh đang dùng (backend đã lọc bộ).
  const activeTemplates = useMemo(() => (lookups?.templates ?? []).filter((t) => t.active), [lookups]);

  /** Số ô ca theo mẫu đã có người mà chưa có lễ tân trong tuần — để Manager rà lại lịch. */
  const missingReceptionCount = useMemo(() => {
    let count = 0;
    for (const [key, list] of groupByCell(shifts, peopleById)) {
      if (lacksReceptionist(key.split('|')[0], list, peopleById)) count += 1;
    }
    return count;
  }, [shifts, peopleById]);

  const openCount = shifts.filter((s) => !s.staffId).length;
  const staffWithShifts = schedulable.filter((p) => weekLoad.has(p.id)).length;
  const policy = lookups?.policy;
  const maxWeekHours = Number(policy?.maxHoursPerWeek);

  // ── Thao tác ───────────────────────────────────────────────────────────────

  function openCreate(initial, returnTo) {
    setBanner(null);
    setDialog({ kind: 'create', initial, returnTo });
  }

  function openCell(cell) {
    setBanner(null);
    setDialog({ kind: 'cell', cell });
  }

  /** Giao ca cho một ô: theo mẫu của hàng đó, hoặc giờ tự do với hàng "Ca giờ khác". */
  const initialFor = ({ date, templateId }) => (templateId ? { date, templateId } : { date, mode: 'free' });

  /** Đóng hộp thoại con: có ô đang xem dở thì quay lại ô đó. */
  function closeDialog() {
    setDialog((current) => (current?.returnTo ? { kind: 'cell', cell: current.returnTo } : null));
  }

  /** Tải lại lịch TRƯỚC khi báo thành công, để lưới (và ô đang xem) đã đúng khi người dùng đọc thông báo. */
  async function handleSaved(text) {
    const back = dialog?.returnTo;
    setDialog(null);
    await loadShifts();
    setBanner({ type: 'success', text });
    if (back) setDialog({ kind: 'cell', cell: back });
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
            Bấm vào một ô để xem chi tiết ca, thêm người, sửa hoặc gỡ; ô trống bấm + để giao ca.
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

        <div className="sched-summary">
          <span className="chip">{shifts.length} ca trong tuần</span>
          <span className={`chip ${openCount > 0 ? 'chip--warn' : ''}`}>{openCount} chỗ chưa phân công</span>
          {missingReceptionCount > 0 && (
            <span className="chip chip--danger">{missingReceptionCount} ca thiếu lễ tân</span>
          )}
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
            <b>{policy.minDaysOffPerWeek}</b> ngày nghỉ/tuần · mỗi ca theo mẫu có ít nhất <b>1 lễ tân</b>
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
          <ShiftBoard
            days={days}
            today={today}
            templates={activeTemplates}
            templatesById={templatesById}
            shifts={shifts}
            peopleById={peopleById}
            onOpenCell={openCell}
            onAdd={(cell) => openCreate(initialFor(cell))}
          />

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
            Chỉ xếp ca cho nhân viên; quản lý khách sạn không có ca. Một ca có thể có nhiều người, cùng quyền
            hay khác quyền; mỗi người là một ca riêng và được kiểm tra riêng. Một người không được có hai ca
            chồng giờ nhau. Mỗi ca theo mẫu đã có người phải có ít nhất 1 lễ tân: không thêm được người vào ca
            chưa có lễ tân nếu không kèm một lễ tân, và không gỡ, xóa hay dời được lễ tân duy nhất của ca khi
            ca còn người khác — "Ca giờ khác" không bắt buộc. Ca vi phạm bất kỳ quy định nào ở trên đều bị
            chặn, không có ngoại lệ; giao cho nhiều người mà có người vi phạm thì chưa lưu ai. Ca qua đêm thuộc
            về ngày bắt đầu: toàn bộ số giờ tính vào ngày đó và tuần chứa ngày đó. "Ngày làm liền" đếm số ngày
            có ca, hai ca trong một ngày vẫn là một ngày. Chỗ chưa phân công không bị kiểm tra cho tới khi
            giao người.
          </p>
        </div>
      </div>

      {dialog?.kind === 'cell' && lookups && (
        <ShiftCellDialog
          cell={dialog.cell}
          templates={activeTemplates}
          templatesById={templatesById}
          shifts={shifts}
          peopleById={peopleById}
          onEdit={(shift) => setDialog({ kind: 'edit', shift, returnTo: dialog.cell })}
          onAdd={() => openCreate(initialFor(dialog.cell), dialog.cell)}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === 'create' && lookups && (
        <CreateShiftsDialog
          locationId={user?.locationId}
          days={days}
          shifts={shifts}
          people={schedulable}
          peopleById={peopleById}
          weekLoad={weekLoad}
          maxWeekHours={maxWeekHours}
          templates={activeTemplates}
          initial={dialog.initial}
          onClose={closeDialog}
          onSaved={handleSaved}
        />
      )}
      {dialog?.kind === 'edit' && lookups && (
        <EditShiftDialog
          shift={dialog.shift}
          shifts={shifts}
          people={schedulable}
          peopleById={peopleById}
          templates={activeTemplates}
          templatesById={templatesById}
          onClose={closeDialog}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
}
