import React, { useState, useEffect } from 'react';
import { assetService } from '../../services/assetApi';

export const ConsumableInventoryScreen = () => {
  const [loading, setLoading] = useState(true);
  const [consumables, setConsumables] = useState([]);
  const [search, setSearch] = useState('');
  const [purposeTab, setPurposeTab] = useState('all'); // 'all' | 'guest' | 'facility'
    const [stats, setStats] = useState({ totalStock: 0, categoriesCount: 0, guestCount: 0, facilityCount: 0 });

  // Audit Modal
  const [showBatchAudit, setShowBatchAudit] = useState(false);
  const [auditList, setAuditList] = useState([]);
  const [batchQuantities, setBatchQuantities] = useState({});
  const [isSaving, setIsSaving] = useState(false);
  const [toastMessage, setToastMessage] = useState('');

  useEffect(() => {
    loadConsumables();
  }, [search, purposeTab]);

  const loadConsumables = async () => {
    setLoading(true);
    try {
      const data = await assetService.getConsumables({ search, purpose: purposeTab });
      setConsumables(data.items || []);
      const overview = await assetService.getOverviewStats();
      if (overview.consumables) setStats(overview.consumables);
    } catch (e) {
      console.error('Failed to load consumables', e);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenAudit = (itemsToAudit) => {
    const items = Array.isArray(itemsToAudit) ? itemsToAudit : [itemsToAudit];
    setAuditList(items);
    const initialQty = {};
    items.forEach(item => {
      initialQty[item.id] = item.quantity;
    });
    setBatchQuantities(initialQty);
    setShowBatchAudit(true);
  };

  const handleAdjustQty = (id, delta) => {
    setBatchQuantities(prev => ({
      ...prev,
      [id]: Math.max(0, (prev[id] || 0) + delta)
    }));
  };

  const handleSetQty = (id, value) => {
    setBatchQuantities(prev => ({
      ...prev,
      [id]: Math.max(0, parseInt(value, 10) || 0)
    }));
  };

  const handleSaveAudit = async () => {
    setIsSaving(true);
    try {
      const lines = auditList.map(item => ({
        itemId: item.id,
        quantity: batchQuantities[item.id]
      }));
      await assetService.stockCount(lines);
      setToastMessage(`Đã cập nhật kiểm kê cho ${lines.length} dòng vật tư!`);
      setShowBatchAudit(false);
      await loadConsumables();
      setTimeout(() => setToastMessage(''), 3500);
    } catch (e) {
      console.error('Audit update failed', e);
    } finally {
      setIsSaving(false);
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

        <div className="flex items-center gap-2">
          <button
            onClick={() => {
              if (consumables.length > 0) handleOpenAudit(consumables);
            }}
            className="px-4 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold flex items-center gap-1.5 shadow-sm transition-colors cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">fact_check</span>
            <span>Kiểm kê lô hiển thị</span>
          </button>
        </div>
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
            <span className="text-xs text-[#5B6472]">Đồ dùng để duy trì cơ sở (Facility) {stats.facilityCount}</span>
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
                <th className="py-3 px-4 text-right">Hành Động</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#eff4ff]">
              {loading ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-[#5B6472]">
                    <div className="flex items-center justify-center gap-2">
                      <div className="w-4 h-4 border-2 border-[#00375e] border-t-transparent rounded-full animate-spin"></div>
                      <span>Đang tải sổ vật tư...</span>
                    </div>
                  </td>
                </tr>
              ) : consumables.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-[#5B6472]">
                    Không tìm thấy danh mục vật tư nào phù hợp.
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

                    {/* Quantity */}
                    <td className="py-3 px-4">
                      <span className="font-bold text-sm text-[#00375e] font-mono">
                        {item.quantity.toLocaleString('vi-VN')}
                      </span>
                      <span className="text-[11px] text-[#5B6472] ml-1">{item.unit}</span>
                    </td>

                    {/* Last Audit Date */}
                    <td className="py-3 px-4 text-[#1C2330]">
                      {item.lastAuditDate}
                    </td>

                    {/* Last Audit User */}
                    <td className="py-3 px-4 text-[#5B6472]">
                      <span className="font-medium text-[#1C2330]">{item.lastAuditUser}</span>
                    </td>

                    {/* Action */}
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => handleOpenAudit(item)}
                        className="px-3 py-1.5 rounded-lg bg-[#00375e] text-white hover:bg-[#1f4e78] font-semibold text-xs transition-colors cursor-pointer shadow-xs inline-flex items-center gap-1"
                        type="button"
                      >
                        <span className="material-symbols-outlined text-[14px]">edit_note</span>
                        <span>Kiểm kê</span>
                      </button>
                    </td>
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
                <div className="text-[11px] text-[#5B6472] mt-1">Cập nhật số lượng thực tế tại kho cho các danh mục dưới đây. Các dòng không thay đổi sẽ vẫn được giữ nguyên.</div>
              </div>

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
                          Tồn kho trên hệ thống: <span className="font-bold font-mono">{item.quantity}</span> {item.unit}
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
                        value={batchQuantities[item.id] ?? 0}
                        onChange={(e) => handleSetQty(item.id, e.target.value)}
                        className="w-16 text-center py-1.5 bg-white border border-[#0e61a1] rounded-lg font-bold font-mono text-sm text-[#00375e] focus:outline-none focus:ring-2 focus:ring-[#0e61a1]/20"
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
    </div>
  );
}
