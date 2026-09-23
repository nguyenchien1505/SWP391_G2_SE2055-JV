import React, { useState, useMemo } from 'react';
import { assetService } from '../../services/assetApi';

const CATEGORIES = [
  { value: 'AC', name: 'Điều hòa nhiệt độ (AC)', prefix: 'TS-AC', purpose: 'GUEST_USE' },
  { value: 'TV', name: 'Smart TV màn hình phẳng (TV)', prefix: 'TS-TV', purpose: 'GUEST_USE' },
  { value: 'RF', name: 'Tủ lạnh mini quầy bar (RF)', prefix: 'TS-RF', purpose: 'GUEST_USE' },
  { value: 'SF', name: 'Két sắt mini điện tử (SF)', prefix: 'TS-SF', purpose: 'GUEST_USE' },
  { value: 'WH', name: 'Máy nước nóng gián tiếp (WH)', prefix: 'TS-WH', purpose: 'GUEST_USE' },
  { value: 'HD', name: 'Máy sấy tóc ion cao cấp (HD)', prefix: 'TS-HD', purpose: 'GUEST_USE' },
  { value: 'CS', name: 'Điều hòa âm trần Cassette', prefix: 'TS-CS', purpose: 'FACILITY_MAINTENANCE' },
  { value: 'ST', name: 'Điều hòa tủ đứng công suất lớn', prefix: 'TS-ST', purpose: 'FACILITY_MAINTENANCE' },
  { value: 'PC', name: 'Máy tính để bàn lễ tân', prefix: 'TS-PC', purpose: 'INTERNAL_OPS' }
];

const getPurposeLabel = (purpose) => {
  switch (purpose) {
    case 'GUEST_USE': return 'Phục vụ khách hàng';
    case 'FACILITY_MAINTENANCE': return 'Duy trì & Vận hành cơ sở';
    case 'INTERNAL_OPS': return 'Nội bộ';
    default: return purpose;
  }
};

const ROOM_OPTIONS = [
  { code: '101', label: 'Phòng 101 (Standard King · Tầng 1)' },
  { code: '104', label: 'Phòng 104 (Standard Đơn · Tầng 1)' },
  { code: '205', label: 'Phòng 205 (Superior Đôi · Tầng 2)' },
  { code: '310', label: 'Phòng 310 (Deluxe Hướng Biển · Tầng 3)' },
  { code: '312', label: 'Phòng 312 (Deluxe Twin · Tầng 3)' },
  { code: '405', label: 'Phòng 405 (Superior Đơn · Tầng 4)' },
  { code: '502', label: 'Phòng 502 (Executive Suite · Tầng 5)' }
];

const AREA_OPTIONS = [
  { code: 'LBY', label: 'Sảnh chính (Lobby - Tầng 1)' },
  { code: 'RST', label: 'Nhà hàng Sao Mai (Tầng 2)' },
  { code: 'GYM', label: 'Phòng Gym & Spa (Tầng M)' },
  { code: 'BSM', label: 'Kho tổng tầng hầm (Kho lưu trữ)' }
];

