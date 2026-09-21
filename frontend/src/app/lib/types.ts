export type Role = "PLATFORM_ADMIN" | "DIRECTOR" | "MANAGER" | "STAFF";
export type PositionType = "RECEPTION" | "HOUSEKEEPING" | "OTHER";
export type TenantStatus = "TRIAL" | "ACTIVE" | "PAYMENT_OVERDUE" | "SUSPENDED";

export interface CurrentUser {
  id: string;
  email: string;
  role: Role;
  tenantId: string | null;
  locationId: string | null;
  positionId: string | null;
  positionType: PositionType | null;
  mustChangePassword: boolean;
  readOnly: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface Tenant {
  id: string;
  name: string;
  contactEmail: string;
  contactPhone: string;
  status: TenantStatus;
  suspendReason: string | null;
  createdAt: string;
}

export type SuspendReason = "TRIAL_EXPIRED" | "PAYMENT_FAILED" | "ADMIN_LOCKED";

export interface SubscriptionSummary {
  trial: boolean;
  trialEndsAt: string | null;
  quotaLocation: number;
  quotaUser: number;
  quotaRoom: number;
  pricePerLocation: number;
  pricePerUser: number;
  pricePerRoom: number;
  currentPeriodStart: string | null;
  nextBillingDate: string | null;
}

export interface TenantDetail extends Omit<Tenant, "suspendReason"> {
  suspendReason: SuspendReason | null;
  suspendedAt: string | null;
  reactivatedAt: string | null;
  updatedAt: string;
  subscription: SubscriptionSummary | null;
}

export interface UsageMetric {
  used: number;
  quota: number | null;
  remaining: number | null;
  usedPercent: number | null;
  overQuota: boolean;
}

export interface TenantUsage {
  tenantId: string;
  tenantName: string;
  status: TenantStatus;
  trial: boolean;
  trialEndsAt: string | null;
  locations: UsageMetric;
  staff: UsageMetric;
  rooms: UsageMetric;
}

export interface PricingConfig {
  id: string;
  pricePerLocation: number;
  pricePerUser: number;
  pricePerRoom: number;
  effectiveFrom: string;
}

export interface SystemConfig {
  trialDays: number;
  gracePeriodDays: number;
  updatedAt: string | null;
}
