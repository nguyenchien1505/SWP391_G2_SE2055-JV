import React, { useState, useEffect, useMemo } from 'react';
import { BatchCreateAssetsModal } from '../modals/BatchCreateAssetsModal';
import { assetService } from '../../services/assetApi';
import { useAuth } from '../../context/AuthContext';
import { PURPOSE_LABEL } from '../asset-categories/categoryLabels';

const PAGE_SIZE = 10;

const STATUS_OPTIONS = [
  { value: 'Good', label: 'Tốt (Good)' },
  { value: 'Damaged', label: 'Bị hỏng (Damaged)' },
  { value: 'Repairing', label: 'Đang sửa (Repairing)' },
  { value: 'Disposed', label: 'Đã thanh lý' },
];
const STATUS_LABEL = Object.fromEntries(STATUS_OPTIONS.map((o) => [o.value, o.label]));

function csvCell(value) {
  return `"${String(value ?? '').replace(/"/g, '""')}"`;
}

/**
 * Danh sách tài sản cố định — BR-ASSET-01..03, BR-ASSET-12..14. Backend chưa hỗ trợ tìm
 * kiếm/lọc, nên lấy trọn danh sách trong phạm vi người dùng rồi lọc + phân trang ở client.
 * Chỉ Manager được thêm/sửa (BR-ASSET-09); Giám đốc và Staff chỉ xem.
 */
