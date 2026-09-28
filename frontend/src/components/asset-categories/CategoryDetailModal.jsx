import { categoryIcon, KIND_LABEL, PURPOSE_LABEL } from './categoryLabels';

/** Pop-up chi tiết danh mục. Nút tạm ngưng / sửa chỉ hiện với Giám đốc (`canManage`). */
export default function CategoryDetailModal({ item, canManage, busy, onClose, onEdit, onToggleStatus }) {
  const isFixed = item.assetKind === 'FIXED';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-white rounded-2xl shadow-2xl border border-[#DFE3E8] w-full max-w-2xl overflow-hidden max-h-[90vh] flex flex-col">
        <div className="px-6 py-4 bg-[#eff4ff] border-b border-[#DFE3E8] flex items-center justify-between">
          <div className="flex items-center gap-3 min-w-0">
            <div
              className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 text-white ${
                isFixed ? 'bg-[#1f4e78]' : 'bg-[#2E7D32]'
              }`}
            >
              <span className="material-symbols-outlined text-[24px]">{categoryIcon(item.name, item.assetKind)}</span>
            </div>
            <div className="min-w-0">
              <span
                className={`px-2 py-0.5 rounded-full text-[11px] font-bold inline-flex items-center gap-1 ${
                  item.active ? 'bg-[#E8F5E9] text-[#2E7D32]' : 'bg-gray-100 text-[#5B6472]'
                }`}
              >
                <span className="material-symbols-outlined text-xs">{item.active ? 'check_circle' : 'pause_circle'}</span>
                {item.active ? 'Đang áp dụng' : 'Tạm ngưng'}
              </span>
              <h2 className="text-lg font-bold text-[#1C2330] mt-0.5 break-words">{item.name}</h2>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-gray-400 hover:text-gray-700 hover:bg-white"
            aria-label="Đóng"
          >
            <span className="material-symbols-outlined text-xl">close</span>
          </button>
        </div>

        <div className="p-6 overflow-y-auto space-y-6 text-sm">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="p-3 rounded-xl bg-[#eff4ff] border border-[#d0e4ff]">
              <span className="text-xs text-[#5B6472] block">Loại tài sản</span>
              <span className="font-bold text-[#1C2330] text-sm mt-0.5 block">{KIND_LABEL[item.assetKind]}</span>
            </div>
            <div className="p-3 rounded-xl bg-[#eff4ff] border border-[#d0e4ff]">
              <span className="text-xs text-[#5B6472] block">Mục đích</span>
              <span className="font-bold text-[#1C2330] text-sm mt-0.5 block">{PURPOSE_LABEL[item.purpose]}</span>
            </div>
            <div className="p-3 rounded-xl bg-[#eff4ff] border border-[#d0e4ff]">
              <span className="text-xs text-[#5B6472] block">Đơn vị tính</span>
              <span className="font-bold text-[#1C2330] text-sm mt-0.5 block">
                {isFixed ? 'Quản lý theo cá thể' : item.unit}
              </span>
            </div>
          </div>

          <div className="p-3.5 rounded-xl bg-gray-50 border border-gray-200 text-xs sm:text-sm text-[#1C2330] leading-relaxed">
            {isFixed
              ? 'Mỗi tài sản thuộc danh mục này được theo dõi như một cá thể riêng, có mã và trạng thái riêng tại từng khách sạn.'
              : 'Vật tư thuộc danh mục này được theo dõi theo số lượng tồn kho tại từng khách sạn, tính theo đơn vị ở trên.'}
          </div>

          <div className="p-3.5 rounded-xl bg-[#eff4ff] border border-[#d0e4ff] text-xs text-[#5B6472] space-y-1">
            <div className="flex items-center justify-between">
              <span>Phạm vi áp dụng:</span>
              <span className="font-bold text-[#1C2330]">Toàn chuỗi (cấp Tenant)</span>
            </div>
            <div className="flex items-center justify-between">
              <span>Khi tạm ngưng:</span>
              <span className="text-[#1C2330] font-semibold text-right">
                Khóa tạo tài sản mới, giữ nguyên dữ liệu cũ
              </span>
            </div>
          </div>
        </div>

        <div className="px-6 py-4 bg-gray-50 border-t border-[#DFE3E8] flex items-center justify-between gap-3">
          {canManage ? (
            <button
              type="button"
              onClick={() => onToggleStatus(item)}
              disabled={busy}
              className={`px-4 py-2 rounded-lg text-xs sm:text-sm font-semibold flex items-center gap-1.5 transition-colors border disabled:opacity-50 ${
                item.active
                  ? 'bg-orange-50 text-[#EF6C00] border-orange-200 hover:bg-orange-100'
                  : 'bg-green-50 text-[#2E7D32] border-green-200 hover:bg-green-100'
              }`}
            >
              <span className="material-symbols-outlined text-base">{item.active ? 'pause_circle' : 'play_circle'}</span>
              <span>{item.active ? 'Tạm ngưng danh mục' : 'Kích hoạt danh mục'}</span>
            </button>
          ) : (
            <span />
          )}
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-lg text-xs sm:text-sm font-semibold text-[#1C2330] hover:bg-gray-200"
            >
              Đóng
            </button>
            {canManage && (
              <button
                type="button"
                onClick={() => onEdit(item)}
                className="px-4 py-2 rounded-lg text-xs sm:text-sm font-semibold bg-[#1f4e78] text-white hover:bg-[#00375e] flex items-center gap-1.5 shadow-sm"
              >
                <span className="material-symbols-outlined text-base">edit</span>
                <span>Chỉnh sửa danh mục</span>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
