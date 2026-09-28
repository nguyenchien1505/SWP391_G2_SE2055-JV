import { categoryIcon } from './categoryLabels';

/**
 * Bảng danh mục + phân trang phía client. `canManage = false` (Manager) thì ẩn công tắc trạng
 * thái và nút sửa — backend cũng chặn mọi thao tác ghi với Manager (BR-ASSET-09).
 */
export default function CategoryTable({
  items,
  totalItems,
  currentPage,
  pageSize,
  loading,
  canManage,
  busyId,
  onPageChange,
  onToggleStatus,
  onEdit,
  onViewDetail,
}) {
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const startItem = totalItems === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const endItem = Math.min(currentPage * pageSize, totalItems);

  return (
    <div className="bg-white rounded-xl shadow-sm border border-[#DFE3E8]/80 overflow-hidden mb-6">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse min-w-[900px]">
          <thead>
            <tr className="bg-[#eff4ff] text-[#5B6472] text-[12px] font-semibold uppercase tracking-wider border-b border-[#DFE3E8]">
              <th className="py-3.5 px-4 font-semibold">Tên Danh Mục Tài Sản</th>
              <th className="py-3.5 px-4 font-semibold">Phân Loại Tài Sản</th>
              <th className="py-3.5 px-4 font-semibold">Mục Đích Sử Dụng</th>
              <th className="py-3.5 px-4 font-semibold text-center">Đơn Vị Tính</th>
              <th className="py-3.5 px-4 font-semibold">Trạng Thái Hệ Thống</th>
              <th className="py-3.5 px-4 font-semibold text-right">Thao Tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#eff4ff] text-[#1C2330] text-sm">
            {loading ? (
              <tr>
                <td colSpan="6" className="py-12 text-center text-[#5B6472]">
                  Đang tải danh mục…
                </td>
              </tr>
            ) : items.length === 0 ? (
              <tr>
                <td colSpan="6" className="py-12 text-center text-[#5B6472]">
                  <div className="flex flex-col items-center justify-center gap-2">
                    <span className="material-symbols-outlined text-4xl text-gray-400">inventory_2</span>
                    <p className="font-semibold text-base">Không tìm thấy danh mục phù hợp</p>
                    <p className="text-xs">Vui lòng thử điều chỉnh lại từ khóa tìm kiếm hoặc bộ lọc.</p>
                  </div>
                </td>
              </tr>
            ) : (
              items.map((item) => {
                const isFixed = item.assetKind === 'FIXED';
                const isGuest = item.purpose === 'GUEST_USE';

                return (
                  <tr
                    key={item.id}
                    className={`hover:bg-[#eff4ff]/60 transition-colors group ${!item.active ? 'bg-gray-50/50' : ''}`}
                  >
                    <td className="py-3.5 px-4">
                      <div className="flex items-center gap-3">
                        <div
                          className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 transition-transform group-hover:scale-105 ${
                            !item.active
                              ? 'bg-[#dae3f4] text-[#616161]'
                              : isFixed
                                ? 'bg-[#d1e4ff]/60 text-[#0e61a1]'
                                : 'bg-[#E8F5E9] text-[#2E7D32]'
                          }`}
                        >
                          <span className="material-symbols-outlined text-[20px]">
                            {categoryIcon(item.name, item.assetKind)}
                          </span>
                        </div>
                        <button
                          type="button"
                          onClick={() => onViewDetail(item)}
                          className="font-semibold text-sm text-[#1C2330] hover:text-[#0e61a1] text-left truncate transition-colors"
                        >
                          {item.name}
                        </button>
                      </div>
                    </td>

                    <td className="py-3.5 px-4 whitespace-nowrap">
                      {isFixed ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-[#E3F2FD] text-[#1565C0] border border-[#1565C0]/15">
                          <span className="material-symbols-outlined text-sm">domain_verification</span>
                          <span>Tài sản cố định (Fixed)</span>
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-[#E8F5E9] text-[#2E7D32] border border-[#2E7D32]/15">
                          <span className="material-symbols-outlined text-sm">local_cafe</span>
                          <span>Vật tư tiêu hao (Consumable)</span>
                        </span>
                      )}
                    </td>

                    <td className="py-3.5 px-4 whitespace-nowrap">
                      {isGuest ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-xs font-medium bg-[#e5eeff] text-[#1C2330]">
                          <span className="material-symbols-outlined text-xs text-[#0e61a1]">hotel</span>
                          <span>Guest Use (Dùng cho khách)</span>
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-xs font-medium bg-[#fff3e0] text-[#694400] border border-[#ef6c00]/20">
                          <span className="material-symbols-outlined text-xs text-[#EF6C00]">build</span>
                          <span>Facility Maintenance (Duy trì cơ sở)</span>
                        </span>
                      )}
                    </td>

                    <td className="py-3.5 px-4 text-center whitespace-nowrap">
                      {isFixed ? (
                        <span className="text-[#5B6472] font-mono text-base">—</span>
                      ) : (
                        <span className="px-2.5 py-0.5 rounded bg-[#eff4ff] font-semibold text-xs text-[#1C2330] border border-[#d0e4ff]">
                          {item.unit}
                        </span>
                      )}
                    </td>

                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <div className="flex items-center gap-2.5">
                        {canManage && (
                          <button
                            type="button"
                            role="switch"
                            aria-checked={item.active}
                            onClick={() => onToggleStatus(item)}
                            disabled={busyId === item.id}
                            aria-label={`Chuyển trạng thái ${item.name}`}
                            className={`w-10 h-5 rounded-full relative p-0.5 transition-colors focus:outline-none cursor-pointer disabled:opacity-50 ${
                              item.active ? 'bg-[#2E7D32]' : 'bg-[#72777f]'
                            }`}
                          >
                            <div
                              className={`w-4 h-4 bg-white rounded-full shadow-md transition-transform duration-200 ${
                                item.active ? 'translate-x-5' : 'translate-x-0'
                              }`}
                            />
                          </button>
                        )}
                        {item.active ? (
                          <span className="inline-flex items-center gap-1 text-xs text-[#2E7D32] font-semibold">
                            <span className="material-symbols-outlined text-xs">check_circle</span>
                            <span>Đang áp dụng</span>
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-xs text-[#EF6C00] font-semibold">
                            <span className="material-symbols-outlined text-xs">pause_circle</span>
                            <span>Tạm ngưng</span>
                          </span>
                        )}
                      </div>
                    </td>

                    <td className="py-3.5 px-4 text-right whitespace-nowrap">
                      <div className="flex items-center justify-end gap-1">
                        {canManage && (
                          <button
                            type="button"
                            onClick={() => onEdit(item)}
                            className="p-1.5 rounded-lg text-[#5B6472] hover:text-[#0e61a1] hover:bg-[#eff4ff] transition-colors"
                            title="Chỉnh sửa danh mục"
                          >
                            <span className="material-symbols-outlined text-[18px]">edit</span>
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => onViewDetail(item)}
                          className="p-1.5 rounded-lg text-[#5B6472] hover:text-[#00375e] hover:bg-[#eff4ff] transition-colors"
                          title="Xem chi tiết"
                        >
                          <span className="material-symbols-outlined text-[18px]">visibility</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      <div className="px-4 py-3 bg-[#eff4ff] border-t border-[#DFE3E8] flex flex-col sm:flex-row items-center justify-between gap-3">
        <div className="text-xs text-[#5B6472]">
          Hiển thị{' '}
          <span className="font-bold text-[#1C2330]">
            {startItem} - {endItem}
          </span>{' '}
          trên tổng số <span className="font-bold text-[#1C2330]">{totalItems}</span> danh mục
        </div>

        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => onPageChange(Math.max(1, currentPage - 1))}
            disabled={currentPage === 1}
            className="p-1.5 rounded bg-white text-[#5B6472] hover:bg-[#e5eeff] hover:text-[#1C2330] disabled:opacity-40 disabled:pointer-events-none border border-[#DFE3E8]"
            aria-label="Trang trước"
          >
            <span className="material-symbols-outlined text-[18px]">chevron_left</span>
          </button>
          {Array.from({ length: totalPages }, (_, i) => i + 1).map((pageNum) => (
            <button
              key={pageNum}
              type="button"
              onClick={() => onPageChange(pageNum)}
              className={`w-8 h-8 rounded text-xs font-semibold flex items-center justify-center transition-colors ${
                currentPage === pageNum
                  ? 'bg-[#00375e] text-white shadow-sm'
                  : 'bg-white text-[#1C2330] hover:bg-[#e5eeff] border border-[#DFE3E8]'
              }`}
            >
              {pageNum}
            </button>
          ))}
          <button
            type="button"
            onClick={() => onPageChange(Math.min(totalPages, currentPage + 1))}
            disabled={currentPage === totalPages}
            className="p-1.5 rounded bg-white text-[#5B6472] hover:bg-[#e5eeff] hover:text-[#1C2330] disabled:opacity-40 disabled:pointer-events-none border border-[#DFE3E8]"
            aria-label="Trang sau"
          >
            <span className="material-symbols-outlined text-[18px]">chevron_right</span>
          </button>
        </div>
      </div>
    </div>
  );
}
