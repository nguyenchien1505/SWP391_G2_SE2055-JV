import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { readErrorMessage } from '../api/client';
import { fetchRoomTypes } from '../api/rooms';
import { fetchAssetCategories } from '../api/assetCategories';
import {
  createDepartment,
  createPosition,
  createRoomType,
  deleteDepartment,
  deletePosition,
  deleteRoomType,
  fetchDepartments,
  fetchPositions,
  setDepartmentActive,
  setPositionActive,
  setRoomTypeActive,
  updateDepartment,
  updatePosition,
  updateRoomType,
} from '../api/organization';
import { POSITION_TYPE_LABEL } from '../components/StaffForm';
import CatalogItemForm, { CATALOG_KINDS } from '../components/catalog/CatalogItemForm';
import CatalogUsageView from '../components/catalog/CatalogUsageView';
import FormModal from '../components/FormModal';
import StatCard from '../components/StatCard';
import ConfirmDialog from '../components/ConfirmDialog';

const PAGE_SIZE = 10;

const TABS = [
  { key: 'roomType', label: '1. Loại phòng', hint: 'Đơn, Đôi, Gia đình…' },
  { key: 'department', label: '2. Phòng ban', hint: 'Tiền sảnh, Buồng phòng…' },
  { key: 'position', label: '3. Vị trí công việc', hint: 'Lễ tân, Nhân viên dọn phòng…' },
];

/** Gọi API ghi theo loại danh mục — một chỗ để trang không phải rẽ nhánh khắp nơi. */
const ACTIONS = {
  roomType: {
    create: (values) => createRoomType(values.name),
    update: (id, values) => updateRoomType(id, values.name),
    setActive: setRoomTypeActive,
    remove: deleteRoomType,
  },
  department: {
    create: (values) => createDepartment(values.name),
    update: (id, values) => updateDepartment(id, values.name),
    setActive: setDepartmentActive,
    remove: deleteDepartment,
  },
  position: {
    create: (values) => createPosition(values),
    update: (id, values) => updatePosition(id, values),
    setActive: setPositionActive,
    remove: deletePosition,
  },
};

/** Vì sao không xóa được — hiện trong hộp xác nhận để Giám đốc biết trước (BR-ORG-10, BR-ROOM-08). */
const DELETE_RULE = {
  roomType: 'Không xóa được nếu còn phòng thuộc loại này, kể cả phòng đã xóa.',
  department: 'Không xóa được nếu phòng ban còn vị trí công việc nào.',
  position: 'Không xóa được nếu có nhân viên giữ vị trí này, kể cả người đã nghỉ việc.',
};

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString('vi-VN') : '—';
}

const byName = (a, b) => a.name.localeCompare(b.name, 'vi');

/**
 * Danh mục cấu hình hệ thống của Giám đốc — thiết kế "qu_n_l_danh_m_c" trong docs/FE_Lâm_Dũng: Loại
 * phòng, Phòng ban, Vị trí công việc (BR-ORG-06, BR-ORG-07, BR-ORG-11). Danh mục tài sản & vật tư
 * (tab thứ 4 của thiết kế) đã có màn riêng nên ở đây chỉ dẫn sang.
 *
 * <p>Bỏ các phần của thiết kế không có trong BR/DB: đơn giá tham chiếu, định mức dọn, sức chứa theo
 * loại (sức chứa nằm ở từng phòng — BR-ROOM-05), mã loại, đồng bộ OTA, lịch sử phiên bản.
 *
 * <p>Ẩn/hiện thay cho xóa (BR-ORG-14); xóa chỉ được khi chưa ai dùng (BR-ORG-10) — backend chặn và
 * trả câu lỗi nêu lý do. Bấm vào tên một mục để xem chính những thứ đang dùng nó (phòng / vị trí /
 * nhân viên) — thứ chặn việc xóa và chịu ảnh hưởng khi sửa.
 */
