import { STAFF_PERMISSIONS, permissionSummary } from '../../permissions';

/**
 * Người trên lịch làm việc: sắp xếp, mô tả, và lọc theo quyền nghiệp vụ.
 *
 * Quyền Lễ tân / Dọn dẹp do Manager tick cho TỪNG nhân viên (THAY_DOI_BR — thay cho "quyền theo Loại
 * Position"), một người có thể có cả hai hoặc không có quyền nào. Manager không có quyền nào trong hai
 * quyền đó (không làm thay Lễ tân, không bấm hoàn thành dọn phòng) nên nằm ở nhóm "Chỉ quyền chung".
 */

const GENERAL = 'GENERAL';

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
  if (person.role === 'MANAGER') return 'Quản lý khách sạn';
  if (person.status === 'TERMINATED') return 'Đã nghỉ việc';
  if (person.readOnly) return 'Không còn thuộc khách sạn này';
  const position = person.positionName ?? 'Nhân viên';
  const locked = person.status === 'INACTIVE' ? ' · đang tạm khóa' : '';
  return `${position} · ${permissionSummary(person.permissions)}${locked}`;
}

export function personOptionLabel(person) {
  const role = person.role === 'MANAGER' ? 'Quản lý' : person.positionName;
  const status = person.status === 'INACTIVE' ? ' · đang tạm khóa' : '';
  return `${person.fullName}${role ? ` — ${role}` : ''}${status}`;
}

/** Quản lý lên đầu, rồi theo vị trí, rồi theo tên — so sánh theo tiếng Việt. */
export function sortPeople(people) {
  const rank = (p) => (p.role === 'MANAGER' ? 0 : 1);
  return [...people].sort(
    (a, b) =>
      rank(a) - rank(b)
      || (a.positionName ?? '').localeCompare(b.positionName ?? '', 'vi')
      || (a.fullName ?? '').localeCompare(b.fullName ?? '', 'vi'),
  );
}
