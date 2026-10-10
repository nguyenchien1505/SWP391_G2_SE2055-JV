import { useState } from 'react';
import { categoryIcon, KIND_LABEL, PURPOSE_LABEL, UNIT_SUGGESTIONS } from './categoryLabels';

const NAME_MAX = 100; // CreateAssetCategoryRequest.name @Size(max = 100)
const UNIT_MAX = 20; // CreateAssetCategoryRequest.unit @Size(max = 20)

const EMPTY_FORM = { id: null, name: '', assetKind: 'FIXED', purpose: 'GUEST_USE', unit: '', active: true };

function toForm(category) {
  return category
    ? {
        id: category.id,
        name: category.name ?? '',
        assetKind: category.assetKind,
        purpose: category.purpose,
        unit: category.unit ?? '',
        active: category.active,
      }
    : EMPTY_FORM;
}

/**
 * Màn khai báo / chỉnh sửa danh mục kèm bản xem trước. Loại tài sản chỉ chọn được khi tạo mới:
 * backend coi `assetKind` là bất biến vì tài sản cá thể / tồn kho đã trỏ vào danh mục.
 *
 * `onSave(form, isEdit)` ném lỗi (thông điệp tiếng Việt) nếu backend từ chối.
 */
export default function CategoryFormView({ initialData, categoriesList, onSave, onCancel }) {
  const [mode, setMode] = useState(initialData ? 'edit' : 'create');
  const [form, setForm] = useState(() => toForm(initialData));
  const [errorMsg, setErrorMsg] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const isEdit = mode === 'edit';
  const isFixed = form.assetKind === 'FIXED';
  const set = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));

  function switchMode(next) {
    setMode(next);
    setErrorMsg('');
    setForm(next === 'create' ? EMPTY_FORM : toForm(initialData ?? categoriesList[0]));
  }

  function selectToEdit(id) {
    setForm(toForm(categoriesList.find((c) => c.id === id)));
    setErrorMsg('');
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!form.name.trim()) {
      setErrorMsg('Vui lòng nhập tên danh mục chuẩn hóa');
      return;
    }
    if (!isFixed && !form.unit.trim()) {
      setErrorMsg('Vui lòng chọn hoặc nhập đơn vị tính cho vật tư tiêu hao');
      return;
    }

    setErrorMsg('');
    setSubmitting(true);
    try {
      await onSave(
        { ...form, name: form.name.trim(), unit: isFixed ? null : form.unit.trim() },
        isEdit,
      );
    } catch (err) {
      setErrorMsg(err.message || 'Có lỗi xảy ra khi lưu danh mục');
    } finally {
      setSubmitting(false);
    }
  }

  const kindCard = (kind, icon, title, desc, footer, accent) => {
    const selected = form.assetKind === kind;
    const locked = isEdit && !selected;
    return (
      <div
        onClick={() => !isEdit && set('assetKind', kind)}
        className={`relative flex flex-col p-4 rounded-xl transition-all duration-200 border-2 ${
          isEdit ? 'cursor-not-allowed' : 'cursor-pointer'
        } ${
          selected
            ? `bg-[#eff4ff] shadow-sm ${accent.border}`
            : `bg-white border-[#DFE3E8] ${locked ? 'opacity-40' : 'opacity-80 hover:opacity-100 hover:border-gray-300'}`
        }`}
      >
        <div className="flex items-start justify-between">
          <div
            className={`w-9 h-9 rounded-lg flex items-center justify-center ${
              selected ? `${accent.solid} text-white` : accent.soft
            }`}
          >
            <span className="material-symbols-outlined text-[20px]">{icon}</span>
          </div>
          <span className={`material-symbols-outlined text-[22px] ${selected ? accent.text : 'text-gray-300'}`}>
            {selected ? 'check_circle' : 'radio_button_unchecked'}
          </span>
        </div>
        <div className="mt-3 font-bold text-sm text-[#1C2330]">{title}</div>
        <div className="mt-1 text-xs text-[#5B6472] leading-snug">{desc}</div>
        <div className={`mt-2.5 inline-flex items-center gap-1 text-[11px] font-bold uppercase tracking-wider ${accent.text}`}>
          {footer}
        </div>
      </div>
    );
  };

  const purposeOption = (value, icon, title, desc) => (
    <label
      className={`flex items-center gap-3 p-3 rounded-lg cursor-pointer transition-all border ${
        form.purpose === value ? 'bg-[#eff4ff] border-[#0e61a1]/50' : 'bg-white border-[#DFE3E8] hover:bg-gray-50'
      }`}
    >
      <input
        type="radio"
        name="purpose"
        checked={form.purpose === value}
        onChange={() => set('purpose', value)}
        className="accent-[#1f4e78] w-4 h-4 cursor-pointer"
      />
      <div className="flex flex-col">
        <span className="font-bold text-xs sm:text-sm text-[#1C2330] flex items-center gap-1.5">
          <span className="material-symbols-outlined text-[17px] text-[#0e61a1]">{icon}</span>
          {title}
        </span>
        <span className="text-[11px] text-[#5B6472]">{desc}</span>
      </div>
    </label>
  );

  return (
    <div className="flex flex-col w-full pb-10">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-6 border-b border-[#DFE3E8]/80 mb-6">
        <div className="flex items-center gap-1.5 text-[#5B6472] text-xs sm:text-sm font-medium">
          <button type="button" onClick={onCancel} className="hover:text-[#0e61a1] flex items-center gap-1 transition-colors">
            <span className="material-symbols-outlined text-[18px]">domain</span>
            <span>Cấu hình Tenant</span>
          </button>
          <span className="material-symbols-outlined text-[16px] text-gray-400">chevron_right</span>
          <button type="button" onClick={onCancel} className="hover:text-[#0e61a1] transition-colors">
            Danh mục tài sản
          </button>
          <span className="material-symbols-outlined text-[16px] text-gray-400">chevron_right</span>
          <span className="text-[#1C2330] font-bold">{isEdit ? 'Chỉnh sửa danh mục' : 'Khai báo danh mục'}</span>
        </div>
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#e0e9f9] text-[#0e61a1] text-xs font-semibold self-start sm:self-auto">
          <span className="material-symbols-outlined text-[14px]">shield_person</span>
          Vai trò: Giám đốc điều hành
        </span>
      </div>

      <div className="bg-[#eff4ff] p-1.5 rounded-xl inline-flex items-center gap-1 mb-6 shadow-sm border border-[#d0e4ff]/80 self-start">
        {[
          ['create', 'add_circle', 'Thêm danh mục mới'],
          ['edit', 'edit_note', 'Chỉnh sửa danh mục có sẵn'],
        ].map(([value, icon, label]) => (
          <button
            key={value}
            type="button"
            onClick={() => switchMode(value)}
            disabled={value === 'edit' && categoriesList.length === 0}
            className={`px-4 py-2 rounded-lg text-sm font-semibold transition-all flex items-center gap-2 disabled:opacity-40 ${
              mode === value ? 'text-white bg-[#1f4e78] shadow-sm' : 'text-[#5B6472] hover:text-[#1C2330] hover:bg-white/60'
            }`}
          >
            <span className="material-symbols-outlined text-[18px]">{icon}</span>
            <span>{label}</span>
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <div className="lg:col-span-7 bg-white rounded-xl p-5 sm:p-7 shadow-sm border border-[#DFE3E8]/80 flex flex-col gap-6">
          <div>
            <span className="px-2.5 py-0.5 rounded bg-[#d0e4ff] text-[#001d35] text-[11px] font-bold uppercase tracking-wider">
              Cấp Tenant toàn hệ thống
            </span>
            <h1 className="text-xl sm:text-2xl font-bold text-[#1C2330] tracking-tight mt-1.5">
              {isEdit ? 'Chỉnh Sửa Danh Mục Tài Sản & Vật Tư' : 'Thêm Danh Mục Tài Sản & Vật Tư Mới'}
            </h1>
            <p className="text-xs sm:text-sm text-[#5B6472] mt-1">
              Thiết lập chuẩn danh mục tài sản áp dụng chung cho mọi khách sạn trong chuỗi.
            </p>
          </div>

          {isEdit && categoriesList.length > 0 && (
            <div className="p-3 rounded-lg bg-[#eff4ff] border border-[#d0e4ff]">
              <label className="text-xs font-semibold text-[#00375e] block mb-1">Chọn danh mục cần chỉnh sửa:</label>
              <select
                value={form.id ?? ''}
                onChange={(e) => selectToEdit(e.target.value)}
                className="w-full bg-white border border-[#DFE3E8] rounded-lg px-3 py-2 text-sm text-[#1C2330] font-medium outline-none focus:border-[#0e61a1]"
              >
                {categoriesList.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name} ({KIND_LABEL[cat.assetKind]})
                  </option>
                ))}
              </select>
            </div>
          )}

          {errorMsg && (
            <div className="p-3 rounded-lg bg-red-50 text-[#D32F2F] text-xs font-medium border border-red-200 flex items-center gap-2">
              <span className="material-symbols-outlined text-base">error</span>
              <span>{errorMsg}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="category-name" className="font-bold text-sm text-[#1C2330] flex items-center justify-between">
                <span>
                  Tên danh mục chuẩn hóa <span className="text-[#D32F2F]">*</span>
                </span>
                <span className="text-xs text-[#5B6472] font-normal">
                  {form.name.length}/{NAME_MAX} ký tự
                </span>
              </label>
              <div className="relative">
                <input
                  id="category-name"
                  type="text"
                  maxLength={NAME_MAX}
                  value={form.name}
                  onChange={(e) => set('name', e.target.value)}
                  placeholder="Nhập tên danh mục chuẩn (VD: Smart TV 55 inch 4K Ultra HD)..."
                  className="w-full h-11 px-4 pr-10 rounded-lg bg-white border border-[#DFE3E8] text-[#1C2330] text-sm shadow-sm outline-none focus:border-[#0e61a1] focus:ring-1 focus:ring-[#0e61a1] transition-all"
                />
                <span className="absolute right-3 top-3 material-symbols-outlined text-gray-400 text-[20px] pointer-events-none">
                  style
                </span>
              </div>
              <p className="text-xs text-[#5B6472]">
                Tên danh mục nên rõ quy cách, kích thước hoặc chủng loại để tránh nhầm lẫn khi chi nhánh kiểm kê.
              </p>
            </div>

            <div className="flex flex-col gap-2">
              <label className="font-bold text-sm text-[#1C2330] flex items-center justify-between">
                <span>
                  Loại tài sản (Asset Type) <span className="text-[#D32F2F]">*</span>
                </span>
                {isEdit && (
                  <span className="text-xs text-[#5B6472] font-normal flex items-center gap-1">
                    <span className="material-symbols-outlined text-[15px]">lock</span>
                    Không đổi được sau khi tạo
                  </span>
                )}
              </label>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {kindCard(
                  'FIXED',
                  'devices',
                  'Tài sản cố định (Fixed Asset)',
                  'Quản lý theo từng cá thể có mã định danh riêng biệt (Tivi, điều hòa, tủ lạnh mini, két sắt, máy pha cà phê).',
                  <>
                    <span className="material-symbols-outlined text-[13px]">tag</span> Theo dõi cá thể (Serialized)
                  </>,
                  { border: 'border-[#0e61a1]', solid: 'bg-[#1f4e78]', soft: 'bg-[#e0e9f9] text-[#0e61a1]', text: 'text-[#0e61a1]' },
                )}
                {kindCard(
                  'CONSUMABLE',
                  'inventory',
                  'Vật tư tiêu hao (Consumable)',
                  'Quản lý theo số lượng tồn kho tổng, xuất kho và kiểm kê định kỳ (Nước suối, khăn tắm, xà phòng, hóa chất).',
                  <>
                    <span className="material-symbols-outlined text-[13px]">analytics</span> Theo dõi tồn kho định mức
                  </>,
                  { border: 'border-[#2E7D32]', solid: 'bg-[#2E7D32]', soft: 'bg-[#E8F5E9] text-[#2E7D32]', text: 'text-[#2E7D32]' },
                )}
              </div>
            </div>

            <div className="flex flex-col gap-2">
              <label className="font-bold text-sm text-[#1C2330]">
                Mục đích sử dụng (Purpose) <span className="text-[#D32F2F]">*</span>
              </label>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {purposeOption('GUEST_USE', 'hotel', 'Dùng cho khách (Guest Use)', 'Trực tiếp trong phòng khách & dịch vụ phục vụ khách.')}
                {purposeOption(
                  'FACILITY_MAINTENANCE',
                  'engineering',
                  'Duy trì cơ sở (Facility Maintenance)',
                  'Thiết bị kỹ thuật, đồ dùng buồng phòng & vệ sinh chung.',
                )}
              </div>
            </div>

            <div className="flex flex-col gap-2 p-4 rounded-xl bg-[#eff4ff] border border-[#d0e4ff]">
              <div className="flex items-center justify-between">
                <label htmlFor="category-unit" className="font-bold text-sm text-[#1C2330] flex items-center gap-2">
                  <span>Đơn vị tính (Unit)</span>
                  {!isFixed && (
                    <span className="px-1.5 py-0.5 rounded bg-red-100 text-[#D32F2F] text-[11px] font-bold">Bắt buộc</span>
                  )}
                </label>
                {isFixed && (
                  <span className="text-xs text-[#5B6472] flex items-center gap-1 font-medium">
                    <span className="material-symbols-outlined text-[15px]">lock</span>
                    Không áp dụng cho Tài sản cố định
                  </span>
                )}
              </div>

              {isFixed ? (
                <div className="flex items-start gap-2.5 p-3 rounded-lg bg-white/80 border border-[#d0e4ff] text-[#5B6472]">
                  <span className="material-symbols-outlined text-[#0e61a1] text-[18px] shrink-0 mt-0.5">info</span>
                  <p className="text-xs leading-relaxed">
                    <strong className="text-[#1C2330]">Tài sản cố định</strong> được quản lý theo từng cá thể độc lập (mã
                    tài sản riêng), nên không có đơn vị tính.
                  </p>
                </div>
              ) : (
                <div className="flex flex-col gap-2 mt-1">
                  <input
                    id="category-unit"
                    type="text"
                    maxLength={UNIT_MAX}
                    value={form.unit}
                    onChange={(e) => set('unit', e.target.value)}
                    placeholder="Ví dụ: Chai, Chiếc, Bộ, Cuộn, Can, Gói, Hộp..."
                    className="w-full h-11 px-4 rounded-lg bg-white border border-[#DFE3E8] text-[#1C2330] text-sm shadow-sm outline-none focus:border-[#0e61a1]"
                  />
                  <div className="flex flex-wrap items-center gap-1.5 pt-1">
                    <span className="text-xs text-[#5B6472] mr-1">Gợi ý phổ biến:</span>
                    {UNIT_SUGGESTIONS.map((u) => (
                      <button
                        key={u}
                        type="button"
                        onClick={() => set('unit', u)}
                        className={`px-2.5 py-0.5 rounded-full text-xs transition-colors border ${
                          form.unit === u
                            ? 'bg-[#0e61a1] text-white border-[#0e61a1]'
                            : 'bg-white text-[#5B6472] hover:bg-[#0e61a1] hover:text-white border-[#DFE3E8]'
                        }`}
                      >
                        {u}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <div className="flex items-center justify-between p-4 rounded-xl bg-[#eff4ff] border border-[#d0e4ff] shadow-sm">
              <div className="flex items-start gap-3">
                <div
                  className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${
                    form.active ? 'bg-[#E8F5E9] text-[#2E7D32]' : 'bg-gray-200 text-[#5B6472]'
                  }`}
                >
                  <span className="material-symbols-outlined text-[20px]">{form.active ? 'check_circle' : 'pause_circle'}</span>
                </div>
                <div>
                  <span className="font-bold text-sm text-[#1C2330] block">Kích hoạt áp dụng ngay (Active)</span>
                  <span className="text-xs text-[#5B6472] block mt-0.5">
                    Danh mục kích hoạt sẽ hiển thị cho các Quản lý chi nhánh lựa chọn khi tạo tài sản mới.
                  </span>
                </div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={form.active}
                onClick={() => set('active', !form.active)}
                className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full transition-colors duration-200 focus:outline-none ${
                  form.active ? 'bg-[#1f4e78]' : 'bg-gray-300'
                }`}
              >
                <span
                  className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-sm transition duration-200 mt-0.5 ml-0.5 ${
                    form.active ? 'translate-x-5' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            <div className="flex items-center justify-between pt-4 border-t border-[#DFE3E8]">
              <button
                type="button"
                onClick={onCancel}
                className="h-10 px-5 rounded-lg text-[#1C2330] hover:bg-gray-100 transition-colors font-bold text-sm flex items-center gap-1.5"
              >
                <span className="material-symbols-outlined text-[18px]">close</span>
                <span>Hủy bỏ</span>
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="h-10 px-6 rounded-lg bg-[#1f4e78] text-white hover:bg-[#00375e] transition-all shadow-sm font-bold text-sm flex items-center gap-2 disabled:opacity-50"
              >
                <span className="material-symbols-outlined text-[18px]">done_all</span>
                <span>{submitting ? 'Đang lưu...' : 'Lưu & Áp dụng toàn chuỗi'}</span>
              </button>
            </div>
          </form>
        </div>

        <div className="lg:col-span-5 flex flex-col gap-6">
          <div className="bg-white rounded-xl p-5 sm:p-6 shadow-sm border border-[#DFE3E8]/80">
            <div className="flex items-center justify-between pb-2 mb-3 border-b border-gray-100">
              <div className="flex items-center gap-2">
                <span className="relative flex h-2.5 w-2.5">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#2E7D32] opacity-75" />
                  <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-[#2E7D32]" />
                </span>
                <span className="font-bold text-sm text-[#1C2330]">Bản xem trước hiển thị (Live Preview)</span>
              </div>
              <span className="text-[11px] font-semibold text-[#5B6472] bg-[#eff4ff] px-2 py-0.5 rounded border border-[#d0e4ff]">
                Tự động cập nhật
              </span>
            </div>
            <p className="text-xs text-[#5B6472] mb-4">
              Danh mục sau khi lưu sẽ hiển thị như sau trên bảng danh sách của Giám đốc và màn hình tạo tài sản của Quản lý
              chi nhánh:
            </p>

            <div className="bg-[#f8f9ff] rounded-xl p-4 shadow-sm border border-[#d0e4ff] space-y-4">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-3 min-w-0">
                  <div className="w-10 h-10 rounded-lg bg-[#1f4e78] text-white flex items-center justify-center shrink-0 shadow-sm">
                    <span className="material-symbols-outlined text-[22px]">{categoryIcon(form.name, form.assetKind)}</span>
                  </div>
                  <div className="min-w-0">
                    <h3 className="font-bold text-sm text-[#1C2330] leading-tight line-clamp-2 break-words">
                      {form.name.trim() || 'Tên danh mục chưa đặt'}
                    </h3>
                    <div className="flex flex-wrap items-center gap-1.5 mt-1.5">
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-[#d0e4ff] text-[#001d35] uppercase">
                        {KIND_LABEL[form.assetKind]}
                      </span>
                      {!isFixed && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-[#e5eeff] text-[#5B6472]">
                          ĐVT: {form.unit || '—'}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
                <span
                  className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold shrink-0 ${
                    form.active ? 'bg-[#E8F5E9] text-[#2E7D32]' : 'bg-gray-200 text-[#5B6472]'
                  }`}
                >
                  <span className="material-symbols-outlined text-[13px]">{form.active ? 'check_circle' : 'pause_circle'}</span>
                  <span>{form.active ? 'Hoạt động' : 'Tạm ngưng'}</span>
                </span>
              </div>

              <div className="grid grid-cols-2 gap-2 pt-1 text-xs">
                <div className="p-2.5 rounded-lg bg-white border border-[#DFE3E8]">
                  <span className="text-[#5B6472] block font-medium">Mục đích sử dụng</span>
                  <span className="font-bold text-[#1C2330] flex items-center gap-1 mt-0.5 text-xs">
                    <span className="material-symbols-outlined text-[15px] text-[#0e61a1]">
                      {form.purpose === 'GUEST_USE' ? 'hotel' : 'engineering'}
                    </span>
                    {PURPOSE_LABEL[form.purpose]}
                  </span>
                </div>
                <div className="p-2.5 rounded-lg bg-white border border-[#DFE3E8]">
                  <span className="text-[#5B6472] block font-medium">Phạm vi áp dụng</span>
                  <span className="font-bold text-[#0e61a1] flex items-center gap-1 mt-0.5 text-xs">
                    <span className="material-symbols-outlined text-[15px]">corporate_fare</span>
                    Toàn chuỗi
                  </span>
                </div>
              </div>
            </div>

            <div className="mt-4 p-3 rounded-lg bg-[#eff4ff] border border-[#d0e4ff]">
              <div className="flex items-center justify-between text-xs mb-1.5">
                <span className="font-bold text-[#5B6472] flex items-center gap-1">
                  <span className="material-symbols-outlined text-[14px] text-[#0e61a1]">visibility</span>
                  Góc nhìn Quản lý chi nhánh
                </span>
                <span
                  className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                    form.active ? 'text-[#2E7D32] bg-[#E8F5E9]' : 'text-[#EF6C00] bg-[#fff3e0]'
                  }`}
                >
                  {form.active ? 'Khả dụng' : 'Khóa tạo mới'}
                </span>
              </div>
              <div className="h-9 px-3 rounded bg-white border border-[#DFE3E8] flex items-center justify-between text-xs text-[#1C2330]">
                <span className="flex items-center gap-1.5 truncate">
                  <span className="material-symbols-outlined text-[#0e61a1] text-[16px]">subdirectory_arrow_right</span>
                  <span className="truncate font-medium">
                    {form.name || 'Chưa đặt tên'} ({KIND_LABEL[form.assetKind]})
                  </span>
                </span>
                <span className={`text-[11px] font-semibold shrink-0 ${form.active ? 'text-[#0e61a1]' : 'text-gray-400'}`}>
                  + Tạo tài sản
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