export default function OrganizationCatalogPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const canManage = user?.role === 'DIRECTOR';

  const [tab, setTab] = useState('roomType');
  const [data, setData] = useState({ roomType: [], department: [], position: [] });
  const [assetCategoryCount, setAssetCategoryCount] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [departmentFilter, setDepartmentFilter] = useState('ALL');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [page, setPage] = useState(0);

  // Pop-up: formOpen = form thêm/sửa (editing null = thêm mới); pending = đang hỏi xác nhận xóa.
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [pending, setPending] = useState(null);
  // Pop-up "đang dùng": { kind, item, parent } — parent là phòng ban khi đi xuống xem một vị trí của nó.
  const [usageOf, setUsageOf] = useState(null);
  const [banner, setBanner] = useState(null); // { type, text, usage? }

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      // Lấy cả mục đã ẩn — Giám đốc phải thấy thì mới hiện lại được (BR-ORG-14).
      const [roomTypes, departments, positions] = await Promise.all([
        fetchRoomTypes({ includeInactive: true }),
        fetchDepartments({ includeInactive: true }),
        fetchPositions({ includeInactive: true }),
      ]);
      setData({ roomType: roomTypes ?? [], department: departments, position: positions });
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được danh mục.'));
    } finally {
      setLoading(false);
    }
    // Chỉ để hiện số trên thẻ thống kê — lỗi ở đây không chặn cả trang.
    fetchAssetCategories()
      .then((list) => setAssetCategoryCount(list.length))
      .catch(() => setAssetCategoryCount(null));
  }, []);

  useEffect(() => {
    if (canManage) {
      load();
    }
  }, [canManage, load]);

  // Đổi tab thì bỏ bộ lọc cũ — mỗi tab có cột khác nhau.
  useEffect(() => {
    setKeyword('');
    setStatusFilter('ALL');
    setDepartmentFilter('ALL');
    setTypeFilter('ALL');
    setPage(0);
  }, [tab]);
  useEffect(() => setPage(0), [keyword, statusFilter, departmentFilter, typeFilter]);

  const departmentById = useMemo(
    () => Object.fromEntries(data.department.map((d) => [d.id, d])),
    [data.department],
  );
  const positionCountByDepartment = useMemo(() => {
    const counts = {};
    for (const p of data.position) counts[p.departmentId] = (counts[p.departmentId] ?? 0) + 1;
    return counts;
  }, [data.position]);

  const rows = useMemo(() => {
    const needle = keyword.trim().toLowerCase();
    return data[tab]
      .filter((item) => {
        const matchKeyword = needle === '' || item.name.toLowerCase().includes(needle);
        const matchStatus =
          statusFilter === 'ALL' || (statusFilter === 'ACTIVE' ? item.active : !item.active);
        const matchDepartment = tab !== 'position' || departmentFilter === 'ALL' || item.departmentId === departmentFilter;
        const matchType = tab !== 'position' || typeFilter === 'ALL' || item.positionType === typeFilter;
        return matchKeyword && matchStatus && matchDepartment && matchType;
      })
      .sort(byName);
  }, [data, tab, keyword, statusFilter, departmentFilter, typeFilter]);

  const totalPages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const pageRows = rows.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  const stats = useMemo(() => {
    const summary = (list) => ({ total: list.length, active: list.filter((i) => i.active).length });
    const activePositions = data.position.filter((p) => p.active);
    const byType = (type) => activePositions.filter((p) => p.positionType === type).length;
    return {
      roomType: summary(data.roomType),
      department: summary(data.department),
      position: summary(data.position),
      positionTypes: `Lễ tân ${byType('RECEPTION')} · Dọn dẹp ${byType('HOUSEKEEPING')} · Khác ${byType('OTHER')}`,
    };
  }, [data]);

  const kindTitle = CATALOG_KINDS[tab].title;

  function openForm(item = null) {
    setEditing(item);
    setBanner(null);
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditing(null);
  }

  async function handleSubmit(values) {
    try {
      if (editing) {
        await ACTIONS[tab].update(editing.id, values);
        setBanner({ type: 'success', text: `Đã cập nhật ${kindTitle.toLowerCase()} "${values.name}".` });
      } else {
        await ACTIONS[tab].create(values);
        setBanner({ type: 'success', text: `Đã thêm ${kindTitle.toLowerCase()} "${values.name}".` });
      }
      closeForm();
      await load();
      return null;
    } catch (err) {
      return readErrorMessage(err, 'Lưu danh mục không thành công.');
    }
  }

  async function toggleActive(item, kind = tab) {
    setBanner(null);
    try {
      await ACTIONS[kind].setActive(item.id, !item.active);
      const hiddenNote =
        kind === 'department' && item.active
          ? ' Các vị trí thuộc phòng ban này cũng không chọn được nữa khi tạo nhân viên.'
          : '';
      setBanner({
        type: 'success',
        text: item.active
          ? `Đã ẩn "${item.name}" khỏi các danh sách chọn.${hiddenNote}`
          : `Đã dùng lại "${item.name}".`,
      });
      await load();
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không đổi được trạng thái.') });
    }
  }

  async function handleDelete() {
    const target = pending;
    setPending(null);
    try {
      await ACTIONS[tab].remove(target.id);
      setBanner({ type: 'success', text: `Đã xóa ${kindTitle.toLowerCase()} "${target.name}".` });
      await load();
    } catch (err) {
      setBanner({
        type: 'error',
        text: readErrorMessage(err, 'Không xóa được mục này.'),
        usage: { kind: tab, item: target },
      });
    }
  }

  function openUsage(kind, item, parent = null) {
    setBanner(null);
    setUsageOf({ kind, item, parent });
  }

  /** Bản đầy đủ của mục (phòng ban, ngày tạo…) — danh sách "đang dùng" chỉ trả vài trường. */
  function fullItem(kind, item) {
    return data[kind].find((i) => i.id === item.id) ?? item;
  }

  // Sửa / xóa từ pop-up "đang dùng": chuyển sang tab của mục đó (vị trí mở từ một phòng ban) để
  // form và hộp xác nhận dùng đúng loại danh mục.
  function editFromUsage() {
    const { kind, item } = usageOf;
    setUsageOf(null);
    setTab(kind);
    openForm(fullItem(kind, item));
  }

  function deleteFromUsage() {
    const { kind, item } = usageOf;
    setUsageOf(null);
    setTab(kind);
    setPending(fullItem(kind, item));
  }

  function toggleFromUsage() {
    const { kind, item } = usageOf;
    setUsageOf(null);
    toggleActive(fullItem(kind, item), kind);
  }

  if (!canManage) {
    return (
      <div className="page">
        <div className="page__head">
          <div>
            <p className="breadcrumb">Cấu hình hệ thống › Danh mục nghiệp vụ toàn chuỗi</p>
            <h1>Danh mục Cấu hình Hệ thống</h1>
          </div>
        </div>
        <div className="alert alert--info">
          Màn hình này dành cho Giám đốc: chỉ Giám đốc tạo và quản lý Loại phòng, Phòng ban, Vị trí công việc
          dùng chung cho cả chuỗi.
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Cấu hình hệ thống › Danh mục nghiệp vụ toàn chuỗi</p>
          <h1>
            Danh mục Cấu hình Hệ thống <span className="chip">Cấp Giám đốc điều hành chuỗi</span>
          </h1>
          <p className="muted">Chuẩn hóa danh mục dùng chung cho mọi khách sạn trong chuỗi.</p>
        </div>
        <div className="page__actions">
          <button type="button" className="btn btn--primary" onClick={() => openForm()}>
            + Thêm {kindTitle.toLowerCase()}
          </button>
        </div>
      </div>

      <div className="stat-grid">
        <StatCard
          label="Loại phòng"
          value={String(stats.roomType.active).padStart(2, '0')}
          note={`Đang dùng / ${stats.roomType.total} loại`}
          icon="🛏"
          tone="blue"
        />
        <StatCard
          label="Phòng ban"
          value={String(stats.department.active).padStart(2, '0')}
          note={`Đang dùng / ${stats.department.total} · ${data.position.length} vị trí`}
          icon="🏢"
          tone="indigo"
        />
        <StatCard
          label="Vị trí công việc"
          value={String(stats.position.active).padStart(2, '0')}
          note={stats.positionTypes}
          icon="🪪"
          tone="green"
        />
        <StatCard
          label="Danh mục tài sản & vật tư"
          value={assetCategoryCount == null ? '—' : String(assetCategoryCount).padStart(2, '0')}
          note="Quản lý ở màn Danh mục tài sản"
          icon="📦"
          tone="amber"
        />
      </div>

      <div className="tabs" role="tablist" aria-label="Loại danh mục">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            className={`tab ${tab === t.key ? 'tab--active' : ''}`}
            onClick={() => setTab(t.key)}
          >
            <b>
              {t.label} <span className="tab__count">{data[t.key].length}</span>
            </b>
            <small>{t.hint}</small>
          </button>
        ))}
        <button type="button" className="tab tab--link" onClick={() => navigate('/danh-muc-tai-san')}>
          <b>4. Danh mục tài sản &amp; Vật tư ↗</b>
          <small>Mở màn Danh mục tài sản</small>
        </button>
      </div>

      {banner && (
        <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="alert">
          {banner.text}
          {banner.usage && (
            <button
              type="button"
              className="link-button"
              onClick={() => openUsage(banner.usage.kind, banner.usage.item)}
            >
              Xem những gì đang dùng mục này ›
            </button>
          )}
          <button type="button" className="alert__close" onClick={() => setBanner(null)}>
            ✕
          </button>
        </div>
      )}

      <section className="panel">
        <div className="toolbar">
          <input
            className="toolbar__search"
            type="search"
            placeholder={`Tìm theo tên ${kindTitle.toLowerCase()}…`}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          {tab === 'position' && (
            <>
              <select value={departmentFilter} onChange={(e) => setDepartmentFilter(e.target.value)}>
                <option value="ALL">Tất cả phòng ban</option>
                {[...data.department].sort(byName).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                    {d.active ? '' : ' (đã ẩn)'}
                  </option>
                ))}
              </select>
              <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
                <option value="ALL">Tất cả loại vị trí</option>
                {Object.entries(POSITION_TYPE_LABEL).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </>
          )}
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="ALL">Trạng thái: Tất cả</option>
            <option value="ACTIVE">Đang dùng</option>
            <option value="HIDDEN">Đã ẩn</option>
          </select>
        </div>

        <div className="panel__head">
          <h2>{kindTitle}</h2>
          <span className="chip">
            Hiển thị {rows.length} / {data[tab].length}
          </span>
        </div>

        {loading && <p className="state">Đang tải dữ liệu…</p>}
        {loadError && <div className="alert alert--error">{loadError}</div>}

        {!loading && !loadError && rows.length === 0 && (
          <div className="state state--empty">
            <p>
              {data[tab].length === 0
                ? `Chưa có ${kindTitle.toLowerCase()} nào.`
                : `Không có ${kindTitle.toLowerCase()} nào khớp bộ lọc.`}
            </p>
            {data[tab].length === 0 && (
              <p className="muted">Bấm "+ Thêm {kindTitle.toLowerCase()}" để tạo mục đầu tiên.</p>
            )}
          </div>
        )}

        {!loading && pageRows.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Tên {kindTitle.toLowerCase()}</th>
                  {tab === 'department' && <th>Vị trí công việc</th>}
                  {tab === 'position' && <th>Phòng ban</th>}
                  {tab === 'position' && <th>Loại (tick sẵn quyền)</th>}
                  <th>Ngày tạo</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {pageRows.map((item) => {
                  const department = tab === 'position' ? departmentById[item.departmentId] : null;
                  return (
                    <tr key={item.id} className={editing?.id === item.id ? 'is-editing' : ''}>
                      <td>
                        <button
                          type="button"
                          className="link-button"
                          title="Xem những gì đang dùng mục này"
                          onClick={() => openUsage(tab, item)}
                        >
                          {item.name}
                        </button>
                      </td>
                      {tab === 'department' && <td>{positionCountByDepartment[item.id] ?? 0} vị trí</td>}
                      {tab === 'position' && (
                        <td>
                          <div className="cell-manager">
                            {department ? (
                              <button
                                type="button"
                                className="link-button"
                                title="Xem các vị trí thuộc phòng ban này"
                                onClick={() => openUsage('department', department)}
                              >
                                {department.name}
                              </button>
                            ) : (
                              <b>{item.departmentName ?? '—'}</b>
                            )}
                            {department && !department.active && <small>Phòng ban đã ẩn — vị trí không chọn được</small>}
                          </div>
                        </td>
                      )}
                      {tab === 'position' && <td>{POSITION_TYPE_LABEL[item.positionType] ?? item.positionType}</td>}
                      <td>{formatDate(item.createdAt)}</td>
                      <td>
                        <span className={`badge badge--${item.active ? 'green' : 'grey'}`}>
                          {item.active ? 'Đang dùng' : 'Đã ẩn'}
                        </span>
                      </td>
                      <td className="cell-actions">
                        <button
                          type="button"
                          className="icon-btn"
                          title="Sửa"
                          aria-label="Sửa"
                          onClick={() => openForm(item)}
                        >
                          ✏️
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
                          title={item.active ? 'Ẩn khỏi danh sách chọn' : 'Dùng lại'}
                          aria-label={item.active ? 'Ẩn khỏi danh sách chọn' : 'Dùng lại'}
                          onClick={() => toggleActive(item)}
                        >
                          {item.active ? '🙈' : '👁'}
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
                          title="Xóa (chỉ khi chưa được dùng)"
                          aria-label="Xóa"
                          onClick={() => setPending(item)}
                        >
                          🗑
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
            {rows.length === 0
              ? '0 kết quả'
              : `${page * PAGE_SIZE + 1} - ${Math.min((page + 1) * PAGE_SIZE, rows.length)} / ${rows.length}`}
          </span>
          <div className="pager">
            <button type="button" className="btn btn--ghost btn--sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              ‹ Trước
            </button>
            <span>
              Trang {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Sau ›
            </button>
          </div>
        </div>
      </section>

      <div className="note">
        <span aria-hidden="true">ⓘ</span>
        <div>
          <b>Quy định danh mục toàn chuỗi</b>
          <p>
            Danh mục dùng chung cho mọi khách sạn trong chuỗi, chỉ Giám đốc tạo và sửa. Tên không được trùng trong
            chuỗi. Mỗi vị trí thuộc đúng một phòng ban và không đổi phòng ban sau khi tạo. Không xóa được mục đang
            được dùng (tính cả nhân viên đã nghỉ việc và phòng đã xóa) — hãy ẩn để bỏ khỏi các danh sách chọn. Ẩn một
            phòng ban thì mọi vị trí bên trong cũng không chọn được nữa. Loại vị trí chỉ để tick sẵn quyền nghiệp vụ khi
            tạo nhân viên. Bấm vào tên một mục để xem những phòng, vị trí, nhân viên đang dùng nó.
          </p>
        </div>
      </div>

      {usageOf && (
        <FormModal wide onClose={() => setUsageOf(null)}>
          <CatalogUsageView
            key={`${usageOf.kind}-${usageOf.item.id}`}
            kind={usageOf.kind}
            item={usageOf.item}
            onClose={() => setUsageOf(null)}
            onEdit={editFromUsage}
            onDelete={deleteFromUsage}
            onToggleActive={toggleFromUsage}
            onOpenPosition={(position) => openUsage('position', position, usageOf)}
            onBack={usageOf.parent ? () => setUsageOf(usageOf.parent) : null}
            backLabel={usageOf.parent ? `Quay lại ${usageOf.parent.item.name}` : ''}
          />
        </FormModal>
      )}

      {formOpen && (
        <FormModal onClose={closeForm}>
          <CatalogItemForm
            key={`${tab}-${editing?.id ?? 'new'}`}
            kind={tab}
            editing={editing}
            departments={data.department}
            onSubmit={handleSubmit}
            onCancel={closeForm}
          />
        </FormModal>
      )}

      {pending && (
        <ConfirmDialog
          title={`Xóa ${kindTitle.toLowerCase()} "${pending.name}"?`}
          message={
            <>
              Không hoàn tác được. {DELETE_RULE[tab]} Bấm vào tên mục để xem những gì đang dùng nó. Nếu chỉ
              muốn ngừng dùng, hãy bấm 🙈 để ẩn thay vì xóa.
            </>
          }
          confirmLabel="Xóa"
          onCancel={() => setPending(null)}
          onConfirm={handleDelete}
        />
      )}
    </div>
  );
}
