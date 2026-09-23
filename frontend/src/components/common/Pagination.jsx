import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

export const Pagination = ({
  currentPage,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  entityName = 'danh mục quy chuẩn',
}) => {
  const startItem = totalElements === 0 ? 0 : currentPage * pageSize + 1;
  const endItem = Math.min((currentPage + 1) * pageSize, totalElements);

  // Generate page numbers
  const pages = [];
  for (let i = 0; i < totalPages; i++) {
    pages.push(i);
  }

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-3 py-3 px-1 text-xs text-[#5B6472]">
      <div>
        Hiển thị <span className="font-semibold text-[#1C2330]">{startItem} - {endItem}</span> trong số{' '}
        <span className="font-semibold text-[#1C2330]">{totalElements}</span> {entityName}
      </div>

      <div className="flex items-center gap-1.5">
        <button
          type="button"
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage === 0}
          className="w-7 h-7 flex items-center justify-center rounded border border-[#DFE3E8] bg-white text-[#5B6472] hover:bg-[#F7F8FA] hover:text-[#1C2330] disabled:opacity-40 disabled:pointer-events-none transition-colors cursor-pointer"
          aria-label="Trang trước"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>

        {pages.map((p) => {
          const isActive = p === currentPage;
          return (
            <button
              key={p}
              type="button"
              onClick={() => onPageChange(p)}
              className={`w-7 h-7 flex items-center justify-center rounded text-xs font-medium transition-colors cursor-pointer ${
                isActive
                  ? 'bg-[#00375E] text-white font-semibold'
                  : 'bg-white border border-[#DFE3E8] text-[#1C2330] hover:bg-[#F7F8FA]'
              }`}
            >
              {p + 1}
            </button>
          );
        })}

        <button
          type="button"
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage >= totalPages - 1}
          className="w-7 h-7 flex items-center justify-center rounded border border-[#DFE3E8] bg-white text-[#5B6472] hover:bg-[#F7F8FA] hover:text-[#1C2330] disabled:opacity-40 disabled:pointer-events-none transition-colors cursor-pointer"
          aria-label="Trang sau"
        >
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};
