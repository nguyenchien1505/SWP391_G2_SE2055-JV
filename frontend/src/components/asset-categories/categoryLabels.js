/** Nhãn hiển thị cho enum của backend (AssetKind, AssetPurpose). */
export const KIND_LABEL = {
  FIXED: 'Tài sản cố định',
  CONSUMABLE: 'Vật tư tiêu hao',
};

export const PURPOSE_LABEL = {
  GUEST_USE: 'Dùng cho khách',
  FACILITY_MAINTENANCE: 'Duy trì cơ sở',
};

export const UNIT_SUGGESTIONS = ['Chai', 'Chiếc', 'Bộ', 'Cuộn', 'Hộp', 'Can', 'Gói', 'Đôi'];

/** Backend không lưu biểu tượng — đoán theo tên để bảng dễ nhìn. */
export function categoryIcon(name, assetKind) {
  const lower = (name ?? '').toLowerCase();
  if (lower.includes('tv') || lower.includes('tivi')) return 'tv';
  if (lower.includes('điều hòa')) return 'ac_unit';
  if (lower.includes('tủ mát') || lower.includes('tủ lạnh') || lower.includes('minibar')) return 'kitchen';
  if (lower.includes('nóng lạnh')) return 'water_heater';
  if (lower.includes('nước')) return 'local_drink';
  if (lower.includes('khăn')) return 'dry_cleaning';
  if (lower.includes('hút bụi')) return 'cleaning_services';
  if (lower.includes('lau sàn') || lower.includes('xà phòng') || lower.includes('hóa chất')) return 'sanitizer';
  if (lower.includes('amenities') || lower.includes('dầu gội') || lower.includes('sữa tắm')) return 'shower';
  if (lower.includes('két')) return 'lock';
  if (lower.includes('giường') || lower.includes('ga ') || lower.includes('gối')) return 'bed';
  if (lower.includes('đèn')) return 'lightbulb';
  if (lower.includes('sấy')) return 'wind_power';
  if (lower.includes('ấm') || lower.includes('cà phê')) return 'coffee_maker';
  if (lower.includes('bàn là') || lower.includes('bàn ủi')) return 'iron';
  if (lower.includes('giấy')) return 'receipt_long';
  if (lower.includes('dép')) return 'footprint';
  return assetKind === 'FIXED' ? 'devices' : 'inventory';
}
