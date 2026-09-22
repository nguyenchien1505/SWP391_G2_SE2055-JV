import React from 'react';

export function Table({
  columns,
  data,
  keyExtractor,
  onRowClick,
  isLoading = false,
  emptyMessage = 'Không có dữ liệu hiển thị',
}) {
  return (
    <div className="w-full overflow-x-auto bg-white border border-[#DFE3E8] rounded-lg shadow-2xs">
      <table className="w-full text-left border-collapse min-w-[800px]">
        <thead>
          <tr className="border-b border-[#DFE3E8] bg-[#F7F9FC]">
            {columns.map((col, idx) => (
              <th
                key={idx}
                style={{ width: col.width }}
                className={`px-4 py-3 text-[11px] font-bold uppercase tracking-wider text-[#5B6472] select-none ${
                  col.headerClassName || ''
                }`}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-[#DFE3E8]/70 text-sm">
          {isLoading ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-12 text-center text-[#5B6472]">
                <div className="inline-flex items-center gap-2">
                  <svg className="animate-spin h-5 w-5 text-[#00375E]" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  <span>Đang tải dữ liệu từ máy chủ...</span>
                </div>
              </td>
            </tr>
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-12 text-center text-[#5B6472]">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((item) => (
              <tr
                key={keyExtractor(item)}
                onClick={() => onRowClick?.(item)}
                className={`transition-colors duration-100 ${
                  onRowClick ? 'cursor-pointer hover:bg-[#F4F7FC]' : 'hover:bg-[#F9FAFC]'
                }`}
              >
                {columns.map((col, cIdx) => (
                  <td key={cIdx} className={`px-4 py-3.5 align-middle ${col.className || ''}`}>
                    {typeof col.accessor === 'function'
                      ? col.accessor(item)
                      : col.accessor
                      ? item[col.accessor]
                      : null}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
