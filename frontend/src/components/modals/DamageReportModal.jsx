import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { assetService } from '../../services/assetApi';

/** Khớp @Size(max = 500) của ResolveDamageReportRequest. */
const NOTE_MAX = 500;

const ASSET_STATUS = {
  Good: { label: 'Tốt', className: 'bg-[#E8F5E9] text-[#2E7D32] border-[#2E7D32]/30' },
  Damaged: { label: 'Hỏng', className: 'bg-[#FFEBEE] text-[#D32F2F] border-[#D32F2F]/30' },
  Repairing: { label: 'Đang sửa', className: 'bg-[#FFF8E1] text-[#F57F17] border-[#F9A825]/40' },
  Disposed: { label: 'Đã thanh lý', className: 'bg-[#F5F5F5] text-[#616161] border-[#616161]/30' },
};

const assetStatusOf = (status) =>
  ASSET_STATUS[status] ?? { label: status || 'Không rõ', className: 'bg-[#F5F5F5] text-[#616161] border-[#DFE3E8]' };

/** Lựa chọn trạng thái tài sản khi đóng phiếu. '' = giữ nguyên (BR-ASSET-06: Manager tự quyết). */
const STATUS_CHOICES = [
  { value: '', label: 'Giữ nguyên', hint: 'Báo nhầm, hoặc sẽ đổi sau ở màn tài sản' },
  { value: 'Damaged', label: 'Hỏng', hint: 'Đã xác nhận hỏng, chưa sửa' },
  { value: 'Repairing', label: 'Đang sửa', hint: 'Đã gọi thợ / đang sửa chữa' },
  { value: 'Good', label: 'Tốt', hint: 'Đã sửa xong hoặc không hỏng' },
  { value: 'Disposed', label: 'Thanh lý', hint: 'Không sửa được — không quay lại được' },
];

/** Khung hộp thoại: nền mờ + hộp trắng có tiêu đề và nút đóng. */
function Shell({ title, badge, onClose, busy, children }) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-start sm:items-center justify-center overflow-y-auto p-4 bg-[#001D35]/40 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="damage-report-title"
      onClick={() => !busy && onClose()}
    >
      <div
        className="w-full max-w-5xl my-auto bg-[#F7F8FA] rounded-2xl shadow-xl border border-[#DFE3E8]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3 px-6 py-4 bg-white border-b border-[#DFE3E8] rounded-t-2xl">
          <div className="flex flex-wrap items-center gap-2.5 min-w-0">
            <h2 id="damage-report-title" className="text-lg font-bold text-[#00375e] tracking-tight break-words">
              {title}
            </h2>
            {badge}
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={busy}
            className="p-1.5 rounded text-[#5B6472] hover:text-[#1C2330] hover:bg-[#F7F8FA] transition-colors cursor-pointer disabled:opacity-50"
            aria-label="Đóng"
          >
            <span className="material-symbols-outlined text-[20px] leading-none">close</span>
          </button>
        </div>
        <div className="p-5">{children}</div>
      </div>
    </div>
  );
}

/**
 * Hộp thoại chi tiết / xử lý một báo hỏng — BR-ASSET-06, BR-ASSET-11. Mở từ danh sách báo hỏng,
 * Dashboard, hồ sơ tài sản và bảng chi tiết khu vực.
 *
 * Manager đóng phiếu, tùy chọn kèm trạng thái mới cho tài sản và ghi chú; backend làm cả hai
 * trong CÙNG một transaction. Giám đốc chỉ xem. Báo hỏng không bao giờ tự đổi trạng thái phòng.
 * Bấm một "báo hỏng khác của tài sản này" thì chuyển sang phiếu đó ngay trong hộp thoại.
 *
 * @param onResolved gọi với phiếu vừa đóng để màn bên dưới nạp lại dữ liệu
 */