export const AssetManagementScreen = ({ onNavigate }) => {
  const { user } = useAuth();
  const canManage = user?.role === 'MANAGER';

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [assets, setAssets] = useState([]);
  const [page, setPage] = useState(1);

  // Filters — tài sản đã thanh lý ẩn mặc định khỏi danh sách vận hành (BR-ASSET-14).
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [status, setStatus] = useState('');
  const [hideDisposed, setHideDisposed] = useState(true);

  const [toast, setToast] = useState(null); // { message, error }
  const [copiedCode, setCopiedCode] = useState('');
  const [batchOpen, setBatchOpen] = useState(false);

  const loadAssets = async () => {
    setLoading(true);
    setLoadError('');
    try {
      setAssets(await assetService.getAllFixedAssets());
    } catch (err) {
      console.error('Error fetching assets:', err);
      setLoadError(err.message || 'Không tải được danh sách tài sản.');
      setAssets([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAssets();
  }, []);

  useEffect(() => {
    setPage(1);
  }, [search, category, status, hideDisposed]);

  const stats = useMemo(() => {
    const count = (st) => assets.filter((a) => a.status === st).length;
    return {
      total: assets.length,
      good: count('Good'),
      damaged: count('Damaged'),
      repairing: count('Repairing'),
      disposed: count('Disposed'),
    };
  }, [assets]);

  const categoryOptions = useMemo(() => {
    const map = new Map();
    assets.forEach((a) => map.set(a.categoryId, a.category));
    return [...map.entries()].sort((x, y) => x[1].localeCompare(y[1], 'vi'));
  }, [assets]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return assets.filter(
      (a) =>
        (!q || [a.code, a.name, a.location].some((v) => (v || '').toLowerCase().includes(q))) &&
        (!category || a.categoryId === category) &&
        (!status || a.status === status) &&
        // Chọn lọc "Đã thanh lý" thì phải thấy chúng dù đang bật ẩn.
        (!hideDisposed || status === 'Disposed' || a.status !== 'Disposed')
    );
  }, [assets, search, category, status, hideDisposed]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageItems = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
  const hasFilter = search || category || status || !hideDisposed;

  const showToast = (message, error = false) => {
    setToast({ message, error });
    setTimeout(() => setToast(null), 3500);
  };

  const handleResetFilters = () => {
    setSearch('');
    setCategory('');
    setStatus('');
    setHideDisposed(true);
  };

  const handleCopy = (code) => {
    navigator.clipboard.writeText(code);
    setCopiedCode(code);
    setTimeout(() => setCopiedCode(''), 2000);
  };

  const handleExportCSV = () => {
    if (filtered.length === 0) {
      showToast('Không có tài sản nào để xuất.', true);
      return;
    }
    const headers = ['Mã tài sản', 'Tên thiết bị', 'Danh mục', 'Mục đích', 'Vị trí', 'Trạng thái'];
    const rows = filtered.map((a) => [
      a.code,
      a.name,
      a.category,
      PURPOSE_LABEL[a.purpose] || a.purpose,
      a.location,
      STATUS_LABEL[a.status] || a.status,
    ]);
    // BOM UTF-8 để Excel đọc đúng tiếng Việt.
    const csv = '﻿' + [headers, ...rows].map((r) => r.map(csvCell).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `SaoMai_TaiSanCoDinh_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    showToast(`Đã xuất ${filtered.length} tài sản ra file Excel (CSV).`);
  };

  const getStatusBadge = (st) => {
    switch (st) {
      case 'Good':
        return {
          label: 'Tốt (Good)',
          classes: 'bg-[#E8F5E9] text-[#2E7D32] border border-[#2E7D32]/20',
          dot: 'bg-[#2E7D32]'
        };
      case 'Damaged':
        return {
          label: 'Bị hỏng (Damaged)',
          classes: 'bg-[#FFEBEE] text-[#D32F2F] border border-[#D32F2F]/20',
          dot: 'bg-[#D32F2F]'
        };
      case 'Repairing':
        return {
          label: 'Đang sửa (Repairing)',
          classes: 'bg-[#FFFDE7] text-[#F9A825] border border-[#F9A825]/30',
          dot: 'bg-[#F9A825]'
        };
      case 'Disposed':
        return {
          label: 'Đã thanh lý',
          classes: 'bg-[#EEEEEE] text-[#616161] border border-[#616161]/20',
          dot: 'bg-[#616161]'
        };
      default:
        return {
          label: st,
          classes: 'bg-[#eff4ff] text-[#00375e]',
          dot: 'bg-[#00375e]'
        };
    }
  };

  const inputClass =
    'p-2 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl text-xs text-[#1C2330] focus:outline-none focus:border-[#0e61a1]';

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      {toast && (
        <div
          className={`fixed bottom-6 right-6 z-50 text-white px-4 py-3 rounded-xl shadow-xl flex items-center gap-2 text-xs font-semibold animate-in slide-in-from-bottom-3 duration-200 ${
            toast.error ? 'bg-[#D32F2F]' : 'bg-[#00375e]'
          }`}
        >
          <span className="material-symbols-outlined text-[18px]">{toast.error ? 'error' : 'check_circle'}</span>
          <span>{toast.message}</span>
        </div>
      )}

      {/* Top Banner Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-white p-5 rounded-2xl border border-[#DFE3E8] shadow-[0_1px_8px_rgba(0,0,0,0.02)]">
        <div>
          <div className="flex items-center gap-2 text-xs text-[#5B6472] mb-1">
            <span className="font-semibold text-[#00375e]">TÀI SẢN VẬT TƯ</span>
            <span>/</span>
            <span className="text-[#0e61a1]">TÀI SẢN CỤ THỂ</span>
          </div>
          <h1 className="text-xl font-bold text-[#00375e] tracking-tight">
            Danh Sách Tài Sản Cố Định (Fixed Assets)
          </h1>
          <p className="text-xs text-[#5B6472] mt-0.5">
            Quản lý cá thể hóa từng mã tài sản, vòng đời thiết bị và vị trí gắn liền với phòng buồng.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            onClick={handleExportCSV}
            className="px-4 py-2 rounded-xl border border-[#DFE3E8] bg-white text-[#1C2330] hover:bg-[#F7F8FA] text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px] text-[#2E7D32]">table_view</span>
            <span>Xuất Excel</span>
          </button>

          {canManage && (
            <button
              onClick={() => setBatchOpen(true)}
              className="px-4 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold flex items-center gap-1.5 shadow-sm transition-colors cursor-pointer"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">add_circle</span>
              <span>Thêm tài sản cố định</span>
            </button>
          )}
        </div>
      </div>

      {loadError && (
        <div className="p-3 rounded-xl bg-[#FFEBEE] text-[#D32F2F] text-xs border border-[#D32F2F]/30 flex items-center justify-between gap-3">
          <span>{loadError}</span>
          <button type="button" onClick={loadAssets} className="font-semibold underline cursor-pointer">
            Thử lại
          </button>
        </div>
      )}

      {/* 4 Metric Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white p-4 rounded-xl border border-[#DFE3E8] shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs text-[#5B6472]">Tổng cụ thể</span>
            <span className="material-symbols-outlined text-[20px] text-[#0e61a1]">devices</span>
          </div>
          <div className="text-2xl font-extrabold text-[#00375e] mt-2">{stats.total}</div>
          <div className="text-[11px] text-[#2E7D32] mt-0.5 font-medium">Tất cả tài sản hệ thống</div>
        </div>

        <div className="bg-white p-4 rounded-xl border border-[#DFE3E8] shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs text-[#5B6472]">Hoạt động tốt</span>
            <span className="material-symbols-outlined text-[20px] text-[#2E7D32]">check_circle</span>
          </div>
          <div className="text-2xl font-extrabold text-[#2E7D32] mt-2">{stats.good}</div>
          <div className="text-[11px] text-[#5B6472] mt-0.5 font-medium">Trạng thái Good</div>
        </div>

        <div className="bg-white p-4 rounded-xl border border-[#DFE3E8] shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs text-[#5B6472]">Sự cố & Sửa chữa</span>
            <span className="material-symbols-outlined text-[20px] text-[#F9A825]">build_circle</span>
          </div>
          <div className="text-2xl font-extrabold text-[#F9A825] mt-2">{stats.damaged + stats.repairing}</div>
          <div className="text-[11px] text-[#5B6472] mt-0.5 font-medium">Bị hỏng hoặc đang sửa</div>
        </div>

        <div className="bg-white p-4 rounded-xl border border-[#DFE3E8] shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs text-[#5B6472]">Đã thanh lý</span>
            <span className="material-symbols-outlined text-[20px] text-[#616161]">delete</span>
          </div>
          <div className="text-2xl font-extrabold text-[#616161] mt-2">{stats.disposed}</div>
          <div className="text-[11px] text-[#5B6472] mt-0.5 font-medium">Không còn sử dụng</div>
        </div>
      </div>

      {/* Main Content Area */}
      <div className="bg-white rounded-2xl border border-[#DFE3E8] shadow-xs overflow-hidden">
        {/* Filter Toolbar */}
        <div className="p-4 border-b border-[#DFE3E8] flex flex-col lg:flex-row lg:items-center gap-3">
          <div className="relative flex-1 min-w-0">
            <span className="material-symbols-outlined text-[18px] text-[#94A3B8] absolute left-2.5 top-1/2 -translate-y-1/2">
              search
            </span>
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Tìm theo mã, tên thiết bị hoặc vị trí..."
              className={`${inputClass} w-full pl-9`}
            />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select value={category} onChange={(e) => setCategory(e.target.value)} className={inputClass}>
              <option value="">Tất cả danh mục</option>
              {categoryOptions.map(([id, name]) => (
                <option key={id} value={id}>{name}</option>
              ))}
            </select>
            <select value={status} onChange={(e) => setStatus(e.target.value)} className={inputClass}>
              <option value="">Tất cả trạng thái</option>
              {STATUS_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
            <label className="flex items-center gap-1.5 text-xs text-[#1C2330] cursor-pointer select-none">
              <input
                type="checkbox"
                checked={hideDisposed}
                onChange={(e) => setHideDisposed(e.target.checked)}
                className="cursor-pointer"
              />
              Ẩn tài sản đã thanh lý
            </label>
            {hasFilter && (
              <button
                type="button"
                onClick={handleResetFilters}
                className="text-xs text-[#0e61a1] hover:underline font-semibold cursor-pointer"
              >
                Xóa bộ lọc
              </button>
            )}
          </div>
        </div>

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#eff4ff] text-[#00375e] font-semibold border-b border-[#DFE3E8]">
              <tr>
                <th className="py-3 px-4">Mã Tài Sản</th>
                <th className="py-3 px-4">Danh Mục & Tên Thiết Bị</th>
                <th className="py-3 px-4">Mục Đích</th>
                <th className="py-3 px-4">Vị Trí Hiện Diện</th>
                <th className="py-3 px-4">Trạng Thái</th>
                <th className="py-3 px-4 text-right">Thao Tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#eff4ff]">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-[#5B6472]">
                    Đang tải danh sách tài sản...
                  </td>
                </tr>
              ) : pageItems.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-[#5B6472]">
                    Không tìm thấy tài sản nào phù hợp.
                  </td>
                </tr>
              ) : (
                pageItems.map((a) => {
                  const badge = getStatusBadge(a.status);
                  return (
                    <tr key={a.id} className="hover:bg-[#F7F8FA] transition-colors cursor-pointer" onClick={() => onNavigate('asset-detail', a.id)}>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-1.5" onClick={(e) => { e.stopPropagation(); handleCopy(a.code); }}>
                          <span className="font-semibold text-[#1C2330]">{a.code}</span>
                          <span className="material-symbols-outlined text-[14px] text-[#94A3B8] hover:text-[#0e61a1]">
                            {copiedCode === a.code ? 'check' : 'content_copy'}
                          </span>
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="font-semibold text-[#0e61a1]">{a.name}</div>
                        <div className="text-[11px] text-[#5B6472]">{a.category || 'N/A'}</div>
                      </td>
                      <td className="py-3 px-4">
                        <span className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded text-[11px]">
                          {PURPOSE_LABEL[a.purpose] || '—'}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-1 text-[#1C2330]">
                          <span className="material-symbols-outlined text-[14px] text-[#0e61a1]">location_on</span>
                          {a.location}
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <div className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold ${badge.classes}`}>
                          <div className={`w-1.5 h-1.5 rounded-full ${badge.dot}`}></div>
                          {badge.label}
                        </div>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <button className="text-[#0e61a1] hover:underline font-semibold" onClick={(e) => { e.stopPropagation(); onNavigate('asset-detail', a.id); }}>Chi tiết</button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {!loading && filtered.length > 0 && (
          <div className="px-4 py-3 border-t border-[#DFE3E8] flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs text-[#5B6472]">
            <span>
              Hiển thị {(currentPage - 1) * PAGE_SIZE + 1}–{Math.min(currentPage * PAGE_SIZE, filtered.length)} / {filtered.length} tài sản
            </span>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setPage(currentPage - 1)}
                disabled={currentPage <= 1}
                className="px-2 py-1 rounded-lg border border-[#DFE3E8] hover:bg-[#F7F8FA] disabled:opacity-40 cursor-pointer disabled:cursor-not-allowed flex items-center"
                aria-label="Trang trước"
              >
                <span className="material-symbols-outlined text-[16px]">chevron_left</span>
              </button>
              {Array.from({ length: totalPages }, (_, i) => i + 1)
                .filter((p) => p === 1 || p === totalPages || Math.abs(p - currentPage) <= 1)
                .map((p, idx, arr) => (
                  <React.Fragment key={p}>
                    {idx > 0 && p - arr[idx - 1] > 1 && <span className="px-1">…</span>}
                    <button
                      type="button"
                      onClick={() => setPage(p)}
                      className={`min-w-[28px] px-2 py-1 rounded-lg font-semibold cursor-pointer ${
                        p === currentPage ? 'bg-[#00375e] text-white' : 'border border-[#DFE3E8] hover:bg-[#F7F8FA]'
                      }`}
                    >
                      {p}
                    </button>
                  </React.Fragment>
                ))}
              <button
                type="button"
                onClick={() => setPage(currentPage + 1)}
                disabled={currentPage >= totalPages}
                className="px-2 py-1 rounded-lg border border-[#DFE3E8] hover:bg-[#F7F8FA] disabled:opacity-40 cursor-pointer disabled:cursor-not-allowed flex items-center"
                aria-label="Trang sau"
              >
                <span className="material-symbols-outlined text-[16px]">chevron_right</span>
              </button>
            </div>
          </div>
        )}
      </div>
      {batchOpen && (
        <BatchCreateAssetsModal onClose={() => setBatchOpen(false)} onCreated={loadAssets} />
      )}
    </div>
  );
};
