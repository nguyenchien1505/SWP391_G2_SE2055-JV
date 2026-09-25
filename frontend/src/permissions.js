/**
 * Quyền nghiệp vụ Manager tick cho từng nhân viên — khớp enum `StaffPermission` ở backend. Một
 * người có thể có nhiều quyền (Lễ tân + Dọn dẹp) hoặc không có quyền nào (chỉ quyền chung —
 * BR-PERM-06). Thêm quyền mới: thêm một dòng ở đây và ở backend.
 */
export const STAFF_PERMISSIONS = [
  { value: 'RECEPTION', label: 'Lễ tân', hint: 'Đặt / hủy đặt phòng, check-in, check-out' },
  { value: 'HOUSEKEEPING', label: 'Dọn dẹp', hint: 'Nhận việc dọn phòng, bấm hoàn thành' },
];

const LABEL = Object.fromEntries(STAFF_PERMISSIONS.map((p) => [p.value, p.label]));

/** Quyền tick sẵn khi chọn vị trí — theo Loại của vị trí; Loại "Khác" không kèm quyền nào. */
export function defaultPermissionsFor(positionType) {
  return STAFF_PERMISSIONS.some((p) => p.value === positionType) ? [positionType] : [];
}

/** "Lễ tân · Dọn dẹp"; rỗng thì "Chỉ quyền chung". */
export function permissionSummary(permissions) {
  const labels = STAFF_PERMISSIONS.filter((p) => (permissions ?? []).includes(p.value)).map((p) => p.label);
  return labels.length === 0 ? 'Chỉ quyền chung' : labels.join(' · ');
}

export function permissionLabel(value) {
  return LABEL[value] ?? value;
}

/** Người đang đăng nhập có quyền này không — đọc từ `/auth/me` (`permissions`). */
export function hasPermission(user, permission) {
  return (user?.permissions ?? []).includes(permission);
}
