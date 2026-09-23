import React, { useState, useEffect } from 'react';
import { assetService } from '../../services/assetApi';

export const AssetManagementScreen = ({ onNavigate, onSelectAsset }) => {
  const [loading, setLoading] = useState(true);
  const [assets, setAssets] = useState([]);
  const [totalCount, setTotalCount] = useState(0);
  const [stats, setStats] = useState({ total: 0, good: 0, damaged: 0, repairing: 0, disposed: 0 });

  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(10);
  const [totalPages, setTotalPages] = useState(1);

  // Filters
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [purpose, setPurpose] = useState('');
  const [status, setStatus] = useState('');
  const [location, setLocation] = useState('');
  const [hideDisposed, setHideDisposed] = useState(false);

  // Modals / Toasts
  const [selectedAssetForAction, setSelectedAssetForAction] = useState(null);
  const [actionType, setActionType] = useState(null);
  const [newLocationInput, setNewLocationInput] = useState('');
  const [newStatusInput, setNewStatusInput] = useState('Good');
  const [actionSuccessToast, setActionSuccessToast] = useState('');
  const [copiedCode, setCopiedCode] = useState('');

  const loadAssets = async () => {
    try {
      setLoading(true);
      const res = await assetService.getFixedAssets({
        search,
        category,
        purpose,
        status,
        location,
        hideDisposed,
        page,
        limit
      });
      if (res && Array.isArray(res.items)) {
        setAssets(res.items);
        setTotalCount(res.total || 0);
        setTotalPages(res.totalPages || 1);
      } else {
        setAssets([]);
        setTotalCount(0);
        setTotalPages(1);
      }
    } catch (err) { fetch('/api/ERROR_LOG_THIS_LOAD_ASSETS_' + encodeURIComponent(err.stack || err));
      console.error('Error fetching assets:', err);
      setAssets([]);
      setTotalCount(0);
    } finally {
      setLoading(false);
    }
  };

  const loadStats = async () => {
    try {
      const data = await assetService.getOverviewStats();
      if (data && data.fixedAssets) {
        setStats(data.fixedAssets);
      }
    } catch (err) {
      console.error('Error fetching stats:', err);
    }
  };

  useEffect(() => {
    loadAssets();
    loadStats();
  }, [search, category, purpose, status, location, hideDisposed, page, limit]);

  const handleResetFilters = () => {
    setSearch('');
    setCategory('');
    setPurpose('');
    setStatus('');
    setLocation('');
    setHideDisposed(false);
    setPage(1);
  };

  const handleCopy = (code) => {
    navigator.clipboard.writeText(code);
    setCopiedCode(code);
    setTimeout(() => setCopiedCode(''), 2000);
  };

  const handleExportCSV = () => {
    const headers = ['Mã tài sản,Tên thiết bị,Danh mục,Mục đích,Vị trí,Trạng thái'];
    const rows = assets.map((a) =>
      `"${a.code}","${a.name}","${a.category}","${a.purpose}","${a.location}","${a.status}"`
    );
    const csvContent = 'data:text/csv;charset=utf-8,\uFEFF' + [headers, ...rows].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `SaoMai_TaiSanCoDinh_${new Date().toISOString().slice(0,10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setActionSuccessToast('Đã xuất danh sách tài sản ra file Excel (CSV) thành công!');
    setTimeout(() => setActionSuccessToast(''), 3500);
  };

  const handleSaveQuickAction = async () => {
    if (!selectedAssetForAction) return;

    try {
      if (actionType === 'location') {
        // Mock UI operation only (not actually persisting since API doesn't fully support it yet in mock)
        setActionSuccessToast(`Đã chuyển vị trí tài sản ${selectedAssetForAction.code} sang "${newLocationInput}" thành công!`);
      } else if (actionType === 'status') {
        setActionSuccessToast(`Đã cập nhật trạng thái tài sản ${selectedAssetForAction.code} thành "${newStatusInput}"!`);
      }
      setSelectedAssetForAction(null);
      setActionType(null);
      loadAssets();
      loadStats();
    } catch (e) {
      console.error(e);
    }
    setTimeout(() => setActionSuccessToast(''), 3500);
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

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      {actionSuccessToast && (
        <div className="fixed bottom-6 right-6 z-50 bg-[#00375e] text-white px-4 py-3 rounded-xl shadow-xl flex items-center gap-2 text-xs font-semibold animate-in slide-in-from-bottom-3 duration-200">
          <span className="material-symbols-outlined text-[18px] text-[#2E7D32] bg-white rounded-full">check_circle</span>
          <span>{actionSuccessToast}</span>
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

          <button
            onClick={() => onNavigate('batch-create')}
            className="px-4 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold flex items-center gap-1.5 shadow-sm transition-colors cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">add_circle</span>
            <span>Thêm tài sản cố định</span>
          </button>
        </div>
      </div>

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
              ) : assets.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-[#5B6472]">
                    Không tìm thấy tài sản nào phù hợp.
                  </td>
                </tr>
              ) : (
                assets.map((a) => {
                  const badge = getStatusBadge(a.status);
                  return (
                    <tr key={a.id} className="hover:bg-[#F7F8FA] transition-colors cursor-pointer" onClick={() => onNavigate('asset-detail', a.code)}>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-1.5" onClick={(e) => { e.stopPropagation(); handleCopy(a.code); }}>
                          <span className="font-semibold text-[#1C2330]">{a.code}</span>
                          <span className="material-symbols-outlined text-[14px] text-[#94A3B8] hover:text-[#0e61a1]">content_copy</span>
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="font-semibold text-[#0e61a1]">{a.name}</div>
                        <div className="text-[11px] text-[#5B6472]">{a.category || 'N/A'}</div>
                      </td>
                      <td className="py-3 px-4">
                        <span className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded text-[11px]">{a.purpose || 'Internal'}</span>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-1 text-[#1C2330]">
                          <span className="material-symbols-outlined text-[14px] text-[#0e61a1]">location_on</span>
                          {a.location || 'Kho chung'}
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <div className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold ${badge.classes}`}>
                          <div className={`w-1.5 h-1.5 rounded-full ${badge.dot}`}></div>
                          {badge.label}
                        </div>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <button className="text-[#0e61a1] hover:underline font-semibold" onClick={(e) => { e.stopPropagation(); onNavigate('asset-detail', a.code); }}>Chi tiết</button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
