import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { readErrorMessage } from '../api/client';
import {
  createAssetCategory,
  fetchAssetCategories,
  setAssetCategoryActive,
  updateAssetCategory,
} from '../api/assetCategories';
import CategoryMetrics from '../components/asset-categories/CategoryMetrics';
import CategoryFilterToolbar from '../components/asset-categories/CategoryFilterToolbar';
import CategoryTable from '../components/asset-categories/CategoryTable';
import CategoryFormView from '../components/asset-categories/CategoryFormView';
import CategoryDetailModal from '../components/asset-categories/CategoryDetailModal';
import { KIND_LABEL, PURPOSE_LABEL } from '../components/asset-categories/categoryLabels';

const PAGE_SIZE = 10;
const DEFAULT_FILTERS = { search: '', kind: 'ALL', purpose: 'ALL', status: 'ALL' };

function csvCell(value) {
  return `"${String(value ?? '').replace(/"/g, '""')}"`;
}

/**
 * Quản trị danh mục tài sản & vật tư cấp Tenant — BR-ASSET-08, BR-ASSET-09, BR-ORG-14.
 * Giám đốc thêm / sửa / tạm ngưng; Manager chỉ xem (backend trả 403 nếu Manager ghi).
 */
export default function AssetCategoriesPage() {
  const { user } = useAuth();
  const canManage = user?.role === 'DIRECTOR';

  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [currentPage, setCurrentPage] = useState(1);

  // view: 'list' | 'form'. editing = danh mục đang sửa, null là tạo mới.
  const [view, setView] = useState('list');
  const [editing, setEditing] = useState(null);
  const [viewingId, setViewingId] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const [toast, setToast] = useState(null); // { title, message, type }
  const toastTimer = useRef(null);

  const showToast = useCallback((title, message, type = 'success') => {
    clearTimeout(toastTimer.current);
    setToast({ title, message, type });
    toastTimer.current = setTimeout(() => setToast(null), 4500);
  }, []);

  useEffect(() => () => clearTimeout(toastTimer.current), []);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      setCategories(await fetchAssetCategories());
    } catch (err) {
      setLoadError(readErrorMessage(err, 'Không tải được danh mục tài sản.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const metrics = useMemo(() => {
    const active = categories.filter((c) => c.active).length;
    const fixed = categories.filter((c) => c.assetKind === 'FIXED').length;
    return {
      total: categories.length,
      fixed,
      consumable: categories.length - fixed,
      active,
      inactive: categories.length - active,
    };
  }, [categories]);

  const filtered = useMemo(() => {
    const q = filters.search.trim().toLowerCase();
    return categories.filter(
      (c) =>
        (!q || c.name.toLowerCase().includes(q)) &&
        (filters.kind === 'ALL' || c.assetKind === filters.kind) &&
        (filters.purpose === 'ALL' || c.purpose === filters.purpose) &&
        (filters.status === 'ALL' || c.active === (filters.status === 'ACTIVE')),
    );
  }, [categories, filters]);

  const paginated = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
  const viewing = categories.find((c) => c.id === viewingId) ?? null;

  function changeFilters(next) {
    setFilters(next);
    setCurrentPage(1);
  }

  function replaceCategory(updated) {
    setCategories((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
  }

  async function handleToggleStatus(item) {
    setBusyId(item.id);
    try {
      const updated = await setAssetCategoryActive(item.id, !item.active);
      replaceCategory(updated);
      showToast(
        'Cập nhật trạng thái',
        updated.active
          ? `Đã kích hoạt áp dụng "${updated.name}" trên toàn chuỗi.`
          : `Đã chuyển "${updated.name}" sang Tạm ngưng. Các chi nhánh không thể tạo mới tài sản thuộc danh mục này.`,
      );
    } catch (err) {
      showToast('Thao tác thất bại', readErrorMessage(err), 'error');
    } finally {
      setBusyId(null);
    }
  }

  /** Ném Error với thông điệp tiếng Việt để form hiện ngay tại chỗ. */
  async function handleSave(form, isEdit) {
    try {
      let saved;
      if (isEdit) {
        const original = categories.find((c) => c.id === form.id);
        saved = await updateAssetCategory(form.id, { name: form.name, purpose: form.purpose, unit: form.unit });
        if (original && original.active !== form.active) {
          saved = await setAssetCategoryActive(form.id, form.active);
        }
      } else {
        saved = await createAssetCategory({
          name: form.name,
          assetKind: form.assetKind,
          purpose: form.purpose,
          unit: form.unit,
        });
        // Backend tạo mới luôn ở trạng thái hoạt động; tạm ngưng là thao tác riêng (BR-ORG-14).
        if (!form.active) {
          saved = await setAssetCategoryActive(saved.id, false);
        }
      }
      showToast(
        'Đã lưu danh mục thành công!',
        isEdit ? `Đã cập nhật "${saved.name}".` : `Đã thêm "${saved.name}" và áp dụng cho toàn chuỗi.`,
      );
      await load();
      setView('list');
      setEditing(null);
    } catch (err) {
      throw new Error(readErrorMessage(err, 'Có lỗi xảy ra khi lưu danh mục'));
    }
  }

  function openForm(item = null) {
    setViewingId(null);
    setEditing(item);
    setView('form');
  }

  function handleExport() {
    if (filtered.length === 0) {
      showToast('Không có dữ liệu', 'Không có danh mục nào để xuất Excel.', 'error');
      return;
    }
    const headers = ['Tên danh mục', 'Phân loại', 'Mục đích sử dụng', 'Đơn vị tính', 'Trạng thái'];
    const rows = filtered.map((c) =>
      [
        c.name,
        KIND_LABEL[c.assetKind],
        PURPOSE_LABEL[c.purpose],
        c.unit || '—',
        c.active ? 'Đang áp dụng' : 'Tạm ngưng',
      ].map(csvCell),
    );
    // BOM UTF-8 để Excel đọc đúng tiếng Việt.
    const csv = '﻿' + [headers.map(csvCell), ...rows].map((r) => r.join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `DanhMuc_TaiSan_${new Date().toISOString().split('T')[0]}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    showToast('Xuất tệp thành công', `Đã xuất ${filtered.length} danh mục ra tệp Excel (.csv UTF-8).`);
  }

  return (
    <div className="flex flex-col w-full text-[#1C2330]">
      {view === 'form' && canManage ? (
        <CategoryFormView
          key={editing?.id ?? 'new'}
          initialData={editing}
          categoriesList={categories}
          onSave={handleSave}
          onCancel={() => {
            setView('list');
            setEditing(null);
          }}
        />
      ) : (
        <>
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
            <div className="flex flex-col">
              <div className="flex items-center gap-1.5 text-[#5B6472] text-xs mb-1 font-semibold">
                <span>Cấu hình Tenant</span>
                <span className="material-symbols-outlined text-xs">chevron_right</span>
                <span className="text-[#00375e] font-bold">Danh mục tài sản</span>
              </div>
              <div className="flex items-center gap-2.5">
                <h1 className="text-xl sm:text-2xl font-bold text-[#1C2330] tracking-tight">Danh Mục Tài Sản & Vật Tư</h1>
                <span className="px-2.5 py-0.5 rounded-full bg-[#00375e]/10 text-[#00375e] text-[11px] uppercase tracking-wider font-bold">
                  Tenant Level
                </span>
              </div>
              <p className="text-xs sm:text-sm text-[#5B6472] mt-0.5">
                Chuẩn hóa danh mục tài sản dùng chung cho toàn bộ các khách sạn thành viên trong chuỗi.
              </p>
            </div>

            <div className="flex items-center gap-2.5 self-start md:self-auto">
              <button
                type="button"
                onClick={handleExport}
                className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-white text-[#1C2330] hover:bg-[#eff4ff] border border-[#DFE3E8] transition-all text-xs sm:text-sm font-semibold"
              >
                <span className="material-symbols-outlined text-[18px] text-[#0e61a1]">file_download</span>
                <span>Xuất danh mục Excel</span>
              </button>
              {canManage && (
                <button
                  type="button"
                  onClick={() => openForm(null)}
                  className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-[#1f4e78] text-white hover:bg-[#00375e] transition-all shadow-sm text-xs sm:text-sm font-bold"
                >
                  <span className="material-symbols-outlined text-[20px]">add_circle</span>
                  <span>Thêm danh mục mới</span>
                </button>
              )}
            </div>
          </div>

          {loadError && (
            <div className="mb-4 p-3 rounded-lg bg-red-50 text-[#D32F2F] text-sm border border-red-200 flex items-center justify-between gap-3">
              <span>{loadError}</span>
              <button type="button" onClick={load} className="font-semibold underline">
                Thử lại
              </button>
            </div>
          )}

          <CategoryMetrics metrics={metrics} onFilterKind={(kind) => changeFilters({ ...filters, kind })} />

          <CategoryFilterToolbar
            filters={filters}
            onChange={changeFilters}
            onReset={() => changeFilters(DEFAULT_FILTERS)}
            counts={metrics}
          />

          <CategoryTable
            items={paginated}
            totalItems={filtered.length}
            currentPage={currentPage}
            pageSize={PAGE_SIZE}
            loading={loading}
            canManage={canManage}
            busyId={busyId}
            onPageChange={setCurrentPage}
            onToggleStatus={handleToggleStatus}
            onEdit={openForm}
            onViewDetail={(item) => setViewingId(item.id)}
          />

          <div className="bg-[#eff4ff] rounded-xl p-4 sm:p-5 flex items-start gap-4 border border-[#d0e4ff]">
            <div className="w-10 h-10 rounded-lg bg-[#d0e4ff] text-[#00375e] flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined text-[24px]">verified_user</span>
            </div>
            <div className="flex flex-col">
              <span className="font-semibold text-sm sm:text-base text-[#1C2330]">
                Quy tắc nghiệp vụ quản trị cấp Tenant (Toàn chuỗi)
              </span>
              <p className="text-xs sm:text-sm text-[#5B6472] mt-1 leading-relaxed">
                Khi chuyển một danh mục sang <span className="font-bold text-[#EF6C00]">Tạm ngưng (Inactive)</span>, các
                khách sạn trong chuỗi không thể tạo thêm tài sản mới thuộc danh mục này. Toàn bộ tài sản, tồn kho và lịch
                sử đã có vẫn được giữ nguyên.
              </p>
            </div>
          </div>
        </>
      )}

      {viewing && (
        <CategoryDetailModal
          item={viewing}
          canManage={canManage}
          busy={busyId === viewing.id}
          onClose={() => setViewingId(null)}
          onEdit={openForm}
          onToggleStatus={handleToggleStatus}
        />
      )}

      <div
        className={`fixed bottom-6 right-6 z-50 transition-all duration-300 max-w-md ${
          toast ? 'translate-y-0 opacity-100' : 'translate-y-8 opacity-0 pointer-events-none'
        }`}
        role="status"
      >
        {toast && (
          <div
            className={`flex items-start gap-3 p-4 rounded-xl shadow-2xl text-white ${
              toast.type === 'error' ? 'bg-[#D32F2F]' : 'bg-[#00375e]'
            }`}
          >
            <span className="material-symbols-outlined text-[24px] shrink-0 mt-0.5">
              {toast.type === 'error' ? 'error' : 'verified'}
            </span>
            <div className="flex flex-col pr-2 min-w-0">
              <span className="font-bold text-sm">{toast.title}</span>
              <span className="text-xs text-white/85 mt-0.5 leading-snug">{toast.message}</span>
            </div>
            <button
              type="button"
              onClick={() => setToast(null)}
              className="p-1 text-white/70 hover:text-white shrink-0 -mr-1"
              aria-label="Đóng thông báo"
            >
              <span className="material-symbols-outlined text-base">close</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
