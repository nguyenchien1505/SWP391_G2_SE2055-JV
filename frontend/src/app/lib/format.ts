export const formatDate = (v: string | null) =>
  v ? new Date(v).toLocaleDateString("vi-VN") : "—";

export const formatDateTime = (v: string | null) =>
  v ? new Date(v).toLocaleString("vi-VN") : "—";

export const ROLE_LABEL = {
  PLATFORM_ADMIN: "Admin nền tảng",
  DIRECTOR: "Giám đốc",
  MANAGER: "Quản lý",
  STAFF: "Nhân viên",
} as const;

export const formatVnd = (v: number | null | undefined) =>
  v == null ? "—" : `${v.toLocaleString("vi-VN")} ₫`;

export const TENANT_STATUS_LABEL = {
  TRIAL: "Dùng thử",
  ACTIVE: "Đang hoạt động",
  PAYMENT_OVERDUE: "Quá hạn thanh toán",
  SUSPENDED: "Đã khóa",
} as const;

export const SUSPEND_REASON_LABEL = {
  TRIAL_EXPIRED: "Hết hạn dùng thử",
  PAYMENT_FAILED: "Thanh toán thất bại",
  ADMIN_LOCKED: "Admin khóa",
} as const;

export const LOCATION_STATUS_LABEL = {
  NOT_OPERATIONAL: "Chưa vận hành",
  OPERATIONAL: "Đang vận hành",
} as const;
