import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { readErrorMessage } from '../api/client';
import {
  createLocation,
  deleteLocation,
  fetchLocations,
  updateLocation,
  updateLocationContact,
} from '../api/locations';
import { fetchStaffDirectory, groupStaffByLocation } from '../api/users';
import LocationForm from '../components/LocationForm';
import FormModal from '../components/FormModal';
import StatCard from '../components/StatCard';
import StatusBadge from '../components/StatusBadge';
import ConfirmDialog from '../components/ConfirmDialog';

const PAGE_SIZE = 10;

function initials(name) {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '??';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

export default function LocationsPage() {
  const { user } = useAuth();
  const canManage = user?.role === 'DIRECTOR' || user?.role === 'PLATFORM_ADMIN';

  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState(null);
  const [staffByLocation, setStaffByLocation] = useState({});
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Pop-up tạo / sửa: formOpen = đang mở; editing = null là tạo mới, có giá trị là đang sửa.
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [banner, setBanner] = useState(null); // { type, text }
  const [pendingDelete, setPendingDelete] = useState(null);

  function openForm(row = null) {
    setEditing(row);
    setBanner(null);
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditing(null);
  }

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const data = await fetchLocations({ page, size: PAGE_SIZE });
      setPageData(data);

      // Hai cột "Quản lý phụ trách" và "Quy mô nhân sự" không có trong API /locations,
      // phải suy từ danh sách nhân sự (User giữ locationId, không phải chiều ngược lại).
      try {
        setStaffByLocation(groupStaffByLocation(await fetchStaffDirectory()));
      } catch {
        setStaffByLocation({});
      }
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được danh sách khách sạn.'));
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    load();
  }, [load]);

  const rows = pageData?.content ?? [];

  const visibleRows = useMemo(() => {
    const needle = keyword.trim().toLowerCase();
    return rows.filter((row) => {
      const matchKeyword =
        needle === '' ||
        row.name.toLowerCase().includes(needle) ||
        (row.address ?? '').toLowerCase().includes(needle);
      const matchStatus = statusFilter === 'ALL' || row.status === statusFilter;
      return matchKeyword && matchStatus;
    });
  }, [rows, keyword, statusFilter]);

  const stats = useMemo(() => {
    const totalRooms = rows.reduce((sum, row) => sum + (row.totalRooms ?? 0), 0);
    const operational = rows.filter((row) => row.status === 'OPERATIONAL').length;
    const headcount = Object.values(staffByLocation).reduce(
      (sum, entry) => sum + entry.staffCount + (entry.manager ? 1 : 0),
      0,
    );
    return { totalRooms, operational, headcount };
  }, [rows, staffByLocation]);

  async function handleSubmit(values) {
    try {
      if (!editing) {
        await createLocation(values);
        setBanner({ type: 'success', text: `Đã thêm khách sạn "${values.name}".` });
      } else if (canManage) {
        await updateLocation(editing.id, values);
        setBanner({ type: 'success', text: `Đã cập nhật "${values.name}".` });
      } else {
        // BR-ORG-03: Manager chỉ sửa được thông tin vận hành.
        await updateLocationContact(editing.id, {
          address: values.address,
          phone: values.phone,
        });
        setBanner({ type: 'success', text: 'Đã cập nhật thông tin liên hệ.' });
      }
      closeForm();
      await load();
      return null;
    } catch (err) {
      return readErrorMessage(err, 'Lưu khách sạn không thành công.');
    }
  }

  async function handleConfirmDelete() {
    const target = pendingDelete;
    setPendingDelete(null);
    try {
      await deleteLocation(target.id);
      setBanner({ type: 'success', text: `Đã xóa khách sạn "${target.name}".` });
      if (rows.length === 1 && page > 0) {
        setPage((p) => p - 1);
      } else {
        await load();
      }
    } catch (err) {
      // BR-ORG-05: backend chặn cứng khi còn nhân sự / phòng / khu vực trực thuộc.
      setBanner({ type: 'error', text: readErrorMessage(err, 'Không xóa được khách sạn.') });
    }
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Quản trị chuỗi › Danh sách khách sạn &amp; chi nhánh</p>
          <h1>Quản lý Danh sách Khách sạn</h1>
        </div>
        {canManage && (
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => openForm()}
          >
            + Thêm khách sạn mới
          </button>
        )}
      </div>

      <div className="stat-grid">
        <StatCard
          label="Tổng số khách sạn"
          value={String(pageData?.totalElements ?? 0).padStart(2, '0')}
          note="Thuộc tập đoàn"
          icon="🏢"
          tone="blue"
        />
        <StatCard
          label="Tổng số phòng (trang này)"
          value={stats.totalRooms}
          note="Đếm từ số phòng thực tế"
          icon="🛏"
          tone="indigo"
        />
        <StatCard
          label="Đang hoạt động"
          value={`${stats.operational} / ${rows.length}`}
          note="Đã có quản lý phụ trách"
          icon="✅"
          tone="green"
        />
        <StatCard
          label="Nhân sự trực tiếp"
          value={stats.headcount}
          note="Bao gồm quản lý chi nhánh"
          icon="👥"
          tone="amber"
        />
      </div>

      {banner && (
        <div className={`alert alert--${banner.type === 'error' ? 'error' : 'success'}`} role="alert">
          {banner.text}
          <button type="button" className="alert__close" onClick={() => setBanner(null)}>
            ✕
          </button>
        </div>
      )}

      <div>
        <section className="panel">
          <div className="toolbar">
            <input
              className="toolbar__search"
              type="search"
              placeholder="Tìm theo tên hoặc địa chỉ…"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="ALL">Trạng thái: Tất cả</option>
              <option value="OPERATIONAL">Đang hoạt động</option>
              <option value="NOT_OPERATIONAL">Chưa vận hành</option>
            </select>
          </div>

          <div className="panel__head">
            <h2>
              <span className="dot dot--online" aria-hidden="true" /> Mạng lưới chi nhánh hiện tại
            </h2>
            <span className="chip">
              Hiển thị {visibleRows.length} / {rows.length}
            </span>
          </div>

          {loading && <p className="state">Đang tải dữ liệu…</p>}
          {loadError && <div className="alert alert--error">{loadError}</div>}

          {!loading && !loadError && visibleRows.length === 0 && (
            <div className="state state--empty">
              <p>
                {rows.length === 0
                  ? 'Chưa có khách sạn nào trong hệ thống.'
                  : 'Không có khách sạn nào khớp bộ lọc.'}
              </p>
              {rows.length === 0 && canManage && (
                <p className="muted">Bấm "+ Thêm khách sạn mới" để thêm khách sạn đầu tiên.</p>
              )}
            </div>
          )}

          {!loading && visibleRows.length > 0 && (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Khách sạn &amp; vị trí</th>
                    <th>Quản lý phụ trách</th>
                    <th>Quy mô</th>
                    <th>Trạng thái</th>
                    <th aria-label="Thao tác" />
                  </tr>
                </thead>
                <tbody>
                  {visibleRows.map((row) => {
                    const staff = staffByLocation[row.id];
                    return (
                      <tr key={row.id} className={editing?.id === row.id ? 'is-editing' : ''}>
                        <td>
                          <div className="cell-hotel">
                            <span className="avatar" aria-hidden="true">
                              {initials(row.name)}
                            </span>
                            <div>
                              <b>{row.name}</b>
                              <small>📍 {row.address}</small>
                              <small className="muted">
                                ☎ {row.phone}
                                {row.starRating ? ` · ${row.starRating}★` : ''}
                              </small>
                            </div>
                          </div>
                        </td>
                        <td>
                          {staff?.manager ? (
                            <div className="cell-manager">
                              <b>{staff.manager.fullName}</b>
                              <small>{staff.manager.phone}</small>
                            </div>
                          ) : (
                            <span className="muted">Chưa phân công</span>
                          )}
                        </td>
                        <td>
                          <b className="metric">{row.totalRooms}</b>
                          <small className="muted"> phòng</small>
                          <br />
                          <small className="muted">{staff?.staffCount ?? 0} nhân sự</small>
                        </td>
                        <td>
                          <StatusBadge status={row.status} />
                        </td>
                        <td className="cell-actions">
                          <button
                            type="button"
                            className="btn btn--ghost btn--sm"
                            onClick={() => openForm(row)}
                          >
                            Sửa
                          </button>
                          {canManage && (
                            <button
                              type="button"
                              className="btn btn--danger btn--sm"
                              onClick={() => setPendingDelete(row)}
                            >
                              Xóa
                            </button>
                          )}
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
              Đang quản lý {pageData?.totalElements ?? 0} chi nhánh trực thuộc tập đoàn
            </span>
            <div className="pager">
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                disabled={(pageData?.number ?? 0) === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                ‹
              </button>
              <span>
                Trang {(pageData?.number ?? 0) + 1} / {Math.max(1, pageData?.totalPages ?? 1)}
              </span>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                disabled={(pageData?.number ?? 0) + 1 >= (pageData?.totalPages ?? 1)}
                onClick={() => setPage((p) => p + 1)}
              >
                ›
              </button>
            </div>
          </div>
        </section>
      </div>

      <div className="note">
        <span aria-hidden="true">ⓘ</span>
        <div>
          <b>Quy định thêm khách sạn mới</b>
          <p>
            Khách sạn mới luôn ở trạng thái <b>Chưa vận hành</b> và chỉ chuyển sang{' '}
            <b>Đang hoạt động</b> sau khi được gán một Quản lý chi nhánh. Mỗi khách
            sạn chỉ có đúng một quản lý. Chỉ Giám đốc được thêm và xóa khách sạn; Quản lý chi
            nhánh chỉ cập nhật được địa chỉ và số điện thoại liên hệ.
          </p>
        </div>
      </div>

      {formOpen && (
        <FormModal onClose={closeForm}>
          <LocationForm
            key={editing?.id ?? 'new'}
            editing={editing}
            canManage={canManage}
            onCancel={closeForm}
            onSubmit={handleSubmit}
          />
        </FormModal>
      )}

      {pendingDelete && (
        <ConfirmDialog
          title="Xóa khách sạn?"
          message={
            <>
              Bạn sắp xóa <b>{pendingDelete.name}</b>. Thao tác này không hoàn tác được. Hệ thống
              sẽ từ chối nếu khách sạn còn nhân sự, phòng hoặc khu vực trực thuộc.
            </>
          }
          confirmLabel="Xóa khách sạn"
          onCancel={() => setPendingDelete(null)}
          onConfirm={handleConfirmDelete}
        />
      )}
    </div>
  );
}
