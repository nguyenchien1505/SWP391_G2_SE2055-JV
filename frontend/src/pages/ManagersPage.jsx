import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { readErrorMessage } from '../api/client';
import { fetchLocations } from '../api/locations';
import {
  createUser,
  fetchUsersByRole,
  resetUserPassword,
  terminateUser,
  updateUser,
} from '../api/users';
import ManagerForm from '../components/ManagerForm';
import FormModal from '../components/FormModal';
import StatCard from '../components/StatCard';
import ConfirmDialog from '../components/ConfirmDialog';
import TempPasswordDialog from '../components/TempPasswordDialog';

const PAGE_SIZE = 10;

/** Trạng thái hiển thị gộp từ `status` và cờ mật khẩu tạm — đúng 4 nhóm lọc của thiết kế. */
function displayStatus(user) {
  if (user.status === 'TERMINATED') return { key: 'TERMINATED', label: 'Đã nghỉ việc', tone: 'grey' };
  if (user.status === 'INACTIVE') return { key: 'INACTIVE', label: 'Đang tạm khóa', tone: 'orange' };
  if (user.mustChangePassword) return { key: 'MUST_CHANGE', label: 'Chờ đổi mật khẩu lần đầu', tone: 'blue' };
  return { key: 'ACTIVE', label: 'Đang hoạt động', tone: 'green' };
}

function initials(name) {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '??';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString('vi-VN') : '—';
}

