import React, { useState, useEffect, useCallback } from 'react';
import { assetService } from '../../services/assetApi';
import { useAuth } from '../../context/AuthContext';
import { BatchCreateAssetsModal } from '../modals/BatchCreateAssetsModal';
import { DamageReportModal } from '../modals/DamageReportModal';

const pct = (part, total) => (total ? ((part / total) * 100).toFixed(1) : '0.0');

/** Ô báo lỗi thay cho số liệu của một thẻ — không hiện số 0 như thể là dữ liệu thật. */
function SectionError({ message, onRetry }) {
  return (
    <div className="mt-4 p-3 rounded-xl bg-[#FFEBEE] border border-[#D32F2F]/30 text-xs text-[#D32F2F] space-y-2">
      <div className="flex items-start gap-1.5">
        <span className="material-symbols-outlined text-[16px] shrink-0">error</span>
        <span>Không tải được số liệu: {message}</span>
      </div>
      <button type="button" onClick={onRetry} className="font-semibold underline cursor-pointer">
        Thử lại
      </button>
    </div>
  );
}

/**
 * Dashboard tổng quan tài sản. Số liệu luôn đọc mới mỗi lần mở / bấm Làm mới. Phạm vi do
 * backend quyết định: Giám đốc toàn Tenant, Manager và Staff trong khách sạn của mình.
 * Staff không xem được vật tư (API chỉ mở cho Giám đốc/Manager) nên ẩn thẻ đó.
 */
