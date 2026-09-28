/** Thẻ KPI đầu trang danh mục tài sản. Bấm thẻ Fixed / Consumable để lọc nhanh theo loại. */
export default function CategoryMetrics({ metrics, onFilterKind }) {
  const { total, fixed, consumable, active, inactive } = metrics;
  const activePercent = total > 0 ? (active / total) * 100 : 0;
  const inactivePercent = total > 0 ? 100 - activePercent : 0;

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
      <div className="bg-white rounded-xl p-4 sm:p-5 shadow-sm border border-[#DFE3E8]/80 flex flex-col justify-between hover:border-[#0e61a1]/40 transition-colors">
        <div className="flex items-start justify-between">
          <div>
            <span className="text-xs font-semibold text-[#5B6472] uppercase tracking-wider block">
              Tổng số danh mục
            </span>
            <div className="text-[28px] font-bold text-[#1C2330] mt-1 leading-tight">{total}</div>
          </div>
          <div className="p-2.5 rounded-lg bg-[#eff4ff] text-[#00375e]">
            <span className="material-symbols-outlined text-[24px]">category</span>
          </div>
        </div>
        <div className="mt-4 text-xs text-[#5B6472]">Dùng chung cho toàn bộ khách sạn trong chuỗi</div>
      </div>

      <div
        onClick={() => onFilterKind('FIXED')}
        className="bg-white rounded-xl p-4 sm:p-5 shadow-sm border border-[#DFE3E8]/80 flex flex-col justify-between hover:border-[#0e61a1] transition-all cursor-pointer group"
      >
        <div className="flex items-start justify-between">
          <div>
            <span className="text-xs font-semibold text-[#0e61a1] uppercase tracking-wider block">
              Tài sản cố định (Fixed)
            </span>
            <div className="text-[28px] font-bold text-[#0e61a1] mt-1 leading-tight flex items-baseline gap-1.5">
              {fixed} <span className="text-xs font-normal text-[#5B6472]">loại</span>
            </div>
          </div>
          <div className="p-2.5 rounded-lg bg-[#d1e4ff]/60 text-[#0e61a1] group-hover:bg-[#0e61a1] group-hover:text-white transition-colors">
            <span className="material-symbols-outlined text-[24px]">inventory</span>
          </div>
        </div>
        <p className="mt-4 text-xs text-[#5B6472] truncate">Quản lý theo từng cá thể (Tivi, điều hòa, minibar...)</p>
      </div>

      <div
        onClick={() => onFilterKind('CONSUMABLE')}
        className="bg-white rounded-xl p-4 sm:p-5 shadow-sm border border-[#DFE3E8]/80 flex flex-col justify-between hover:border-[#2E7D32] transition-all cursor-pointer group"
      >
        <div className="flex items-start justify-between">
          <div>
            <span className="text-xs font-semibold text-[#2E7D32] uppercase tracking-wider block">
              Vật tư tiêu hao (Consumable)
            </span>
            <div className="text-[28px] font-bold text-[#2E7D32] mt-1 leading-tight flex items-baseline gap-1.5">
              {consumable} <span className="text-xs font-normal text-[#5B6472]">loại</span>
            </div>
          </div>
          <div className="p-2.5 rounded-lg bg-[#E8F5E9] text-[#2E7D32] group-hover:bg-[#2E7D32] group-hover:text-white transition-colors">
            <span className="material-symbols-outlined text-[24px]">soap</span>
          </div>
        </div>
        <p className="mt-4 text-xs text-[#5B6472] truncate">Quản lý theo tồn kho (khăn tắm, nước suối, amenities...)</p>
      </div>

      <div className="bg-white rounded-xl p-4 sm:p-5 shadow-sm border border-[#DFE3E8]/80 flex flex-col justify-between">
        <div className="flex items-start justify-between">
          <div>
            <span className="text-xs font-semibold text-[#5B6472] uppercase tracking-wider block">
              Trạng thái áp dụng
            </span>
            <div className="flex items-baseline gap-2 mt-1">
              <span className="text-[28px] font-bold text-[#2E7D32] leading-tight">{active}</span>
              <span className="text-xs text-[#5B6472]">Hoạt động /</span>
              <span className="text-[28px] font-bold text-[#EF6C00] leading-tight">{inactive}</span>
              <span className="text-xs text-[#5B6472]">Tạm ngưng</span>
            </div>
          </div>
          <div className="p-2.5 rounded-lg bg-[#FFFDE7] text-[#F9A825]">
            <span className="material-symbols-outlined text-[24px]">toggle_on</span>
          </div>
        </div>
        <div
          className="mt-4 w-full bg-[#eff4ff] rounded-full h-2 overflow-hidden flex"
          title={`${activePercent.toFixed(1)}% Hoạt động - ${inactivePercent.toFixed(1)}% Tạm ngưng`}
        >
          <div className="bg-[#2E7D32] h-full transition-all duration-500" style={{ width: `${activePercent}%` }} />
          <div className="bg-[#EF6C00] h-full transition-all duration-500" style={{ width: `${inactivePercent}%` }} />
        </div>
      </div>
    </div>
  );
}
