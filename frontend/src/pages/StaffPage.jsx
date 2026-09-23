import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { readErrorMessage } from '../api/client';
import { fetchLocation } from '../api/locations';
import { fetchPositions } from '../api/organization';
import { createUser, fetchUsersByRole, resetUserPassword, terminateUser, updateUser } from '../api/users';
import StaffForm, { POSITION_TYPE_LABEL } from '../components/StaffForm';
import StaffDetail from '../components/StaffDetail';
import FormModal from '../components/FormModal';
import StatCard from '../components/StatCard';
import ConfirmDialog from '../components/ConfirmDialog';
import TempPasswordDialog from '../components/TempPasswordDialog';

const PAGE_SIZE = 10;

/** Trạng thái hiển thị gộp từ `status` và cờ mật khẩu tạm — cùng cách với màn Manager. */
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
function downloadCsv(rows, positionOf) {
  const header = ['Họ và tên', 'Email', 'Số điện thoại', 'Phòng ban', 'Vị trí', 'Ngày vào làm', 'Trạng thái'];
  const lines = rows.map((u) => {
    const position = positionOf(u);
    return [
      u.fullName,
      u.email,
      u.phone,
      position?.departmentName,
      position?.name,
      formatDate(u.startWorkDate),
      displayStatus(u).label,
    ]
      .map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`)
      .join(',');
  });
  const blob = new Blob(['﻿' + [header.join(','), ...lines].join('\r\n')], {
    type: 'text/csv;charset=utf-8',
  });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `danh-sach-nhan-vien-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}

/**
 * Quản lý tài khoản nhân viên của Manager — thiết kế "danh_s_ch_form_nh_n_vi_n" trong
 * docs/FE_Lâm_Dũng.
 *
 * <p>BR-PERM-03: Manager CRUD Staff trong khách sạn của mình; backend tự giới hạn phạm vi. "Xóa" là
 * cho nghỉ việc (xóa mềm — BR-USER-04): tài khoản không đăng nhập được, lịch sử giữ nguyên, ca và
 * việc dọn tương lai tự gỡ thành "chưa phân công".
 *
 * <p>Bỏ các phần của thiết kế không có trong BR/DB: Mã nhân viên, CCCD, loại hợp đồng
 * (BR-USER-02), các chỉ số ca trực / nghỉ phép / chấm công (chưa có dữ liệu). Cột "Tình trạng ca"
 * thay bằng trạng thái tài khoản.
 */
export default function StaffPage() {
  const { user } = useAuth();
  const canManage = user?.role === 'MANAGER' && Boolean(user?.locationId);

  const [staff, setStaff] = useState([]);
  const [positions, setPositions] = useState([]);
  const [location, setLocation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [keyword, setKeyword] = useState('');
  const [departmentFilter, setDepartmentFilter] = useState('ALL');
  const [positionFilter, setPositionFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(0);

  // Pop-up: formOpen = form tạo/sửa đang mở (editing null = tạo mới); viewing = xem chi tiết.
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [viewing, setViewing] = useState(null);
  const [banner, setBanner] = useState(null); // { type, text }
  const [pending, setPending] = useState(null); // { action, target }
  const [issued, setIssued] = useState(null); // dữ liệu hộp thoại mật khẩu tạm

  function openForm(member = null) {
    setViewing(null);
    setEditing(member);
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
      const [staffList, positionList, currentLocation] = await Promise.all([
        fetchUsersByRole('STAFF'),
        fetchPositions({ includeInactive: true }),
        fetchLocation(user.locationId),
      ]);
      setStaff(staffList);
      setPositions(positionList);
      setLocation(currentLocation);
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được danh sách nhân viên.'));
    } finally {
      setLoading(false);
    }
  }, [user?.locationId]);

  useEffect(() => {
    if (canManage) {
      load();
    }
  }, [canManage, load]);

  const positionById = useMemo(() => Object.fromEntries(positions.map((p) => [p.id, p])), [positions]);
  const positionOf = useCallback((member) => positionById[member.positionId], [positionById]);

  const departments = useMemo(
    () => [...new Set(positions.map((p) => p.departmentName).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'vi')),
    [positions],
  );
  const positionsInFilter = useMemo(
    () => positions.filter((p) => departmentFilter === 'ALL' || p.departmentName === departmentFilter),
    [positions, departmentFilter],
  );

  const filtered = useMemo(() => {
    const needle = keyword.trim().toLowerCase();
    return staff.filter((member) => {
      const position = positionOf(member);
      const matchKeyword =
        needle === '' ||
        [member.fullName, member.email, member.phone, position?.name].some((v) =>
          (v ?? '').toLowerCase().includes(needle),
        );
      const matchDepartment = departmentFilter === 'ALL' || position?.departmentName === departmentFilter;
      const matchPosition = positionFilter === 'ALL' || member.positionId === positionFilter;
      const matchStatus = statusFilter === 'ALL' || displayStatus(member).key === statusFilter;
      return matchKeyword && matchDepartment && matchPosition && matchStatus;
    });
  }, [staff, keyword, departmentFilter, positionFilter, statusFilter, positionOf]);

  useEffect(() => setPage(0), [keyword, departmentFilter, positionFilter, statusFilter]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageRows = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  const stats = useMemo(() => {
    const count = (key) => staff.filter((m) => displayStatus(m).key === key).length;
    const working = staff.filter((m) => m.status !== 'TERMINATED');
    const byType = (type) => working.filter((m) => positionOf(m)?.positionType === type).length;
    return {
      working: working.length,
      active: count('ACTIVE'),
      mustChange: count('MUST_CHANGE'),
      inactive: count('INACTIVE'),
      terminated: count('TERMINATED'),
      reception: byType('RECEPTION'),
      housekeeping: byType('HOUSEKEEPING'),
    };
  }, [staff, positionOf]);

  async function handleSubmit(values) {
    try {
      if (editing) {
        await updateUser(editing.id, values);
        setBanner({ type: 'success', text: `Đã cập nhật hồ sơ "${values.fullName}".` });
        closeForm();
      } else {
        const result = await createUser({ ...values, locationId: user.locationId });
        setIssued({
          title: 'Tạo hồ sơ nhân viên thành công!',
          description: 'Tài khoản đăng nhập đã được cấp. Nhân viên phải đổi mật khẩu ở lần đăng nhập đầu tiên.',
          user: result.user,
          locationName: location?.name,
          tempPassword: result.tempPassword,
        });
        // Đóng pop-up form trước để hộp thoại mật khẩu tạm hiện một mình.
        closeForm();
      }
      await load();
      return null;
    } catch (err) {
      return readErrorMessage(err, 'Lưu hồ sơ nhân viên không thành công.');
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
          description: 'Mật khẩu cũ không còn dùng được; nhân viên phải đổi mật khẩu ở lần đăng nhập tới.',
          user: result.user,
          locationName: location?.name,
          tempPassword: result.tempPassword,
        });
      } else if (action === 'lock' || action === 'unlock') {
        await updateUser(target.id, { enabled: action === 'unlock' });
        setBanner({
          type: 'success',
          text:
            action === 'lock'
              ? `Đã tạm khóa tài khoản "${target.fullName}".`
              : `Đã mở khóa tài khoản "${target.fullName}".`,
        });
      } else if (action === 'terminate') {
        await terminateUser(target.id);
        closeForm();
        setBanner({
          type: 'success',
          text: `Đã cho "${target.fullName}" nghỉ việc. Ca làm và việc dọn phòng từ ngày mai đã chuyển về "chưa phân công".`,
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
          Mật khẩu hiện tại của <b>{t.fullName}</b> sẽ không dùng được nữa. Họ phải đổi mật khẩu ở lần đăng
          nhập tới.
        </>
      ),
    },
    lock: {
      title: 'Tạm khóa tài khoản?',
      label: 'Tạm khóa',
      message: (t) => (
        <>
          <b>{t.fullName}</b> bị đăng xuất ngay và không đăng nhập được cho tới khi được mở khóa. Hồ sơ, ca làm
          và việc đã giao giữ nguyên.
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
      title: 'Cho nhân viên nghỉ việc?',
      label: 'Cho nghỉ việc',
      message: (t) => (
        <>
          Không hoàn tác được. Tài khoản <b>{t.fullName}</b> chuyển "Đã nghỉ việc" và không đăng nhập được nữa;
          dữ liệu lịch sử giữ nguyên. Ca làm và việc dọn phòng <b>từ ngày mai</b> tự chuyển về "chưa phân
          công" — ca của hôm nay giữ nguyên. Email này không dùng lại được.
        </>
      ),
    },
  };

  if (!canManage) {
    return (
      <div className="page">
        <div className="page__head">
          <div>
            <p className="breadcrumb">Quản lý nhân sự › Đội ngũ vận hành khách sạn</p>
            <h1>Quản lý Danh sách Nhân viên Chi nhánh</h1>
          </div>
        </div>
        <div className="alert alert--info">
          Màn hình này dành cho Quản lý khách sạn: Manager tạo và quản lý tài khoản nhân viên trong khách sạn
          của mình.
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="breadcrumb">Quản lý nhân sự › Đội ngũ vận hành khách sạn</p>
          <h1>
            Quản lý Danh sách Nhân viên Chi nhánh{' '}
            {location && <span className="chip chip--green">{location.name}</span>}
          </h1>
          <p className="muted">Tạo tài khoản, cập nhật hồ sơ và cho nghỉ việc nhân viên trong khách sạn của bạn.</p>
        </div>
        <div className="page__actions">
          <button
            type="button"
            className="btn btn--ghost"
            onClick={() => downloadCsv(filtered, positionOf)}
            disabled={filtered.length === 0}
          >
            ⤓ Xuất danh sách
          </button>
          <button type="button" className="btn btn--primary" onClick={() => openForm()}>
            + Tạo nhân viên mới
          </button>
        </div>
      </div>

      <div className="stat-grid">
        <StatCard
          label="Tổng nhân sự"
          value={String(stats.working).padStart(2, '0')}
          note={`Lễ tân ${stats.reception} · Dọn dẹp ${stats.housekeeping}`}
          icon="👥"
          tone="blue"
        />
        <StatCard label="Đang hoạt động" value={String(stats.active).padStart(2, '0')} note="Đã đổi mật khẩu, đăng nhập bình thường" icon="✅" tone="green" />
        <StatCard label="Chờ đổi mật khẩu" value={String(stats.mustChange).padStart(2, '0')} note="Chưa đăng nhập lần đầu" icon="⏳" tone="amber" />
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

      <section className="panel">
        <div className="toolbar">
          <input
            className="toolbar__search"
            type="search"
            placeholder="Tìm theo tên, email, số điện thoại hoặc vị trí…"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <select
            value={departmentFilter}
            onChange={(e) => {
              setDepartmentFilter(e.target.value);
              setPositionFilter('ALL');
            }}
          >
            <option value="ALL">Tất cả Bộ phận</option>
            {departments.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
          <select value={positionFilter} onChange={(e) => setPositionFilter(e.target.value)}>
            <option value="ALL">Tất cả Vị trí / Chức danh</option>
            {positionsInFilter.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
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
          <h2>Danh sách nhân viên</h2>
          <span className="chip">
            Hiển thị {filtered.length} / {staff.length}
          </span>
        </div>

        {loading && <p className="state">Đang tải dữ liệu…</p>}
        {loadError && <div className="alert alert--error">{loadError}</div>}

        {!loading && !loadError && filtered.length === 0 && (
          <div className="state state--empty">
            <p>{staff.length === 0 ? 'Khách sạn chưa có nhân viên nào.' : 'Không có nhân viên nào khớp bộ lọc.'}</p>
            {staff.length === 0 && <p className="muted">Bấm "+ Tạo nhân viên mới" để tạo hồ sơ đầu tiên.</p>}
          </div>
        )}

        {!loading && pageRows.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Nhân viên</th>
                  <th>Bộ phận &amp; Vị trí</th>
                  <th>Liên hệ</th>
                  <th>Ngày vào làm</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {pageRows.map((member) => {
                  const status = displayStatus(member);
                  const closed = status.key === 'TERMINATED';
                  const position = positionOf(member);
                  return (
                    <tr key={member.id} className={editing?.id === member.id ? 'is-editing' : ''}>
                      <td>
                        <div className="cell-hotel">
                          {member.avatarUrl ? (
                            <img className="avatar avatar--img" src={member.avatarUrl} alt="" />
                          ) : (
                            <span className="avatar" aria-hidden="true">
                              {initials(member.fullName)}
                            </span>
                          )}
                          <div>
                            <b>{member.fullName}</b>
                            <small>{POSITION_TYPE_LABEL[position?.positionType] ?? 'Nhân viên'}</small>
                          </div>
                        </div>
                      </td>
                      <td>
                        <div className="cell-manager">
                          <b>{position?.departmentName ?? '—'}</b>
                          <small>{position?.name ?? '—'}</small>
                        </div>
                      </td>
                      <td>
                        <div className="cell-manager">
                          <b>{member.phone}</b>
                          <small>{member.email}</small>
                        </div>
                      </td>
                      <td>{formatDate(member.startWorkDate)}</td>
                      <td>
                        <span className={`badge badge--${status.tone}`}>{status.label}</span>
                      </td>
                      <td className="cell-actions">
                        <button
                          type="button"
                          className="icon-btn"
                          title="Xem hồ sơ"
                          aria-label="Xem hồ sơ"
                          onClick={() => setViewing(member)}
                        >
                          👁
                        </button>
                        {!closed && (
                          <>
                            <button
                              type="button"
                              className="icon-btn"
                              title="Cấp lại mật khẩu tạm"
                              aria-label="Cấp lại mật khẩu tạm"
                              onClick={() => setPending({ action: 'reset', target: member })}
                            >
                              🔑
                            </button>
                            <button
                              type="button"
                              className="icon-btn"
                              title="Sửa hồ sơ"
                              aria-label="Sửa hồ sơ"
                              onClick={() => openForm(member)}
                            >
                              ✏️
                            </button>
                            <button
                              type="button"
                              className="icon-btn"
                              title={status.key === 'INACTIVE' ? 'Mở khóa tài khoản' : 'Tạm khóa tài khoản'}
                              aria-label={status.key === 'INACTIVE' ? 'Mở khóa tài khoản' : 'Tạm khóa tài khoản'}
                              onClick={() =>
                                setPending({ action: status.key === 'INACTIVE' ? 'unlock' : 'lock', target: member })
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

      <div className="note">
        <span aria-hidden="true">ⓘ</span>
        <div>
          <b>Quy định tài khoản nhân viên</b>
          <p>
            Manager tạo và quản lý nhân viên trong khách sạn của mình. Mật khẩu tạm chỉ hiển thị một lần
            và phải đổi ở lần đăng nhập đầu. Vị trí do Giám đốc định nghĩa; phòng ban tự suy ra từ vị trí.
            Cho nghỉ việc không cần Giám đốc duyệt; email của người đã nghỉ không dùng lại được. Số nhân
            viên bị giới hạn bởi gói dịch vụ của chuỗi.
          </p>
        </div>
      </div>

      {formOpen && (
        <FormModal onClose={closeForm}>
          <StaffForm
            key={editing?.id ?? 'new'}
            editing={editing}
            positions={positions}
            locationName={location?.name}
            onCancel={closeForm}
            onSubmit={handleSubmit}
            onTerminate={() => setPending({ action: 'terminate', target: editing })}
          />
        </FormModal>
      )}

      {viewing && (
        <FormModal onClose={() => setViewing(null)}>
          <StaffDetail
            staff={viewing}
            position={positionOf(viewing)}
            locationName={location?.name}
            status={displayStatus(viewing)}
            onClose={() => setViewing(null)}
            onEdit={viewing.status === 'TERMINATED' ? null : () => openForm(viewing)}
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
