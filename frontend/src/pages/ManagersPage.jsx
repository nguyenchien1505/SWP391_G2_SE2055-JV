import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { readErrorMessage } from '../api/client';
import { fetchLocations } from '../api/locations';
import {
  createUser,
  deleteUserPermanently,
  fetchUsersByRole,
  resetUserPassword,
  terminateUser,
  terminateWithHandover,
  updateUser,
} from '../api/users';
import ManagerForm from '../components/ManagerForm';
import FormModal from '../components/FormModal';
import StatCard from '../components/StatCard';
import ConfirmDialog from '../components/ConfirmDialog';
import TempPasswordDialog from '../components/TempPasswordDialog';
import TerminateManagerDialog from '../components/TerminateManagerDialog';

const PAGE_SIZE = 10;

/** Giá trị bộ lọc cơ sở cho Quản lý dự bị (chưa gán khách sạn). */
const RESERVE_FILTER = 'RESERVE';

/** Quản lý dự bị = còn làm việc nhưng chưa phụ trách khách sạn nào. */
const isReserve = (user) => user.status !== 'TERMINATED' && !user.locationId;

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
    [
      u.fullName,
      u.email,
      u.phone,
      u.locationId ? locationName(u.locationId) : 'Quản lý dự bị',
      displayStatus(u).label,
      formatDate(u.createdAt),
    ]
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
 *
 * <p>Quản lý DỰ BỊ (chưa gán khách sạn) được tạo bằng lựa chọn "Khác", gán khách sạn sau ở màn
 * sửa, hoặc nhận bàn giao khi một Quản lý đang phụ trách khách sạn nghỉ việc. Quản lý đã nghỉ
 * việc mà chưa phát sinh dữ liệu thì xóa vĩnh viễn được.
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
  const [handoverTarget, setHandoverTarget] = useState(null); // Quản lý đang cho nghỉ, cần bàn giao

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

  // Người nhận bàn giao phải đăng nhập được ngay — bỏ qua Quản lý dự bị đang bị khóa.
  const activeReserves = useMemo(
    () => managers.filter((m) => isReserve(m) && m.status === 'ACTIVE'),
    [managers],
  );

  const filtered = useMemo(() => {
    const needle = keyword.trim().toLowerCase();
    return managers.filter((m) => {
      const matchKeyword =
        needle === '' ||
        [m.fullName, m.email, m.phone, locationName(m.locationId)].some((v) =>
          (v ?? '').toLowerCase().includes(needle),
        );
      const matchLocation =
        locationFilter === 'ALL' ||
        (locationFilter === RESERVE_FILTER ? isReserve(m) : m.locationId === locationFilter);
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
      reserve: managers.filter(isReserve).length,
      covered: locations.length - freeLocations.length,
    };
  }, [managers, locations, freeLocations]);

  async function handleSubmit(values) {
    try {
      if (editing) {
        await updateUser(editing.id, values);
        setBanner({
          type: 'success',
          text: values.locationId
            ? `Đã gán "${values.fullName}" phụ trách khách sạn "${locationName(values.locationId)}"; khách sạn chuyển "Đang hoạt động".`
            : `Đã cập nhật hồ sơ "${values.fullName}".`,
        });
        closeForm();
      } else {
        const result = await createUser(values);
        setIssued({
          title: values.locationId ? 'Cấp tài khoản Quản lý thành công!' : 'Cấp tài khoản Quản lý dự bị thành công!',
          description: values.locationId
            ? 'Tài khoản đã được tạo; khách sạn được gán chuyển sang trạng thái "Đang hoạt động".'
            : 'Tài khoản đã được tạo ở trạng thái dự bị: chưa phụ trách khách sạn nào, chưa dùng được chức năng quản lý cho tới khi được gán khách sạn.',
          user: result.user,
          locationName: values.locationId ? locationName(values.locationId) : null,
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
        // Chỉ Quản lý dự bị đi đường này — người đang phụ trách khách sạn phải qua bàn giao.
        await terminateUser(target.id);
        closeForm();
        setBanner({ type: 'success', text: `Đã cho Quản lý dự bị "${target.fullName}" nghỉ việc.` });
      } else if (action === 'purge') {
        await deleteUserPermanently(target.id);
        setBanner({ type: 'success', text: `Đã xóa vĩnh viễn tài khoản "${target.fullName}" (${target.email}).` });
      }
      await load();
    } catch (err) {
      setBanner({ type: 'error', text: readErrorMessage(err, 'Thao tác không thành công.') });
    }
  }

  /** Nút "Cho nghỉ việc" trong form sửa: đang phụ trách khách sạn thì phải bàn giao. */
  function startTerminate(target) {
    if (target.locationId) {
      closeForm();
      setBanner(null);
      setHandoverTarget(target);
    } else {
      setPending({ action: 'terminate', target });
    }
  }

  async function handleHandover(handover) {
    const target = handoverTarget;
    const hotel = locationName(target.locationId);
    try {
      const result = await terminateWithHandover(target.id, handover);
      setHandoverTarget(null);
      setBanner({
        type: 'success',
        text: `Đã cho "${target.fullName}" nghỉ việc; "${result.replacement.fullName}" nhận bàn giao khách sạn "${hotel}".`,
      });
      if (result.tempPassword) {
        setIssued({
          title: 'Bàn giao thành công — đã cấp tài khoản Quản lý mới',
          description: `"${target.fullName}" đã nghỉ việc; khách sạn tiếp tục "Đang hoạt động" dưới quyền Quản lý mới.`,
          user: result.replacement,
          locationName: hotel,
          tempPassword: result.tempPassword,
        });
      }
      await load();
      return null;
    } catch (err) {
      return readErrorMessage(err, 'Cho nghỉ việc không thành công.');
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
          <b>{t.fullName}</b> bị đăng xuất ngay và không đăng nhập được cho tới khi được mở khóa.{' '}
          {t.locationId
            ? 'Khách sạn vẫn giữ người này là quản lý.'
            : 'Quản lý dự bị đang bị khóa thì không được chọn để nhận bàn giao khách sạn.'}
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
      title: 'Cho quản lý dự bị nghỉ việc?',
      label: 'Cho nghỉ việc',
      message: (t) => (
        <>
          Không hoàn tác được. Tài khoản <b>{t.fullName}</b> chuyển "Đã nghỉ việc" và không đăng nhập được
          nữa. Người này chưa phụ trách khách sạn nào nên không cần bàn giao.
        </>
      ),
    },
    purge: {
      title: 'Xóa vĩnh viễn tài khoản?',
      label: 'Xóa vĩnh viễn',
      message: (t) => (
        <>
          Tài khoản <b>{t.fullName}</b> ({t.email}) bị xóa hẳn khỏi hệ thống và email được giải phóng để
          dùng lại. Chỉ xóa được khi người này <b>chưa phát sinh dữ liệu nào</b> (ca làm, công việc, bản
          ghi do họ tạo hoặc sửa…); nếu đã có, hệ thống sẽ từ chối và giữ tài khoản ở trạng thái "Đã
          nghỉ việc".
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
          note={`${freeLocations.length === 0 ? 'Đầy đủ quản lý' : `Còn ${freeLocations.length} khách sạn chưa có quản lý`} · ${stats.reserve} dự bị`}
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
              <option value={RESERVE_FILTER}>Quản lý dự bị ({stats.reserve})</option>
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
                          {isReserve(m) ? (
                            <div className="cell-manager">
                              <span className="badge badge--violet">Dự bị</span>
                              <small>Chưa gán khách sạn</small>
                            </div>
                          ) : (
                            <div className="cell-manager">
                              <b>🏨 {location?.name ?? '—'}</b>
                              {location && <small>{location.totalRooms} phòng</small>}
                            </div>
                          )}
                        </td>
                        <td>{formatDate(m.createdAt)}</td>
                        <td>
                          <span className={`badge badge--${status.tone}`}>{status.label}</span>
                        </td>
                        <td className="cell-actions">
                          {closed ? (
                            <button
                              type="button"
                              className="icon-btn"
                              title="Xóa vĩnh viễn (chỉ khi chưa phát sinh dữ liệu)"
                              aria-label="Xóa vĩnh viễn"
                              onClick={() => setPending({ action: 'purge', target: m })}
                            >
                              🗑
                            </button>
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
          <p>
            Chọn "Khác" khi tạo để có <b>Quản lý dự bị</b> — chưa phụ trách khách sạn, gán sau ở màn sửa.
            Cho nghỉ việc một Quản lý đang phụ trách khách sạn thì phải bàn giao ngay cho Quản lý dự bị
            hoặc Quản lý mới. Quản lý đã nghỉ việc mà chưa phát sinh dữ liệu thì xóa vĩnh viễn được
            (🗑), khi đó email được dùng lại.
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
            onTerminate={() => startTerminate(editing)}
          />
        </FormModal>
      )}

      {handoverTarget && (
        <TerminateManagerDialog
          target={handoverTarget}
          locationName={locationName(handoverTarget.locationId)}
          reserveManagers={activeReserves}
          onCancel={() => setHandoverTarget(null)}
          onConfirm={handleHandover}
        />
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