/** Xuất CSV có BOM để Excel đọc đúng tiếng Việt. */
function downloadCsv(rows, locationName) {
  const header = ['Họ và tên', 'Email', 'Số điện thoại', 'Khách sạn', 'Trạng thái', 'Ngày tạo'];
  const lines = rows.map((u) =>
    [u.fullName, u.email, u.phone, locationName(u.locationId), displayStatus(u).label, formatDate(u.createdAt)]
      .map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`)
      .join(','),
  );
  const blob = new Blob(['﻿' + [header.join(','), ...lines].join('\r\n')], {
    type: 'text/csv;charset=utf-8',
  });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `danh-sach-quan-ly-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}

/**
 * Quản lý tài khoản Manager — thiết kế "danh_s_ch_manager_m_t_kh_u_t_m" trong docs/FE_Lâm_Dũng.
 *
 * <p>BR-PERM-02: chỉ Giám đốc CRUD Manager. Bỏ các phần của thiết kế không có trong BR/DB:
 * chức danh GM/AGM (Manager không có Position — BR-USER-05, DM-01; mỗi khách sạn chỉ 1
 * Manager), cột "Đăng nhập gần nhất" và chỉ số 2FA (hệ thống không lưu).
 */
export default function ManagersPage() {
  const { user } = useAuth();
  const canManage = user?.role === 'DIRECTOR';

  const [managers, setManagers] = useState([]);
  const [locations, setLocations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [keyword, setKeyword] = useState('');
  const [locationFilter, setLocationFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(0);

  // Pop-up tạo / sửa: formOpen = đang mở; editing = null là tạo mới, có giá trị là đang sửa.
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [banner, setBanner] = useState(null); // { type, text }
  const [pending, setPending] = useState(null); // { action, target }
  const [issued, setIssued] = useState(null); // dữ liệu hộp thoại mật khẩu tạm

  function openForm(manager = null) {
    setEditing(manager);
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
      const [managerList, locationPage] = await Promise.all([
        fetchUsersByRole('MANAGER'),
        fetchLocations({ page: 0, size: 100 }),
      ]);
      setManagers(managerList);
      setLocations(locationPage.content ?? []);
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được danh sách quản lý.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canManage) {
      load();
    }
  }, [canManage, load]);

  const locationById = useMemo(
    () => Object.fromEntries(locations.map((loc) => [loc.id, loc])),
    [locations],
  );
  const locationName = useCallback((id) => locationById[id]?.name ?? '—', [locationById]);

  // DM-13: khách sạn "Chưa vận hành" nghĩa là chưa có Manager — chỉ những khách sạn này mới
  // gán được Manager mới (mỗi khách sạn một Manager).
  const freeLocations = useMemo(
    () => locations.filter((loc) => loc.status === 'NOT_OPERATIONAL'),
    [locations],
  );

  const filtered = useMemo(() => {
    const needle = keyword.trim().toLowerCase();
    return managers.filter((m) => {
      const matchKeyword =
        needle === '' ||
        [m.fullName, m.email, m.phone, locationName(m.locationId)].some((v) =>
          (v ?? '').toLowerCase().includes(needle),
        );
      const matchLocation = locationFilter === 'ALL' || m.locationId === locationFilter;
      const matchStatus = statusFilter === 'ALL' || displayStatus(m).key === statusFilter;
      return matchKeyword && matchLocation && matchStatus;
    });
  }, [managers, keyword, locationFilter, statusFilter, locationName]);

  useEffect(() => setPage(0), [keyword, locationFilter, statusFilter]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageRows = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  const stats = useMemo(() => {
    const count = (key) => managers.filter((m) => displayStatus(m).key === key).length;
    return {
      working: managers.length - count('TERMINATED'),
      active: count('ACTIVE'),
      mustChange: count('MUST_CHANGE'),
      inactive: count('INACTIVE'),
      terminated: count('TERMINATED'),
      covered: locations.length - freeLocations.length,
    };
  }, [managers, locations, freeLocations]);

  async function handleSubmit(values) {
    try {
      if (editing) {
        await updateUser(editing.id, values);
        setBanner({ type: 'success', text: `Đã cập nhật hồ sơ "${values.fullName}".` });
        closeForm();
      } else {
        const result = await createUser(values);
        setIssued({
          title: 'Cấp tài khoản Quản lý thành công!',
          description:
            'Tài khoản đã được tạo; khách sạn được gán chuyển sang trạng thái "Đang hoạt động".',
          user: result.user,
          locationName: locationName(values.locationId),
          tempPassword: result.tempPassword,
        });
        // Đóng pop-up form trước để hộp thoại mật khẩu tạm hiện một mình.
        closeForm();
      }
      await load();
      return null;
    } catch (err) {
      return readErrorMessage(err, 'Lưu tài khoản không thành công.');
    }
  }

  async function handleConfirm() {
    const { action, target } = pending;
    setPending(null);
    try {
      if (action === 'reset') {
        const result = await resetUserPassword(target.id);
        setIssued({
          title: 'Đã cấp lại mật khẩu tạm',
          description: 'Mật khẩu cũ không còn dùng được; người nhận phải đổi mật khẩu ở lần đăng nhập tới.',
          user: result.user,
          locationName: locationName(target.locationId),
          tempPassword: result.tempPassword,
        });
      } else if (action === 'lock' || action === 'unlock') {
        await updateUser(target.id, { enabled: action === 'unlock' });
        setBanner({
          type: 'success',
          text: action === 'lock' ? `Đã tạm khóa tài khoản "${target.fullName}".` : `Đã mở khóa tài khoản "${target.fullName}".`,
        });
      } else if (action === 'terminate') {
        await terminateUser(target.id);
        closeForm();
        setBanner({
          type: 'success',
          text: `Đã cho "${target.fullName}" nghỉ việc. Khách sạn "${locationName(target.locationId)}" chuyển về "Chưa vận hành".`,
        });
      }
      await load();
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Thao tác không thành công.') });
    }
  }

  const CONFIRM_TEXT = {
    reset: {
      title: 'Cấp lại mật khẩu tạm?',
      label: 'Cấp lại mật khẩu',
      message: (t) => (
        <>
          Mật khẩu hiện tại của <b>{t.fullName}</b> sẽ không dùng được nữa. Họ phải đổi mật khẩu ở lần
          đăng nhập tới (BR-USER-07).
        </>
      ),
    },
    lock: {
      title: 'Tạm khóa tài khoản?',
      label: 'Tạm khóa',
      message: (t) => (
        <>
          <b>{t.fullName}</b> bị đăng xuất ngay và không đăng nhập được cho tới khi được mở khóa. Khách sạn
          vẫn giữ người này là quản lý.
        </>
      ),
    },
    unlock: {
      title: 'Mở khóa tài khoản?',
      label: 'Mở khóa',
      message: (t) => (
        <>
          <b>{t.fullName}</b> sẽ đăng nhập lại được bình thường.
        </>
      ),
    },
    terminate: {
      title: 'Cho quản lý nghỉ việc?',
      label: 'Cho nghỉ việc',
      message: (t) => (
        <>
          Không hoàn tác được. Tài khoản <b>{t.fullName}</b> chuyển "Đã nghỉ việc", ca làm tương lai bị gỡ,
          và khách sạn <b>{locationName(t.locationId)}</b> chuyển về "Chưa vận hành" cho tới khi có quản lý
          mới (BR-USER-04, DM-13). Email này không dùng lại được (BR-USER-06).
        </>
      ),
    },
  };

  if (!canManage) {
    return (
      <div className="page">
        <div className="page__head">
          <div>
            <p className="breadcrumb">Quản trị nhân sự › Ban quản lý chi nhánh</p>
            <h1>Quản lý Danh sách Quản lý Khách sạn</h1>
          </div>
        </div>
        <div className="alert alert--info">
          Màn hình này dành cho Giám đốc: chỉ Giám đốc tạo và quản lý tài khoản Quản lý khách sạn
          (BR-PERM-02).
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Quản trị nhân sự › Ban quản lý chi nhánh</p>
          <h1>
            Quản lý Danh sách Quản lý Khách sạn (Managers){' '}
            <span className="chip">{stats.working} tài khoản</span>
          </h1>
          <p className="muted">Phân bổ Quản lý theo từng khách sạn trong chuỗi — mỗi khách sạn một Quản lý.</p>
        </div>
        <div className="page__actions">
          <button
            type="button"
            className="btn btn--ghost"
            onClick={() => downloadCsv(filtered, locationName)}
            disabled={filtered.length === 0}
          >
            ⤓ Xuất danh sách
          </button>
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => openForm()}
          >
            + Tạo tài khoản Quản lý mới
          </button>
        </div>
      </div>

      <div className="stat-grid">
        <StatCard label="Đang hoạt động" value={String(stats.active).padStart(2, '0')} note="Đã đổi mật khẩu, đăng nhập bình thường" icon="✅" tone="green" />
        <StatCard label="Chờ đổi mật khẩu" value={String(stats.mustChange).padStart(2, '0')} note="Chưa đăng nhập lần đầu" icon="⏳" tone="amber" />
        <StatCard
          label="Khách sạn có quản lý"
          value={`${stats.covered}/${locations.length}`}
          note={freeLocations.length === 0 ? 'Đầy đủ quản lý' : `Còn ${freeLocations.length} khách sạn chưa có quản lý`}
          icon="🏨"
          tone="blue"
        />
        <StatCard label="Đang tạm khóa" value={String(stats.inactive).padStart(2, '0')} note={`Đã nghỉ việc: ${stats.terminated}`} icon="🔒" tone="indigo" />
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
              placeholder="Tìm theo tên, email, số điện thoại hoặc khách sạn…"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <select value={locationFilter} onChange={(e) => setLocationFilter(e.target.value)}>
              <option value="ALL">Tất cả cơ sở ({locations.length})</option>
              {locations.map((loc) => (
                <option key={loc.id} value={loc.id}>
                  {loc.name}
                </option>
              ))}
            </select>
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="ALL">Trạng thái: Tất cả</option>
              <option value="ACTIVE">Đang hoạt động</option>
              <option value="MUST_CHANGE">Chờ đổi mật khẩu lần đầu</option>
              <option value="INACTIVE">Đang tạm khóa</option>
              <option value="TERMINATED">Đã nghỉ việc</option>
            </select>
          </div>

          <div className="panel__head">
            <h2>Danh sách nhân sự cấp Quản lý chi nhánh</h2>
            <span className="chip">
              Hiển thị {filtered.length} / {managers.length}
            </span>
          </div>

          {loading && <p className="state">Đang tải dữ liệu…</p>}
          {loadError && <div className="alert alert--error">{loadError}</div>}

          {!loading && !loadError && filtered.length === 0 && (
            <div className="state state--empty">
              <p>{managers.length === 0 ? 'Chưa có tài khoản Quản lý nào.' : 'Không có quản lý nào khớp bộ lọc.'}</p>
              {managers.length === 0 && (
                <p className="muted">Bấm "+ Tạo tài khoản Quản lý mới" để cấp tài khoản đầu tiên.</p>
              )}
            </div>
          )}

          {!loading && pageRows.length > 0 && (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Họ và tên</th>
                    <th>Thông tin liên hệ</th>
                    <th>Cơ sở phụ trách</th>
                    <th>Ngày tạo</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {pageRows.map((m) => {
                    const status = displayStatus(m);
                    const closed = status.key === 'TERMINATED';
                    const location = locationById[m.locationId];
                    return (
                      <tr key={m.id} className={editing?.id === m.id ? 'is-editing' : ''}>
                        <td>
                          <div className="cell-hotel">
                            {m.avatarUrl ? (
                              <img className="avatar avatar--img" src={m.avatarUrl} alt="" />
                            ) : (
                              <span className="avatar" aria-hidden="true">
                                {initials(m.fullName)}
                              </span>
                            )}
                            <div>
                              <b>{m.fullName}</b>
                              <small>Quản lý khách sạn (Manager)</small>
                            </div>
                          </div>
                        </td>
                        <td>
                          <div className="cell-manager">
                            <b>{m.email}</b>
                            <small>☎ {m.phone}</small>
                          </div>
                        </td>
                        <td>
                          <div className="cell-manager">
                            <b>🏨 {location?.name ?? '—'}</b>
                            {location && <small>{location.totalRooms} phòng</small>}
                          </div>
                        </td>
                        <td>{formatDate(m.createdAt)}</td>
                        <td>
                          <span className={`badge badge--${status.tone}`}>{status.label}</span>
                        </td>
                        <td className="cell-actions">
                          {closed ? (
                            <span className="muted">—</span>
                          ) : (
                            <>
                              <button
                                type="button"
                                className="icon-btn"
                                title="Cấp lại mật khẩu tạm"
                                aria-label="Cấp lại mật khẩu tạm"
                                onClick={() => setPending({ action: 'reset', target: m })}
                              >
                                🔑
                              </button>
                              <button
                                type="button"
                                className="icon-btn"
                                title="Sửa hồ sơ"
                                aria-label="Sửa hồ sơ"
                                onClick={() => openForm(m)}
                              >
                                ✏️
                              </button>
                              <button
                                type="button"
                                className="icon-btn"
                                title={status.key === 'INACTIVE' ? 'Mở khóa tài khoản' : 'Tạm khóa tài khoản'}
                                aria-label={status.key === 'INACTIVE' ? 'Mở khóa tài khoản' : 'Tạm khóa tài khoản'}
                                onClick={() =>
                                  setPending({ action: status.key === 'INACTIVE' ? 'unlock' : 'lock', target: m })
                                }
                              >
                                {status.key === 'INACTIVE' ? '🔓' : '🔒'}
                              </button>
                            </>
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
              {filtered.length === 0
                ? '0 kết quả'
                : `${page * PAGE_SIZE + 1} - ${Math.min((page + 1) * PAGE_SIZE, filtered.length)} / ${filtered.length}`}
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
      </div>

      <div className="note">
        <span aria-hidden="true">ⓘ</span>
        <div>
          <b>Quy định tài khoản Quản lý</b>
          <p>
            Chỉ Giám đốc tạo và quản lý tài khoản Quản lý (BR-PERM-02). Mỗi khách sạn có đúng một
            Quản lý; khách sạn có Quản lý mới chuyển sang <b>Đang hoạt động</b> (BR-ORG-02). Mật khẩu
            tạm chỉ hiển thị một lần và phải đổi ở lần đăng nhập đầu (BR-USER-07). Email là tên
            đăng nhập, không đổi và không dùng lại được kể cả sau khi nghỉ việc (BR-USER-06).
          </p>
        </div>
      </div>

      {formOpen && (
        <FormModal onClose={closeForm}>
          <ManagerForm
            key={editing?.id ?? 'new'}
            editing={editing}
            freeLocations={freeLocations}
            locationName={editing ? locationName(editing.locationId) : undefined}
            onCancel={closeForm}
            onSubmit={handleSubmit}
            onTerminate={() => setPending({ action: 'terminate', target: editing })}
          />
        </FormModal>
      )}

      {/* Render SAU pop-up form để hộp thoại xác nhận "Cho nghỉ việc" nổi lên trên form. */}
      {pending && (
        <ConfirmDialog
          title={CONFIRM_TEXT[pending.action].title}
          message={CONFIRM_TEXT[pending.action].message(pending.target)}
          confirmLabel={CONFIRM_TEXT[pending.action].label}
          onCancel={() => setPending(null)}
          onConfirm={handleConfirm}
        />
      )}

      {issued && <TempPasswordDialog {...issued} onClose={() => setIssued(null)} />}
    </div>
  );
}
