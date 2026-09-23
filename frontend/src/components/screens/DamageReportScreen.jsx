import React, { useState, useEffect } from 'react';
import { assetService } from '../../services/assetApi';

export const DamageReportScreen = ({ incidentId = 'RP-2024-089', onNavigate, onSelectAsset }) => {
  const [loading, setLoading] = useState(true);
  const [incident, setIncident] = useState(null);

  // Manager processing form
  const [newAssetStatus, setNewAssetStatus] = useState('Repairing');
  const [processingNote, setProcessingNote] = useState('Đã liên hệ kỹ thuật Daikin Nha Trang kiểm tra máng thoát nước và nạp gas bổ sung.');
  const [confirmCheckbox, setConfirmCheckbox] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [toastMessage, setToastMessage] = useState('');
  const [selectedPhoto, setSelectedPhoto] = useState(null);

  useEffect(() => {
    loadIncident();
  }, [incidentId]);

  const loadIncident = async () => {
    setLoading(true);
    try {
      let data = await assetService.getIncident(incidentId);
      if (!data) {
        const recent = await assetService.getDamageReports({ limit: 1 });
        if (recent.items && recent.items.length > 0) {
          data = recent.items[0];
        }
      }
      setIncident(data || null);
      if (data?.managerNote) {
        setProcessingNote(data.managerNote);
      }
    } catch (e) {
      console.error('Failed to load incident', e);
    } finally {
      setLoading(false);
    }
  };

  const handleResolve = async (e) => {
    e.preventDefault();
    if (!confirmCheckbox) {
      setErrorMsg('Vui lòng tích chọn xác nhận chuyển trạng thái phiếu sang Đã xử lý.');
      return;
    }
    if (processingNote.trim().length < 10) {
      setErrorMsg('Ghi chú xử lý phải có ít nhất 10 ký tự để lưu hồ sơ.');
      return;
    }

    setErrorMsg('');
    setIsSubmitting(true);
    try {
      const updated = await assetService.resolveIncident(incident.id, {
        newAssetStatus,
        processingNote
      });
      setIncident(updated);
      setToastMessage('Đã chuyển phiếu sang "Đã xử lý" và cập nhật trạng thái tài sản thành công!');
      setTimeout(() => setToastMessage(''), 4000);
    } catch (err) {
      setErrorMsg('Xử lý thất bại. Vui lòng thử lại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[500px]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-9 h-9 border-3 border-[#00375e] border-t-transparent rounded-full animate-spin"></div>
          <span className="text-sm font-medium text-[#5B6472]">Đang tải hồ sơ sự cố {incidentId}...</span>
        </div>
      </div>
    );
  }

  if (!incident) {
    return (
      <div className="flex items-center justify-center min-h-[500px]">
        <div className="text-center space-y-3">
          <span className="material-symbols-outlined text-[48px] text-[#D32F2F]">error</span>
          <p className="text-sm font-medium text-[#1C2330]">Không tìm thấy hồ sơ sự cố báo hỏng.</p>
          <button onClick={() => onNavigate('overview')} className="text-xs text-[#0e61a1] hover:underline font-semibold">Quay lại Dashboard</button>
        </div>
      </div>
    );
  }

  const isProcessed = incident.ticketStatus === 'Processed';

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      {/* Toast */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 bg-[#00375e] text-white px-4 py-3 rounded-xl shadow-xl flex items-center gap-2 text-xs font-semibold animate-in slide-in-from-bottom-3 duration-200">
          <span className="material-symbols-outlined text-[18px] text-[#2E7D32] bg-white rounded-full">check_circle</span>
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Top Banner & Breadcrumbs */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-white p-5 rounded-2xl border border-[#DFE3E8] shadow-[0_1px_8px_rgba(0,0,0,0.02)]">
        <div>
          <div className="flex items-center gap-2 text-xs text-[#5B6472] mb-1">
            <button
              onClick={() => onNavigate('fixed-assets')}
              className="text-[#0e61a1] hover:underline cursor-pointer"
              type="button"
            >
              Tổng quan tài sản
            </button>
            <span>/</span>
            <span className="text-[#5B6472]">Sổ sự cố</span>
            <span>/</span>
            <span className="font-mono font-bold text-[#00375e]">{incident.id}</span>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-xl font-bold text-[#00375e] tracking-tight">
              Xử Lý Sự Cố Báo Hỏng: {incident.assetName} ({incident.room})
            </h1>
            <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold ${
              isProcessed ? 'bg-[#E8F5E9] text-[#2E7D32] border border-[#2E7D32]/30' : 'bg-[#FFEBEE] text-[#D32F2F] border border-[#D32F2F]/30'
            }`}>
              {isProcessed ? 'Đã xử lý (Processed)' : 'Mới tiếp nhận (New)'}
            </span>
          </div>
          <p className="text-xs text-[#5B6472] mt-0.5">
            Phiếu sự cố buồng phòng phân quyền Quản lý Khách sạn chi nhánh Sao Mai Nha Trang.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => {
              window.print();
            }}
            className="px-3.5 py-2 rounded-xl border border-[#DFE3E8] bg-white text-[#1C2330] hover:bg-[#F7F8FA] text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">print</span>
            <span>In phiếu</span>
          </button>
        </div>
      </div>

      {/* 2-Column Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column (7 cols): Field Report Info & Manager Decision Box */}
        <div className="lg:col-span-7 space-y-5">
          {/* Card 1: Field Incident Details */}
          <div className="bg-white rounded-2xl border border-[#DFE3E8] p-6 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-[#DFE3E8] pb-3">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[#D32F2F] text-[20px]">report</span>
                <h2 className="font-bold text-sm text-[#00375e]">Thông Tin Sự Cố Từ Hiện Trường</h2>
              </div>
              <span className="font-mono text-xs font-bold text-[#0e61a1] bg-[#eff4ff] px-2 py-0.5 rounded-md">
                {incident.id}
              </span>
            </div>

            {/* Info Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                <span className="text-[#5B6472] text-[11px] block">Mã định danh thiết bị:</span>
                <div className="flex items-center justify-between font-mono font-bold text-sm text-[#00375e] mt-0.5">
                  <span>{incident.assetCode}</span>
                  <button
                    onClick={() => {
                      onNavigate('asset-detail', incident.assetCode);
                    }}
                    className="text-[#0e61a1] text-xs hover:underline cursor-pointer"
                    type="button"
                  >
                    Xem hồ sơ
                  </button>
                </div>
              </div>

              <div className="p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                <span className="text-[#5B6472] text-[11px] block">Vị trí phòng xảy ra sự cố:</span>
                <span className="font-bold text-xs text-[#0e61a1] mt-0.5 block">
                  {incident.room}
                </span>
              </div>

              <div className="p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                <span className="text-[#5B6472] text-[11px] block">Người lập báo cáo:</span>
                <span className="font-semibold text-[#1C2330] mt-0.5 block">
                  {incident.reportedBy} ({incident.reportedRole})
                </span>
              </div>

              <div className="p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                <span className="text-[#5B6472] text-[11px] block">Thời gian phát hiện:</span>
                <span className="font-semibold text-[#1C2330] mt-0.5 block">
                  {incident.reportedTime}
                </span>
              </div>
            </div>

            {/* Actual Description */}
            <div className="p-4 bg-[#eff4ff] rounded-xl border border-[#d1e4ff] space-y-1 text-xs">
              <span className="font-bold text-[#00375e]">Mô tả hiện trạng thực tế từ nhân viên buồng:</span>
              <p className="text-[#1C2330] leading-relaxed">
                "{incident.description}"
              </p>
            </div>
          </div>

          {/* Card 2: Manager Decision & Resolution Area (MANDATORY SPEC COMPLIANCE) */}
          <div className="bg-white rounded-2xl border border-[#DFE3E8] p-6 shadow-xs space-y-5">
            <div className="flex items-center justify-between border-b border-[#DFE3E8] pb-3">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[#00375e] text-[20px]">gavel</span>
                <h2 className="font-bold text-sm text-[#00375e]">
                  Khu Vực Xử Lý Nghiệp Vụ Của Quản Lý
                </h2>
              </div>
              <span className="text-xs text-[#2E7D32] font-semibold bg-[#E8F5E9] px-2 py-0.5 rounded-full">
                Quyền hạn Quản lý khách sạn
              </span>
            </div>

            {/* Spec Rule Banner */}
            <div className="p-3.5 bg-[#FFF3E0] border border-[#EF6C00]/40 rounded-xl text-xs text-[#EF6C00] flex items-start gap-2.5">
              <span className="material-symbols-outlined text-[20px] shrink-0 text-[#EF6C00]">warning</span>
              <div>
                <span className="font-bold">Quy tắc nghiệp vụ hệ thống bắt buộc: </span>
                <span className="text-[#1C2330]">
                  Phiếu báo hỏng <span className="font-semibold text-[#D32F2F]">không tự động đổi</span> trạng thái tài sản khi tạo. Quản lý bắt buộc phải chỉ định trạng thái mới cho thiết bị cố định trước khi chuyển trạng thái phiếu sang <span className="font-semibold text-[#2E7D32]">Đã xử lý (Processed)</span>.
                </span>
              </div>
            </div>

            {isProcessed ? (
              <div className="p-4 bg-[#E8F5E9] border border-[#2E7D32]/30 rounded-xl space-y-2 text-xs">
                <div className="flex items-center gap-2 font-bold text-[#2E7D32] text-sm">
                  <span className="material-symbols-outlined text-[20px]">check_circle</span>
                  <span>Phiếu sự cố này đã được xử lý hoàn tất!</span>
                </div>
                <div className="text-[#1C2330]">
                  Xử lý bởi: <span className="font-semibold">{incident.resolvedBy || 'Lê Hoàng Phúc (Quản lý)'}</span> vào lúc {incident.resolvedAt || 'Hôm nay'}.
                </div>
                <div className="p-2.5 bg-white rounded-lg border border-[#2E7D32]/20 font-medium text-[#1C2330]">
                  Ghi chú xử lý: "{incident.managerNote || processingNote}"
                </div>
              </div>
            ) : (
              <form onSubmit={handleResolve} className="space-y-4 text-xs">
                {/* Current Asset Status display */}
                <div className="flex items-center justify-between p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                  <span className="text-[#5B6472]">Trạng thái thiết bị hiện tại:</span>
                  <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-[#FFEBEE] text-[#D32F2F] border border-[#D32F2F]/20">
                    Bị hỏng (Damaged)
                  </span>
                </div>

                {/* Radio selection for new asset status */}
                <div>
                  <label className="block font-semibold text-[#1C2330] mb-2">
                    Chỉ định trạng thái mới cho thiết bị {incident.assetCode} (*):
                  </label>
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                    {[
                      { value: 'Repairing', label: 'Đang sửa chữa (Repairing)', color: 'text-[#F9A825]' },
                      { value: 'Good', label: 'Tốt (Đã sửa xong)', color: 'text-[#2E7D32]' },
                      { value: 'Disposed', label: 'Thanh lý phế liệu', color: 'text-[#616161]' }
                    ].map((opt) => (
                      <label
                        key={opt.value}
                        className={`p-3 rounded-xl border flex items-center gap-2 transition-all cursor-pointer ${
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
                          onChange={(e) => setNewAssetStatus(e.target.value)}
                          className="text-[#00375e] focus:ring-0"
                        />
                        <span className={`font-semibold ${opt.color}`}>{opt.label}</span>
                      </label>
                    ))}
                  </div>
                </div>

                {/* Manager Note */}
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="font-semibold text-[#1C2330]">
                      Ghi chú phương án xử lý của Quản lý (*):
                    </label>
                    <span className="text-[11px] text-[#5B6472]">Tối thiểu 10 ký tự</span>
                  </div>
                  <textarea
                    rows={3}
                    value={processingNote}
                    onChange={(e) => setProcessingNote(e.target.value)}
                    placeholder="Nhập chi tiết biện pháp khắc phục, đơn vị sửa chữa ngoài, thời gian dự kiến hoàn trả..."
                    className="w-full p-3 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl text-xs text-[#1C2330] focus:outline-none focus:border-[#0e61a1] leading-relaxed"
                  />
                </div>

                {/* Mandatory Confirmation Checkbox */}
                <div className="p-3.5 bg-[#eff4ff] rounded-xl border border-[#d1e4ff]">
                  <label className="flex items-start gap-2.5 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={confirmCheckbox}
                      onChange={(e) => setConfirmCheckbox(e.target.checked)}
                      className="mt-0.5 rounded text-[#00375e] focus:ring-0 cursor-pointer"
                    />
                    <div className="text-xs">
                      <span className="font-bold text-[#00375e]">
                        Xác nhận chuyển trạng thái phiếu sự cố này sang: Đã xử lý (Processed)
                      </span>
                      <p className="text-[11px] text-[#5B6472] mt-0.5">
                        Đồng thời tự động cập nhật trạng thái thiết bị <span className="font-mono">{incident.assetCode}</span> thành "{newAssetStatus}".
                      </p>
                    </div>
                  </label>
                </div>

                {/* Error notice */}
                {errorMsg && (
                  <div className="p-3 bg-[#FFEBEE] border border-[#D32F2F]/30 text-[#D32F2F] rounded-xl text-xs font-medium">
                    {errorMsg}
                  </div>
                )}

                {/* Submit Action */}
                <div className="pt-2 flex items-center justify-end">
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="px-6 py-2.5 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] font-bold text-xs shadow-sm flex items-center gap-2 transition-colors cursor-pointer disabled:opacity-50"
                  >
                    {isSubmitting ? (
                      <>
                        <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                        <span>Đang cập nhật phiếu...</span>
                      </>
                    ) : (
                      <>
                        <span className="material-symbols-outlined text-[18px]">check</span>
                        <span>Đánh dấu Đã xử lý (Mark as Processed)</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>

        {/* Right Column (5 cols): Asset Profile & Room Incident History */}
        <div className="lg:col-span-5 space-y-5">
          {/* Room Out-of-Order Warning Badge */}
          <div className="p-4 bg-[#FFEBEE] rounded-2xl border border-[#D32F2F]/30 flex items-start gap-3 text-xs">
            <span className="material-symbols-outlined text-[#D32F2F] text-[24px] shrink-0">do_not_disturb_on</span>
            <div>
              <div className="font-bold text-sm text-[#D32F2F]">
                Hiện trạng buồng: Phòng 205 (OOO)
              </div>
              <p className="text-[#5B6472] mt-0.5 leading-relaxed">
                Tạm khóa kinh doanh (Out-of-Order) trên phần mềm lễ tân cho đến khi điều hòa được sửa chữa xong và có xác nhận của Buồng phòng.
              </p>
            </div>
          </div>

          {/* Asset Specification Profile Card */}
          <div className="bg-white rounded-2xl border border-[#DFE3E8] p-5 shadow-xs space-y-3">
            <div className="flex items-center justify-between border-b border-[#DFE3E8] pb-2.5">
              <h3 className="font-bold text-xs text-[#00375e] flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[16px] text-[#0e61a1]">info</span>
                Hồ sơ thiết bị liên quan
              </h3>
              <button
                onClick={() => {
                  onNavigate('asset-detail', incident.assetCode);
                }}
                className="text-xs text-[#0e61a1] hover:underline font-semibold cursor-pointer"
                type="button"
              >
                Chi tiết
              </button>
            </div>

            <div className="space-y-2 text-xs">
              <div className="flex items-center justify-between py-1 border-b border-[#eff4ff]">
                <span className="text-[#5B6472]">Tên thiết bị:</span>
                <span className="font-semibold text-[#1C2330]">Điều hòa Daikin Inverter 2.0HP</span>
              </div>
              <div className="flex items-center justify-between py-1 border-b border-[#eff4ff]">
                <span className="text-[#5B6472]">Model:</span>
                <span className="font-mono font-medium text-[#1C2330]">FTKF50XVMV</span>
              </div>
              <div className="flex items-center justify-between py-1 border-b border-[#eff4ff]">
                <span className="text-[#5B6472]">Giá trị sổ sách:</span>
                <span className="font-bold text-[#00375e]">15,850,000 đ</span>
              </div>
              <div className="flex items-center justify-between py-1 border-b border-[#eff4ff]">
                <span className="text-[#5B6472]">Bảo hành chính hãng:</span>
                <span className="font-medium text-[#2E7D32]">Đến 12/2025</span>
              </div>
              <div className="flex items-center justify-between py-1">
                <span className="text-[#5B6472]">Định kỳ bảo dưỡng:</span>
                <span className="font-medium text-[#1C2330]">2 lần / năm (Tháng 5 & 11)</span>
              </div>
            </div>
          </div>

          {/* Incident History Timeline for Room 205 */}
          <div className="bg-white rounded-2xl border border-[#DFE3E8] p-5 shadow-xs space-y-4">
            <h3 className="font-bold text-xs text-[#00375e] flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px] text-[#0e61a1]">history</span>
              Lịch sử bảo trì & sự cố Phòng 205
            </h3>

            <div className="relative pl-6 space-y-4 before:content-[''] before:absolute before:left-2 before:top-2 before:bottom-2 before:w-0.5 before:bg-[#DFE3E8]">
              {/* Event 1 */}
              <div className="relative text-xs">
                <span className="absolute -left-6 top-0.5 w-3 h-3 rounded-full bg-[#D32F2F] ring-4 ring-white"></span>
                <div className="font-semibold text-[#D32F2F]">24/10/2024: Sự cố máng xả Daikin (Hiện tại)</div>
                <div className="text-[11px] text-[#5B6472]">Chảy nước thảm buồng, chờ kỹ thuật ngoài.</div>
              </div>

              {/* Event 2 */}
              <div className="relative text-xs">
                <span className="absolute -left-6 top-0.5 w-3 h-3 rounded-full bg-[#2E7D32] ring-4 ring-white"></span>
                <div className="font-semibold text-[#1C2330]">15/08/2024: Vệ sinh lưới lọc bụi định kỳ</div>
                <div className="text-[11px] text-[#5B6472]">Lê Văn Hùng kiểm tra đạt 100% độ lạnh.</div>
              </div>

              {/* Event 3 */}
              <div className="relative text-xs">
                <span className="absolute -left-6 top-0.5 w-3 h-3 rounded-full bg-[#2E7D32] ring-4 ring-white"></span>
                <div className="font-semibold text-[#1C2330]">10/05/2024: Thay pin điều khiển remote</div>
                <div className="text-[11px] text-[#5B6472]">Cấp 2 pin AAA mới vào phòng khách.</div>
              </div>

              {/* Event 4 */}
              <div className="relative text-xs">
                <span className="absolute -left-6 top-0.5 w-3 h-3 rounded-full bg-[#0e61a1] ring-4 ring-white"></span>
                <div className="font-semibold text-[#1C2330]">12/01/2024: Lắp đặt thiết bị mới bàn giao</div>
                <div className="text-[11px] text-[#5B6472]">Đưa vào hồ sơ tài sản cố định chi nhánh.</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Lightbox for Photo Zoom */}
      {selectedPhoto && (
        <div
          onClick={() => setSelectedPhoto(null)}
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm cursor-zoom-out animate-in fade-in duration-150"
        >
          <div className="max-w-3xl max-h-[85vh] overflow-hidden rounded-2xl">
            <img src={selectedPhoto} alt="Zoomed" className="w-full h-full object-contain" />
          </div>
        </div>
      )}
    </div>
  );
}
