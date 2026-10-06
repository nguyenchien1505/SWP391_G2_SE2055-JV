import { STAFF_PERMISSIONS, permissionSummary } from '../../permissions';

/**
 * Người trên lịch làm việc: sắp xếp, mô tả, và lọc theo quyền nghiệp vụ.
 *
 * Chỉ NHÂN VIÊN có ca — quản lý khách sạn không có ca làm việc (chốt 05/10/2026). Quyền Lễ tân / Dọn
 * dẹp do Manager tick cho TỪNG nhân viên (THAY_DOI_BR — thay cho "quyền theo Loại Position"), một người
 * có thể có cả hai hoặc không có quyền nào.
 */

const GENERAL = 'GENERAL';
const RECEPTION = 'RECEPTION';

/** Tất cả / Lễ tân / Dọn dẹp / Chỉ quyền chung — thêm quyền mới ở permissions.js là tự có thêm bộ lọc. */
export const PERMISSION_FILTERS = [
  { value: 'ALL', label: 'Tất cả' },
  ...STAFF_PERMISSIONS.map(({ value, label }) => ({ value, label })),
  { value: GENERAL, label: 'Chỉ quyền chung' },
];

/** Nhân viên đa quyền khớp MỌI quyền họ có. */
export function matchesPermissionFilter(person, filter) {
  const permissions = person.permissions ?? [];
  if (filter === 'ALL') return true;
  if (filter === GENERAL) return permissions.length === 0;
  return permissions.includes(filter);
}

/** Có quyền Lễ tân (kể cả người kiêm Dọn dẹp). Người không tra được (đã chuyển đi) coi như không. */
export const isReceptionist = (person) => (person?.permissions ?? []).includes(RECEPTION);

/** Trong nhóm người này có ít nhất một lễ tân không — quy tắc mỗi ca theo mẫu phải có lễ tân. */
export const hasReceptionist = (people) => people.some(isReceptionist);

/** Nhân viên được xếp ca: role STAFF và chưa nghỉ việc. */
export const isSchedulable = (person) => person.role === 'STAFF' && person.status !== 'TERMINATED';

/** "Lễ tân 2 · Dọn dẹp 1" — đếm theo quyền những người đã có trong một ca; người đa quyền tính ở mỗi quyền. */
export function coverageSummary(people) {
  return STAFF_PERMISSIONS
    .map(({ value, label }) => [label, people.filter((p) => (p?.permissions ?? []).includes(value)).length])
    .filter(([, count]) => count > 0)
    .map(([label, count]) => `${label} ${count}`)
    .join(' · ');
}

/** Dòng phụ dưới tên: vị trí + quyền, kèm trạng thái nếu đang tạm khóa / đã nghỉ. */
export function personSubtitle(person) {
  // Ca cũ của quản lý (trước khi bỏ quản lý khỏi lịch) vẫn phải đọc được.
  if (person.role === 'MANAGER') return 'Quản lý khách sạn';
  if (person.status === 'TERMINATED') return 'Đã nghỉ việc';
  const position = person.positionName ?? 'Nhân viên';
  const locked = person.status === 'INACTIVE' ? ' · đang tạm khóa' : '';
  return `${position} · ${permissionSummary(person.permissions)}${locked}`;
}

export function personOptionLabel(person) {
  const status = person.status === 'INACTIVE' ? ' · đang tạm khóa' : '';
  const reception = isReceptionist(person) ? ' · Lễ tân' : '';
  return `${person.fullName}${person.positionName ? ` — ${person.positionName}` : ''}${reception}${status}`;
}

/** Theo vị trí, rồi theo tên — so sánh theo tiếng Việt. */
export function sortPeople(people) {
  return [...people].sort(
    (a, b) =>
      (a.positionName ?? '').localeCompare(b.positionName ?? '', 'vi')
      || (a.fullName ?? '').localeCompare(b.fullName ?? '', 'vi'),
  );
}
