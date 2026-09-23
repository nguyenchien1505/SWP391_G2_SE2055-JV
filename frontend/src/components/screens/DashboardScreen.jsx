import React, { useState, useEffect } from 'react';
import { assetService } from '../../services/assetApi';

export const DashboardScreen = ({ onNavigate, onSelectIncident, onSelectAsset }) => {
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState(null);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const data = await assetService.getOverviewStats();
      setStats(data);
    } catch (e) {
      console.error('Failed to load overview data', e);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[500px]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-9 h-9 border-3 border-[#00375e] border-t-transparent rounded-full animate-spin"></div>
          <span className="text-sm font-medium text-[#5B6472]">Đang tải dữ liệu tổng quan...</span>
        </div>
      </div>
    );
  }

  if (!stats) {
    return (
      <div className="flex items-center justify-center min-h-[500px]">
        <span className="text-sm font-medium text-[#D32F2F]">Không thể tải dữ liệu tổng quan từ hệ thống.</span>
      </div>
    );
  }

  const { fixedAssets, consumables, incidents } = stats;

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      {/* Top Banner & Quick Navigation CTAs */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-white p-5 rounded-2xl border border-[#DFE3E8] shadow-[0_1px_8px_rgba(0,0,0,0.02)]">
        <div>
          <div className="flex items-center gap-2 text-xs text-[#5B6472] mb-1">
            <span className="font-semibold text-[#00375e]">Sao Mai Nha Trang</span>
            <span>/</span>
            <span>Báo cáo vận hành cơ sở vật chất</span>
            <span className="text-[#2E7D32] bg-[#E8F5E9] px-2 py-0.5 rounded-full text-[11px] font-semibold ml-2">
              Thời gian thực
            </span>
          </div>
          <h1 className="text-xl font-bold text-[#00375e] tracking-tight">
            Tổng Quan Tài Sản & Cơ Sở Vật Chất Chi Nhánh
          </h1>
          <p className="text-xs text-[#5B6472] mt-0.5">
            Giám sát {fixedAssets.total} thiết bị cụ thể, {consumables.categoriesCount} nhóm vật tư tiêu hao và {incidents.damageTotal} sự cố kỹ thuật buồng phòng.
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => onNavigate('fixed-assets')}
            className="px-3.5 py-2 rounded-xl bg-[#eff4ff] text-[#00375e] hover:bg-[#d1e4ff] text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[16px]">inventory_2</span>
            <span>Quản lý TSCĐ</span>
          </button>

          <button
            onClick={() => onNavigate('consumables')}
            className="px-3.5 py-2 rounded-xl bg-[#eff4ff] text-[#00375e] hover:bg-[#d1e4ff] text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[16px]">category</span>
            <span>Vật tư tiêu hao</span>
          </button>

          <div className="w-px h-6 bg-[#DFE3E8] mx-1"></div>

          <button
            onClick={() => onNavigate('batch-create')}
            className="px-3.5 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold flex items-center gap-1.5 shadow-sm transition-colors cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[16px]">playlist_add</span>
            <span>Thêm tài sản hàng loạt</span>
          </button>
        </div>
      </div>

      {/* 3 Bento Metric Cards */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-5">
        {/* Card 1: Fixed Assets Bento */}
        <div className="bg-white rounded-2xl border border-[#DFE3E8] p-5 shadow-[0_1px_8px_rgba(0,0,0,0.02)] flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-[#eff4ff] text-[#0e61a1] flex items-center justify-center">
                  <span className="material-symbols-outlined text-[18px]">devices</span>
                </div>
                <div>
                  <h3 className="font-bold text-sm text-[#00375e]">Tài sản cố định</h3>
                  <span className="text-[11px] text-[#5B6472]">Tổng số thiết bị quản lý</span>
                </div>
              </div>
              <button
                onClick={() => onNavigate('fixed-assets')}
                className="text-xs text-[#0e61a1] hover:underline font-semibold flex items-center gap-0.5 cursor-pointer"
                type="button"
              >
                <span>Xem chi tiết</span>
                <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
              </button>
            </div>

            <div className="mt-4 flex items-baseline gap-2">
              <span className="text-3xl font-extrabold text-[#00375e] tracking-tight">
                {fixedAssets.total}
              </span>
              <span className="text-xs font-medium text-[#5B6472]">thiết bị cụ thể</span>
            </div>

            {/* Ratio Multi-bar */}
            <div className="mt-3 w-full h-3 bg-[#eff4ff] rounded-full overflow-hidden flex">
              <div className="bg-[#2E7D32] h-full" style={{ width: fixedAssets.total ? `${((fixedAssets.good / fixedAssets.total) * 100).toFixed(1)}%` : '0%' }} title={`Tốt: ${fixedAssets.total ? ((fixedAssets.good / fixedAssets.total) * 100).toFixed(1) : 0}%`}></div>
              <div className="bg-[#F9A825] h-full" style={{ width: fixedAssets.total ? `${((fixedAssets.repairing / fixedAssets.total) * 100).toFixed(1)}%` : '0%' }} title={`Đang sửa: ${fixedAssets.total ? ((fixedAssets.repairing / fixedAssets.total) * 100).toFixed(1) : 0}%`}></div>
              <div className="bg-[#D32F2F] h-full" style={{ width: fixedAssets.total ? `${((fixedAssets.damaged / fixedAssets.total) * 100).toFixed(1)}%` : '0%' }} title={`Bị hỏng: ${fixedAssets.total ? ((fixedAssets.damaged / fixedAssets.total) * 100).toFixed(1) : 0}%`}></div>
              <div className="bg-[#616161] h-full" style={{ width: fixedAssets.total ? `${((fixedAssets.disposed / fixedAssets.total) * 100).toFixed(1)}%` : '0%' }} title={`Thanh lý: ${fixedAssets.total ? ((fixedAssets.disposed / fixedAssets.total) * 100).toFixed(1) : 0}%`}></div>
            </div>

            {/* 4 Status Pills Grid */}
            <div className="grid grid-cols-2 gap-2.5 mt-4">
              <div className="p-2.5 rounded-xl bg-[#E8F5E9] border border-[#2E7D32]/20">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-medium text-[#2E7D32]">Tốt (Good)</span>
                  <span className="font-bold text-sm text-[#2E7D32]">{fixedAssets.good}</span>
                </div>
                <div className="text-[10px] text-[#2E7D32]/80 mt-0.5">{fixedAssets.total ? ((fixedAssets.good / fixedAssets.total) * 100).toFixed(1) : 0}% sẵn sàng phục vụ</div>
              </div>

              <div className="p-2.5 rounded-xl bg-[#FFEBEE] border border-[#D32F2F]/20">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-medium text-[#D32F2F]">Bị hỏng</span>
                  <span className="font-bold text-sm text-[#D32F2F]">{fixedAssets.damaged}</span>
                </div>
                <div className="text-[10px] text-[#D32F2F]/80 mt-0.5">Cần thay thế/sửa gấp</div>
              </div>

              <div className="p-2.5 rounded-xl bg-[#FFFDE7] border border-[#F9A825]/30">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-medium text-[#F9A825]">Đang sửa</span>
                  <span className="font-bold text-sm text-[#F9A825]">{fixedAssets.repairing}</span>
                </div>
                <div className="text-[10px] text-[#F9A825]/80 mt-0.5">Đơn vị ngoài xử lý</div>
              </div>

              <div className="p-2.5 rounded-xl bg-[#EEEEEE] border border-[#616161]/20">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-medium text-[#616161]">Thanh lý</span>
                  <span className="font-bold text-sm text-[#616161]">{fixedAssets.disposed}</span>
                </div>
                <div className="text-[10px] text-[#616161]/80 mt-0.5">Hết hạn khấu hao</div>
              </div>
            </div>
          </div>
        </div>

        {/* Card 2: Consumables Bento */}
        <div className="bg-white rounded-2xl border border-[#DFE3E8] p-5 shadow-[0_1px_8px_rgba(0,0,0,0.02)] flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-[#eff4ff] text-[#0e61a1] flex items-center justify-center">
                  <span className="material-symbols-outlined text-[18px]">inventory</span>
                </div>
                <div>
                  <h3 className="font-bold text-sm text-[#00375e]">Vật tư tiêu hao</h3>
                  <span className="text-[11px] text-[#5B6472]">Tồn kho & Định mức buồng</span>
                </div>
              </div>
              <button
                onClick={() => onNavigate('consumables')}
                className="text-xs text-[#0e61a1] hover:underline font-semibold flex items-center gap-0.5 cursor-pointer"
                type="button"
              >
                <span>Kiểm kê</span>
                <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
              </button>
            </div>

            <div className="mt-4 flex items-baseline gap-2">
              <span className="text-3xl font-extrabold text-[#00375e] tracking-tight">
                {consumables.totalStock.toLocaleString('vi-VN')}
              </span>
              <span className="text-xs font-medium text-[#5B6472]">đơn vị lưu kho</span>
            </div>

            <div className="mt-4 space-y-3">
              <div className="flex items-center justify-between text-xs py-1 border-b border-[#eff4ff]">
                <span className="text-[#5B6472]">Tổng số chủng loại quản lý:</span>
                <span className="font-bold text-[#00375e]">{consumables.categoriesCount} nhóm mặt hàng</span>
              </div>

              <div className="flex items-center justify-between text-xs py-1 border-b border-[#eff4ff]">
                <span className="text-[#5B6472]">Lần kiểm gần nhất:</span>
                <span className="font-medium text-[#1C2330]">{consumables.lastAudit}</span>
              </div>
            </div>
          </div>

          <div className="mt-4 pt-3 border-t border-[#DFE3E8]/60 flex items-center justify-between text-xs">
            <span className="text-[#5B6472]">Tồn kho ổn định trong tuần</span>
            <span className="text-[#2E7D32] font-semibold bg-[#E8F5E9] px-2 py-0.5 rounded-full text-[11px]">
              Đạt chuẩn vận hành
            </span>
          </div>
        </div>

        {/* Card 3: Incident Reports Bento */}
        <div className="bg-white rounded-2xl border border-[#DFE3E8] p-5 shadow-[0_1px_8px_rgba(0,0,0,0.02)] flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-[#FFEBEE] text-[#D32F2F] flex items-center justify-center">
                  <span className="material-symbols-outlined text-[18px]">warning</span>
                </div>
                <div>
                  <h3 className="font-bold text-sm text-[#00375e]">Sự cố tài sản</h3>
                  <span className="text-[11px] text-[#5B6472]">Báo hỏng & Báo mất thiết bị</span>
                </div>
              </div>
              <button
                onClick={() => onNavigate('issue-reports')}
                className="text-xs text-[#D32F2F] hover:underline font-semibold flex items-center gap-0.5 cursor-pointer"
                type="button"
              >
                <span>Xử lý ngay</span>
                <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
              </button>
            </div>

            <div className="mt-4 flex items-baseline gap-2">
              <span className="text-3xl font-extrabold text-[#D32F2F] tracking-tight">
                {incidents.pending < 10 ? `0${incidents.pending}` : incidents.pending}
              </span>
              <span className="text-xs font-semibold text-[#D32F2F] bg-[#FFEBEE] px-2 py-0.5 rounded-md">
                Chờ Quản lý chỉ định trạng thái
              </span>
            </div>

            <div className="mt-4 space-y-2.5">
              <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#F7F8FA] border border-[#DFE3E8]">
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-[#D32F2F]"></span>
                  <span className="text-xs text-[#5B6472]">Sự cố hỏng hóc (Damage):</span>
                </div>
                <span className="font-bold text-xs text-[#1C2330]">{incidents.damageTotal} vụ</span>
              </div>

              <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#F7F8FA] border border-[#DFE3E8]">
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-[#EF6C00]"></span>
                  <span className="text-xs text-[#5B6472]">Mất mát thiết bị (Lost):</span>
                </div>
                <span className="font-bold text-xs text-[#1C2330]">{incidents.lostTotal} vụ</span>
              </div>

              <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#E8F5E9] border border-[#2E7D32]/20">
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-[#2E7D32]"></span>
                  <span className="text-xs text-[#2E7D32] font-medium">Đã hoàn tất xử lý:</span>
                </div>
                <span className="font-bold text-xs text-[#2E7D32]">{incidents.resolved} phiếu</span>
              </div>
            </div>
          </div>

          <div className="mt-3 text-[11px] text-[#5B6472] bg-[#eff4ff] p-2 rounded-xl border border-[#d1e4ff]/60">
            *Quy tắc nghiệp vụ: Quản lý phải đổi trạng thái thiết bị trước khi đánh dấu Đã xử lý phiếu.
          </div>
        </div>
      </div>

      {/* Main Table: Top 5 Urgent Incidents */}
      <div className="bg-white rounded-2xl border border-[#DFE3E8] shadow-[0_1px_8px_rgba(0,0,0,0.02)] overflow-hidden">
        <div className="p-5 border-b border-[#DFE3E8] flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <h2 className="font-bold text-base text-[#00375e] flex items-center gap-2">
              <span className="material-symbols-outlined text-[20px] text-[#D32F2F]">notification_important</span>
              Top 5 sự cố mới phát sinh cần xử lý gấp
            </h2>
            <p className="text-xs text-[#5B6472]">
              Ưu tiên các sự cố ảnh hưởng trực tiếp đến buồng phòng đón khách trong ngày.
            </p>
          </div>
          <button
            onClick={() => onNavigate('issue-reports')}
            className="text-xs font-semibold text-[#0e61a1] hover:underline flex items-center gap-1 cursor-pointer"
            type="button"
          >
            <span>Xem toàn bộ sổ sự cố</span>
            <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#eff4ff] text-[#00375e] font-semibold border-b border-[#DFE3E8]">
              <tr>
                <th className="py-3 px-4">Mã Phiếu</th>
                <th className="py-3 px-4">Mã & Tên Tài Sản</th>
                <th className="py-3 px-4">Vị Trí Hiện Diện</th>
                <th className="py-3 px-4">Người Báo & Thời Gian</th>
                <th className="py-3 px-4">Trạng Thái Phiếu</th>
                <th className="py-3 px-4 text-right">Thao Tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#eff4ff]">
              {incidents.top5.map((inc) => (
                <tr key={inc.id} className="hover:bg-[#f8f9ff] transition-colors">
                  <td className="py-3.5 px-4 font-mono font-bold text-[#00375e]">
                    {inc.id}
                  </td>
                  <td className="py-3.5 px-4">
                    <div className="font-semibold text-[#1C2330]">{inc.assetName}</div>
                    <div className="font-mono text-[11px] text-[#5B6472]">{inc.assetCode}</div>
                  </td>
                  <td className="py-3.5 px-4">
                    <span className="inline-flex items-center gap-1 font-semibold text-[#0e61a1] bg-[#eff4ff] px-2 py-0.5 rounded-md">
                      <span className="material-symbols-outlined text-[13px]">room</span>
                      {inc.room}
                    </span>
                    <div className="text-[11px] text-[#5B6472] mt-0.5">{inc.roomSub}</div>
                  </td>
                  <td className="py-3.5 px-4">
                    <div className="font-medium text-[#1C2330]">{inc.reportedBy}</div>
                    <div className="text-[11px] text-[#5B6472]">{inc.reportedTime}</div>
                  </td>
                  <td className="py-3.5 px-4">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                      inc.ticketStatus === 'New'
                        ? 'bg-[#FFEBEE] text-[#D32F2F]'
                        : 'bg-[#E8F5E9] text-[#2E7D32]'
                    }`}>
                      {inc.ticketStatusLabel}
                    </span>
                  </td>
                  <td className="py-3.5 px-4 text-right">
                    <button
                      onClick={() => {
                        onNavigate('incident-detail', inc.id);
                      }}
                      className="px-3 py-1.5 rounded-lg bg-[#00375e] text-white hover:bg-[#1f4e78] font-semibold text-xs shadow-xs inline-flex items-center gap-1 cursor-pointer transition-colors"
                      type="button"
                    >
                      <span>Xử lý ngay</span>
                      <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Footer System Telemetry */}
      <div className="p-4 bg-[#eff4ff] rounded-xl border border-[#d1e4ff] text-xs text-[#5B6472] flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-[#0e61a1] text-[18px]">verified</span>
          <span>
            Dữ liệu báo cáo tài sản tự động liên kết tài khoản Giám đốc chi nhánh Sao Mai Nha Trang.
          </span>
        </div>
        <span className="font-mono text-[11px] text-[#00375e]">Cơ sở dữ liệu cập nhật: 24/10/2024 · 14:32</span>
      </div>
    </div>
  );
}
