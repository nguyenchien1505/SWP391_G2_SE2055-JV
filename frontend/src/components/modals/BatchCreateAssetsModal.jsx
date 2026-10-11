import React, { useState, useMemo, useEffect } from 'react';
import { assetService } from '../../services/assetApi';
import { fetchAllRooms } from '../../api/rooms';
import { fetchAllAreas } from '../../api/locations';
import { useAuth } from '../../context/AuthContext';

const MAX_QUANTITY = 50;

const PURPOSE_GROUPS = [
  { value: 'GUEST_USE', label: 'Phục vụ khách hàng' },
  { value: 'FACILITY_MAINTENANCE', label: 'Duy trì & Vận hành cơ sở' },
];

const getPurposeLabel = (purpose) =>
  PURPOSE_GROUPS.find((g) => g.value === purpose)?.label || purpose || '—';

/** Bỏ dấu tiếng Việt để mã tài sản chỉ gồm ký tự ASCII. */
const toAscii = (s) =>
  String(s ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D');

/** Chữ cái đầu của các từ có chữ: "Tủ lạnh mini bar" → "TLM", "Hành Lang" → "HL". */
const initials = (s, max) =>
  toAscii(s)
    .split(/[^A-Za-z0-9]+/)
    .filter((w) => /[A-Za-z]/.test(w))
    .map((w) => w[0])
    .join('')
    .toUpperCase()
    .slice(0, max);

/**
 * Hộp thoại khai báo hàng loạt tài sản cố định — mã `[Mã-DM]-[Vị-Trí]-[STT]`. STT nối tiếp số lớn
 * nhất đang dùng cho cùng tiền tố (BR-ASSET-12: mã duy nhất trong khách sạn), nên chạy
 * nhiều lần cho cùng phòng không đâm trùng mã. Chỉ dùng danh mục/vị trí thật từ backend.
 *
 * Mở từ Danh sách tài sản, Dashboard, Sơ đồ phòng và Quản lý khu vực — chỉ Manager (BR-ASSET-09).
 *
 * @param initialRoomId mở từ nút "Thêm tài sản" trên sơ đồ phòng — chọn sẵn phòng đó làm vị trí
 * @param initialAreaId mở từ nút "Thêm tài sản" ở màn Quản lý khu vực — chọn sẵn khu vực đó
 * @param onCreated     gọi sau mỗi lần tạo thành công để màn bên dưới nạp lại dữ liệu
 */
export const BatchCreateAssetsModal = ({ initialRoomId, initialAreaId, onClose, onCreated }) => {
  const { user } = useAuth();

  const [categoriesData, setCategoriesData] = useState([]);
  const [roomsData, setRoomsData] = useState([]);
  const [areasData, setAreasData] = useState([]);
  const [loadingData, setLoadingData] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [positionType, setPositionType] = useState(initialAreaId ? 'AREA' : 'ROOM'); // 'ROOM' | 'AREA'
  const [selectedPositionId, setSelectedPositionId] = useState(initialAreaId ?? initialRoomId ?? '');
  const [quantity, setQuantity] = useState(5);
  const [nextSeq, setNextSeq] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [successResult, setSuccessResult] = useState(null);

  useEffect(() => {
    async function load() {
      setLoadingData(true);
      setLoadError('');
      try {
        const locationId = user?.locationId;
        const [cats, rms, ars] = await Promise.all([
          assetService.getAssetCategories(),
          fetchAllRooms({ locationId }),
          fetchAllAreas(),
        ]);

        // Danh mục tạm ngưng không được dùng cho tài sản mới (BR-ORG-14).
        setCategoriesData(
          cats
            .filter((c) => c.assetKind === 'FIXED' && c.active)
            .map((c) => ({
              id: c.id,
              name: c.name,
              prefix: 'TS-' + (initials(c.name, 3) || 'XX'),
              purpose: c.purpose,
            }))
        );
        setRoomsData(
          rms
            .filter((r) => !locationId || r.locationId === locationId)
            .map((r) => ({
              id: r.id,
              code: toAscii(r.roomNumber).replace(/[^A-Za-z0-9]/g, '').toUpperCase() || 'P',
              label: `Phòng ${r.roomNumber} (Tầng ${r.floor || '?'})`,
            }))
        );
        setAreasData(
          ars
            .filter((a) => !locationId || a.locationId === locationId)
            .map((a) => ({ id: a.id, code: initials(a.name, 3) || 'KV', label: a.name }))
        );
      } catch (e) {
        console.error(e);
        setLoadError(e.message || 'Không tải được danh mục / vị trí. Vui lòng thử lại.');
      } finally {
        setLoadingData(false);
      }
    }
    load();
  }, [user?.locationId]);

  // Esc để đóng, trừ lúc đang tạo — tránh đóng giữa chừng rồi không biết kết quả.
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !isSubmitting && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [isSubmitting, onClose]);

  const currentPositionList = positionType === 'ROOM' ? roomsData : areasData;

  const selectedCategory = useMemo(
    () => categoriesData.find((c) => c.id === selectedCategoryId) || categoriesData[0] || null,
    [categoriesData, selectedCategoryId]
  );

  const selectedPosition = useMemo(
    () => currentPositionList.find((p) => p.id === selectedPositionId) || currentPositionList[0] || null,
    [currentPositionList, selectedPositionId]
  );

  const codeBase = selectedCategory && selectedPosition ? `${selectedCategory.prefix}-${selectedPosition.code}-` : '';

  // STT bắt đầu = số lớn nhất đang dùng + 1, tính lại khi đổi danh mục/vị trí hoặc sau mỗi lần tạo.
  useEffect(() => {
    if (!codeBase) {
      setNextSeq(null);
      return;
    }
    let cancelled = false;
    setNextSeq(null);
    assetService
      .getNextBatchSequence(codeBase)
      .then((seq) => !cancelled && setNextSeq(seq))
      .catch((e) => {
        console.error(e);
        if (!cancelled) setNextSeq(1);
      });
    return () => {
      cancelled = true;
    };
  }, [codeBase, refreshKey]);

  // Generate live preview code list
  const previewCodes = useMemo(() => {
    if (!codeBase || nextSeq === null) return [];
    const count = Math.min(Math.max(1, quantity), MAX_QUANTITY);
    return Array.from({ length: count }, (_, i) => `${codeBase}${String(nextSeq + i).padStart(3, '0')}`);
  }, [codeBase, nextSeq, quantity]);

  const canSubmit = !isSubmitting && !!selectedCategory && !!selectedPosition && previewCodes.length > 0;

  const handleQuantityChange = (newVal) => {
    const val = parseInt(newVal, 10);
    if (isNaN(val)) return;
    setQuantity(Math.min(Math.max(1, val), MAX_QUANTITY));
  };

  const handlePositionTypeSwitch = (type) => {
    setPositionType(type);
    setSelectedPositionId('');
  };

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setIsSubmitting(true);
    setSubmitError('');
    setSuccessResult(null);
    try {
      const res = await assetService.createBatchAssets({
        categoryId: selectedCategory.id,
        name: selectedCategory.name,
        codeBase,
        positionType,
        positionId: selectedPosition.id,
        quantity: previewCodes.length,
      });
      setSuccessResult(res);
      onCreated?.(res);
    } catch (e) {
      console.error('Batch creation failed', e);
      setSubmitError(e.message || 'Lỗi khởi tạo tài sản hàng loạt. Vui lòng kiểm tra lại.');
    } finally {
      setIsSubmitting(false);
      setRefreshKey((k) => k + 1);
    }
  };

  const selectClass =
    'w-full p-2.5 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl text-xs text-[#1C2330] font-medium focus:outline-none focus:border-[#0e61a1] cursor-pointer disabled:cursor-not-allowed disabled:opacity-60';

  return (
    <div
      className="fixed inset-0 z-50 flex items-start sm:items-center justify-center overflow-y-auto p-4 bg-[#001D35]/40 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="batch-create-title"
      onClick={() => !isSubmitting && onClose()}
    >
      <div
        className="w-full max-w-5xl my-auto bg-[#F7F8FA] rounded-2xl shadow-xl border border-[#DFE3E8]"
        onClick={(e) => e.stopPropagation()}
      >
      {/* Header */}
      <div className="flex items-start justify-between gap-3 px-6 py-4 bg-white border-b border-[#DFE3E8] rounded-t-2xl">
        <div>
          <h2 id="batch-create-title" className="text-lg font-bold text-[#00375e] tracking-tight">
            Thêm tài sản cố định
          </h2>
          <p className="text-xs text-[#5B6472] mt-0.5">
            Tự động sinh mã định danh duy nhất theo cấu trúc chuẩn: <code className="font-mono text-[#00375e] font-bold">[Mã-DM]-[Vị-Trí]-[STT]</code>
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          disabled={isSubmitting}
          className="p-1.5 rounded text-[#5B6472] hover:text-[#1C2330] hover:bg-[#F7F8FA] transition-colors cursor-pointer disabled:opacity-50"
          aria-label="Đóng"
        >
          <span className="material-symbols-outlined text-[20px] leading-none">close</span>
        </button>
      </div>

      <div className="p-5 space-y-5">
      {loadError && (
        <div className="p-3 rounded-xl bg-[#FFEBEE] text-[#D32F2F] text-xs border border-[#D32F2F]/30">
          {loadError}
        </div>
      )}

      {submitError && (
        <div className="p-3 rounded-xl bg-[#FFEBEE] text-[#D32F2F] text-xs border border-[#D32F2F]/30 flex items-start gap-2">
          <span className="material-symbols-outlined text-[18px] shrink-0">error</span>
          <span>{submitError}</span>
        </div>
      )}

      {/* Success Notification */}
      {successResult && (
        <div className="p-5 bg-[#E8F5E9] border border-[#2E7D32]/30 rounded-2xl flex flex-col md:flex-row items-start md:items-center justify-between gap-4 animate-in zoom-in-95 duration-200">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-[#2E7D32] text-white flex items-center justify-center shrink-0 shadow-sm">
              <span className="material-symbols-outlined text-[24px]">check_circle</span>
            </div>
            <div>
              <div className="font-bold text-sm text-[#2E7D32]">
                Khởi tạo thành công {successResult.count} tài sản cố định mới!
              </div>
              <div className="text-xs text-[#2E7D32]/80 mt-0.5">
                Các mã từ <span className="font-mono font-bold">{successResult.codes[0]}</span> đến{' '}
                <span className="font-mono font-bold">{successResult.codes[successResult.codes.length - 1]}</span> đã được lưu vào hệ thống.
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 bg-[#2E7D32] text-white text-xs font-semibold rounded-xl hover:bg-[#1b5e20] transition-colors cursor-pointer shadow-xs"
              type="button"
            >
              Đóng
            </button>
            <button
              onClick={() => setSuccessResult(null)}
              className="px-3 py-2 text-xs font-medium text-[#2E7D32] hover:bg-[#2E7D32]/10 rounded-xl cursor-pointer"
              type="button"
            >
              Tiếp tục tạo thêm
            </button>
          </div>
        </div>
      )}

      {/* 2-Column Split Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Form Setup (7 cols) */}
        <div className="lg:col-span-7 bg-white rounded-2xl border border-[#DFE3E8] p-6 shadow-xs space-y-5">
          <div className="flex items-center justify-between border-b border-[#DFE3E8] pb-3">
            <div className="flex items-center gap-2">
              <span className="w-6 h-6 rounded-full bg-[#00375e] text-white flex items-center justify-center text-xs font-bold">
                1
              </span>
              <h2 className="font-bold text-sm text-[#00375e]">
                Thông Số Khởi Tạo - Thiết lập thông tin
              </h2>
            </div>
          </div>

          {/* Form Fields */}
          <div className="space-y-4">
            {/* Category Select */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-xs font-semibold text-[#1C2330]">Danh mục tài sản (*)</label>
                {selectedCategory && (
                  <span className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${
                    selectedCategory.purpose === 'GUEST_USE'
                      ? 'bg-[#eff4ff] text-[#00375e]'
                      : 'bg-[#F7F8FA] text-[#5B6472] border border-[#DFE3E8]'
                  }`}>
                    Mục đích: {getPurposeLabel(selectedCategory.purpose)}
                  </span>
                )}
              </div>
              <select
                value={selectedCategory?.id || ''}
                onChange={(e) => setSelectedCategoryId(e.target.value)}
                disabled={categoriesData.length === 0}
                className={selectClass}
              >
                {categoriesData.length === 0 && (
                  <option value="">
                    {loadingData ? 'Đang tải danh mục...' : 'Chưa có danh mục tài sản cố định nào đang áp dụng'}
                  </option>
                )}
                {PURPOSE_GROUPS.map((group) => {
                  const items = categoriesData.filter((c) => c.purpose === group.value);
                  return items.length === 0 ? null : (
                    <optgroup key={group.value} label={group.label}>
                      {items.map((cat) => (
                        <option key={cat.id} value={cat.id}>
                          {cat.name} — Prefix: [{cat.prefix}]
                        </option>
                      ))}
                    </optgroup>
                  );
                })}
              </select>
            </div>

            {/* Position Type Selector */}
            <div>
              <label className="block text-xs font-semibold text-[#1C2330] mb-1.5">
                Loại vị trí gắn liền (*)
              </label>
              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => handlePositionTypeSwitch('ROOM')}
                  className={`p-3 rounded-xl border flex items-center gap-2.5 transition-all cursor-pointer ${
                    positionType === 'ROOM'
                      ? 'border-[#00375e] bg-[#eff4ff] text-[#00375e] font-semibold ring-1 ring-[#00375e]'
                      : 'border-[#DFE3E8] text-[#5B6472] hover:bg-[#F7F8FA]'
                  }`}
                >
                  <span className="material-symbols-outlined text-[20px]">meeting_room</span>
                  <div className="text-left">
                    <div className="text-xs">Gắn vào Phòng (Room)</div>
                    <div className="text-[10px] text-[#72777f]">Buồng khách</div>
                  </div>
                </button>

                <button
                  type="button"
                  onClick={() => handlePositionTypeSwitch('AREA')}
                  className={`p-3 rounded-xl border flex items-center gap-2.5 transition-all cursor-pointer ${
                    positionType === 'AREA'
                      ? 'border-[#00375e] bg-[#eff4ff] text-[#00375e] font-semibold ring-1 ring-[#00375e]'
                      : 'border-[#DFE3E8] text-[#5B6472] hover:bg-[#F7F8FA]'
                  }`}
                >
                  <span className="material-symbols-outlined text-[20px]">domain</span>
                  <div className="text-left">
                    <div className="text-xs">Gắn vào Khu vực (Area)</div>
                    <div className="text-[10px] text-[#72777f]">Sảnh, Nhà hàng, Gym, Kho</div>
                  </div>
                </button>
              </div>
            </div>

            {/* Specific Location Dropdown */}
            <div>
              <label className="block text-xs font-semibold text-[#1C2330] mb-1.5">
                Vị trí cụ thể tiếp nhận thiết bị (*)
              </label>
              <select
                value={selectedPosition?.id || ''}
                onChange={(e) => setSelectedPositionId(e.target.value)}
                disabled={currentPositionList.length === 0}
                className={selectClass}
              >
                {currentPositionList.length === 0 && (
                  <option value="">
                    {loadingData
                      ? 'Đang tải vị trí...'
                      : positionType === 'ROOM'
                        ? 'Khách sạn chưa có phòng nào'
                        : 'Khách sạn chưa có khu vực nào'}
                  </option>
                )}
                {currentPositionList.map((pos) => (
                  <option key={pos.id} value={pos.id}>
                    {pos.label} — Mã vị trí: [{pos.code}]
                  </option>
                ))}
              </select>
            </div>

            {/* Quantity Stepper & Presets */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-xs font-semibold text-[#1C2330]">Số lượng cần tạo (*)</label>
                <span className="text-[11px] text-[#5B6472]">Tối đa {MAX_QUANTITY} thiết bị / lần khởi tạo</span>
              </div>
              <div className="flex items-center gap-3">
                <div className="flex items-center border border-[#DFE3E8] rounded-xl overflow-hidden bg-white shadow-xs">
                  <button
                    type="button"
                    onClick={() => handleQuantityChange(quantity - 1)}
                    disabled={quantity <= 1}
                    className="px-3 py-2 bg-[#F7F8FA] hover:bg-[#eff4ff] text-[#00375e] font-bold text-sm transition-colors cursor-pointer disabled:opacity-40"
                  >
                    -
                  </button>
                  <input
                    type="number"
                    min="1"
                    max={MAX_QUANTITY}
                    value={quantity}
                    onChange={(e) => handleQuantityChange(e.target.value)}
                    className="w-16 text-center py-2 text-sm font-bold text-[#00375e] focus:outline-none"
                  />
                  <button
                    type="button"
                    onClick={() => handleQuantityChange(quantity + 1)}
                    disabled={quantity >= MAX_QUANTITY}
                    className="px-3 py-2 bg-[#F7F8FA] hover:bg-[#eff4ff] text-[#00375e] font-bold text-sm transition-colors cursor-pointer disabled:opacity-40"
                  >
                    +
                  </button>
                </div>

                {/* Quick Presets */}
                <div className="flex items-center gap-1.5">
                  {[1, 5, 10, 20].map((num) => (
                    <button
                      key={num}
                      type="button"
                      onClick={() => setQuantity(num)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                        quantity === num
                          ? 'bg-[#00375e] text-white'
                          : 'bg-[#eff4ff] text-[#00375e] hover:bg-[#d1e4ff]'
                      }`}
                    >
                      {num}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Initial Status (Fixed as Good) */}
            <div>
              <label className="block text-xs font-semibold text-[#1C2330] mb-1.5">
                Trạng thái khởi tạo mặc định
              </label>
              <div className="p-3 bg-[#E8F5E9] border border-[#2E7D32]/30 rounded-xl flex items-center justify-between text-xs">
                <div className="flex items-center gap-2 text-[#2E7D32] font-semibold">
                  <span className="material-symbols-outlined text-[18px]">verified</span>
                  <span>Tốt (Good / Operational)</span>
                </div>
              </div>
            </div>

            {/* Tip Card */}
            <div className="p-3.5 bg-[#eff4ff] rounded-xl border border-[#d1e4ff] flex items-start gap-2.5 text-xs text-[#00375e]">
              <span className="material-symbols-outlined text-[20px] text-[#0e61a1] shrink-0">info</span>
              <div>
                <span className="font-semibold">Số thứ tự tự nối tiếp. </span>
                <span className="text-[#5B6472]">
                  STT bắt đầu từ số lớn nhất đang dùng cho cùng danh mục và vị trí, nên có thể khai báo thêm nhiều lần mà không trùng mã.
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Real-time Live Preview (5 cols) */}
        <div className="lg:col-span-5 bg-white rounded-2xl border border-[#DFE3E8] p-6 shadow-xs flex flex-col justify-between space-y-4">
          <div>
            <div className="flex items-center justify-between border-b border-[#DFE3E8] pb-3 mb-4">
              <div>
                <h2 className="font-bold text-sm text-[#00375e] flex items-center gap-1.5">
                  Danh Sách Mã Sẽ Tạo
                </h2>
                <span className="text-[11px] text-[#5B6472]">Bản xem trước theo thời gian thực</span>
              </div>
              <span className="px-2.5 py-0.5 bg-[#eff4ff] text-[#00375e] text-xs font-bold rounded-full">
                {previewCodes.length} mã
              </span>
            </div>

            {/* Scrollable list of generated codes */}
            <div className="space-y-2 max-h-[360px] overflow-y-auto pr-1">
              {previewCodes.length === 0 && (
                <div className="p-4 text-center text-xs text-[#5B6472]">
                  {codeBase ? 'Đang tính số thứ tự...' : 'Chọn danh mục và vị trí để xem trước mã.'}
                </div>
              )}
              {previewCodes.map((code) => (
                <div
                  key={code}
                  className="p-3 bg-[#F7F8FA] hover:bg-[#eff4ff] border border-[#DFE3E8] rounded-xl flex items-center justify-between transition-colors"
                >
                  <div className="flex items-center gap-2.5">
                    <div>
                      <div className="font-mono font-bold text-xs text-[#00375e]">{code}</div>
                      <div className="text-[10px] text-[#5B6472]">
                        {selectedCategory?.name} · {selectedPosition?.label.split(' (')[0]}
                      </div>
                    </div>
                  </div>
                  <span className="px-2 py-0.5 bg-[#E8F5E9] text-[#2E7D32] text-[10px] font-semibold rounded-full border border-[#2E7D32]/20">
                    Tốt (Good)
                  </span>
                </div>
              ))}
            </div>

            {/* Summary Confirmation Callout */}
            <div className="mt-4 p-3 bg-[#eff4ff]/70 rounded-xl border border-[#d1e4ff] text-xs space-y-1">
              <div className="font-semibold text-[#00375e] flex items-center justify-between">
                <span>Tổng số cá thể sẽ thêm vào hệ thống:</span>
                <span className="text-sm font-bold">{previewCodes.length} thiết bị</span>
              </div>
              <div className="text-[11px] text-[#5B6472]">
                Vị trí đích: <span className="font-medium text-[#1C2330]">{selectedPosition?.label || '—'}</span>
              </div>
            </div>
          </div>

          {/* Action Footer */}
          <div className="pt-3 border-t border-[#DFE3E8] flex items-center justify-between gap-3">
            <button
              onClick={onClose}
              disabled={isSubmitting}
              className="px-4 py-2.5 rounded-xl border border-[#DFE3E8] text-xs font-semibold text-[#5B6472] hover:bg-[#F7F8FA] transition-colors cursor-pointer"
              type="button"
            >
              Hủy bỏ
            </button>

            <button
              onClick={handleSubmit}
              disabled={!canSubmit}
              className="px-6 py-2.5 rounded-xl bg-[#00375e] hover:bg-[#1f4e78] text-white text-xs font-bold shadow-sm flex items-center gap-2 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              type="button"
            >
              {isSubmitting ? (
                <>
                  <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                  <span>Đang khởi tạo...</span>
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined text-[18px]">done_all</span>
                  <span>Xác nhận tạo {previewCodes.length} tài sản</span>
                </>
              )}
            </button>
          </div>
        </div>
      </div>
      </div>
      </div>
    </div>
  );
};
