import React, { useState, useEffect } from 'react';
import { assetService } from '../../services/assetApi';
import { fetchAllRooms } from '../../api/rooms';
import { fetchAllAreas } from '../../api/locations';
import { useAuth } from '../../context/AuthContext';
import { PURPOSE_LABEL } from '../asset-categories/categoryLabels';

const STATUS_LABEL = {
  Good: 'Tốt (Good)',
  Damaged: 'Bị hỏng (Damaged)',
  Repairing: 'Đang sửa chữa (Repairing)',
  Disposed: 'Đã thanh lý (Disposed)',
};

/** Giá trị <select> vị trí: `ROOM_<uuid>` hoặc `AREA_<uuid>`. */
const locationValueOf = (asset) =>
  asset?.roomId ? `ROOM_${asset.roomId}` : asset?.areaId ? `AREA_${asset.areaId}` : '';

/**
 * Chi tiết một tài sản cố định. `assetRef` là id (đường chuẩn từ danh sách) hoặc mã tài
 * sản (link cũ). Chỉ Manager được sửa / chuyển vị trí / đổi trạng thái / xóa — BR-ASSET-02,
 * BR-ASSET-09; Giám đốc và Staff chỉ xem.
 */
export const AssetDetailScreen = ({ assetRef, onNavigate }) => {
  const { user } = useAuth();
  const canManage = user?.role === 'MANAGER';

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [asset, setAsset] = useState(null);
  const [copied, setCopied] = useState(false);
  const [saving, setSaving] = useState(false);

  // Modals
  const [showLocationModal, setShowLocationModal] = useState(false);
  const [showStatusModal, setShowStatusModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  const [newLocation, setNewLocation] = useState('');
  const [newStatus, setNewStatus] = useState('Good');
  const [editData, setEditData] = useState({ name: '', categoryId: '', note: '' });
  const [categories, setCategories] = useState([]);

  const [roomsData, setRoomsData] = useState([]);
  const [areasData, setAreasData] = useState([]);
  const [toast, setToast] = useState(null); // { message, error }

  // Chỉ Manager mới chuyển vị trí được, và chỉ trong khách sạn của tài sản (BR-ASSET-13).
  useEffect(() => {
    if (!canManage || !asset?.locationId) return;
    fetchAllRooms({ locationId: asset.locationId })
      .then((rooms) => setRoomsData(rooms.filter((r) => r.locationId === asset.locationId)))
      .catch(console.error);
    fetchAllAreas()
      .then((areas) => setAreasData(areas.filter((a) => a.locationId === asset.locationId)))
      .catch(console.error);
  }, [canManage, asset?.locationId]);

  useEffect(() => {
    loadAssetDetail();
    loadCategories();
  }, [assetRef]);

  const showToast = (message, error = false) => {
    setToast({ message, error });
    setTimeout(() => setToast(null), 3500);
  };

  const loadCategories = async () => {
    try {
      const cats = await assetService.getAssetCategories();
      setCategories(cats.filter((c) => c.assetKind === 'FIXED'));
    } catch (e) {
      console.error('Failed to load categories', e);
    }
  };

  const loadAssetDetail = async () => {
    setLoading(true);
    setLoadError('');
    try {
      const data = await assetService.getAssetDetail(assetRef);
      setAsset(data);
      setNewLocation(locationValueOf(data));
      setNewStatus(data.status);
    } catch (e) {
      console.error('Failed to load asset', e);
      setLoadError(e.message || 'Không tải được thông tin tài sản.');
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

  const openLocationModal = () => {
    setNewLocation(locationValueOf(asset));
    setShowLocationModal(true);
  };

  const openStatusModal = () => {
    setNewStatus(asset.status);
    setShowStatusModal(true);
  };

  const openEditModal = () => {
    setEditData({ name: asset.name || '', categoryId: asset.categoryId || '', note: asset.note || '' });
    setShowEditModal(true);
  };

  /** Chạy một thao tác ghi: lỗi backend hiện nguyên văn, modal giữ nguyên để sửa lại. */
  const runAction = async (action, successMessage, closeModal) => {
    setSaving(true);
    try {
      const updated = await action();
      if (updated) setAsset(updated);
      closeModal();
      showToast(successMessage);
    } catch (e) {
      console.error(e);
      showToast(e.message || 'Thao tác thất bại, vui lòng thử lại.', true);
    } finally {
      setSaving(false);
    }
  };

  const handleSaveLocation = () => {
    if (!asset) return;
    if (newLocation === locationValueOf(asset)) {
      setShowLocationModal(false);
      return;
    }
    const [type, id] = newLocation.split('_');
    return runAction(
      () => assetService.updateAssetLocation(asset.id, id, type),
      `Đã chuyển vị trí thiết bị ${asset.code}!`,
      () => setShowLocationModal(false)
    );
  };

  const handleSaveStatus = () => {
    if (!asset) return;
    if (newStatus === asset.status) {
      setShowStatusModal(false);
      return;
    }
    return runAction(
      () => assetService.updateAssetStatus(asset.id, newStatus),
      `Đã cập nhật trạng thái thiết bị ${asset.code} thành "${STATUS_LABEL[newStatus] || newStatus}"!`,
      () => setShowStatusModal(false)
    );
  };

  const handleSaveEdit = () => {
    if (!asset) return;
    if (!editData.name.trim()) {
      showToast('Tên tài sản không được để trống.', true);
      return;
    }
    return runAction(
      () => assetService.updateFixedAssetInfo(asset.id, { ...editData, name: editData.name.trim() }),
      `Đã cập nhật thông tin thiết bị ${asset.code}!`,
      () => setShowEditModal(false)
    );
  };

  const handleDelete = async () => {
    if (!asset) return;
    setSaving(true);
    try {
      await assetService.deleteFixedAsset(asset.id);
      setShowDeleteModal(false);
      onNavigate('fixed-assets');
    } catch (e) {
      console.error(e);
      showToast(e.message || 'Không thể xóa tài sản này.', true);
    } finally {
      setSaving(false);
    }
  };

  if (loadError) {
    return (
      <div className="flex items-center justify-center min-h-[500px]">
        <div className="flex flex-col items-center gap-3 text-center">
          <span className="material-symbols-outlined text-[40px] text-[#D32F2F]">error</span>
          <span className="text-sm font-medium text-[#1C2330]">{loadError}</span>
          <div className="flex items-center gap-2">
            <button
              onClick={loadAssetDetail}
              className="px-4 py-2 rounded-xl border border-[#DFE3E8] text-xs font-semibold text-[#1C2330] hover:bg-[#F7F8FA] cursor-pointer"
              type="button"
            >
              Thử lại
            </button>
            <button
              onClick={() => onNavigate('fixed-assets')}
              className="px-4 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold cursor-pointer"
              type="button"
            >
              Về danh sách tài sản
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (loading || !asset) {
    return (
      <div className="flex items-center justify-center min-h-[500px]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-9 h-9 border-3 border-[#00375e] border-t-transparent rounded-full animate-spin"></div>
          <span className="text-sm font-medium text-[#5B6472]">Đang tải hồ sơ tài sản...</span>
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
      {toast && (
        <div
          className={`fixed bottom-6 right-6 z-[60] text-white px-4 py-3 rounded-xl shadow-xl flex items-center gap-2 text-xs font-semibold animate-in slide-in-from-bottom-3 duration-200 max-w-md ${
            toast.error ? 'bg-[#D32F2F]' : 'bg-[#00375e]'
          }`}
        >
          <span className="material-symbols-outlined text-[18px] shrink-0">{toast.error ? 'error' : 'check_circle'}</span>
          <span>{toast.message}</span>
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
          {canManage && (
          <>
          <button
            onClick={openEditModal}
            disabled={asset.status === 'Disposed'}
            className={`px-3.5 py-2 rounded-xl border border-[#DFE3E8] bg-white text-[#1C2330] text-xs font-semibold flex items-center gap-1.5 transition-colors shadow-xs ${asset.status === 'Disposed' ? 'opacity-50 cursor-not-allowed' : 'hover:bg-[#F7F8FA] cursor-pointer'}`}
            type="button"
          >
            <span className="material-symbols-outlined text-[18px] text-[#0e61a1]">edit</span>
            <span>Sửa thông tin</span>
          </button>

          <button
            onClick={openLocationModal}
            disabled={asset.status === 'Disposed'}
            className={`px-3.5 py-2 rounded-xl border border-[#DFE3E8] bg-white text-[#1C2330] text-xs font-semibold flex items-center gap-1.5 transition-colors shadow-xs ${asset.status === 'Disposed' ? 'opacity-50 cursor-not-allowed' : 'hover:bg-[#F7F8FA] cursor-pointer'}`}
            type="button"
          >
            <span className="material-symbols-outlined text-[18px] text-[#0e61a1]">near_me</span>
            <span>Chuyển vị trí</span>
          </button>

          <button
            onClick={openStatusModal}
            disabled={asset.status === 'Disposed'}
            className={`px-3.5 py-2 rounded-xl border border-[#DFE3E8] bg-white text-[#1C2330] text-xs font-semibold flex items-center gap-1.5 transition-colors shadow-xs ${asset.status === 'Disposed' ? 'opacity-50 cursor-not-allowed' : 'hover:bg-[#F7F8FA] cursor-pointer'}`}
            type="button"
          >
            <span className="material-symbols-outlined text-[18px] text-[#F9A825]">sync_alt</span>
            <span>Đổi trạng thái</span>
          </button>
          </>
          )}

          <button
            onClick={() => onNavigate('issue-reports')}
            disabled={asset.status === 'Disposed'}
            className={`px-3.5 py-2 rounded-xl border border-[#D32F2F]/30 text-xs font-semibold flex items-center gap-1.5 transition-colors shadow-xs ${asset.status === 'Disposed' ? 'bg-[#FFEBEE]/50 text-[#D32F2F]/50 opacity-50 cursor-not-allowed' : 'bg-[#FFEBEE] text-[#D32F2F] hover:bg-[#ffebee]/80 cursor-pointer'}`}
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">report_problem</span>
            <span>Báo hỏng / Báo mất</span>
          </button>

          {canManage && (
            <button
              onClick={() => setShowDeleteModal(true)}
              className="px-3.5 py-2 rounded-xl bg-white text-[#D32F2F] hover:bg-[#FFEBEE] border border-[#D32F2F]/30 text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">delete_forever</span>
              <span>Xóa tài sản</span>
            </button>
          )}
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

              {canManage && asset.status !== 'Disposed' && (
                <button
                  onClick={openLocationModal}
                  className="text-xs text-[#0e61a1] hover:underline font-semibold cursor-pointer"
                  type="button"
                >
                  Chuyển vị trí khác
                </button>
              )}
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
                  {PURPOSE_LABEL[asset.purpose] || '—'}
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
                {roomsData.length > 0 && (
                  <optgroup label="Phòng (Rooms)">
                    {roomsData.map(r => (
                      <option key={r.id} value={`ROOM_${r.id}`}>Phòng {r.roomNumber} (Tầng {r.floor || '?'})</option>
                    ))}
                  </optgroup>
                )}
                {areasData.length > 0 && (
                  <optgroup label="Khu vực (Areas)">
                    {areasData.map(a => (
                      <option key={a.id} value={`AREA_${a.id}`}>{a.name}</option>
                    ))}
                  </optgroup>
                )}
                {roomsData.length === 0 && areasData.length === 0 && (
                  <option value="" disabled>Đang tải danh sách vị trí...</option>
                )}
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
                disabled={saving}
                className="px-4 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold shadow-xs cursor-pointer disabled:opacity-50 disabled:cursor-wait"
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
                disabled={saving}
                className="px-4 py-2 rounded-xl bg-[#00375e] text-white hover:bg-[#1f4e78] text-xs font-semibold shadow-xs cursor-pointer disabled:opacity-50 disabled:cursor-wait"
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
                <label className="font-semibold text-[#1C2330]">Tên tài sản (*):</label>
                <input
                  type="text"
                  value={editData.name}
                  onChange={(e) => setEditData({...editData, name: e.target.value})}
                  className="w-full p-2.5 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl focus:outline-none focus:border-[#0e61a1]"
                  placeholder="Nhập tên tài sản"
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
                    {/* Danh mục đang ẩn không chọn mới được (BR-ORG-14), trừ danh mục hiện tại. */}
                    {categories
                      .filter(c => c.active || c.id === asset.categoryId)
                      .map(c => (
                        <option key={c.id} value={c.id}>{c.name}{c.active ? '' : ' (tạm ngưng)'}</option>
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
                disabled={saving}
                className="px-4 py-2 rounded-xl bg-[#0e61a1] text-white hover:bg-[#0a4675] text-xs font-semibold shadow-xs cursor-pointer disabled:opacity-50 disabled:cursor-wait"
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
              <span className="text-[#D32F2F] font-semibold">Cảnh báo:</span> Hành động này không thể hoàn tác. Chỉ nên xóa nếu tài sản được nhập sai hoàn toàn và chưa từng được sử dụng. Nếu tài sản đã cũ, hãy dùng chức năng <strong>Đổi trạng thái -&gt; Đã thanh lý</strong>.
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
                disabled={saving}
                className="px-5 py-2.5 rounded-xl bg-[#D32F2F] text-white hover:bg-[#b71c1c] text-xs font-semibold shadow-xs cursor-pointer disabled:opacity-50 disabled:cursor-wait w-full flex justify-center items-center gap-1.5"
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