export const DashboardScreen = ({ onNavigate }) => {
  const { user } = useAuth();
  const isManager = user?.role === 'MANAGER';
  const isDirector = user?.role === 'DIRECTOR';
  const showConsumables = isManager || isDirector;

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [stats, setStats] = useState(null);
  const [batchOpen, setBatchOpen] = useState(false);
  const [openIncidentId, setOpenIncidentId] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      setStats(await assetService.getOverviewStats({ includeConsumables: showConsumables }));
    } catch (e) {
      console.error('Failed to load overview data', e);
      setLoadError(e.message || 'Không thể tải dữ liệu tổng quan từ hệ thống.');
    } finally {
      setLoading(false);
    }
  }, [showConsumables]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (loading && !stats) {
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
        <div className="flex flex-col items-center gap-3 text-center">
          <span className="material-symbols-outlined text-[40px] text-[#D32F2F]">error</span>
          <span className="text-sm font-medium text-[#D32F2F]">
            Không thể tải dữ liệu tổng quan: {loadError}
          </span>
          <button
            type="button"
            onClick={loadData}
            className="px-4 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold cursor-pointer"
          >
            Thử lại
          </button>
        </div>
      </div>
    );
  }

  const { fixedAssets, consumables, incidents, errors, loadedAt } = stats;
  const scopeLabel = isDirector ? 'Toàn chuỗi khách sạn' : 'Khách sạn của bạn';

  const summary = [
    fixedAssets && `${fixedAssets.total} thiết bị cụ thể`,
    consumables && `${consumables.categoriesCount} mặt hàng vật tư tiêu hao`,
    incidents && `${incidents.pending} phiếu báo hỏng đang chờ xử lý`,
  ].filter(Boolean);

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      {/* Top Banner & Quick Navigation CTAs */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-white p-5 rounded-2xl border border-[#DFE3E8] shadow-[0_1px_8px_rgba(0,0,0,0.02)]">
        <div>
          <div className="flex items-center gap-2 text-xs text-[#5B6472] mb-1">
            <span className="font-semibold text-[#00375e]">{scopeLabel}</span>
            <span>/</span>
            <span>Báo cáo vận hành cơ sở vật chất</span>
          </div>
          <h1 className="text-xl font-bold text-[#00375e] tracking-tight">
            Tổng Quan Tài Sản & Cơ Sở Vật Chất
          </h1>
          {summary.length > 0 && (
            <p className="text-xs text-[#5B6472] mt-0.5">Đang giám sát {summary.join(', ')}.</p>
          )}
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

          {showConsumables && (
            <button
              onClick={() => onNavigate('consumables')}
              className="px-3.5 py-2 rounded-xl bg-[#eff4ff] text-[#00375e] hover:bg-[#d1e4ff] text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer"
              type="button"
            >
              <span className="material-symbols-outlined text-[16px]">category</span>
              <span>Vật tư tiêu hao</span>
            </button>
          )}

          {isManager && (
            <>
              <div className="w-px h-6 bg-[#DFE3E8] mx-1"></div>
              <button
                onClick={() => setBatchOpen(true)}
                className="px-3.5 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold flex items-center gap-1.5 shadow-sm transition-colors cursor-pointer"
                type="button"
              >
                <span className="material-symbols-outlined text-[16px]">playlist_add</span>
                <span>Thêm tài sản hàng loạt</span>
              </button>
            </>
          )}
        </div>
      </div>

      {/* Bento Metric Cards */}
      <div className={`grid grid-cols-1 gap-5 ${showConsumables ? 'xl:grid-cols-3' : 'xl:grid-cols-2'}`}>
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

            {!fixedAssets ? (
              <SectionError message={errors.fixedAssets} onRetry={loadData} />
            ) : (
              <>
                <div className="mt-4 flex items-baseline gap-2">
                  <span className="text-3xl font-extrabold text-[#00375e] tracking-tight">
                    {fixedAssets.total}
                  </span>
                  <span className="text-xs font-medium text-[#5B6472]">thiết bị cụ thể (kể cả đã thanh lý)</span>
                </div>

                {/* Ratio Multi-bar */}
                <div className="mt-3 w-full h-3 bg-[#eff4ff] rounded-full overflow-hidden flex">
                  <div className="bg-[#2E7D32] h-full" style={{ width: `${pct(fixedAssets.good, fixedAssets.total)}%` }} title={`Tốt: ${pct(fixedAssets.good, fixedAssets.total)}%`}></div>
                  <div className="bg-[#F9A825] h-full" style={{ width: `${pct(fixedAssets.repairing, fixedAssets.total)}%` }} title={`Đang sửa: ${pct(fixedAssets.repairing, fixedAssets.total)}%`}></div>
                  <div className="bg-[#D32F2F] h-full" style={{ width: `${pct(fixedAssets.damaged, fixedAssets.total)}%` }} title={`Bị hỏng: ${pct(fixedAssets.damaged, fixedAssets.total)}%`}></div>
                  <div className="bg-[#616161] h-full" style={{ width: `${pct(fixedAssets.disposed, fixedAssets.total)}%` }} title={`Thanh lý: ${pct(fixedAssets.disposed, fixedAssets.total)}%`}></div>
                </div>

                {/* 4 Status Pills Grid */}
                <div className="grid grid-cols-2 gap-2.5 mt-4">
                  <div className="p-2.5 rounded-xl bg-[#E8F5E9] border border-[#2E7D32]/20">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-medium text-[#2E7D32]">Tốt (Good)</span>
                      <span className="font-bold text-sm text-[#2E7D32]">{fixedAssets.good}</span>
                    </div>
                    <div className="text-[10px] text-[#2E7D32]/80 mt-0.5">{pct(fixedAssets.good, fixedAssets.total)}% sẵn sàng phục vụ</div>
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
                    <div className="text-[10px] text-[#F9A825]/80 mt-0.5">Đang được sửa chữa</div>
                  </div>

                  <div className="p-2.5 rounded-xl bg-[#EEEEEE] border border-[#616161]/20">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-medium text-[#616161]">Thanh lý</span>
                      <span className="font-bold text-sm text-[#616161]">{fixedAssets.disposed}</span>
                    </div>
                    <div className="text-[10px] text-[#616161]/80 mt-0.5">Không còn sử dụng</div>
                  </div>
                </div>
              </>
            )}
          </div>
        </div>

        {/* Card 2: Consumables Bento — chỉ Giám đốc/Manager */}
        {showConsumables && (
          <div className="bg-white rounded-2xl border border-[#DFE3E8] p-5 shadow-[0_1px_8px_rgba(0,0,0,0.02)] flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-[#eff4ff] text-[#0e61a1] flex items-center justify-center">
                    <span className="material-symbols-outlined text-[18px]">inventory</span>
                  </div>
                  <div>
                    <h3 className="font-bold text-sm text-[#00375e]">Vật tư tiêu hao</h3>
                    <span className="text-[11px] text-[#5B6472]">Tồn kho & kiểm kê</span>
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

              {!consumables ? (
                <SectionError message={errors.consumables} onRetry={loadData} />
              ) : (
                <>
                  {/* Không cộng dồn số lượng: mỗi mặt hàng một đơn vị tính (Chai, Cái…). */}
                  <div className="mt-4 flex items-baseline gap-2">
                    <span className="text-3xl font-extrabold text-[#00375e] tracking-tight">
                      {consumables.categoriesCount}
                    </span>
                    <span className="text-xs font-medium text-[#5B6472]">mặt hàng đang quản lý</span>
                  </div>

                  <div className="mt-4 space-y-3">
                    <div className="flex items-center justify-between text-xs py-1 border-b border-[#eff4ff]">
                      <span className="text-[#5B6472]">Dùng cho khách / Duy trì cơ sở:</span>
                      <span className="font-bold text-[#00375e]">
                        {consumables.guestCount} / {consumables.facilityCount} mặt hàng
                      </span>
                    </div>

                    <div className="flex items-center justify-between text-xs py-1 border-b border-[#eff4ff]">
                      <span className="text-[#5B6472]">Lần kiểm gần nhất:</span>
                      <span className="font-medium text-[#1C2330]">{consumables.lastAudit}</span>
                    </div>
                  </div>
                </>
              )}
            </div>

            {consumables && (
              <div className="mt-4 pt-3 border-t border-[#DFE3E8]/60 flex items-center justify-between text-xs">
                <span className="text-[#5B6472]">Tình trạng tồn kho</span>
                {consumables.outOfStockCount > 0 ? (
                  <span className="text-[#EF6C00] font-semibold bg-[#FFF3E0] px-2 py-0.5 rounded-full text-[11px]">
                    {consumables.outOfStockCount} mặt hàng đã hết
                  </span>
                ) : (
                  <span className="text-[#2E7D32] font-semibold bg-[#E8F5E9] px-2 py-0.5 rounded-full text-[11px]">
                    Không có mặt hàng hết
                  </span>
                )}
              </div>
            )}
          </div>
        )}

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
                  <span className="text-[11px] text-[#5B6472]">Phiếu báo hỏng thiết bị</span>
                </div>
              </div>
              <button
                onClick={() => onNavigate('issue-reports')}
                className="text-xs text-[#D32F2F] hover:underline font-semibold flex items-center gap-0.5 cursor-pointer"
                type="button"
              >
                <span>{isManager ? 'Xử lý ngay' : 'Xem sổ sự cố'}</span>
                <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
              </button>
            </div>

            {!incidents ? (
              <SectionError message={errors.incidents} onRetry={loadData} />
            ) : (
              <>
                <div className="mt-4 flex items-baseline gap-2">
                  <span className="text-3xl font-extrabold text-[#D32F2F] tracking-tight">
                    {incidents.pending}
                  </span>
                  <span className="text-xs font-semibold text-[#D32F2F] bg-[#FFEBEE] px-2 py-0.5 rounded-md">
                    phiếu đang chờ Quản lý xử lý
                  </span>
                </div>

                <div className="mt-4 space-y-2.5">
                  <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#F7F8FA] border border-[#DFE3E8]">
                    <div className="flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-[#D32F2F]"></span>
                      <span className="text-xs text-[#5B6472]">Tổng phiếu báo hỏng:</span>
                    </div>
                    <span className="font-bold text-xs text-[#1C2330]">{incidents.total} phiếu</span>
                  </div>

                  <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#E8F5E9] border border-[#2E7D32]/20">
                    <div className="flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-[#2E7D32]"></span>
                      <span className="text-xs text-[#2E7D32] font-medium">Đã hoàn tất xử lý:</span>
                    </div>
                    <span className="font-bold text-xs text-[#2E7D32]">{incidents.resolved} phiếu</span>
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Main Table: Top 5 Urgent Incidents */}
      <div className="bg-white rounded-2xl border border-[#DFE3E8] shadow-[0_1px_8px_rgba(0,0,0,0.02)] overflow-hidden">
        <div className="p-5 border-b border-[#DFE3E8] flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <h2 className="font-bold text-base text-[#00375e] flex items-center gap-2">
              <span className="material-symbols-outlined text-[20px] text-[#D32F2F]">notification_important</span>
              5 phiếu báo hỏng mới nhất đang chờ xử lý
            </h2>
            <p className="text-xs text-[#5B6472]">
              Sắp xếp theo thời gian báo, phiếu mới nhất ở trên cùng.
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
              {!incidents ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-[#D32F2F]">
                    Không tải được danh sách phiếu báo hỏng.
                  </td>
                </tr>
              ) : incidents.top5.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-[#5B6472]">
                    Không có phiếu báo hỏng nào đang chờ xử lý.
                  </td>
                </tr>
              ) : (
                incidents.top5.map((inc) => (
                  <tr key={inc.id} className="hover:bg-[#f8f9ff] transition-colors">
                    <td className="py-3.5 px-4 font-mono font-bold text-[#00375e]" title={inc.id}>
                      #{inc.shortId}
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
                    </td>
                    <td className="py-3.5 px-4">
                      <div className="font-medium text-[#1C2330]">{inc.reportedBy}</div>
                      {inc.reporterEmail && inc.reporterEmail !== inc.reportedBy && (
                        <div className="text-[11px] text-[#5B6472]">{inc.reporterEmail}</div>
                      )}
                      <div className="text-[11px] text-[#5B6472]">{inc.reportedTime}</div>
                    </td>
                    <td className="py-3.5 px-4">
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold bg-[#FFEBEE] text-[#D32F2F]">
                        {inc.ticketStatusLabel}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <button
                        onClick={() => setOpenIncidentId(inc.id)}
                        className="px-3 py-1.5 rounded-lg bg-[#00375e] text-white hover:bg-[#1f4e78] font-semibold text-xs shadow-xs inline-flex items-center gap-1 cursor-pointer transition-colors"
                        type="button"
                      >
                        <span>{isManager ? 'Xử lý ngay' : 'Xem phiếu'}</span>
                        <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Footer: thời điểm tải số liệu thật + làm mới */}
      <div className="p-4 bg-[#eff4ff] rounded-xl border border-[#d1e4ff] text-xs text-[#5B6472] flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-[#0e61a1] text-[18px]">schedule</span>
          <span>
            Số liệu cập nhật lúc{' '}
            <span className="font-mono text-[#00375e]">{loadedAt.toLocaleString('vi-VN')}</span>
          </span>
        </div>
        <button
          type="button"
          onClick={loadData}
          disabled={loading}
          className="px-3 py-1.5 rounded-lg border border-[#d1e4ff] bg-white text-[#00375e] font-semibold flex items-center gap-1 hover:bg-[#F7F8FA] cursor-pointer disabled:opacity-50 disabled:cursor-wait self-start sm:self-auto"
        >
          <span className={`material-symbols-outlined text-[16px] ${loading ? 'animate-spin' : ''}`}>refresh</span>
          <span>{loading ? 'Đang tải...' : 'Làm mới'}</span>
        </button>
      </div>
      {openIncidentId && (
        <DamageReportModal
          incidentId={openIncidentId}
          onClose={() => setOpenIncidentId(null)}
          onResolved={loadData}
        />
      )}
      {batchOpen && (
        <BatchCreateAssetsModal onClose={() => setBatchOpen(false)} onCreated={loadData} />
      )}
    </div>
  );
};