export const BatchCreateAssetsScreen = ({ onNavigate, onSelectAsset }) => {
  const [selectedCategoryValue, setSelectedCategoryValue] = useState('TV');
  const [positionType, setPositionType] = useState('ROOM'); // 'ROOM' | 'AREA'
  const [selectedPositionCode, setSelectedPositionCode] = useState('310');
  const [quantity, setQuantity] = useState(5);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successResult, setSuccessResult] = useState(null);

  const selectedCategory = useMemo(() => {
    return CATEGORIES.find((c) => c.value === selectedCategoryValue) || CATEGORIES[0];
  }, [selectedCategoryValue]);

  const currentPositionList = positionType === 'ROOM' ? ROOM_OPTIONS : AREA_OPTIONS;

  const selectedPosition = useMemo(() => {
    return currentPositionList.find((p) => p.code === selectedPositionCode) || currentPositionList[0];
  }, [currentPositionList, selectedPositionCode]);

  // Generate live preview code list
  const previewCodes = useMemo(() => {
    const list = [];
    const count = Math.min(Math.max(1, quantity), 50);
    for (let i = 1; i <= count; i++) {
      const seq = String(i).padStart(3, '0');
      const fullCode = `${selectedCategory.prefix}-${selectedPosition.code}-${seq}`;
      list.push(fullCode);
    }
    return list;
  }, [selectedCategory, selectedPosition, quantity]);

  const handleQuantityChange = (newVal) => {
    const val = parseInt(newVal, 10);
    if (isNaN(val)) return;
    setQuantity(Math.min(Math.max(1, val), 50));
  };

  const handlePositionTypeSwitch = (type) => {
    setPositionType(type);
    if (type === 'ROOM') {
      setSelectedPositionCode(ROOM_OPTIONS[0].code);
    } else {
      setSelectedPositionCode(AREA_OPTIONS[0].code);
    }
  };

  const handleSubmit = async () => {
    setIsSubmitting(true);
    try {
      const res = await assetService.createBatchAssets({
        category: selectedCategory,
        positionType,
        position: selectedPosition,
        quantity: previewCodes.length,
        status: 'Good'
      });
      setSuccessResult(res);
    } catch (e) {
      console.error('Batch creation failed', e);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      {/* Top Banner */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3 bg-white p-5 rounded-2xl border border-[#DFE3E8] shadow-[0_1px_8px_rgba(0,0,0,0.02)]">
        <div>
          <div className="flex items-center gap-2 text-xs text-[#5B6472] mb-1">
            <span className="font-semibold text-[#00375e]">TÀI SẢN VẬT TƯ</span>
            <span>/</span>
            <span className="text-[#0e61a1]">TÀI SẢN CÁ THỂ</span>
            <span>/</span>
            <span className="text-[#1C2330]">Khai Báo Hàng Loạt</span>
          </div>
          <h1 className="text-xl font-bold text-[#00375e] tracking-tight">
            Khai Báo Tài Sản Cố Định Hàng Loạt
          </h1>
          <p className="text-xs text-[#5B6472] mt-0.5">
            Tự động sinh mã định danh duy nhất theo cấu trúc chuẩn: <code className="font-mono text-[#00375e] font-bold">[Mã-DM]-[Vị-Trí]-[STT]</code>
          </p>
        </div>

        <div className="flex items-center gap-2">
          <span className="text-xs text-[#5B6472]">Chi nhánh phiên:</span>
          <span className="px-3 py-1 bg-[#eff4ff] text-[#00375e] font-bold text-xs rounded-xl border border-[#d1e4ff]">
            Sao Mai Nha Trang
          </span>
        </div>
      </div>

      {/* Success Modal Notification */}
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
                Các mã từ <span className="font-mono font-bold">{previewCodes[0]}</span> đến{' '}
                <span className="font-mono font-bold">{previewCodes[previewCodes.length - 1]}</span> đã sẵn sàng trong cơ sở dữ liệu.
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => onNavigate('fixed-assets')}
              className="px-4 py-2 bg-[#2E7D32] text-white text-xs font-semibold rounded-xl hover:bg-[#1b5e20] transition-colors cursor-pointer shadow-xs"
              type="button"
            >
              Xem danh sách tài sản
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
            <span className="text-xs text-[#5B6472]">Quy tắc tự động hóa v2.4</span>
          </div>

          {/* Form Fields */}
          <div className="space-y-4">
            {/* Category Select */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-xs font-semibold text-[#1C2330]">Danh mục tài sản (*)</label>
                <span className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${
                  selectedCategory.purpose === 'GUEST_USE'
                    ? 'bg-[#eff4ff] text-[#00375e]'
                    : 'bg-[#F7F8FA] text-[#5B6472] border border-[#DFE3E8]'
                }`}>
                  Mục đích: {getPurposeLabel(selectedCategory.purpose)}
                </span>
              </div>
              <select
                value={selectedCategoryValue}
                onChange={(e) => setSelectedCategoryValue(e.target.value)}
                className="w-full p-2.5 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl text-xs text-[#1C2330] font-medium focus:outline-none focus:border-[#0e61a1] cursor-pointer"
              >
                <optgroup label="Phục vụ khách hàng">
                  {CATEGORIES.filter(c => c.purpose === 'GUEST_USE').map((cat) => (
                    <option key={cat.value} value={cat.value}>
                      {cat.name} — Prefix: [{cat.prefix}]
                    </option>
                  ))}
                </optgroup>
                <optgroup label="Duy trì & Vận hành cơ sở">
                  {CATEGORIES.filter(c => c.purpose === 'FACILITY_MAINTENANCE').map((cat) => (
                    <option key={cat.value} value={cat.value}>
                      {cat.name} — Prefix: [{cat.prefix}]
                    </option>
                  ))}
                </optgroup>
                <optgroup label="Nội bộ">
                  {CATEGORIES.filter(c => c.purpose === 'INTERNAL_OPS').map((cat) => (
                    <option key={cat.value} value={cat.value}>
                      {cat.name} — Prefix: [{cat.prefix}]
                    </option>
                  ))}
                </optgroup>
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
                    <div className="text-[10px] text-[#72777f]">Buồng khách Deluxe/Suite</div>
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
                value={selectedPositionCode}
                onChange={(e) => setSelectedPositionCode(e.target.value)}
                className="w-full p-2.5 bg-[#F7F8FA] border border-[#DFE3E8] rounded-xl text-xs text-[#1C2330] font-medium focus:outline-none focus:border-[#0e61a1] cursor-pointer"
              >
                {currentPositionList.map((pos) => (
                  <option key={pos.code} value={pos.code}>
                    {pos.label} — Mã vị trí: [{pos.code}]
                  </option>
                ))}
              </select>
            </div>

            {/* Quantity Stepper & Presets */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-xs font-semibold text-[#1C2330]">Số lượng cần tạo (*)</label>
                <span className="text-[11px] text-[#5B6472]">Tối đa 50 thiết bị / lần khởi tạo</span>
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
                    max="50"
                    value={quantity}
                    onChange={(e) => handleQuantityChange(e.target.value)}
                    className="w-16 text-center py-2 text-sm font-bold text-[#00375e] focus:outline-none"
                  />
                  <button
                    type="button"
                    onClick={() => handleQuantityChange(quantity + 1)}
                    disabled={quantity >= 50}
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
                <span className="text-[11px] text-[#2E7D32]/80">Quy tắc chuẩn hóa 100%</span>
              </div>
            </div>

            {/* Tip Card */}
            <div className="p-3.5 bg-[#eff4ff] rounded-xl border border-[#d1e4ff] flex items-start gap-2.5 text-xs text-[#00375e]">
              <span className="material-symbols-outlined text-[20px] text-[#0e61a1] shrink-0">info</span>
              <div>
                <span className="font-semibold">Cần phân bổ vào nhiều phòng cùng lúc? </span>
                <span className="text-[#5B6472]">
                  Tính năng khai báo này tự động tạo liên tiếp từ STT 001 đến {String(quantity).padStart(3, '0')}. Hệ thống sẽ gán tự động mã QR tương ứng để in tem nhãn dán thiết bị.
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
                  <span className="material-symbols-outlined text-[18px] text-[#0e61a1]">qr_code_2</span>
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
              {previewCodes.map((code, idx) => (
                <div
                  key={code}
                  className="p-3 bg-[#F7F8FA] hover:bg-[#eff4ff] border border-[#DFE3E8] rounded-xl flex items-center justify-between transition-colors"
                >
                  <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-lg bg-white border border-[#DFE3E8] flex items-center justify-center text-[#00375e] shadow-xs">
                      <span className="material-symbols-outlined text-[18px]">qr_code</span>
                    </div>
                    <div>
                      <div className="font-mono font-bold text-xs text-[#00375e]">{code}</div>
                      <div className="text-[10px] text-[#5B6472]">
                        {selectedCategory.name} · {selectedPosition.label.split(' (')[0]}
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
                Vị trí đích: <span className="font-medium text-[#1C2330]">{selectedPosition.label}</span>
              </div>
            </div>
          </div>

          {/* Action Footer */}
          <div className="pt-3 border-t border-[#DFE3E8] flex items-center justify-between gap-3">
            <button
              onClick={() => onNavigate('fixed-assets')}
              className="px-4 py-2.5 rounded-xl border border-[#DFE3E8] text-xs font-semibold text-[#5B6472] hover:bg-[#F7F8FA] transition-colors cursor-pointer"
              type="button"
            >
              Hủy bỏ
            </button>

            <button
              onClick={handleSubmit}
              disabled={isSubmitting}
              className="px-6 py-2.5 rounded-xl bg-[#00375e] hover:bg-[#1f4e78] text-white text-xs font-bold shadow-sm flex items-center gap-2 transition-colors cursor-pointer disabled:opacity-50"
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
  );
}
