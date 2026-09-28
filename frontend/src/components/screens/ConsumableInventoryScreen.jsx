import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { assetService } from '../../services/assetApi';
import { useAuth } from '../../context/AuthContext';

/** Khớp ràng buộc backend: không âm, tối đa 10 chữ số nguyên và 2 chữ số thập phân (BR-ASSET-04). */
const QTY_PATTERN = /^\d{1,10}(\.\d{1,2})?$/;

/** Chuỗi người dùng nhập → số, hoặc null nếu sai định dạng. */
const parseQty = (value) => {
  const text = String(value ?? '').trim();
  return QTY_PATTERN.test(text) ? Number(text) : null;
};

/** Làm tròn 2 chữ số thập phân — tránh 0.1 + 0.2 = 0.30000000000000004 khi bấm +/-. */
const round2 = (n) => Math.round(n * 100) / 100;

const formatQty = (n) => Number(n).toLocaleString('vi-VN', { maximumFractionDigits: 2 });

const errorMessage = (e, fallback) => {
  if (e?.status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
  if (e?.status === 0) return 'Không kết nối được máy chủ.';
  return e?.message || fallback;
};

export const ConsumableInventoryScreen = () => {
  const { user } = useAuth();
  // Chỉ Manager ghi tồn kho trong khách sạn của mình; Giám đốc chỉ xem (BR-ASSET-09).
  const canEdit = user?.role === 'MANAGER';

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [allItems, setAllItems] = useState([]);
  const [search, setSearch] = useState('');
  const [purposeTab, setPurposeTab] = useState('all'); // 'all' | 'guest' | 'facility'
  const [toastMessage, setToastMessage] = useState('');

  // Audit Modal
  const [showBatchAudit, setShowBatchAudit] = useState(false);
  const [auditList, setAuditList] = useState([]);
  const [batchQuantities, setBatchQuantities] = useState({});
  const [auditError, setAuditError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  // Add Modal
  const [showAdd, setShowAdd] = useState(false);
  const [categories, setCategories] = useState([]);
  const [categoriesLoading, setCategoriesLoading] = useState(false);
  const [addForm, setAddForm] = useState({ categoryId: '', quantity: '0' });
  const [addError, setAddError] = useState('');
  const [isAdding, setIsAdding] = useState(false);

  const [deletingId, setDeletingId] = useState(null);

  const showToast = (message) => {
    setToastMessage(message);
    setTimeout(() => setToastMessage(''), 3500);
  };

  const loadConsumables = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    // Manager cần thêm danh mục tiêu hao của Tenant để thấy cả danh mục Giám đốc tạo nhưng
    // khách sạn mình chưa đưa vào kho. Lỗi tải danh mục không được làm mất danh sách tồn kho.
    const [itemsRes, categoriesRes] = await Promise.allSettled([
      assetService.getAllConsumables(),
      canEdit ? assetService.getActiveConsumableCategories() : Promise.resolve([]),
    ]);
    if (itemsRes.status === 'fulfilled') {
      setAllItems(itemsRes.value);
    } else {
      setLoadError(errorMessage(itemsRes.reason, 'Không tải được danh sách vật tư.'));
    }
    if (categoriesRes.status === 'fulfilled') {
      setCategories(categoriesRes.value);
    } else if (itemsRes.status === 'fulfilled') {
      setLoadError(errorMessage(categoriesRes.reason, 'Không tải được danh mục vật tư của hệ thống.'));
    }
    setLoading(false);
  }, [canEdit]);

  useEffect(() => {
    loadConsumables();
  }, [loadConsumables]);

  // Mỗi khách sạn chỉ có 1 dòng cho mỗi danh mục (DM-11) — danh mục chưa có dòng là chưa vào kho.
  const availableCategories = useMemo(() => {
    const inStock = new Set(allItems.map((i) => i.categoryId));
    return categories.filter((c) => !inStock.has(c.id));
  }, [categories, allItems]);

  // Danh mục Giám đốc tạo KHÔNG tự sinh dòng tồn kho — hiện thành dòng "Chưa có trong kho" để
  // Manager thấy và thêm vào. Giám đốc xem toàn Tenant nên không có khái niệm này.
  const rows = useMemo(() => {
    if (!canEdit) return allItems;
    const notStocked = availableCategories.map((c) => ({
      id: `not-stocked-${c.id}`,
      categoryId: c.id,
      name: c.name,
      description: c.name,
      icon: 'inventory_2',
      unit: c.unit || '',
      quantity: 0,
      notStocked: true,
      lastAuditDate: '—',
      lastAuditUser: '—',
      purpose: c.purpose,
      purposeLabel: c.purposeLabel,
    }));
    return [...allItems, ...notStocked].sort((a, b) => (a.name || '').localeCompare(b.name || '', 'vi'));
  }, [canEdit, allItems, availableCategories]);

  // Lọc ở client trên TOÀN BỘ tồn kho — backend chưa hỗ trợ tìm theo tên/mục đích.
  const consumables = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return rows.filter(
      (i) =>
        (purposeTab === 'all' || i.purpose === purposeTab) &&
        (!keyword || (i.name || '').toLowerCase().includes(keyword))
    );
  }, [rows, search, purposeTab]);

  // Chỉ dòng đã có trong kho mới kiểm kê được.
  const auditable = useMemo(() => consumables.filter((i) => !i.notStocked), [consumables]);

  const stats = useMemo(() => {
    const countDistinct = (items) => new Set(items.map((i) => i.categoryId)).size;
    return {
      categoriesCount: countDistinct(rows),
      guestCount: countDistinct(rows.filter((i) => i.purpose === 'guest')),
      facilityCount: countDistinct(rows.filter((i) => i.purpose === 'facility')),
    };
  }, [rows]);

  // ── Kiểm kê ─────────────────────────────────────────────────────────────

  const handleOpenAudit = (itemsToAudit) => {
    const items = Array.isArray(itemsToAudit) ? itemsToAudit : [itemsToAudit];
    setAuditList(items);
    const initialQty = {};
    items.forEach(item => {
      initialQty[item.id] = String(item.quantity);
    });
    setBatchQuantities(initialQty);
    setAuditError('');
    setShowBatchAudit(true);
  };

  const handleAdjustQty = (id, delta) => {
    setBatchQuantities(prev => {
      const current = parseQty(prev[id]) ?? 0;
      return { ...prev, [id]: String(Math.max(0, round2(current + delta))) };
    });
  };

  const handleSetQty = (id, value) => {
    setBatchQuantities(prev => ({ ...prev, [id]: value }));
  };

  const handleSaveAudit = async () => {
    const invalid = auditList.filter(item => parseQty(batchQuantities[item.id]) === null);
    if (invalid.length > 0) {
      setAuditError(
        `Số lượng không hợp lệ ở: ${invalid.map(i => i.name).join(', ')}. `
          + 'Chỉ nhập số không âm, tối đa 2 chữ số thập phân.'
      );
      return;
    }

    setIsSaving(true);
    setAuditError('');
    try {
      const lines = auditList.map(item => ({
        itemId: item.id,
        quantity: parseQty(batchQuantities[item.id]),
      }));
      await assetService.stockCount(lines);
      setShowBatchAudit(false);
      showToast(`Đã cập nhật kiểm kê cho ${lines.length} dòng vật tư!`);
      await loadConsumables();
    } catch (e) {
      setAuditError(errorMessage(e, 'Không lưu được kết quả kiểm kê.'));
    } finally {
      setIsSaving(false);
    }
  };

  // ── Thêm mặt hàng vào kho ───────────────────────────────────────────────

  /** @param categoryId chọn sẵn danh mục khi mở từ một dòng "Chưa có trong kho". */
  const handleOpenAdd = async (categoryId = '') => {
    setAddForm({ categoryId, quantity: '0' });
    setAddError('');
    setShowAdd(true);
    setCategoriesLoading(true);
    try {
      setCategories(await assetService.getActiveConsumableCategories());
    } catch (e) {
      setAddError(errorMessage(e, 'Không tải được danh mục vật tư.'));
    } finally {
      setCategoriesLoading(false);
    }
  };

  const handleSaveAdd = async () => {
    if (!addForm.categoryId) {
      setAddError('Vui lòng chọn danh mục vật tư.');
      return;
    }
    const quantity = parseQty(addForm.quantity);
    if (quantity === null) {
      setAddError('Số lượng chỉ nhận số không âm, tối đa 2 chữ số thập phân.');
      return;
    }

    setIsAdding(true);
    setAddError('');
    try {
      await assetService.addConsumableItem({ categoryId: addForm.categoryId, quantity });
      setShowAdd(false);
      showToast('Đã thêm vật tư vào kho!');
      await loadConsumables();
    } catch (e) {
      setAddError(errorMessage(e, 'Không thêm được vật tư vào kho.'));
    } finally {
      setIsAdding(false);
    }
  };

  // ── Xóa mặt hàng khỏi kho ───────────────────────────────────────────────

  const handleDelete = async (item) => {
    if (!window.confirm(`Bỏ "${item.name}" khỏi kho? Thao tác này không hoàn tác được.`)) return;
    setDeletingId(item.id);
    try {
      await assetService.deleteConsumableItem(item.id);
      showToast(`Đã bỏ "${item.name}" khỏi kho.`);
      await loadConsumables();
    } catch (e) {
      setLoadError(errorMessage(e, 'Không xóa được vật tư.'));
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 bg-[#00375e] text-white px-4 py-3 rounded-xl shadow-xl flex items-center gap-2 text-xs font-semibold animate-in slide-in-from-bottom-3 duration-200">
          <span className="material-symbols-outlined text-[18px] text-[#2E7D32] bg-white rounded-full">check_circle</span>
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Top Banner */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-white p-5 rounded-2xl border border-[#DFE3E8] shadow-[0_1px_8px_rgba(0,0,0,0.02)]">
        <div>
          <div className="flex items-center gap-2 text-xs text-[#5B6472] mb-1">
            <span className="font-semibold text-[#00375e]">TÀI SẢN VẬT TƯ</span>
            <span>/</span>
            <span className="text-[#0e61a1]">VẬT TƯ TIÊU HAO</span>
          </div>
          <h1 className="text-xl font-bold text-[#00375e] tracking-tight">
            Sổ Quản Lý Vật Tư Tiêu Hao & Định Mức Buồng Phòng
          </h1>
          <p className="text-xs text-[#5B6472] mt-0.5">
            Quản lý số lượng tồn kho định mức buồng phòng, amenities và hóa chất làm sạch chi nhánh Sao Mai Nha Trang.
          </p>
        </div>

        {canEdit && (
          <div className="flex items-center gap-2">
            <button
              onClick={() => handleOpenAdd()}
              className="px-4 py-2 rounded-xl border border-[#00375e] bg-white text-[#00375e] hover:bg-[#eff4ff] text-xs font-semibold flex items-center gap-1.5 shadow-sm transition-colors cursor-pointer"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
              <span>Thêm vật tư vào kho</span>
            </button>
            <button
              onClick={() => handleOpenAudit(auditable)}
              disabled={auditable.length === 0}
              className="px-4 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold flex items-center gap-1.5 shadow-sm transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">fact_check</span>
              <span>Kiểm kê lô hiển thị ({auditable.length})</span>
            </button>
          </div>
        )}
      </div>

      {/* 3 Metric Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white p-4 rounded-xl border border-[#DFE3E8] shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs text-[#5B6472]">Tổng danh mục</span>
            <span className="material-symbols-outlined text-[20px] text-[#0e61a1]">category</span>
          </div>
          <div className="text-2xl font-extrabold text-[#00375e] mt-2">{stats.categoriesCount}</div>
          <div className="text-[11px] text-[#2E7D32] mt-0.5 font-medium">100% nhóm chuẩn hóa</div>
        </div>

        <div className="bg-white p-4 rounded-xl border border-[#DFE3E8] shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs text-[#5B6472]">Đồ dùng cho khách (Guest Use)</span>
            <span className="material-symbols-outlined text-[20px] text-[#2E7D32]">hotel</span>
          </div>
          <div className="text-2xl font-extrabold text-[#2E7D32] mt-2">{stats.guestCount}</div>
          <div className="text-[11px] text-[#5B6472] mt-0.5 font-medium">Amenities, nước khoáng, khăn...</div>
        </div>

        <div className="bg-white p-4 rounded-xl border border-[#DFE3E8] shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs text-[#5B6472]">Đồ dùng để duy trì cơ sở (Facility)</span>
            <span className="material-symbols-outlined text-[20px] text-[#0e61a1]">cleaning_services</span>
          </div>
          <div className="text-2xl font-extrabold text-[#0e61a1] mt-2">{stats.facilityCount}</div>
          <div className="text-[11px] text-[#5B6472] mt-0.5 font-medium">Hóa chất làm sạch, túi rác...</div>
        </div>
      </div>

      {/* Filter and Tab Bar */}
      <div className="bg-white p-4 rounded-2xl border border-[#DFE3E8] shadow-xs space-y-3">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          {/* Tabs */}
          <div className="flex items-center gap-1.5 p-1 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
            <button
              onClick={() => setPurposeTab('all')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                purposeTab === 'all'
                  ? 'bg-[#00375e] text-white shadow-xs'
                  : 'text-[#5B6472] hover:text-[#1C2330]'
              }`}
              type="button"
            >
              Tất cả ({stats.categoriesCount})
            </button>
            <button
              onClick={() => setPurposeTab('guest')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                purposeTab === 'guest'
                  ? 'bg-[#00375e] text-white shadow-xs'
                  : 'text-[#5B6472] hover:text-[#1C2330]'
              }`}
              type="button"
            >
              Dùng cho khách (Guest) {stats.guestCount}
            </button>
            <button
              onClick={() => setPurposeTab('facility')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                purposeTab === 'facility'
                  ? 'bg-[#00375e] text-white shadow-xs'
                  : 'text-[#5B6472] hover:text-[#1C2330]'
              }`}
              type="button"
            >
              Duy trì cơ sở (Facility) {stats.facilityCount}
            </button>
          </div>

          {/* Search box */}
          <div className="relative w-full sm:w-72">
            <span className="material-symbols-outlined absolute left-3 top-2.5 text-[18px] text-[#5B6472]">
              search
            </span>
            <input
              type="text"
              placeholder="Tìm theo tên vật tư..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-9 pr-3 py-2 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl text-xs text-[#1C2330] placeholder-[#72777f] focus:outline-none focus:border-[#0e61a1]"
            />
          </div>
        </div>
      </div>

      {/* Load / delete error */}
      {loadError && (
        <div className="flex items-center justify-between gap-3 p-3.5 rounded-xl border border-[#f5c2c0] bg-[#fdecea] text-xs text-[#b3261e]">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[18px]">error</span>
            <span>{loadError}</span>
          </div>
          <button
            onClick={loadConsumables}
            className="px-3 py-1 rounded-lg border border-[#b3261e] font-semibold hover:bg-white cursor-pointer"
            type="button"
          >
            Tải lại
          </button>
        </div>
      )}

      {/* Consumables Table */}
      <div className="bg-white rounded-2xl border border-[#DFE3E8] shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#eff4ff] text-[#00375e] font-semibold border-b border-[#DFE3E8]">
              <tr>
                <th className="py-3 px-4">Danh Mục Vật Tư</th>
                <th className="py-3 px-4">Mục Đích Sử Dụng</th>
                <th className="py-3 px-4">Đơn Vị Tính</th>
                <th className="py-3 px-4">Tồn Kho Hiện Tại</th>
                <th className="py-3 px-4">Lần Kiểm Gần Nhất</th>
                <th className="py-3 px-4">Người Kiểm Gần Nhất</th>
                {canEdit && <th className="py-3 px-4 text-right">Hành Động</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-[#eff4ff]">
              {loading ? (
                <tr>
                  <td colSpan={canEdit ? 7 : 6} className="py-8 text-center text-[#5B6472]">
                    <div className="flex items-center justify-center gap-2">
                      <div className="w-4 h-4 border-2 border-[#00375e] border-t-transparent rounded-full animate-spin"></div>
                      <span>Đang tải sổ vật tư...</span>
                    </div>
                  </td>
                </tr>
              ) : consumables.length === 0 ? (
                <tr>
                  <td colSpan={canEdit ? 7 : 6} className="py-8 text-center text-[#5B6472]">
                    {loadError
                      ? 'Chưa tải được dữ liệu.'
                      : rows.length === 0
                        ? 'Chưa có danh mục vật tư tiêu hao nào.'
                        : 'Không tìm thấy danh mục vật tư nào phù hợp.'}
                  </td>
                </tr>
              ) : (
                consumables.map((item) => (
                  <tr key={item.id} className="hover:bg-[#f8f9ff] transition-colors">
                    {/* Item Name */}
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-2.5">
                        <div className="w-8 h-8 rounded-lg bg-[#eff4ff] text-[#0e61a1] flex items-center justify-center shrink-0">
                          <span className="material-symbols-outlined text-[18px]">{item.icon}</span>
                        </div>
                        <div>
                          <div className="font-semibold text-[#1C2330]">{item.name}</div>
                          <div className="text-[11px] text-[#5B6472]">{item.description}</div>
                        </div>
                      </div>
                    </td>

                    {/* Purpose */}
                    <td className="py-3 px-4">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium ${
                        item.purpose === 'guest'
                          ? 'bg-[#eff4ff] text-[#00375e]'
                          : 'bg-[#F7F8FA] text-[#5B6472] border border-[#DFE3E8]'
                      }`}>
                        {item.purposeLabel}
                      </span>
                    </td>

                    {/* Unit */}
                    <td className="py-3 px-4 font-medium text-[#1C2330]">
                      {item.unit}
                    </td>

                    {/* Quantity — BR-ASSET-10: chỉ phân biệt còn hàng / hết hàng */}
                    <td className="py-3 px-4">
                      {item.notStocked ? (
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-[#F7F8FA] text-[#5B6472] border border-dashed border-[#c2c7cf]">
                          Chưa có trong kho
                        </span>
                      ) : (
                        <>
                          <span className="font-bold text-sm text-[#00375e] font-mono">
                            {formatQty(item.quantity)}
                          </span>
                          <span className="text-[11px] text-[#5B6472] ml-1">{item.unit}</span>
                          {item.quantity <= 0 && (
                            <span className="ml-2 inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-[#fdecea] text-[#b3261e]">
                              Hết hàng
                            </span>
                          )}
                        </>
                      )}
                    </td>

                    {/* Last Audit Date */}
                    <td className="py-3 px-4 text-[#1C2330]">
                      {item.lastAuditDate}
                    </td>

                    {/* Last Audit User */}
                    <td className="py-3 px-4 text-[#5B6472]">
                      <span className="font-medium text-[#1C2330]">{item.lastAuditUser}</span>
                    </td>

                    {/* Action — chỉ Manager */}
                    {canEdit && item.notStocked && (
                      <td className="py-3 px-4 text-right">
                        <button
                          onClick={() => handleOpenAdd(item.categoryId)}
                          className="px-3 py-1.5 rounded-lg border border-[#00375e] bg-white text-[#00375e] hover:bg-[#eff4ff] font-semibold text-xs transition-colors cursor-pointer inline-flex items-center gap-1"
                          type="button"
                        >
                          <span className="material-symbols-outlined text-[14px]">add</span>
                          <span>Thêm vào kho</span>
                        </button>
                      </td>
                    )}
                    {canEdit && !item.notStocked && (
                      <td className="py-3 px-4 text-right">
                        <div className="inline-flex items-center gap-1.5">
                          <button
                            onClick={() => handleOpenAudit(item)}
                            className="px-3 py-1.5 rounded-lg bg-[#00375e] text-white hover:bg-[#1f4e78] font-semibold text-xs transition-colors cursor-pointer shadow-xs inline-flex items-center gap-1"
                            type="button"
                          >
                            <span className="material-symbols-outlined text-[14px]">edit_note</span>
                            <span>Kiểm kê</span>
                          </button>
                          <button
                            onClick={() => handleDelete(item)}
                            disabled={item.quantity > 0 || deletingId === item.id}
                            title={item.quantity > 0 ? 'Kiểm kê về 0 trước khi bỏ mặt hàng khỏi kho' : 'Bỏ khỏi kho'}
                            className="w-8 h-8 rounded-lg border border-[#DFE3E8] text-[#b3261e] hover:bg-[#fdecea] inline-flex items-center justify-center cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                            type="button"
                          >
                            <span className="material-symbols-outlined text-[16px]">delete</span>
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Batch Audit Modal */}
      {showBatchAudit && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1C2330]/50 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="bg-white w-full max-w-3xl rounded-2xl shadow-xl border border-[#DFE3E8] flex flex-col max-h-[85vh]">
            <div className="flex items-center justify-between border-b border-[#DFE3E8] p-5 shrink-0">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[#0e61a1] text-[20px]">fact_check</span>
                <h3 className="font-bold text-[#00375e] text-base">Kiểm Kê Hàng Loạt</h3>
              </div>
              <button
                onClick={() => setShowBatchAudit(false)}
                className="text-[#5B6472] hover:text-[#1C2330] cursor-pointer"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="p-5 overflow-y-auto space-y-4">
              <div className="p-3.5 bg-[#eff4ff] rounded-xl border border-[#d1e4ff] text-xs">
                <span className="font-semibold text-[#00375e]">Đang kiểm kê {auditList.length} mặt hàng.</span>
                <div className="text-[11px] text-[#5B6472] mt-1">
                  Cập nhật số lượng thực tế tại kho cho các danh mục dưới đây. Mọi dòng trong đợt đều được ghi nhận
                  mốc kiểm kê mới, kể cả dòng giữ nguyên số lượng.
                </div>
              </div>

              {auditError && (
                <div className="p-3 rounded-xl border border-[#f5c2c0] bg-[#fdecea] text-xs text-[#b3261e]">
                  {auditError}
                </div>
              )}

              <div className="space-y-3">
                {auditList.map(item => (
                  <div key={item.id} className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-3 border border-[#DFE3E8] rounded-xl bg-[#F7F8FA] hover:bg-white transition-colors">
                    <div className="flex-1 flex items-center gap-3">
                      <div className="w-10 h-10 rounded-lg bg-white border border-[#DFE3E8] text-[#0e61a1] flex items-center justify-center shrink-0">
                        <span className="material-symbols-outlined text-[20px]">{item.icon}</span>
                      </div>
                      <div>
                        <div className="font-bold text-sm text-[#00375e]">{item.name}</div>
                        <div className="text-[11px] text-[#5B6472] mt-0.5">
                          Tồn kho trên hệ thống: <span className="font-bold font-mono">{formatQty(item.quantity)}</span> {item.unit}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0 bg-white p-1.5 rounded-xl border border-[#DFE3E8]">
                      <button
                        type="button"
                        onClick={() => handleAdjustQty(item.id, -10)}
                        className="w-8 h-8 flex items-center justify-center bg-[#F7F8FA] hover:bg-[#eff4ff] border border-[#DFE3E8] rounded-lg text-xs font-bold text-[#00375e] cursor-pointer"
                        title="-10"
                      >
                        -10
                      </button>
                      <button
                        type="button"
                        onClick={() => handleAdjustQty(item.id, -1)}
                        className="w-8 h-8 flex items-center justify-center bg-[#F7F8FA] hover:bg-[#eff4ff] border border-[#DFE3E8] rounded-lg text-xs font-bold text-[#00375e] cursor-pointer"
                        title="-1"
                      >
                        -1
                      </button>

                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        value={batchQuantities[item.id] ?? ''}
                        onChange={(e) => handleSetQty(item.id, e.target.value)}
                        className={`w-20 text-center py-1.5 bg-white border rounded-lg font-bold font-mono text-sm text-[#00375e] focus:outline-none focus:ring-2 focus:ring-[#0e61a1]/20 ${
                          parseQty(batchQuantities[item.id]) === null ? 'border-[#b3261e]' : 'border-[#0e61a1]'
                        }`}
                      />

                      <button
                        type="button"
                        onClick={() => handleAdjustQty(item.id, 1)}
                        className="w-8 h-8 flex items-center justify-center bg-[#F7F8FA] hover:bg-[#eff4ff] border border-[#DFE3E8] rounded-lg text-xs font-bold text-[#00375e] cursor-pointer"
                        title="+1"
                      >
                        +1
                      </button>
                      <button
                        type="button"
                        onClick={() => handleAdjustQty(item.id, 10)}
                        className="w-8 h-8 flex items-center justify-center bg-[#F7F8FA] hover:bg-[#eff4ff] border border-[#DFE3E8] rounded-lg text-xs font-bold text-[#00375e] cursor-pointer"
                        title="+10"
                      >
                        +10
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Modal Actions */}
            <div className="flex items-center justify-end gap-2 p-4 border-t border-[#DFE3E8] shrink-0 bg-[#F7F8FA] rounded-b-2xl">
              <button
                onClick={() => setShowBatchAudit(false)}
                className="px-4 py-2 rounded-xl border border-[#DFE3E8] bg-white text-xs font-semibold text-[#5B6472] hover:bg-[#eff4ff] cursor-pointer"
                type="button"
              >
                Hủy bỏ
              </button>
              <button
                onClick={handleSaveAudit}
                disabled={isSaving}
                className="px-6 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-bold shadow-sm cursor-pointer disabled:opacity-50 flex items-center gap-2"
                type="button"
              >
                {isSaving ? (
                  <>
                    <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                    <span>Đang lưu...</span>
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined text-[16px]">save</span>
                    <span>Xác nhận & Lưu kiểm kê</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Item Modal */}
      {showAdd && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1C2330]/50 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="bg-white w-full max-w-md rounded-2xl shadow-xl border border-[#DFE3E8] flex flex-col">
            <div className="flex items-center justify-between border-b border-[#DFE3E8] p-5">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[#0e61a1] text-[20px]">add_box</span>
                <h3 className="font-bold text-[#00375e] text-base">Thêm Vật Tư Vào Kho</h3>
              </div>
              <button
                onClick={() => setShowAdd(false)}
                className="text-[#5B6472] hover:text-[#1C2330] cursor-pointer"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="p-5 space-y-4 text-xs">
              {addError && (
                <div className="p-3 rounded-xl border border-[#f5c2c0] bg-[#fdecea] text-[#b3261e]">
                  {addError}
                </div>
              )}

              <label className="block space-y-1.5">
                <span className="font-semibold text-[#1C2330]">Danh mục vật tư</span>
                <select
                  value={addForm.categoryId}
                  onChange={(e) => setAddForm((f) => ({ ...f, categoryId: e.target.value }))}
                  disabled={categoriesLoading}
                  className="w-full px-3 py-2 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl text-xs text-[#1C2330] focus:outline-none focus:border-[#0e61a1]"
                >
                  <option value="">
                    {categoriesLoading
                      ? 'Đang tải danh mục...'
                      : availableCategories.length === 0
                        ? 'Mọi danh mục tiêu hao đã có trong kho'
                        : '-- Chọn danh mục --'}
                  </option>
                  {availableCategories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}{c.unit ? ` (${c.unit})` : ''}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block space-y-1.5">
                <span className="font-semibold text-[#1C2330]">Số lượng ban đầu</span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={addForm.quantity}
                  onChange={(e) => setAddForm((f) => ({ ...f, quantity: e.target.value }))}
                  className="w-full px-3 py-2 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl text-xs font-mono text-[#1C2330] focus:outline-none focus:border-[#0e61a1]"
                />
                <span className="block text-[11px] text-[#5B6472]">Được ghi nhận là lần kiểm kê đầu tiên.</span>
              </label>
            </div>

            <div className="flex items-center justify-end gap-2 p-4 border-t border-[#DFE3E8] bg-[#F7F8FA] rounded-b-2xl">
              <button
                onClick={() => setShowAdd(false)}
                className="px-4 py-2 rounded-xl border border-[#DFE3E8] bg-white text-xs font-semibold text-[#5B6472] hover:bg-[#eff4ff] cursor-pointer"
                type="button"
              >
                Hủy bỏ
              </button>
              <button
                onClick={handleSaveAdd}
                disabled={isAdding || categoriesLoading}
                className="px-6 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-bold shadow-sm cursor-pointer disabled:opacity-50 flex items-center gap-2"
                type="button"
              >
                {isAdding ? 'Đang lưu...' : 'Thêm vào kho'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