export const DamageReportModal = ({ incidentId: initialId, onClose, onResolved }) => {
  const { user } = useAuth();
  const isManager = user?.role === 'MANAGER';
  const [incidentId, setIncidentId] = useState(initialId);

  const [incident, setIncident] = useState(null);
  const [related, setRelated] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [newAssetStatus, setNewAssetStatus] = useState('');
  const [note, setNote] = useState('');
  const [confirmDispose, setConfirmDispose] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');
  const [toast, setToast] = useState('');

  /** Các báo hỏng khác của cùng tài sản — để Manager thấy tài sản này hỏng lặp lại hay không. */
  const loadRelated = useCallback(async (assetId) => {
    try {
      const res = await assetService.getDamageReports({ fixedAssetId: assetId, status: '', limit: 10 });
      setRelated(res.items.filter((r) => r.id !== incidentId));
    } catch {
      setRelated([]); // Chỉ là thông tin phụ — không làm hỏng cả màn hình.
    }
  }, [incidentId]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError('');
    // Chuyển sang phiếu khác: form xử lý làm lại từ đầu.
    setNewAssetStatus('');
    setNote('');
    setConfirmDispose(false);
    setFormError('');
    assetService
      .getDamageReportById(incidentId)
      .then((data) => {
        if (cancelled) return;
        setIncident(data);
        loadRelated(data.assetId);
      })
      .catch((err) => {
        if (cancelled) return;
        setIncident(null);
        setLoadError(
          err?.status === 404
            ? 'Không tìm thấy báo hỏng, hoặc báo hỏng không thuộc khách sạn bạn phụ trách.'
            : err?.message || 'Không tải được báo hỏng.'
        );
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [incidentId, loadRelated]);

  // Esc để đóng, trừ lúc đang lưu — tránh đóng giữa chừng rồi không biết kết quả.
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !submitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submitting, onClose]);

  const handleResolve = async (e) => {
    e.preventDefault();
    if (newAssetStatus === 'Disposed' && !confirmDispose) {
      setFormError('Thanh lý là trạng thái cuối. Vui lòng tích xác nhận trước khi đóng phiếu.');
      return;
    }
    setFormError('');
    setSubmitting(true);
    try {
      const updated = await assetService.resolveDamageReport(incident.id, {
        newAssetStatus: newAssetStatus || null,
        resolutionNote: note,
      });
      setIncident(updated);
      loadRelated(updated.assetId);
      onResolved?.(updated);
      setToast('Đã đóng báo hỏng.');
      setTimeout(() => setToast(''), 4000);
    } catch (err) {
      setFormError(err?.message || 'Không đóng được báo hỏng. Vui lòng thử lại.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <Shell title="Báo hỏng" onClose={onClose}>
      <div className="flex items-center justify-center min-h-[300px]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-9 h-9 border-3 border-[#00375e] border-t-transparent rounded-full animate-spin"></div>
          <span className="text-sm font-medium text-[#5B6472]">Đang tải báo hỏng…</span>
        </div>
      </div>
      </Shell>
    );
  }

  if (!incident) {
    return (
      <Shell title="Báo hỏng" onClose={onClose}>
      <div className="flex items-center justify-center min-h-[300px]">
        <div className="text-center space-y-3">
          <span className="material-symbols-outlined text-[48px] text-[#D32F2F]">error</span>
          <p className="text-sm font-medium text-[#1C2330]">{loadError}</p>
          <button
            type="button"
            onClick={onClose}
            className="text-xs text-[#0e61a1] hover:underline font-semibold cursor-pointer"
          >
            Đóng
          </button>
        </div>
      </div>
      </Shell>
    );
  }

  const isProcessed = incident.ticketStatus === 'Processed';
  const assetStatus = assetStatusOf(incident.assetStatus);
  const choices = STATUS_CHOICES.filter((c) => c.value === '' || c.value !== incident.assetStatus);

  return (
    <Shell
      title={`${incident.shortId} · ${incident.assetName} · ${incident.room}`}
      busy={submitting}
      onClose={onClose}
      badge={
        <span
          className={`px-2.5 py-0.5 rounded-full text-xs font-bold border ${
            isProcessed
              ? 'bg-[#E8F5E9] text-[#2E7D32] border-[#2E7D32]/30'
              : 'bg-[#FFF3E0] text-[#EF6C00] border-[#EF6C00]/30'
          }`}
        >
          {incident.ticketStatusLabel}
        </span>
      }
    >
      {toast && (
        <div className="fixed bottom-6 right-6 z-[60] bg-[#00375e] text-white px-4 py-3 rounded-xl shadow-xl flex items-center gap-2 text-xs font-semibold animate-in slide-in-from-bottom-3 duration-200">
          <span className="material-symbols-outlined text-[18px]">check_circle</span>
          <span>{toast}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Cột trái: nội dung báo hỏng + xử lý */}
        <div className="lg:col-span-7 space-y-5">
          <div className="bg-white rounded-2xl border border-[#DFE3E8] p-6 shadow-xs space-y-4">
            <div className="flex items-center gap-2 border-b border-[#DFE3E8] pb-3">
              <span className="material-symbols-outlined text-[#D32F2F] text-[20px]">report</span>
              <h2 className="font-bold text-sm text-[#00375e]">Nội Dung Báo Hỏng</h2>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                <span className="text-[#5B6472] text-[11px] block">Người báo</span>
                <span className="font-semibold text-[#1C2330] mt-0.5 block break-words">
                  {incident.reportedBy}
                  {incident.reporterEmail && incident.reporterEmail !== incident.reportedBy
                    ? ` (${incident.reporterEmail})`
                    : ''}
                </span>
              </div>
              <div className="p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                <span className="text-[#5B6472] text-[11px] block">Thời gian báo</span>
                <span className="font-semibold text-[#1C2330] mt-0.5 block">{incident.reportedTime}</span>
              </div>
            </div>

            <div className="p-4 bg-[#eff4ff] rounded-xl border border-[#d1e4ff] space-y-1 text-xs">
              <span className="font-bold text-[#00375e]">Mô tả tình trạng</span>
              <p className="text-[#1C2330] leading-relaxed whitespace-pre-line break-words">{incident.description}</p>
            </div>
          </div>

          <div className="bg-white rounded-2xl border border-[#DFE3E8] p-6 shadow-xs space-y-5">
            <div className="flex items-center gap-2 border-b border-[#DFE3E8] pb-3">
              <span className="material-symbols-outlined text-[#00375e] text-[20px]">gavel</span>
              <h2 className="font-bold text-sm text-[#00375e]">Xử Lý</h2>
            </div>

            {isProcessed ? (
              <div className="p-4 bg-[#E8F5E9] border border-[#2E7D32]/30 rounded-xl space-y-2 text-xs">
                <div className="flex items-center gap-2 font-bold text-[#2E7D32] text-sm">
                  <span className="material-symbols-outlined text-[20px]">check_circle</span>
                  <span>Đã xử lý</span>
                </div>
                <div className="text-[#1C2330]">
                  Bởi <span className="font-semibold">{incident.resolvedBy || 'Quản lý'}</span> lúc {incident.resolvedTime}.
                </div>
                {incident.resolutionNote ? (
                  <div className="p-2.5 bg-white rounded-lg border border-[#2E7D32]/20 text-[#1C2330] whitespace-pre-line break-words">
                    {incident.resolutionNote}
                  </div>
                ) : (
                  <div className="text-[#5B6472] italic">Không có ghi chú xử lý.</div>
                )}
              </div>
            ) : !isManager ? (
              <div className="p-3.5 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl text-xs text-[#5B6472]">
                Báo hỏng đang chờ quản lý khách sạn xử lý. Giám đốc chỉ xem.
              </div>
            ) : (
              <form onSubmit={handleResolve} className="space-y-4 text-xs">
                <div className="p-3.5 bg-[#FFF3E0] border border-[#EF6C00]/40 rounded-xl text-[#1C2330] flex items-start gap-2.5">
                  <span className="material-symbols-outlined text-[20px] shrink-0 text-[#EF6C00]">info</span>
                  <span>
                    Báo hỏng <span className="font-semibold">không tự đổi</span> trạng thái tài sản hay phòng. Bạn có
                    thể đổi trạng thái tài sản ngay khi đóng phiếu, hoặc giữ nguyên. Cần khóa phòng thì chuyển phòng sang
                    “Không khả dụng” ở màn phòng.
                  </span>
                </div>

                <fieldset>
                  <legend className="block font-semibold text-[#1C2330] mb-2">
                    Trạng thái tài sản sau khi xử lý
                    <span className="ml-1 font-normal text-[#5B6472]">
                      (hiện tại: {assetStatus.label})
                    </span>
                  </legend>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    {choices.map((opt) => (
                      <label
                        key={opt.value || 'keep'}
                        className={`p-3 rounded-xl border flex items-start gap-2 transition-all cursor-pointer ${
                          newAssetStatus === opt.value
                            ? 'border-[#00375e] bg-[#eff4ff] ring-1 ring-[#00375e]'
                            : 'border-[#DFE3E8] hover:bg-[#F7F8FA]'
                        }`}
                      >
                        <input
                          type="radio"
                          name="newAssetStatus"
                          value={opt.value}
                          checked={newAssetStatus === opt.value}
                          onChange={() => {
                            setNewAssetStatus(opt.value);
                            setConfirmDispose(false);
                          }}
                          className="mt-0.5 text-[#00375e] focus:ring-0"
                        />
                        <span>
                          <span className="font-semibold text-[#1C2330] block">{opt.label}</span>
                          <span className="text-[11px] text-[#5B6472]">{opt.hint}</span>
                        </span>
                      </label>
                    ))}
                  </div>
                </fieldset>

                {newAssetStatus === 'Disposed' && (
                  <label className="p-3.5 bg-[#FFEBEE] rounded-xl border border-[#D32F2F]/30 flex items-start gap-2.5 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={confirmDispose}
                      onChange={(e) => setConfirmDispose(e.target.checked)}
                      className="mt-0.5 rounded cursor-pointer"
                    />
                    <span className="text-[#1C2330]">
                      Tôi hiểu thanh lý là <span className="font-semibold">không quay lại được</span>: tài sản ẩn khỏi danh
                      sách vận hành, không nhận báo hỏng mới, và mọi báo hỏng khác đang chờ của tài sản này sẽ tự đóng.
                    </span>
                  </label>
                )}

                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label htmlFor="resolution-note" className="font-semibold text-[#1C2330]">
                      Ghi chú xử lý <span className="font-normal text-[#5B6472]">(không bắt buộc)</span>
                    </label>
                    <span className="text-[11px] text-[#5B6472]">
                      {note.length}/{NOTE_MAX}
                    </span>
                  </div>
                  <textarea
                    id="resolution-note"
                    rows={3}
                    value={note}
                    maxLength={NOTE_MAX}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="Ví dụ: đã gọi thợ, hẹn sửa chiều nay. Người báo hỏng sẽ thấy ghi chú này."
                    className="w-full p-3 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl text-xs text-[#1C2330] focus:outline-none focus:border-[#0e61a1] leading-relaxed"
                  />
                </div>

                {formError && (
                  <div className="p-3 bg-[#FFEBEE] border border-[#D32F2F]/30 text-[#D32F2F] rounded-xl font-medium" role="alert">
                    {formError}
                  </div>
                )}

                <div className="pt-1 flex justify-end">
                  <button
                    type="submit"
                    disabled={submitting}
                    className="w-full sm:w-auto px-6 py-2.5 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] font-bold text-xs shadow-sm flex items-center justify-center gap-2 transition-colors cursor-pointer disabled:opacity-50"
                  >
                    {submitting ? (
                      <>
                        <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                        <span>Đang lưu…</span>
                      </>
                    ) : (
                      <>
                        <span className="material-symbols-outlined text-[18px]">check</span>
                        <span>Đóng báo hỏng</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>

        {/* Cột phải: tài sản + các báo hỏng khác */}
        <div className="lg:col-span-5 space-y-5">
          <div className="bg-white rounded-2xl border border-[#DFE3E8] p-5 shadow-xs space-y-3">
            <div className="flex items-center justify-between border-b border-[#DFE3E8] pb-2.5">
              <h3 className="font-bold text-xs text-[#00375e] flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[16px] text-[#0e61a1]">info</span>
                Tài sản
              </h3>
              <Link
                to={`/tai-san/${incident.assetId}`}
                className="text-xs text-[#0e61a1] hover:underline font-semibold"
              >
                Hồ sơ tài sản
              </Link>
            </div>
            <div className="space-y-2 text-xs">
              <div className="flex items-center justify-between gap-3 py-1 border-b border-[#eff4ff]">
                <span className="text-[#5B6472]">Mã</span>
                <span className="font-mono font-bold text-[#00375e]">{incident.assetCode}</span>
              </div>
              <div className="flex items-center justify-between gap-3 py-1 border-b border-[#eff4ff]">
                <span className="text-[#5B6472]">Tên</span>
                <span className="font-semibold text-[#1C2330] text-right break-words">{incident.assetName}</span>
              </div>
              <div className="flex items-center justify-between gap-3 py-1 border-b border-[#eff4ff]">
                <span className="text-[#5B6472]">Vị trí</span>
                {incident.roomId ? (
                  <Link to={`/phong/${incident.roomId}`} className="font-semibold text-[#0e61a1] hover:underline">
                    {incident.room}
                  </Link>
                ) : (
                  <span className="font-semibold text-[#1C2330]">{incident.room}</span>
                )}
              </div>
              <div className="flex items-center justify-between gap-3 py-1">
                <span className="text-[#5B6472]">Trạng thái hiện tại</span>
                <span className={`px-2.5 py-0.5 rounded-full text-[11px] font-bold border ${assetStatus.className}`}>
                  {assetStatus.label}
                </span>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-2xl border border-[#DFE3E8] p-5 shadow-xs space-y-3">
            <h3 className="font-bold text-xs text-[#00375e] flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px] text-[#0e61a1]">history</span>
              Báo hỏng khác của tài sản này
            </h3>
            {related.length === 0 ? (
              <p className="text-xs text-[#5B6472]">Chưa có báo hỏng nào khác.</p>
            ) : (
              <ul className="space-y-2">
                {related.map((r) => (
                  <li key={r.id}>
                    <button
                      type="button"
                      onClick={() => setIncidentId(r.id)}
                      className="w-full text-left p-2.5 rounded-xl border border-[#DFE3E8] hover:bg-[#F7F8FA] cursor-pointer text-xs space-y-0.5"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-semibold text-[#1C2330]">{r.reportedTime}</span>
                        <span className={`font-bold ${r.ticketStatus === 'New' ? 'text-[#EF6C00]' : 'text-[#2E7D32]'}`}>
                          {r.ticketStatusLabel}
                        </span>
                      </div>
                      <div className="text-[#5B6472] line-clamp-2 break-words">{r.description}</div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </Shell>
  );
};
