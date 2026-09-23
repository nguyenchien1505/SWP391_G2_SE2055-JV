import React, { useState, useEffect } from 'react';
import { assetService } from '../../services/assetApi';

export const AssetDetailScreen = ({ assetCode = 'TS-310-AC-01', onNavigate, onReportIssue }) => {
  const [loading, setLoading] = useState(true);
  const [asset, setAsset] = useState(null);
  const [copied, setCopied] = useState(false);

  // Modals
  const [showLocationModal, setShowLocationModal] = useState(false);
  const [showStatusModal, setShowStatusModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  const [newLocation, setNewLocation] = useState('');
  const [newStatus, setNewStatus] = useState('Good');
  const [editData, setEditData] = useState({ name: '', categoryId: '', note: '' });
  const [categories, setCategories] = useState([]);
  const [toastMessage, setToastMessage] = useState('');

  useEffect(() => {
    loadAssetDetail();
    loadCategories();
  }, [assetCode]);

  const loadCategories = async () => {
    try {
      const cats = await assetService.getAssetCategories();
      setCategories(cats.filter(c => c.assetKind === 'FIXED'));
    } catch(e) {}
  };

  const loadAssetDetail = async () => {
    setLoading(true);
    try {
      const data = await assetService.getAssetDetail(assetCode);
      setAsset(data);
      if (data) {
        setNewLocation(data.location);
        setNewStatus(data.status);
      }
    } catch (e) {
      console.error('Failed to load asset', e);
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = () => {
    if (asset?.code) {
      navigator.clipboard.writeText(asset.code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleSaveLocation = async () => {
    if (!asset) return;
    const updated = await assetService.updateAssetLocation(asset.code, newLocation);
    setAsset(updated);
    setShowLocationModal(false);
    setToastMessage(`Đã chuyển vị trí thiết bị ${asset.code} sang "${newLocation}"!`);
    setTimeout(() => setToastMessage(''), 3500);
  };

  const handleSaveStatus = async () => {
    if (!asset) return;
    const updated = await assetService.updateAssetStatus(asset.code, newStatus);
    setAsset(updated);
    setShowStatusModal(false);
    setToastMessage(`Đã cập nhật trạng thái thiết bị ${asset.code} thành "${newStatus}"!`);
    setTimeout(() => setToastMessage(''), 3500);
  };

  const handleSaveEdit = async () => {
    if (!asset) return;
    try {
      const updated = await assetService.updateFixedAssetInfo(asset.code, editData);
      setAsset(updated);
      setShowEditModal(false);
      setToastMessage(`Đã cập nhật thông tin thiết bị ${asset.code}!`);
      setTimeout(() => setToastMessage(''), 3500);
    } catch(e) {
      console.error(e);
      alert('Cập nhật thất bại');
    }
  };

  const handleDelete = async () => {
    if (!asset) return;
    try {
      await assetService.deleteFixedAsset(asset.code);
      setShowDeleteModal(false);
      onNavigate('fixed-assets');
    } catch(e) {
      console.error(e);
      alert('Không thể xóa tài sản này (có thể đã có lịch sử báo hỏng/thanh lý)');
    }
  };

  const openEditModal = () => {
    setEditData({ name: asset.name || '', categoryId: asset.categoryId || '', note: asset.note || '' });
    setShowEditModal(true);
  };

  if (loading || !asset) {
    return (
      <div className="flex items-center justify-center min-h-[500px]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-9 h-9 border-3 border-[#00375e] border-t-transparent rounded-full animate-spin"></div>
          <span className="text-sm font-medium text-[#5B6472]">Đang tải hồ sơ tài sản {assetCode}...</span>
        </div>
      </div>
    );
  }

  const getStatusBadge = (st) => {
    switch (st) {
      case 'Good':
        return { label: 'Tốt (Good / Operational)', bg: 'bg-[#E8F5E9]', text: 'text-[#2E7D32]', border: 'border-[#2E7D32]/30' };
      case 'Damaged':
        return { label: 'Bị hỏng (Damaged)', bg: 'bg-[#FFEBEE]', text: 'text-[#D32F2F]', border: 'border-[#D32F2F]/30' };
      case 'Repairing':
        return { label: 'Đang sửa chữa (Repairing)', bg: 'bg-[#FFFDE7]', text: 'text-[#F9A825]', border: 'border-[#F9A825]/40' };
      case 'Disposed':
        return { label: 'Đã thanh lý (Disposed)', bg: 'bg-[#EEEEEE]', text: 'text-[#616161]', border: 'border-[#616161]/30' };
      default:
        return { label: st, bg: 'bg-[#eff4ff]', text: 'text-[#00375e]', border: 'border-[#d1e4ff]' };
    }
  };

  const statusBadge = getStatusBadge(asset.status);

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      {/* Toast */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 bg-[#00375e] text-white px-4 py-3 rounded-xl shadow-xl flex items-center gap-2 text-xs font-semibold animate-in slide-in-from-bottom-3 duration-200">
          <span className="material-symbols-outlined text-[18px] text-[#2E7D32] bg-white rounded-full">check_circle</span>
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Top Header & Breadcrumbs */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-white p-5 rounded-2xl border border-[#DFE3E8] shadow-[0_1px_8px_rgba(0,0,0,0.02)]">
        <div>
          <div className="flex items-center gap-2 text-xs text-[#5B6472] mb-1">
            <button
              onClick={() => onNavigate('fixed-assets')}
              className="text-[#0e61a1] hover:underline cursor-pointer"
              type="button"
            >
              Danh sách tài sản cố định
            </button>
            <span>/</span>
            <span className="font-mono font-bold text-[#00375e]">{asset.code}</span>
          </div>

          <div className="flex items-center gap-3">
            <h1 className="text-xl font-bold text-[#00375e] tracking-tight">
              Chi Tiết Tài Sản: <span className="font-mono">{asset.code}</span>
            </h1>
            <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold border ${statusBadge.bg} ${statusBadge.text} ${statusBadge.border}`}>
              {statusBadge.label}
            </span>
          </div>
          <p className="text-xs text-[#5B6472] mt-0.5">{asset.categoryFullName || asset.name}</p>
        </div>

        {/* Action CTAs */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={openEditModal}
            className="px-3.5 py-2 rounded-xl border border-[#DFE3E8] bg-white text-[#1C2330] hover:bg-[#F7F8FA] text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px] text-[#0e61a1]">edit</span>
            <span>Sửa thông tin</span>
          </button>

          <button
            onClick={() => setShowLocationModal(true)}
            className="px-3.5 py-2 rounded-xl border border-[#DFE3E8] bg-white text-[#1C2330] hover:bg-[#F7F8FA] text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px] text-[#0e61a1]">near_me</span>
            <span>Chuyển vị trí</span>
          </button>

          <button
            onClick={() => setShowStatusModal(true)}
            className="px-3.5 py-2 rounded-xl border border-[#DFE3E8] bg-white text-[#1C2330] hover:bg-[#F7F8FA] text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px] text-[#F9A825]">sync_alt</span>
            <span>Đổi trạng thái</span>
          </button>

          <button
            onClick={() => onNavigate('issue-reports')}
            className="px-3.5 py-2 rounded-xl bg-[#FFEBEE] text-[#D32F2F] hover:bg-[#ffebee]/80 border border-[#D32F2F]/30 text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">report_problem</span>
            <span>Báo hỏng / Báo mất</span>
          </button>

          <button
            onClick={() => setShowDeleteModal(true)}
            className="px-3.5 py-2 rounded-xl bg-white text-[#D32F2F] hover:bg-[#FFEBEE] border border-[#D32F2F]/30 text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">delete_forever</span>
            <span>Xóa tài sản</span>
          </button>
        </div>
      </div>

      {/* 2-Column Split */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Visual Media & Location Card (5 cols) */}
        <div className="lg:col-span-5 space-y-5">
          {/* Current Location Card */}
          <div className="bg-white rounded-2xl border border-[#DFE3E8] p-5 shadow-xs space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-[#eff4ff] text-[#0e61a1] flex items-center justify-center">
                  <span className="material-symbols-outlined text-[18px]">room</span>
                </div>
                <div>
                  <h3 className="font-bold text-xs text-[#00375e]">Vị trí hiện diện</h3>
                  <span className="text-[10px] text-[#5B6472]">Gắn liền phòng buồng hiện hành</span>
                </div>
              </div>

              <button
                onClick={() => setShowLocationModal(true)}
                className="text-xs text-[#0e61a1] hover:underline font-semibold cursor-pointer"
                type="button"
              >
                Chuyển vị trí khác
              </button>
            </div>

            <div className="p-3.5 bg-[#eff4ff] rounded-xl border border-[#d1e4ff] flex items-center gap-3">
              <div className="w-12 h-12 rounded-xl bg-white border border-[#DFE3E8] flex items-center justify-center text-[#00375e] font-extrabold text-lg shadow-xs">
                {asset.location.replace(/[^0-9]/g, '') || 'KVC'}
              </div>
              <div>
                <div className="font-bold text-sm text-[#00375e]">{asset.location}</div>
                <div className="text-xs text-[#5B6472]">{asset.locationSub}</div>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Identification Profile (7 cols) */}
        <div className="lg:col-span-7 space-y-5">
          <div className="bg-white rounded-2xl border border-[#DFE3E8] p-6 shadow-xs space-y-5">
            <div className="flex items-center justify-between border-b border-[#DFE3E8] pb-3">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[#0e61a1] text-[20px]">badge</span>
                <h2 className="font-bold text-sm text-[#00375e]">Hồ Sơ Định Danh Tài Sản</h2>
              </div>
              <span className="text-xs text-[#5B6472]">Chi nhánh Sao Mai Nha Trang</span>
            </div>

            {/* Specs Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
              <div className="p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                <span className="text-[#5B6472] block text-[11px] mb-1">Mã định danh cá thể:</span>
                <div className="flex items-center justify-between font-mono font-bold text-sm text-[#00375e]">
                  <span>{asset.code}</span>
                  <button
                    onClick={handleCopy}
                    className="text-[#0e61a1] hover:underline text-xs flex items-center gap-1 font-normal cursor-pointer"
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[14px]">
                      {copied ? 'check' : 'content_copy'}
                    </span>
                    <span>{copied ? 'Đã sao chép' : 'Sao chép'}</span>
                  </button>
                </div>
              </div>

              <div className="p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                <span className="text-[#5B6472] block text-[11px] mb-1">Phân loại hệ thống:</span>
                <span className="font-semibold text-[#1C2330]">Tài sản cố định (Fixed Asset)</span>
              </div>

              <div className="p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                <span className="text-[#5B6472] block text-[11px] mb-1">Danh mục trang bị:</span>
                <span className="font-semibold text-[#1C2330]">{asset.category}</span>
              </div>

              <div className="p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                <span className="text-[#5B6472] block text-[11px] mb-1">Mục đích sử dụng:</span>
                <span className="font-semibold text-[#00375e] bg-[#eff4ff] px-2 py-0.5 rounded-md">
                  {asset.purpose}
                </span>
              </div>

              <div className="p-3 bg-[#F7F8FA] rounded-xl border border-[#DFE3E8]">
                <span className="text-[#5B6472] block text-[11px] mb-1">Trạng thái vận hành:</span>
                <span className={`font-semibold ${statusBadge.text}`}>{statusBadge.label}</span>
              </div>

              {asset.note && (
                <div className="p-3 bg-[#FFF9C4]/30 rounded-xl border border-[#FBC02D]/30 col-span-1 sm:col-span-2">
                  <span className="text-[#F9A825] font-bold block text-[11px] mb-1 flex items-center gap-1">
                    <span className="material-symbols-outlined text-[14px]">edit_note</span>
                    Ghi chú đặc thù:
                  </span>
                  <span className="font-medium text-[#1C2330] text-xs whitespace-pre-line leading-relaxed">{asset.note}</span>
                </div>
              )}

            </div>

            {/* Operational Rule Callout */}
            <div className="p-4 bg-[#eff4ff] rounded-xl border border-[#d1e4ff] text-xs space-y-1.5">
              <div className="flex items-center gap-2 font-bold text-[#00375e]">
                <span className="material-symbols-outlined text-[18px] text-[#0e61a1]">verified_user</span>
                <span>Quy định vận hành tài sản buồng phòng</span>
              </div>
              <p className="text-[#5B6472] leading-relaxed">
                Thiết bị gắn liền với phòng Deluxe chỉ được phép di chuyển sang phòng cùng hạng hoặc kho kỹ thuật khi có phiếu báo hỏng. Mọi thao tác chuyển đổi trạng thái sang <span className="font-semibold text-[#616161]">Đã thanh lý (Disposed)</span> là quyết định cuối cùng của chu trình vòng đời và không thể hoàn tác.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Change Location Modal */}
      {showLocationModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1C2330]/50 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="bg-white w-full max-w-md rounded-2xl shadow-xl border border-[#DFE3E8] p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-[#DFE3E8] pb-3">
              <h3 className="font-bold text-[#00375e] text-base">Chuyển Vị Trí Thiết Bị</h3>
              <button
                onClick={() => setShowLocationModal(false)}
                className="text-[#5B6472] hover:text-[#1C2330] cursor-pointer"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="text-xs text-[#5B6472]">
              Thiết bị hiện tại: <span className="font-mono font-bold text-[#00375e]">{asset.code}</span> ({asset.location})
            </div>

            <div className="space-y-1.5 text-xs">
              <label className="font-semibold text-[#1C2330]">Chọn vị trí đích mới:</label>
              <select
                value={newLocation}
                onChange={(e) => setNewLocation(e.target.value)}
                className="w-full p-2.5 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl text-xs text-[#1C2330] focus:outline-none focus:border-[#0e61a1]"
              >
                <option value="Phòng 101">Phòng 101 (Standard King · Tầng 1)</option>
                <option value="Phòng 104">Phòng 104 (Standard Đơn · Tầng 1)</option>
                <option value="Phòng 205">Phòng 205 (Superior Đôi · Tầng 2)</option>
                <option value="Phòng 310">Phòng 310 (Deluxe Hướng Biển · Tầng 3)</option>
                <option value="Phòng 312">Phòng 312 (Deluxe Twin · Tầng 3)</option>
                <option value="Phòng 405">Phòng 405 (Superior Đơn · Tầng 4)</option>
                <option value="Phòng 502">Phòng 502 (Executive Suite · Tầng 5)</option>
                <option value="Sảnh chính (Lobby)">Sảnh chính (Lobby - Tầng 1)</option>
                <option value="Phòng Gym & Spa">Phòng Gym & Spa (Tầng M)</option>
                <option value="Nhà hàng Sao Mai">Nhà hàng Sao Mai (Tầng 2)</option>
                <option value="Kho tổng tầng hầm">Kho tổng tầng hầm (Kho bảo trì)</option>
              </select>
            </div>

            <div className="flex items-center justify-end gap-2 pt-3 border-t border-[#DFE3E8]">
              <button
                onClick={() => setShowLocationModal(false)}
                className="px-4 py-2 rounded-xl border border-[#DFE3E8] text-xs font-semibold text-[#5B6472] hover:bg-[#F7F8FA] cursor-pointer"
                type="button"
              >
                Hủy bỏ
              </button>
              <button
                onClick={handleSaveLocation}
                className="px-4 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold shadow-xs cursor-pointer"
                type="button"
              >
                Xác nhận chuyển
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Change Status Modal */}
      {showStatusModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1C2330]/50 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="bg-white w-full max-w-md rounded-2xl shadow-xl border border-[#DFE3E8] p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-[#DFE3E8] pb-3">
              <h3 className="font-bold text-[#00375e] text-base">Đổi Trạng Thái Vận Hành</h3>
              <button
                onClick={() => setShowStatusModal(false)}
                className="text-[#5B6472] hover:text-[#1C2330] cursor-pointer"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="space-y-2 text-xs">
              <label className="font-semibold text-[#1C2330]">Chọn trạng thái mới:</label>
              <div className="space-y-2">
                {[
                  { value: 'Good', label: 'Tốt (Good / Operational)', desc: 'Thiết bị sẵn sàng phục vụ buồng khách' },
                  { value: 'Damaged', label: 'Bị hỏng (Damaged)', desc: 'Cần đội ngũ kỹ thuật can thiệp khẩn cấp' },
                  { value: 'Repairing', label: 'Đang sửa chữa (Repairing)', desc: 'Đã gửi trung tâm bảo hành Daikin' },
                  { value: 'Disposed', label: 'Đã thanh lý (Disposed)', desc: 'Hết hạn sử dụng, thanh lý sổ sách' }
                ].map((opt) => (
                  <label
                    key={opt.value}
                    className={`flex items-start gap-2.5 p-3 rounded-xl border transition-all cursor-pointer ${
                      newStatus === opt.value
                        ? 'border-[#00375e] bg-[#eff4ff]'
                        : 'border-[#DFE3E8] hover:bg-[#F7F8FA]'
                    }`}
                  >
                    <input
                      type="radio"
                      name="modalStatus"
                      value={opt.value}
                      checked={newStatus === opt.value}
                      onChange={(e) => setNewStatus(e.target.value)}
                      className="mt-0.5 text-[#00375e] focus:ring-0 cursor-pointer"
                    />
                    <div>
                      <div className="font-semibold text-[#00375e]">{opt.label}</div>
                      <div className="text-[11px] text-[#5B6472]">{opt.desc}</div>
                    </div>
                  </label>
                ))}
              </div>

              {newStatus === 'Disposed' && (
                <div className="p-3 bg-[#FFEBEE] border border-[#D32F2F]/30 rounded-xl text-xs text-[#D32F2F] flex items-start gap-2">
                  <span className="material-symbols-outlined text-[18px] shrink-0">warning</span>
                  <div>
                    <span className="font-bold">Cảnh báo quy tắc nghiệp vụ: </span>
                    <span>
                      Khi đổi sang "Đã thanh lý (Disposed)", tài sản này sẽ bị khóa vĩnh viễn và không thể tái kích hoạt!
                    </span>
                  </div>
                </div>
              )}
            </div>

            <div className="flex items-center justify-end gap-2 pt-3 border-t border-[#DFE3E8]">
              <button
                onClick={() => setShowStatusModal(false)}
                className="px-4 py-2 rounded-xl border border-[#DFE3E8] text-xs font-semibold text-[#5B6472] hover:bg-[#F7F8FA] cursor-pointer"
                type="button"
              >
                Hủy
              </button>
              <button
                onClick={handleSaveStatus}
                className="px-4 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold shadow-xs cursor-pointer"
                type="button"
              >
                Cập nhật trạng thái
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Edit Modal */}
      {showEditModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1C2330]/50 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="bg-white w-full max-w-md rounded-2xl shadow-xl border border-[#DFE3E8] p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-[#DFE3E8] pb-3">
              <h3 className="font-bold text-[#00375e] text-base">Sửa Thông Tin Tài Sản</h3>
              <button
                onClick={() => setShowEditModal(false)}
                className="text-[#5B6472] hover:text-[#1C2330] cursor-pointer"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div className="space-y-1.5">
                <label className="font-semibold text-[#1C2330]">Tên tài sản:</label>
                <input
                  type="text"
                  value={editData.name}
                  onChange={(e) => setEditData({...editData, name: e.target.value})}
                  className="w-full p-2.5 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl focus:outline-none focus:border-[#0e61a1]"
                  placeholder="Nhập tên tài sản (tùy chọn)"
                />
              </div>

              {categories.length > 0 && (
                <div className="space-y-1.5">
                  <label className="font-semibold text-[#1C2330]">Danh mục:</label>
                  <select
                    value={editData.categoryId}
                    onChange={(e) => setEditData({...editData, categoryId: e.target.value})}
                    className="w-full p-2.5 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl focus:outline-none focus:border-[#0e61a1]"
                  >
                    <option value="">-- Chọn danh mục (nếu muốn đổi) --</option>
                    {categories.map(c => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>
                </div>
              )}

              <div className="space-y-1.5">
                <label className="font-semibold text-[#1C2330]">Ghi chú:</label>
                <textarea
                  value={editData.note}
                  onChange={(e) => setEditData({...editData, note: e.target.value})}
                  className="w-full p-2.5 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl focus:outline-none focus:border-[#0e61a1]"
                  placeholder="Thêm ghi chú đặc thù..."
                  rows={3}
                ></textarea>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-3 border-t border-[#DFE3E8]">
              <button
                onClick={() => setShowEditModal(false)}
                className="px-4 py-2 rounded-xl border border-[#DFE3E8] text-xs font-semibold text-[#5B6472] hover:bg-[#F7F8FA] cursor-pointer"
                type="button"
              >
                Hủy
              </button>
              <button
                onClick={handleSaveEdit}
                className="px-4 py-2 rounded-xl bg-[#0e61a1] text-white hover:bg-[#0a4675] text-xs font-semibold shadow-xs cursor-pointer"
                type="button"
              >
                Lưu thay đổi
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {showDeleteModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1C2330]/50 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="bg-white w-full max-w-sm rounded-2xl shadow-xl border border-[#DFE3E8] p-6 space-y-4 text-center">
            <div className="w-12 h-12 rounded-full bg-[#FFEBEE] text-[#D32F2F] flex items-center justify-center mx-auto mb-2">
              <span className="material-symbols-outlined text-[24px]">delete_forever</span>
            </div>
            
            <h3 className="font-bold text-[#1C2330] text-lg">Xác nhận xóa tài sản?</h3>
            
            <p className="text-xs text-[#5B6472] leading-relaxed">
              Bạn sắp xóa vĩnh viễn tài sản <span className="font-mono font-bold text-[#00375e]">{asset.code}</span> khỏi hệ thống.
              <br/><br/>
              <span className="text-[#D32F2F] font-semibold">Cảnh báo:</span> Hành động này không thể hoàn tác. Chỉ nên xóa nếu tài sản được nhập sai hoàn toàn và chưa từng được sử dụng. Nếu tài sản đã cũ, hãy dùng chức năng <strong>Đổi trạng thái -> Đã thanh lý</strong>.
            </p>

            <div className="flex items-center justify-center gap-3 pt-4">
              <button
                onClick={() => setShowDeleteModal(false)}
                className="px-5 py-2.5 rounded-xl border border-[#DFE3E8] text-xs font-semibold text-[#5B6472] hover:bg-[#F7F8FA] cursor-pointer w-full"
                type="button"
              >
                Hủy bỏ
              </button>
              <button
                onClick={handleDelete}
                className="px-5 py-2.5 rounded-xl bg-[#D32F2F] text-white hover:bg-[#b71c1c] text-xs font-semibold shadow-xs cursor-pointer w-full flex justify-center items-center gap-1.5"
                type="button"
              >
                <span className="material-symbols-outlined text-[16px]">delete</span>
                Xóa vĩnh viễn
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
