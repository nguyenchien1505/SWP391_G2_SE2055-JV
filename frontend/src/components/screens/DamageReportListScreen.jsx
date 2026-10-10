import React, { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { assetService } from '../../services/assetApi';
import { DamageReportModal } from '../modals/DamageReportModal';

const PAGE_SIZE = 10;

/** `status` gửi lên backend: không truyền = chỉ NEW, rỗng = tất cả (DM-16). */
const TABS = [
  { key: 'NEW', label: 'Đang chờ xử lý' },
  { key: 'RESOLVED', label: 'Đã xử lý' },
  { key: '', label: 'Tất cả' },
];

const TICKET_BADGE = {
  New: 'bg-[#FFF3E0] text-[#EF6C00] border border-[#EF6C00]/30',
  Processed: 'bg-[#E8F5E9] text-[#2E7D32] border border-[#2E7D32]/30',
};

/**
 * Danh sách báo hỏng — Manager xử lý trong khách sạn của mình, Giám đốc xem toàn Tenant
 * (BR-ASSET-06). DM-16: không có thông báo đẩy, màn này chính là "hộp thư" — mặc định mở tab
 * phiếu đang chờ. Phân trang ở server.
 */
export const DamageReportListScreen = ({ initialIncidentId }) => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isDirector = user?.role === 'DIRECTOR';

  const [status, setStatus] = useState('NEW');
  const [page, setPage] = useState(1);
  const [data, setData] = useState({ items: [], total: 0, totalPages: 0 });
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  // Phiếu đang mở trong hộp thoại xử lý; mở thẳng khi vào bằng link cũ /bao-hong/:id.
  const [openId, setOpenId] = useState(initialIncidentId ?? null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      setData(await assetService.getDamageReports({ status, page, limit: PAGE_SIZE }));
    } catch (err) {
      setLoadError(err?.message || 'Không tải được danh sách báo hỏng.');
    } finally {
      setLoading(false);
    }
  }, [status, page]);

  useEffect(() => {
    load();
  }, [load]);

  const totalPages = Math.max(1, data.totalPages);

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      <div className="bg-white p-5 rounded-2xl border border-[#DFE3E8] shadow-[0_1px_8px_rgba(0,0,0,0.02)]">
        <div className="flex items-center gap-2 text-xs text-[#5B6472] mb-1">
          <span className="font-semibold text-[#00375e]">TÀI SẢN VẬT TƯ</span>
          <span>/</span>
          <span className="text-[#0e61a1]">BÁO HỎNG</span>
        </div>
        <h1 className="text-xl font-bold text-[#00375e] tracking-tight">Báo Hỏng Tài Sản</h1>
        <p className="text-xs text-[#5B6472] mt-0.5">
          {isDirector
            ? 'Báo hỏng do Lễ tân / Dọn dẹp gửi ở mọi khách sạn. Quản lý từng khách sạn là người xử lý.'
            : 'Báo hỏng do Lễ tân / Dọn dẹp gửi. Báo hỏng không tự đổi trạng thái tài sản hay phòng — bạn mở phiếu để quyết định.'}
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-[#DFE3E8] shadow-xs overflow-hidden">
        <div className="px-4 pt-3 border-b border-[#DFE3E8] flex gap-1 overflow-x-auto" role="tablist">
          {TABS.map((tab) => (
            <button
              key={tab.key || 'all'}
              type="button"
              role="tab"
              aria-selected={status === tab.key}
              onClick={() => {
                setStatus(tab.key);
                setPage(1);
              }}
              className={`px-3 py-2 text-xs font-semibold whitespace-nowrap border-b-2 cursor-pointer ${
                status === tab.key
                  ? 'border-[#00375e] text-[#00375e]'
                  : 'border-transparent text-[#5B6472] hover:text-[#0e61a1]'
              }`}
            >
              {tab.label}
              {status === tab.key && !loading && (
                <span className="ml-1.5 px-1.5 py-0.5 rounded-full bg-[#eff4ff] text-[#0e61a1]">{data.total}</span>
              )}
            </button>
          ))}
        </div>

        {loadError && (
          <div className="m-4 p-3 rounded-xl bg-[#FFEBEE] text-[#D32F2F] text-xs border border-[#D32F2F]/30 flex items-center justify-between gap-3">
            <span>{loadError}</span>
            <button type="button" onClick={load} className="font-semibold underline cursor-pointer">
              Thử lại
            </button>
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#eff4ff] text-[#00375e] font-semibold border-b border-[#DFE3E8]">
              <tr>
                <th className="py-3 px-4">Tài Sản</th>
                <th className="py-3 px-4">Vị Trí</th>
                <th className="py-3 px-4">Mô Tả</th>
                <th className="py-3 px-4">Người Báo</th>
                <th className="py-3 px-4">Thời Gian</th>
                <th className="py-3 px-4">Trạng Thái</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#DFE3E8]">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-[#5B6472]">Đang tải dữ liệu…</td>
                </tr>
              ) : data.items.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-[#5B6472]">
                    {status === 'NEW' ? 'Không có báo hỏng nào đang chờ xử lý.' : 'Không có báo hỏng nào.'}
                  </td>
                </tr>
              ) : (
                data.items.map((inc) => (
                  <tr
                    key={inc.id}
                    className="hover:bg-[#F7F8FA] transition-colors cursor-pointer"
                    onClick={() => setOpenId(inc.id)}
                  >
                    <td className="py-3 px-4">
                      <div className="font-semibold text-[#1C2330]">{inc.assetName}</div>
                      <div className="font-mono text-[11px] text-[#0e61a1]">{inc.assetCode}</div>
                    </td>
                    <td className="py-3 px-4 text-[#1C2330] whitespace-nowrap">{inc.room}</td>
                    <td className="py-3 px-4 text-[#1C2330] max-w-[280px]">
                      <span className="line-clamp-2 break-words">{inc.description}</span>
                    </td>
                    <td className="py-3 px-4 text-[#1C2330] whitespace-nowrap">{inc.reportedBy}</td>
                    <td className="py-3 px-4 text-[#5B6472] whitespace-nowrap">{inc.reportedTime}</td>
                    <td className="py-3 px-4">
                      <span className={`px-2.5 py-0.5 rounded-full text-[11px] font-bold whitespace-nowrap ${TICKET_BADGE[inc.ticketStatus]}`}>
                        {inc.ticketStatusLabel}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {!loading && data.total > 0 && (
          <div className="px-4 py-3 border-t border-[#DFE3E8] flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs text-[#5B6472]">
            <span>
              Hiển thị {(page - 1) * PAGE_SIZE + 1}–{Math.min(page * PAGE_SIZE, data.total)} / {data.total} phiếu
            </span>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setPage(page - 1)}
                disabled={page <= 1}
                className="px-2 py-1 rounded-lg border border-[#DFE3E8] hover:bg-[#F7F8FA] disabled:opacity-40 cursor-pointer disabled:cursor-not-allowed flex items-center"
                aria-label="Trang trước"
              >
                <span className="material-symbols-outlined text-[16px]">chevron_left</span>
              </button>
              <span className="px-2 font-semibold text-[#1C2330]">
                {page} / {totalPages}
              </span>
              <button
                type="button"
                onClick={() => setPage(page + 1)}
                disabled={page >= totalPages}
                className="px-2 py-1 rounded-lg border border-[#DFE3E8] hover:bg-[#F7F8FA] disabled:opacity-40 cursor-pointer disabled:cursor-not-allowed flex items-center"
                aria-label="Trang sau"
              >
                <span className="material-symbols-outlined text-[16px]">chevron_right</span>
              </button>
            </div>
          </div>
        )}
      </div>
      {openId && (
        <DamageReportModal
          incidentId={openId}
          onClose={() => {
            setOpenId(null);
            if (initialIncidentId) navigate('/bao-hong', { replace: true });
          }}
          onResolved={load}
        />
      )}
    </div>
  );
};
