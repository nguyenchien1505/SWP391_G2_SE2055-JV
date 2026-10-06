import { useCallback, useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import {
  copyCommonTemplates,
  createShiftTemplate,
  fetchPolicy,
  fetchShiftTemplates,
  setLocationOwnTemplates,
  setShiftTemplateActive,
  updateShiftTemplate,
} from '../../api/scheduling';
import ConfirmDialog from '../../components/ConfirmDialog';
import FormModal from '../../components/FormModal';
import SchedulePolicyForm from '../../components/scheduling/SchedulePolicyForm';
import ShiftTemplateForm from '../../components/scheduling/ShiftTemplateForm';
import { useTenantLocations } from '../rooms/useRoomLookups';
import { formatHours, shiftTone, timeRange } from './scheduleFormat';
import './scheduling.css';

/** Ô lọc bộ mẫu: mọi bộ, bộ chung, hoặc bộ riêng của một chi nhánh. */
const ALL_SETS = '';
const COMMON = 'COMMON';

/**
 * Quy định xếp ca & Mẫu ca — design.md màn 14 + 15; BR-SCH-01, BR-SCH-02, BR-SCH-04, BR-SCH-20,
 * BR-SCH-22, BR-PERM-02. Chỉ Giám đốc (route bọc RequireDirector); Manager đọc quy định ngay trên
 * bảng xếp lịch.
 *
 * Mẫu ca không có nút xóa: BR-SCH-22 chỉ cho vô hiệu hóa, vì các ca đã xếp vẫn trỏ vào mẫu để tra
 * lịch sử.
 *
 * Bộ mẫu theo chi nhánh (chốt 06/10/2026): có bộ mẫu CHUNG của chuỗi và bộ mẫu RIÊNG của từng chi
 * nhánh; mỗi chi nhánh dùng ĐÚNG MỘT bộ. Giám đốc soạn bộ riêng (tự thêm từng mẫu hoặc sao chép bộ
 * chung) rồi mới bật — thêm mẫu riêng không tự đổi bộ. Quy định xếp ca vẫn MỘT bộ cho cả chuỗi
 * (BR-SCH-01).
 */
export default function ScheduleRulesPage() {
  const [policy, setPolicy] = useState(null);
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [banner, setBanner] = useState(null); // { type, text }
  const [showInactive, setShowInactive] = useState(true);
  const [editing, setEditing] = useState(null); // null | { locationId } (thêm mới) | mẫu đang sửa
  const [toggling, setToggling] = useState(null); // id mẫu đang bật/tắt
  const [filterSet, setFilterSet] = useState(ALL_SETS);
  const [switching, setSwitching] = useState(null); // { location, own } — đang hỏi xác nhận đổi bộ
  const [busyLocation, setBusyLocation] = useState(null); // id chi nhánh đang đổi bộ / sao chép

  const locations = useTenantLocations(true);
  // Bộ đang dùng vừa đổi ở trang này — danh sách chi nhánh của hook không tự tải lại.
  const [ownOverrides, setOwnOverrides] = useState({});
  const usesOwnSet = (location) => ownOverrides[location.id] ?? Boolean(location.ownShiftTemplates);
  const locationById = (id) => (locations ?? []).find((l) => l.id === id);
  const locationName = (id) => locationById(id)?.name ?? 'Chi nhánh đã xóa';

  const loadTemplates = useCallback(async () => {
    setTemplates(await fetchShiftTemplates({ includeInactive: true }));
  }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.all([fetchPolicy(), fetchShiftTemplates({ includeInactive: true })])
      .then(([loadedPolicy, loadedTemplates]) => {
        if (cancelled) return;
        setPolicy(loadedPolicy);
        setTemplates(loadedTemplates);
      })
      .catch((err) => !cancelled && setLoadError(readErrorMessage(err, 'Không tải được quy định xếp ca.')))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  /** Trả về câu lỗi cho form hiện tại chỗ, hoặc null khi lưu xong — quy ước của ShiftTemplateForm. */
  async function handleSubmitTemplate(payload) {
    const isNew = !editing?.id;
    try {
      if (isNew) {
        await createShiftTemplate(payload);
      } else {
        await updateShiftTemplate(editing.id, payload);
      }
    } catch (err) {
      return readErrorMessage(err, 'Không lưu được mẫu ca.');
    }
    // Đã lưu xong. Tải lại TRƯỚC khi báo thành công để bảng đã có dòng mới khi người dùng đọc
    // thông báo; tải lại lỗi thì bảng cũ vẫn hiện, không được báo nhầm là lưu thất bại.
    try {
      await loadTemplates();
    } catch {
      // Lần tải sau sẽ đúng.
    }
    setEditing(null);
    const inOwnSet = isNew && payload.locationId && !usesOwnSet(locationById(payload.locationId) ?? {});
    setBanner({
      type: 'success',
      text: `Đã ${isNew ? 'thêm' : 'cập nhật'} mẫu ca «${payload.name}».`
        + (inOwnSet ? ` Mẫu nằm trong bộ riêng của ${locationName(payload.locationId)} — chi nhánh vẫn dùng bộ chung cho tới khi bạn bật bộ riêng.` : ''),
    });
    return null;
  }

  async function handleToggle(template) {
    setToggling(template.id);
    setBanner(null);
    try {
      // Thay đúng dòng vừa đổi bằng bản backend trả về — bảng và thông báo đổi cùng lúc.
      const updated = await setShiftTemplateActive(template.id, !template.active);
      setTemplates((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
      setBanner({
        type: 'success',
        text: updated.active
          ? `Đã bật lại mẫu «${updated.name}».`
          : `Đã tắt mẫu «${updated.name}». Manager không chọn được mẫu này khi xếp ca mới; các ca đã xếp giữ nguyên.`,
      });
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không đổi được trạng thái mẫu ca.') });
    } finally {
      setToggling(null);
    }
  }

  async function handleCopy(location) {
    setBusyLocation(location.id);
    setBanner(null);
    try {
      const copied = await copyCommonTemplates(location.id);
      await loadTemplates();
      setBanner({
        type: 'success',
        text: copied.length > 0
          ? `Đã sao chép ${copied.length} mẫu chung vào bộ riêng của ${location.name}. Sửa lại cho hợp với chi nhánh rồi bật bộ riêng.`
          : `Bộ riêng của ${location.name} đã có đủ các mẫu chung đang dùng — không sao chép thêm.`,
      });
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không sao chép được mẫu chung.') });
    } finally {
      setBusyLocation(null);
    }
  }

  async function handleConfirmSwitch() {
    const { location, own } = switching;
    setSwitching(null);
    setBusyLocation(location.id);
    setBanner(null);
    try {
      const result = await setLocationOwnTemplates(location.id, own);
      setOwnOverrides((prev) => ({ ...prev, [location.id]: result.ownShiftTemplates }));
      setBanner({
        type: 'success',
        text: own
          ? `${location.name} đã chuyển sang bộ mẫu riêng (${result.ownActiveTemplates} mẫu). Quản lý chi nhánh chỉ thấy các mẫu này khi xếp ca mới.`
          : `${location.name} đã quay về bộ mẫu chung của chuỗi.`,
      });
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không đổi được bộ mẫu của chi nhánh.') });
    } finally {
      setBusyLocation(null);
    }
  }

  const ownActiveCount = (locationId) => templates.filter((t) => t.locationId === locationId && t.active).length;
  const visible = templates
    .filter((t) => showInactive || t.active)
    .filter((t) => filterSet === ALL_SETS || (filterSet === COMMON ? !t.locationId : t.locationId === filterSet));
  const activeCount = templates.filter((t) => t.active).length;

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Quản trị & hệ thống › Quy định & Mẫu ca</p>
          <h1>Quy định xếp ca & Mẫu ca</h1>
          <p className="muted">
            Một bộ quy định cho mọi khách sạn trong chuỗi. Mỗi chi nhánh dùng đúng một bộ mẫu ca: bộ chung
            của chuỗi, hoặc bộ riêng của chi nhánh đó.
          </p>
        </div>
        <div className="page__actions">
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => {
              setBanner(null);
              setEditing({ locationId: null });
            }}
            disabled={loading || Boolean(loadError)}
          >
            + Thêm mẫu ca
          </button>
        </div>
      </div>

      {banner && (
        <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="status">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)} aria-label="Đóng">
            ×
          </button>
        </div>
      )}
      {loadError && <div className="alert alert--error" role="alert">{loadError}</div>}

      {!loadError && (
        <div className="split">
          <section className="panel">
            <div className="panel__head">
              <h2>
                Mẫu ca làm việc <span className="chip">{activeCount} đang dùng</span>
              </h2>
              <div className="template-filters">
                <select
                  className="template-scope-select"
                  value={filterSet}
                  onChange={(e) => setFilterSet(e.target.value)}
                  aria-label="Lọc theo bộ mẫu"
                >
                  <option value={ALL_SETS}>Mọi bộ mẫu</option>
                  <option value={COMMON}>Bộ chung</option>
                  {(locations ?? []).map((location) => (
                    <option key={location.id} value={location.id}>
                      Bộ riêng — {location.name}
                    </option>
                  ))}
                </select>
                <label className="checkbox checkbox--inline">
                  <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
                  Hiện cả mẫu đã tắt
                </label>
              </div>
            </div>

            {loading && <p className="state">Đang tải dữ liệu…</p>}

            {!loading && visible.length === 0 && (
              <div className="state state--empty">
                <p>{templates.length === 0 ? 'Chưa có mẫu ca nào.' : 'Không có mẫu ca nào khớp bộ lọc.'}</p>
                <p className="muted">
                  Bấm «+ Thêm mẫu ca» để tạo các ca dùng hằng ngày như Ca sáng, Ca chiều, Ca đêm. Manager vẫn
                  xếp được ca tự do khi chưa có mẫu.
                </p>
              </div>
            )}

            {!loading && visible.length > 0 && (
              <div className="table-wrap">
                <table className="table template-table">
                  <thead>
                    <tr>
                      <th>Mẫu ca</th>
                      <th>Thuộc bộ</th>
                      <th>Khung giờ</th>
                      <th>Trạng thái</th>
                      <th aria-label="Thao tác" />
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((template) => {
                      const owner = template.locationId ? locationById(template.locationId) : null;
                      return (
                        <tr key={template.id}>
                          <td>
                            <div className="cell-hotel">
                              <span className={`legend shift-tone--${shiftTone(template.startTime)}`} aria-hidden="true" />
                              <span>
                                <b>{template.name}</b>
                                {template.description && <small>{template.description}</small>}
                              </span>
                            </div>
                          </td>
                          <td>
                            <span className={`badge ${template.locationId ? 'badge--orange' : 'badge--blue'}`}>
                              {template.locationId ? 'Bộ riêng' : 'Bộ chung'}
                            </span>
                            {template.locationId && (
                              <small className="template-set-note">
                                {locationName(template.locationId)}
                                {owner && !usesOwnSet(owner) ? ' · chi nhánh chưa bật bộ riêng' : ''}
                              </small>
                            )}
                          </td>
                          <td className="template-time">
                            {timeRange(template.startTime, template.endTime)}
                            <small className="template-hours">
                              {formatHours(template.durationHours)}
                              {template.overnight && <span className="badge badge--violet">Qua đêm</span>}
                            </small>
                          </td>
                          <td>
                            <span className={`badge ${template.active ? 'badge--green' : 'badge--grey'}`}>
                              {template.active ? 'Đang dùng' : 'Đã tắt'}
                            </span>
                          </td>
                          <td className="cell-actions">
                            <button
                              type="button"
                              className="btn btn--ghost btn--sm"
                              onClick={() => {
                                setBanner(null);
                                setEditing(template);
                              }}
                            >
                              Sửa
                            </button>
                            <button
                              type="button"
                              className="btn btn--ghost btn--sm"
                              onClick={() => handleToggle(template)}
                              disabled={toggling === template.id}
                            >
                              {template.active ? 'Tắt' : 'Bật lại'}
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            <div className="panel__foot">
              <span className="muted">
                Mẫu đã tắt không chọn được khi xếp ca mới nhưng vẫn giữ để tra các ca cũ — không xóa được mẫu.
              </span>
            </div>
          </section>

          <SchedulePolicyForm
            policy={policy}
            onSaved={(updated) => {
              setPolicy(updated);
              setBanner({ type: 'success', text: 'Đã lưu quy định xếp ca. Quy định mới áp cho mọi ca xếp từ bây giờ.' });
            }}
          />
        </div>
      )}

      {!loadError && (locations?.length ?? 0) > 0 && (
        <section className="panel location-sets" aria-labelledby="location-sets-title">
          <div className="panel__head">
            <h2 id="location-sets-title">Bộ mẫu ca của từng chi nhánh</h2>
          </div>
          <p className="muted location-sets__intro">
            Mặc định chi nhánh dùng bộ chung. Muốn chi nhánh có bộ ca của riêng mình: thêm mẫu vào bộ riêng
            (hoặc sao chép bộ chung rồi sửa), sau đó bấm «Chuyển sang bộ riêng». Quản lý chi nhánh chỉ thấy bộ
            đang dùng; ca đã xếp giữ nguyên khi đổi bộ.
          </p>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Chi nhánh</th>
                  <th>Đang dùng</th>
                  <th>Bộ riêng</th>
                  <th aria-label="Thao tác" />
                </tr>
              </thead>
              <tbody>
                {locations.map((location) => {
                  const own = usesOwnSet(location);
                  const ownCount = ownActiveCount(location.id);
                  const busy = busyLocation === location.id;
                  return (
                    <tr key={location.id}>
                      <td><b>{location.name}</b></td>
                      <td>
                        <span className={`badge ${own ? 'badge--orange' : 'badge--blue'}`}>
                          {own ? 'Bộ riêng' : 'Bộ chung'}
                        </span>
                      </td>
                      <td>
                        {ownCount > 0 ? `${ownCount} mẫu đang dùng` : <span className="muted">Chưa có mẫu</span>}
                        {ownCount > 0 && (
                          <button type="button" className="link-button location-sets__view" onClick={() => setFilterSet(location.id)}>
                            Xem
                          </button>
                        )}
                      </td>
                      <td className="cell-actions">
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          disabled={busy}
                          onClick={() => {
                            setBanner(null);
                            setEditing({ locationId: location.id });
                          }}
                        >
                          + Thêm mẫu riêng
                        </button>
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          disabled={busy}
                          onClick={() => handleCopy(location)}
                        >
                          Sao chép mẫu chung
                        </button>
                        {own ? (
                          <button
                            type="button"
                            className="btn btn--ghost btn--sm"
                            disabled={busy}
                            onClick={() => setSwitching({ location, own: false })}
                          >
                            Quay về bộ chung
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="btn btn--primary btn--sm"
                            disabled={busy || ownCount === 0}
                            title={ownCount === 0 ? 'Bộ riêng chưa có mẫu nào đang dùng' : undefined}
                            onClick={() => setSwitching({ location, own: true })}
                          >
                            Chuyển sang bộ riêng
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {editing && (
        <FormModal onClose={() => setEditing(null)}>
          <ShiftTemplateForm
            editing={editing.id ? editing : null}
            initialLocationId={editing.id ? null : editing.locationId}
            locations={locations ?? []}
            onSubmit={handleSubmitTemplate}
            onCancel={() => setEditing(null)}
          />
        </FormModal>
      )}

      {switching && (
        <ConfirmDialog
          title={switching.own
            ? `Chuyển ${switching.location.name} sang bộ mẫu riêng?`
            : `Đưa ${switching.location.name} về bộ mẫu chung?`}
          message={switching.own
            ? `Khi xếp ca mới, quản lý chi nhánh chỉ thấy ${ownActiveCount(switching.location.id)} mẫu trong bộ riêng; mẫu chung không hiện ở chi nhánh này nữa. Ca đã xếp giữ nguyên.`
            : 'Khi xếp ca mới, quản lý chi nhánh thấy lại bộ mẫu chung của chuỗi. Bộ riêng vẫn được giữ để bật lại sau; ca đã xếp giữ nguyên.'}
          confirmLabel={switching.own ? 'Chuyển sang bộ riêng' : 'Về bộ chung'}
          confirmTone="primary"
          onCancel={() => setSwitching(null)}
          onConfirm={handleConfirmSwitch}
        />
      )}
    </div>
  );
}
