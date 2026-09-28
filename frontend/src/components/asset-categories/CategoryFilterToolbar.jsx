const SELECT_CLASS =
  'appearance-none bg-[#eff4ff] hover:bg-[#e5eeff] text-xs sm:text-sm text-[#1C2330] pl-3 pr-8 py-1.5 rounded-lg border border-[#DFE3E8] focus:outline-none focus:border-[#0e61a1] cursor-pointer shadow-sm font-medium';

function FilterSelect({ label, value, onChange, children }) {
  return (
    <div className="flex items-center gap-2">
      <label className="text-xs font-semibold text-[#5B6472] whitespace-nowrap">{label}</label>
      <div className="relative">
        <select value={value} onChange={(e) => onChange(e.target.value)} className={SELECT_CLASS}>
          {children}
        </select>
        <span className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-[#5B6472] text-sm">
          expand_more
        </span>
      </div>
    </div>
  );
}

function Pill({ selected, activeClass, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`px-3 py-1 rounded-full text-xs font-semibold whitespace-nowrap transition-colors ${
        selected ? `${activeClass} text-white shadow-sm` : 'bg-[#eff4ff] text-[#5B6472] hover:bg-[#e5eeff] hover:text-[#1C2330]'
      }`}
    >
      {children}
    </button>
  );
}

/** Ô tìm kiếm + lọc theo loại / mục đích / trạng thái. Giá trị 'ALL' nghĩa là không lọc. */
export default function CategoryFilterToolbar({ filters, onChange, onReset, counts }) {
  const set = (key) => (value) => onChange({ ...filters, [key]: value });

  return (
    <div className="bg-white rounded-xl p-4 sm:p-5 shadow-sm border border-[#DFE3E8]/80 mb-4 flex flex-col gap-4">
      <div className="flex flex-col lg:flex-row gap-4 items-stretch lg:items-center justify-between">
        <div className="relative flex-1 max-w-xl">
          <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-[#5B6472] text-[20px]">
            search
          </span>
          <input
            type="text"
            value={filters.search}
            onChange={(e) => set('search')(e.target.value)}
            placeholder="Tìm theo tên danh mục (ví dụ: Tivi, Nước suối, Điều hòa...)"
            className="w-full pl-10 pr-9 py-2 bg-[#eff4ff] hover:bg-[#e5eeff] focus:bg-white rounded-lg text-sm text-[#1C2330] placeholder:text-[#5B6472] border border-transparent focus:border-[#0e61a1] outline-none transition-all"
          />
          {filters.search && (
            <button
              type="button"
              onClick={() => set('search')('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-[#5B6472] hover:text-[#1C2330] p-0.5"
              aria-label="Xóa từ khóa"
            >
              <span className="material-symbols-outlined text-base">close</span>
            </button>
          )}
        </div>

        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 lg:pb-0">
          <span className="text-xs font-semibold text-[#5B6472] whitespace-nowrap mr-1">Bộ lọc nhanh:</span>
          <Pill selected={filters.kind === 'ALL'} activeClass="bg-[#00375e]" onClick={() => set('kind')('ALL')}>
            Tất cả ({counts.total})
          </Pill>
          <Pill selected={filters.kind === 'FIXED'} activeClass="bg-[#0e61a1]" onClick={() => set('kind')('FIXED')}>
            Tài sản cố định ({counts.fixed})
          </Pill>
          <Pill
            selected={filters.kind === 'CONSUMABLE'}
            activeClass="bg-[#2E7D32]"
            onClick={() => set('kind')('CONSUMABLE')}
          >
            Vật tư tiêu hao ({counts.consumable})
          </Pill>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3 sm:gap-4 pt-1 border-t border-gray-100">
        <FilterSelect label="Loại:" value={filters.kind} onChange={set('kind')}>
          <option value="ALL">Tất cả loại tài sản ({counts.total})</option>
          <option value="FIXED">Tài sản cố định - Fixed ({counts.fixed})</option>
          <option value="CONSUMABLE">Vật tư tiêu hao - Consumable ({counts.consumable})</option>
        </FilterSelect>
        <FilterSelect label="Mục đích sử dụng:" value={filters.purpose} onChange={set('purpose')}>
          <option value="ALL">Tất cả mục đích</option>
          <option value="GUEST_USE">Guest Use (Dùng cho khách)</option>
          <option value="FACILITY_MAINTENANCE">Facility Maintenance (Duy trì cơ sở)</option>
        </FilterSelect>
        <FilterSelect label="Trạng thái:" value={filters.status} onChange={set('status')}>
          <option value="ALL">Tất cả trạng thái</option>
          <option value="ACTIVE">Đang áp dụng (Active)</option>
          <option value="INACTIVE">Tạm ngưng (Inactive)</option>
        </FilterSelect>

        <button
          type="button"
          onClick={onReset}
          className="ml-auto text-[#0e61a1] hover:text-[#00375e] text-xs font-semibold flex items-center gap-1 py-1 px-2 rounded hover:bg-[#eff4ff] transition-colors"
        >
          <span className="material-symbols-outlined text-sm">restart_alt</span>
          <span>Đặt lại bộ lọc</span>
        </button>
      </div>
    </div>
  );
}
