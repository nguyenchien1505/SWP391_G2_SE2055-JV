import React, { useState } from 'react';
import { Modal } from '../common/Modal';
import { Button } from '../common/Button';

export const AddCatalogModal = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const [name, setName] = useState('');
  const [assetKind, setAssetKind] = useState('FIXED');
  const [purpose, setPurpose] = useState('GUEST_USE');
  const [unit, setUnit] = useState('');
  
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Vui lòng nhập Tên danh mục.');
      return;
    }

    if (assetKind === 'CONSUMABLE' && !unit.trim()) {
      setError('Vui lòng nhập Đơn vị tính cho vật tư tiêu hao.');
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await onSubmit({
        name: name.trim(),
        assetKind,
        purpose,
        unit: assetKind === 'FIXED' ? null : unit.trim(),
      });
      // Reset form
      setName('');
      setUnit('');
      onClose();
    } catch (err) {
      setError(err?.message || 'Có lỗi xảy ra khi lưu danh mục vào hệ thống.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Thêm Danh Mục Mới"
      subtitle="Danh mục này sẽ được áp dụng cho toàn bộ các chi nhánh."
      maxWidth="md"
      footer={
        <>
          <Button variant="outline" size="md" onClick={onClose} disabled={isSubmitting}>
            Hủy bỏ
          </Button>
          <Button
            variant="primary"
            size="md"
            onClick={handleSubmit}
            isLoading={isSubmitting}
          >
            Lưu danh mục
          </Button>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4 text-xs">
        {error && (
          <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded text-xs leading-relaxed">
            {error}
          </div>
        )}

        <div>
          <label className="block font-semibold text-[#1C2330] mb-1">
            Tên danh mục <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="VD: Smart Tivi 55 inch, Khăn tắm..."
            className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-[#1C2330] focus:border-[#00375E] focus:ring-1 focus:ring-[#00375E] outline-none"
            required
          />
        </div>

        <div>
          <label className="block font-semibold text-[#1C2330] mb-1">
            Loại tài sản <span className="text-red-500">*</span>
          </label>
          <select
            value={assetKind}
            onChange={(e) => setAssetKind(e.target.value)}
            className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-[#1C2330] focus:border-[#00375E] focus:ring-1 focus:ring-[#00375E] outline-none bg-white"
          >
            <option value="FIXED">Tài sản cố định (FIXED)</option>
            <option value="CONSUMABLE">Vật tư tiêu hao (CONSUMABLE)</option>
          </select>
        </div>

        <div>
          <label className="block font-semibold text-[#1C2330] mb-1">
            Mục đích sử dụng <span className="text-red-500">*</span>
          </label>
          <select
            value={purpose}
            onChange={(e) => setPurpose(e.target.value)}
            className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-[#1C2330] focus:border-[#00375E] focus:ring-1 focus:ring-[#00375E] outline-none bg-white"
          >
            <option value="GUEST_USE">Phục vụ khách (GUEST_USE)</option>
            <option value="FACILITY_MAINTENANCE">Bảo trì cơ sở (FACILITY_MAINTENANCE)</option>
          </select>
        </div>

        {assetKind === 'CONSUMABLE' && (
          <div>
            <label className="block font-semibold text-[#1C2330] mb-1">
              Đơn vị tính <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={unit}
              onChange={(e) => setUnit(e.target.value)}
              placeholder="VD: Cái, Lọ, Chai, Cuộn..."
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-[#1C2330] focus:border-[#00375E] focus:ring-1 focus:ring-[#00375E] outline-none"
              required={assetKind === 'CONSUMABLE'}
            />
          </div>
        )}
      </form>
    </Modal>
  );
};
