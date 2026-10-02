import { useCallback, useEffect, useState } from 'react';
import { readErrorMessage } from '../../api/client';
import {
  createShiftTemplate,
  fetchPolicy,
  fetchShiftTemplates,
  setShiftTemplateActive,
  updateShiftTemplate,
} from '../../api/scheduling';
import FormModal from '../../components/FormModal';
import SchedulePolicyForm from '../../components/scheduling/SchedulePolicyForm';
import ShiftTemplateForm from '../../components/scheduling/ShiftTemplateForm';
import { formatHours, shiftTone, timeRange } from './scheduleFormat';
import './scheduling.css';

/**
 * Quy định xếp ca & Mẫu ca — design.md màn 14 + 15; BR-SCH-01, BR-SCH-02, BR-SCH-04, BR-SCH-20,
 * BR-SCH-22, BR-PERM-02. Chỉ Giám đốc (route bọc RequireDirector); Manager đọc quy định ngay trên
 * bảng xếp lịch.
 *
 * Mẫu ca không có nút xóa: BR-SCH-22 chỉ cho vô hiệu hóa, vì các ca đã xếp vẫn trỏ vào mẫu để tra
 * lịch sử.
 */
export default function ScheduleRulesPage() {
  const [policy, setPolicy] = useState(null);
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [banner, setBanner] = useState(null); // { type, text }
  const [showInactive, setShowInactive] = useState(true);
  const [editing, setEditing] = useState(null); // null | 'new' | mẫu đang sửa
  const [toggling, setToggling] = useState(null); // id mẫu đang bật/tắt

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
    const isNew = editing === 'new';
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
    setBanner({ type: 'success', text: `Đã ${isNew ? 'thêm' : 'cập nhật'} mẫu ca «${payload.name}».` });
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

  const visible = showInactive ? templates : templates.filter((t) => t.active);
  const activeCount = templates.filter((t) => t.active).length;

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Quản trị & hệ thống › Quy định & Mẫu ca</p>
          <h1>Quy định xếp ca & Mẫu ca</h1>
          <p className="muted">Một bộ quy định và một danh mục mẫu ca dùng chung cho mọi khách sạn trong chuỗi.</p>
        </div>
        <div className="page__actions">
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => {
              setBanner(null);
              setEditing('new');
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
              <label className="checkbox checkbox--inline">
                <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
                Hiện cả mẫu đã tắt
              </label>
            </div>

            {loading && <p className="state">Đang tải dữ liệu…</p>}

            {!loading && visible.length === 0 && (
              <div className="state state--empty">
                <p>{templates.length === 0 ? 'Chưa có mẫu ca nào.' : 'Không có mẫu ca nào đang dùng.'}</p>
                <p className="muted">
                  Bấm «+ Thêm mẫu ca» để tạo các ca dùng hằng ngày như Ca sáng, Ca chiều, Ca đêm. Manager vẫn
                  xếp được ca tự do khi chưa có mẫu.
                </p>
              </div>
            )}

            {!loading && visible.length > 0 && (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Mẫu ca</th>
                      <th>Khung giờ</th>
                      <th>Thời lượng</th>
                      <th>Trạng thái</th>
                      <th aria-label="Thao tác" />
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((template) => (
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
                        <td className="template-time">
                          {timeRange(template.startTime, template.endTime)}
                          {template.overnight && (
                            <>
                              {' '}
                              <span className="badge badge--violet">Qua đêm</span>
                            </>
                          )}
                        </td>
                        <td>{formatHours(template.durationHours)}</td>
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
                    ))}
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

      {editing && (
        <FormModal onClose={() => setEditing(null)}>
          <ShiftTemplateForm
            editing={editing === 'new' ? null : editing}
            onSubmit={handleSubmitTemplate}
            onCancel={() => setEditing(null)}
          />
        </FormModal>
      )}
    </div>
  );
}
