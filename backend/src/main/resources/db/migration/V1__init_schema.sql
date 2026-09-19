-- =============================================================================
-- Milestone 1 — Hệ thống quản lý vận hành khách sạn (SaaS đa tenant)
-- Schema khởi tạo, dựng theo docs/ERD.drawio + docs/TỔNG QUAN DỰ ÁN.docx.
--
-- Thay thế hoàn toàn schema cũ (V1/V2/V3 trước đây sinh từ
-- docs/Hotel_Management_ERD.drawio — ERD đã bị loại bỏ).
--
-- QUY ƯỚC
--   * Khóa chính  : CHAR(36) UUID, sinh ở tầng ứng dụng (Hibernate GenerationType.UUID).
--   * Tên bảng    : snake_case số nhiều (ERD dùng số ít; `user` là từ khóa SQL).
--   * Enum        : VARCHAR + CHECK constraint. KHÔNG dùng kiểu ENUM của MySQL vì
--                   Hibernate ddl-auto=validate chỉ chấp nhận VARCHAR cho
--                   @Enumerated(EnumType.STRING).
--   * Tiền tệ     : BIGINT (VND số nguyên — BR-SAAS-15). ERD ghi `decimal`; theo
--                   quy ước tài liệu, BR thắng khi mâu thuẫn với ERD/DM.
--   * Audit       : created_at/created_by/updated_at/updated_by trên MỌI bảng (DM-17 —
--                   không có bảng AuditLog chung). ERD chỉ vẽ created_by ở 3 bảng;
--                   DM-17 mới là quyết định chính thức nên áp cho tất cả.
--   * Xóa mềm     : CHỈ ở nơi nghiệp vụ yêu cầu (users.status=TERMINATED — BR-USER-04;
--                   rooms.is_active — BR-ROOM-08). Các bảng danh mục dùng is_active
--                   (BR-ORG-14) vì BR-ORG-10 đã chặn cứng việc xóa.
--
-- SAI LỆCH CÓ CHỦ Ý SO VỚI ERD (đều theo BR, ghi rõ tại từng chỗ)
--   1. Unique composite thay vì unique cột đơn: BR-ORG-13, BR-ROOM-05, BR-ASSET-12.
--   2. damage_reports bổ sung status/description/reported_at/resolved_* — BR-ASSET-11
--      yêu cầu 2 trạng thái Mới/Đã xử lý, ERD thiếu hoàn toàn.
--   3. housekeeping_tasks bổ sung cancel_reason/cancelled_at — BR-HK-09/BR-HK-10 yêu
--      cầu hủy task "kèm lý do".
--   4. transfer_requests bổ sung decided_at/executed_at — BR-TRF-05/BR-TRF-07 cần mốc
--      duyệt và mốc job thực thi (idempotency).
--   5. tenants bổ sung contact_phone — BR-SAAS-13 liệt kê SĐT là 1 trong 5 trường
--      bắt buộc khi đăng ký.
--   6. users.tenant_id NULL cho PLATFORM_ADMIN (BR-PERM-01 — đứng ngoài mọi Tenant).
-- =============================================================================

SET NAMES utf8mb4;

-- =============================================================================
-- NHÓM 1 — Nền tảng SaaS và tổ chức (không phụ thuộc users)
-- =============================================================================

-- ── tenants — BR-SAAS-01, BR-SAAS-13, BR-SAAS-16 ─────────────────────────────
CREATE TABLE tenants (
    id              CHAR(36)     NOT NULL,
    name            VARCHAR(255) NOT NULL COMMENT 'Tên công ty / chuỗi khách sạn',
    contact_email   VARCHAR(255) NOT NULL COMMENT 'Trùng email đăng nhập của Giám đốc — BR-SAAS-13',
    contact_phone   VARCHAR(30)  NOT NULL,
    status          VARCHAR(20)  NOT NULL COMMENT 'TRIAL | ACTIVE | PAYMENT_OVERDUE | SUSPENDED',
    suspend_reason  VARCHAR(30)  NULL     COMMENT 'TRIAL_EXPIRED | PAYMENT_FAILED | ADMIN_LOCKED — BR-SAAS-16',
    suspended_at    DATETIME     NULL,
    reactivated_at  DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      CHAR(36)     NULL,
    updated_at      DATETIME     NULL,
    updated_by      CHAR(36)     NULL,
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uk_tenants_contact_email UNIQUE (contact_email),
    CONSTRAINT ck_tenants_status CHECK (status IN ('TRIAL','ACTIVE','PAYMENT_OVERDUE','SUSPENDED')),
    CONSTRAINT ck_tenants_suspend_reason CHECK (
        suspend_reason IS NULL OR suspend_reason IN ('TRIAL_EXPIRED','PAYMENT_FAILED','ADMIN_LOCKED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ── system_config — BR-SAAS-08, BR-SAAS-10 ───────────────────────────────────
-- Cấu hình cấp hệ thống do Admin Platform quản lý. Đúng 1 bản ghi (seed bên dưới).
CREATE TABLE system_config (
    id                 CHAR(36) NOT NULL,
    trial_days         INT      NOT NULL DEFAULT 30 COMMENT 'Mặc định 1 tháng — BR-SAAS-08',
    grace_period_days  INT      NOT NULL DEFAULT 7  COMMENT 'Số ngày Payment overdue — BR-SAAS-10',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         CHAR(36) NULL,
    updated_at         DATETIME NULL,
    updated_by         CHAR(36) NULL,
    CONSTRAINT pk_system_config PRIMARY KEY (id),
    CONSTRAINT ck_system_config_trial CHECK (trial_days > 0),
    CONSTRAINT ck_system_config_grace CHECK (grace_period_days >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ── pricing_config — BR-SAAS-02 ──────────────────────────────────────────────
-- Đơn giá chung do Admin Platform cấu hình, có hiệu lực theo effective_from.
-- Subscription chốt SNAPSHOT đơn giá tại thời điểm mua (BR-SAAS-05) nên đổi bảng
-- này không ảnh hưởng gói đã bán.
CREATE TABLE pricing_config (
    id                  CHAR(36) NOT NULL,
    price_per_location  BIGINT   NOT NULL COMMENT 'VND / Location / chu kỳ',
    price_per_user      BIGINT   NOT NULL COMMENT 'VND / Staff / chu kỳ — BR-SAAS-03',
    price_per_room      BIGINT   NOT NULL COMMENT 'VND / Phòng / chu kỳ',
    effective_from      DATE     NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          CHAR(36) NULL,
    updated_at          DATETIME NULL,
    updated_by          CHAR(36) NULL,
    CONSTRAINT pk_pricing_config PRIMARY KEY (id),
    CONSTRAINT uk_pricing_config_effective_from UNIQUE (effective_from),
    CONSTRAINT ck_pricing_config_prices CHECK (
        price_per_location >= 0 AND price_per_user >= 0 AND price_per_room >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ── locations — BR-ORG-01..05, DM-13 ─────────────────────────────────────────
CREATE TABLE locations (
    id           CHAR(36)     NOT NULL,
    tenant_id    CHAR(36)     NOT NULL,
    name         VARCHAR(255) NOT NULL,
    address      VARCHAR(500) NOT NULL,
    phone        VARCHAR(30)  NOT NULL,
    star_rating  INT          NULL COMMENT 'BR-ORG-04 mô tả thị trường mục tiêu 2-3 sao; DB nới 1-5, siết ở tầng validate',
    timezone     VARCHAR(64)  NOT NULL DEFAULT 'Asia/Ho_Chi_Minh' COMMENT 'Mốc tính "ca tương lai" — BR-SCH-17',
    status       VARCHAR(20)  NOT NULL COMMENT 'NOT_OPERATIONAL (chưa có Manager) | OPERATIONAL — DM-13',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   CHAR(36)     NULL,
    updated_at   DATETIME     NULL,
    updated_by   CHAR(36)     NULL,
    CONSTRAINT pk_locations PRIMARY KEY (id),
    CONSTRAINT fk_locations_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_locations_status CHECK (status IN ('NOT_OPERATIONAL','OPERATIONAL')),
    CONSTRAINT ck_locations_star_rating CHECK (star_rating IS NULL OR star_rating BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_locations_tenant ON locations (tenant_id);

-- ── departments — BR-ORG-06, BR-ORG-13, BR-ORG-14 ────────────────────────────
CREATE TABLE departments (
    id          CHAR(36)     NOT NULL,
    tenant_id   CHAR(36)     NOT NULL,
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE COMMENT 'BR-ORG-14 — ẩn thay vì xóa, vì BR-ORG-10 chặn xóa cứng',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  CHAR(36)     NULL,
    updated_at  DATETIME     NULL,
    updated_by  CHAR(36)     NULL,
    CONSTRAINT pk_departments PRIMARY KEY (id),
    -- BR-ORG-13: unique trong phạm vi TENANT (ERD đánh UK cột đơn = unique toàn hệ thống, sai).
    CONSTRAINT uk_departments_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_departments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ── positions — BR-ORG-06..09, BR-ORG-13, BR-ORG-14 ──────────────────────────
CREATE TABLE positions (
    id             CHAR(36)     NOT NULL,
    tenant_id      CHAR(36)     NOT NULL,
    department_id  CHAR(36)     NOT NULL COMMENT 'BR-ORG-07 — thuộc đúng 1 Department',
    name           VARCHAR(100) NOT NULL,
    position_type  VARCHAR(20)  NOT NULL COMMENT 'RECEPTION | HOUSEKEEPING | OTHER — quyền gán theo LOẠI, không theo tên (BR-ORG-08)',
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     CHAR(36)     NULL,
    updated_at     DATETIME     NULL,
    updated_by     CHAR(36)     NULL,
    CONSTRAINT pk_positions PRIMARY KEY (id),
    CONSTRAINT uk_positions_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_positions_tenant     FOREIGN KEY (tenant_id)     REFERENCES tenants (id),
    CONSTRAINT fk_positions_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT ck_positions_type CHECK (position_type IN ('RECEPTION','HOUSEKEEPING','OTHER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_positions_department ON positions (department_id);

-- ── room_types — BR-ORG-11, BR-ORG-13, BR-ORG-14 ─────────────────────────────
CREATE TABLE room_types (
    id          CHAR(36)     NOT NULL,
    tenant_id   CHAR(36)     NOT NULL COMMENT 'Danh mục cấp Tenant, dùng chung mọi Location — BR-ORG-11',
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  CHAR(36)     NULL,
    updated_at  DATETIME     NULL,
    updated_by  CHAR(36)     NULL,
    CONSTRAINT pk_room_types PRIMARY KEY (id),
    CONSTRAINT uk_room_types_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_room_types_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ── asset_categories — BR-ASSET-08, BR-ASSET-09 ──────────────────────────────
-- Dùng CHUNG 1 bảng cho cả tài sản cố định và tiêu hao, phân biệt bằng asset_kind.
CREATE TABLE asset_categories (
    id          CHAR(36)     NOT NULL,
    tenant_id   CHAR(36)     NOT NULL,
    name        VARCHAR(100) NOT NULL,
    asset_kind  VARCHAR(20)  NOT NULL COMMENT 'FIXED | CONSUMABLE',
    purpose     VARCHAR(30)  NOT NULL COMMENT 'GUEST_USE (dùng cho khách) | FACILITY_MAINTENANCE (duy trì cơ sở)',
    unit        VARCHAR(20)  NULL     COMMENT 'Chỉ dùng cho asset_kind = CONSUMABLE — BR-ASSET-08',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  CHAR(36)     NULL,
    updated_at  DATETIME     NULL,
    updated_by  CHAR(36)     NULL,
    CONSTRAINT pk_asset_categories PRIMARY KEY (id),
    CONSTRAINT uk_asset_categories_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_asset_categories_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_asset_categories_kind    CHECK (asset_kind IN ('FIXED','CONSUMABLE')),
    CONSTRAINT ck_asset_categories_purpose CHECK (purpose IN ('GUEST_USE','FACILITY_MAINTENANCE')),
    -- BR-ASSET-08: đơn vị tính chỉ có nghĩa với loại tiêu hao.
    CONSTRAINT ck_asset_categories_unit CHECK (
        (asset_kind = 'CONSUMABLE' AND unit IS NOT NULL) OR
        (asset_kind = 'FIXED'      AND unit IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- =============================================================================
-- NHÓM 2 — Tài khoản
-- =============================================================================

-- ── users — BR-USER-01..08, BR-PERM-*, DM-01, DM-02 ──────────────────────────
-- 1 bảng cho mọi vai trò, trường đặc thù nullable (DM-01):
--   PLATFORM_ADMIN : tenant_id, location_id, position_id đều NULL
--   DIRECTOR       : có tenant_id; location_id, position_id NULL; hồ sơ tối thiểu
--   MANAGER        : có tenant_id + location_id; position_id NULL (BR-USER-05)
--   STAFF          : có đủ tenant_id + location_id + position_id
-- Lễ tân / Dọn dẹp KHÔNG phải role — là positions.position_type (BR-ORG-08).
CREATE TABLE users (
    id                    CHAR(36)     NOT NULL,
    tenant_id             CHAR(36)     NULL COMMENT 'NULL cho PLATFORM_ADMIN — BR-PERM-01',
    role                  VARCHAR(20)  NOT NULL COMMENT 'PLATFORM_ADMIN | DIRECTOR | MANAGER | STAFF — DM-01',
    email                 VARCHAR(255) NOT NULL COMMENT 'Username, unique TOÀN HỆ THỐNG kể cả người đã nghỉ — BR-USER-06',
    password_hash         VARCHAR(255) NOT NULL,
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE COMMENT 'BR-USER-07',
    status                VARCHAR(20)  NOT NULL COMMENT 'ACTIVE | INACTIVE | TERMINATED (đã nghỉ việc — BR-USER-04)',
    full_name             VARCHAR(255) NOT NULL,
    phone                 VARCHAR(30)  NOT NULL,
    location_id           CHAR(36)     NULL,
    position_id           CHAR(36)     NULL COMMENT 'Department suy ra từ Position — BR-ORG-07',
    start_work_date       DATE         NULL,
    date_of_birth         DATE         NULL,
    gender                VARCHAR(10)  NULL COMMENT 'MALE | FEMALE | OTHER — BR-USER-08',
    address               VARCHAR(500) NULL,
    avatar_url            VARCHAR(500) NULL,
    terminated_at         DATETIME     NULL,
    terminated_by         CHAR(36)     NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            CHAR(36)     NULL,
    updated_at            DATETIME     NULL,
    updated_by            CHAR(36)     NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_tenant        FOREIGN KEY (tenant_id)     REFERENCES tenants (id),
    CONSTRAINT fk_users_location      FOREIGN KEY (location_id)   REFERENCES locations (id),
    CONSTRAINT fk_users_position      FOREIGN KEY (position_id)   REFERENCES positions (id),
    CONSTRAINT fk_users_terminated_by FOREIGN KEY (terminated_by) REFERENCES users (id),
    CONSTRAINT fk_users_created_by    FOREIGN KEY (created_by)    REFERENCES users (id),
    CONSTRAINT fk_users_updated_by    FOREIGN KEY (updated_by)    REFERENCES users (id),
    CONSTRAINT ck_users_role   CHECK (role IN ('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE','TERMINATED')),
    CONSTRAINT ck_users_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER')),
    -- DM-01: chỉ PLATFORM_ADMIN được đứng ngoài Tenant.
    CONSTRAINT ck_users_tenant_scope CHECK (
        (role = 'PLATFORM_ADMIN' AND tenant_id IS NULL) OR
        (role <> 'PLATFORM_ADMIN' AND tenant_id IS NOT NULL)),
    -- BR-USER-05 + DM-01: chỉ STAFF mới có Position.
    CONSTRAINT ck_users_position_scope CHECK (
        (role = 'STAFF' AND position_id IS NOT NULL) OR
        (role <> 'STAFF' AND position_id IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_users_tenant   ON users (tenant_id);
CREATE INDEX idx_users_location ON users (location_id);
CREATE INDEX idx_users_role     ON users (role);

-- ── FK audit bị hoãn ─────────────────────────────────────────────────────────
-- 6 bảng trên được tạo TRƯỚC `users` nên created_by/updated_by chưa gắn FK được.
ALTER TABLE tenants
    ADD CONSTRAINT fk_tenants_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_tenants_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE system_config
    ADD CONSTRAINT fk_system_config_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_system_config_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE pricing_config
    ADD CONSTRAINT fk_pricing_config_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_pricing_config_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE locations
    ADD CONSTRAINT fk_locations_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_locations_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_departments_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE positions
    ADD CONSTRAINT fk_positions_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_positions_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE room_types
    ADD CONSTRAINT fk_room_types_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_room_types_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE asset_categories
    ADD CONSTRAINT fk_asset_categories_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_asset_categories_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

-- =============================================================================
-- NHÓM 3 — Gói dịch vụ và thanh toán (BR-SAAS-*)
-- =============================================================================

-- ── subscriptions — BR-SAAS-02..08, DM-09 ────────────────────────────────────
-- Mỗi Tenant đúng 1 bản ghi hiện hành ("has current" trong ERD).
CREATE TABLE subscriptions (
    id                    CHAR(36) NOT NULL,
    tenant_id             CHAR(36) NOT NULL,
    quota_location        INT      NOT NULL,
    quota_user            INT      NOT NULL COMMENT 'CHỈ đếm Staff — Giám đốc/Manager không tính (BR-SAAS-03)',
    quota_room            INT      NOT NULL,
    price_per_location    BIGINT   NOT NULL COMMENT 'Snapshot đơn giá lúc chốt gói — BR-SAAS-05',
    price_per_user        BIGINT   NOT NULL,
    price_per_room        BIGINT   NOT NULL,
    is_trial              BOOLEAN  NOT NULL DEFAULT TRUE,
    trial_ends_at         DATE     NULL,
    current_period_start  DATE     NULL,
    next_billing_date     DATE     NULL COMMENT 'Chu kỳ 30 ngày — BR-SAAS-05',
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            CHAR(36) NULL,
    updated_at            DATETIME NULL,
    updated_by            CHAR(36) NULL,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    -- DM-09: mỗi Tenant đúng 1 subscription hiện hành ("has current" trong ERD).
    CONSTRAINT uk_subscriptions_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_subscriptions_tenant     FOREIGN KEY (tenant_id)  REFERENCES tenants (id),
    CONSTRAINT fk_subscriptions_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_subscriptions_updated_by FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT ck_subscriptions_quota CHECK (
        quota_location >= 0 AND quota_user >= 0 AND quota_room >= 0),
    CONSTRAINT ck_subscriptions_price CHECK (
        price_per_location >= 0 AND price_per_user >= 0 AND price_per_room >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ── invoices — BR-SAAS-05, BR-SAAS-06, BR-SAAS-10, BR-SAAS-15, DM-09 ─────────
-- Bản ghi nội bộ, không phải hóa đơn điện tử. amount là VND số nguyên.
-- quota_*_snapshot: BR-SAAS-06 cho phép ghi đè quota giữa kỳ, nếu không chốt lại
-- ở đây thì hóa đơn cũ không giải thích được đã tính trên cơ sở nào.
CREATE TABLE invoices (
    id                       CHAR(36)    NOT NULL,
    tenant_id                CHAR(36)    NOT NULL,
    subscription_id          CHAR(36)    NOT NULL,
    invoice_type             VARCHAR(20) NOT NULL COMMENT 'PERIODIC (chu kỳ) | UPGRADE_DIFF (chênh lệch tăng gói) — BR-SAAS-15',
    period_start             DATE        NULL,
    period_end               DATE        NULL,
    quota_location_snapshot  INT         NOT NULL,
    quota_user_snapshot      INT         NOT NULL,
    quota_room_snapshot      INT         NOT NULL,
    amount                   BIGINT      NOT NULL COMMENT 'VND số nguyên, không tách VAT — BR-SAAS-15',
    status                   VARCHAR(20) NOT NULL COMMENT 'PENDING | PAID | FAILED',
    issued_at                DATETIME    NOT NULL,
    paid_at                  DATETIME    NULL,
    first_failed_at          DATETIME    NULL COMMENT 'Mốc bắt đầu ân hạn — BR-SAAS-10',
    grace_until              DATE        NULL,
    created_at               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by               CHAR(36)    NULL,
    updated_at               DATETIME    NULL,
    updated_by               CHAR(36)    NULL,
    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT fk_invoices_tenant       FOREIGN KEY (tenant_id)       REFERENCES tenants (id),
    CONSTRAINT fk_invoices_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT fk_invoices_created_by   FOREIGN KEY (created_by)      REFERENCES users (id),
    CONSTRAINT fk_invoices_updated_by   FOREIGN KEY (updated_by)      REFERENCES users (id),
    CONSTRAINT ck_invoices_type   CHECK (invoice_type IN ('PERIODIC','UPGRADE_DIFF')),
    CONSTRAINT ck_invoices_status CHECK (status IN ('PENDING','PAID','FAILED')),
    CONSTRAINT ck_invoices_amount CHECK (amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_invoices_tenant_status ON invoices (tenant_id, status);

-- =============================================================================
-- NHÓM 4 — Lịch làm việc (BR-SCH-*)
-- =============================================================================

-- ── schedule_policies — BR-SCH-01, BR-SCH-02, BR-SCH-20, BR-SCH-21, DM-18 ────
-- Mỗi Tenant ĐÚNG 1 bản ghi, sửa đè, không lưu lịch sử phiên bản (DM-18).
CREATE TABLE schedule_policies (
    id                             CHAR(36)     NOT NULL,
    tenant_id                      CHAR(36)     NOT NULL,
    max_hours_per_day              DECIMAL(4,2) NOT NULL DEFAULT 8.00,
    max_hours_per_week             DECIMAL(5,2) NOT NULL DEFAULT 48.00,
    max_consecutive_shifts         INT          NOT NULL DEFAULT 6  COMMENT 'Đếm theo SỐ NGÀY liên tiếp có ca — BR-SCH-14',
    min_rest_hours_between_shifts  DECIMAL(4,2) NOT NULL DEFAULT 12.00,
    min_days_off_per_week          INT          NOT NULL DEFAULT 1,
    swap_response_timeout_hours    INT          NOT NULL DEFAULT 24 COMMENT 'BR-SCH-11, BR-SCH-21',
    created_at                     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                     CHAR(36)     NULL,
    updated_at                     DATETIME     NULL,
    updated_by                     CHAR(36)     NULL,
    CONSTRAINT pk_schedule_policies PRIMARY KEY (id),
    -- DM-18: mỗi Tenant đúng 1 bản ghi, sửa đè, không lưu lịch sử phiên bản.
    CONSTRAINT uk_schedule_policies_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_schedule_policies_tenant     FOREIGN KEY (tenant_id)  REFERENCES tenants (id),
    CONSTRAINT fk_schedule_policies_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_schedule_policies_updated_by FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT ck_schedule_policies_values CHECK (
        max_hours_per_day             > 0  AND
        max_hours_per_week            > 0  AND
        max_consecutive_shifts        > 0  AND
        min_rest_hours_between_shifts >= 0 AND
        min_days_off_per_week         BETWEEN 0 AND 7 AND
        swap_response_timeout_hours   > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ── shift_templates — BR-SCH-04, BR-SCH-22 ───────────────────────────────────
CREATE TABLE shift_templates (
    id           CHAR(36)     NOT NULL,
    tenant_id    CHAR(36)     NOT NULL,
    name         VARCHAR(100) NOT NULL,
    start_time   TIME         NOT NULL,
    end_time     TIME         NOT NULL,
    description  VARCHAR(500) NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE COMMENT 'BR-SCH-22 — không xóa cứng, chỉ vô hiệu hóa',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   CHAR(36)     NULL,
    updated_at   DATETIME     NULL,
    updated_by   CHAR(36)     NULL,
    CONSTRAINT pk_shift_templates PRIMARY KEY (id),
    CONSTRAINT uk_shift_templates_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_shift_templates_tenant     FOREIGN KEY (tenant_id)  REFERENCES tenants (id),
    CONSTRAINT fk_shift_templates_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_shift_templates_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ── shifts — BR-SCH-03..05, BR-SCH-24, BR-DASH-01, DM-03, DM-15 ──────────────
-- DM-03: 1 ca = 1 slot, staff_id NULLABLE. Gỡ ca = set staff_id NULL (không xóa
-- bản ghi), kèm lý do. Không có bảng ShiftAssignment, không có trạng thái nháp.
-- DM-15: check-in/out nằm ngay trên bảng này, không có bảng AttendanceLog.
CREATE TABLE shifts (
    id                  CHAR(36)     NOT NULL,
    tenant_id           CHAR(36)     NOT NULL,
    location_id         CHAR(36)     NOT NULL COMMENT 'Phạm vi kiểm tra trùng ca — BR-SCH-05',
    staff_id            CHAR(36)     NULL     COMMENT 'NULL = chưa phân công — DM-03',
    shift_date          DATE         NOT NULL,
    start_time          TIME         NOT NULL,
    end_time            TIME         NOT NULL,
    is_overnight        BOOLEAN      NOT NULL DEFAULT FALSE,
    duration_hours      DECIMAL(4,2) NOT NULL COMMENT 'Ca qua đêm tính TRỌN vào ngày bắt đầu — BR-SCH-03',
    source_template_id  CHAR(36)     NULL     COMMENT 'NULL = Manager tạo ca tự do — BR-SCH-04',
    check_in_at         DATETIME     NULL     COMMENT 'Chỉ ghi timestamp, không tính đi muộn — BR-DASH-01',
    check_out_at        DATETIME     NULL,
    unassigned_reason   VARCHAR(30)  NULL     COMMENT 'TRANSFER | TERMINATION | LEAVE_APPROVED | MANAGER_MANUAL — BR-SCH-24',
    unassigned_at       DATETIME     NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          CHAR(36)     NULL,
    updated_at          DATETIME     NULL,
    updated_by          CHAR(36)     NULL,
    CONSTRAINT pk_shifts PRIMARY KEY (id),
    CONSTRAINT fk_shifts_tenant     FOREIGN KEY (tenant_id)          REFERENCES tenants (id),
    CONSTRAINT fk_shifts_location   FOREIGN KEY (location_id)        REFERENCES locations (id),
    CONSTRAINT fk_shifts_staff      FOREIGN KEY (staff_id)           REFERENCES users (id),
    CONSTRAINT fk_shifts_template   FOREIGN KEY (source_template_id) REFERENCES shift_templates (id),
    CONSTRAINT fk_shifts_created_by FOREIGN KEY (created_by)         REFERENCES users (id),
    CONSTRAINT fk_shifts_updated_by FOREIGN KEY (updated_by)         REFERENCES users (id),
    CONSTRAINT ck_shifts_unassigned_reason CHECK (
        unassigned_reason IS NULL OR
        unassigned_reason IN ('TRANSFER','TERMINATION','LEAVE_APPROVED','MANAGER_MANUAL')),
    -- Ca đã gỡ người thì phải có lý do, và ngược lại.
    CONSTRAINT ck_shifts_unassigned_pair CHECK (
        (staff_id IS NOT NULL AND unassigned_reason IS NULL AND unassigned_at IS NULL) OR
        (staff_id IS NULL)),
    CONSTRAINT ck_shifts_duration CHECK (duration_hours > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_shifts_staff_date    ON shifts (staff_id, shift_date);
CREATE INDEX idx_shifts_location_date ON shifts (location_id, shift_date);

-- ── leave_requests — BR-SCH-06, BR-SCH-07, BR-SCH-16, BR-SCH-23, DM-07 ───────
CREATE TABLE leave_requests (
    id            CHAR(36)     NOT NULL,
    tenant_id     CHAR(36)     NOT NULL,
    location_id   CHAR(36)     NOT NULL,
    requester_id  CHAR(36)     NOT NULL,
    from_date     DATE         NOT NULL COMMENT 'Ngày nguyên, không nửa ngày — DM-07',
    to_date       DATE         NOT NULL,
    reason        VARCHAR(500) NOT NULL COMMENT 'Bắt buộc nhập — BR-SCH-16',
    status        VARCHAR(20)  NOT NULL COMMENT 'PENDING | APPROVED | REJECTED | CANCELLED — BR-SCH-23',
    approver_id   CHAR(36)     NULL     COMMENT 'Manager duyệt Staff; Giám đốc duyệt Manager — BR-SCH-08',
    decided_at    DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    CHAR(36)     NULL,
    updated_at    DATETIME     NULL,
    updated_by    CHAR(36)     NULL,
    CONSTRAINT pk_leave_requests PRIMARY KEY (id),
    CONSTRAINT fk_leave_requests_tenant     FOREIGN KEY (tenant_id)    REFERENCES tenants (id),
    CONSTRAINT fk_leave_requests_location   FOREIGN KEY (location_id)  REFERENCES locations (id),
    CONSTRAINT fk_leave_requests_requester  FOREIGN KEY (requester_id) REFERENCES users (id),
    CONSTRAINT fk_leave_requests_approver   FOREIGN KEY (approver_id)  REFERENCES users (id),
    CONSTRAINT fk_leave_requests_created_by FOREIGN KEY (created_by)   REFERENCES users (id),
    CONSTRAINT fk_leave_requests_updated_by FOREIGN KEY (updated_by)   REFERENCES users (id),
    CONSTRAINT ck_leave_requests_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_leave_requests_range  CHECK (to_date >= from_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_leave_requests_requester ON leave_requests (requester_id, status);
CREATE INDEX idx_leave_requests_location  ON leave_requests (location_id, status);

-- ── shift_swap_requests — BR-SCH-09..12, BR-SCH-18, BR-SCH-19, BR-SCH-23, DM-08 ─
-- Tên cột theo ERD (requester/peer) thay vì DM-08 (staff_a/staff_b): cùng ngữ nghĩa,
-- tên ERD rõ hơn. Cần cập nhật lại DM-08 cho khớp.
CREATE TABLE shift_swap_requests (
    id                  CHAR(36)    NOT NULL,
    tenant_id           CHAR(36)    NOT NULL,
    location_id         CHAR(36)    NOT NULL,
    requester_id        CHAR(36)    NOT NULL,
    requester_shift_id  CHAR(36)    NOT NULL,
    peer_id             CHAR(36)    NOT NULL COMMENT 'Phải cùng ĐÚNG MỘT Position với requester — BR-SCH-12',
    peer_shift_id       CHAR(36)    NOT NULL,
    status              VARCHAR(30) NOT NULL COMMENT '7 giá trị — BR-SCH-23',
    peer_responded_at   DATETIME    NULL,
    expires_at          DATETIME    NOT NULL COMMENT 'Chốt tại thời điểm TẠO yêu cầu — BR-SCH-21',
    approver_id         CHAR(36)    NULL,
    decided_at          DATETIME    NULL,
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          CHAR(36)    NULL,
    updated_at          DATETIME    NULL,
    updated_by          CHAR(36)    NULL,
    CONSTRAINT pk_shift_swap_requests PRIMARY KEY (id),
    CONSTRAINT fk_swap_tenant          FOREIGN KEY (tenant_id)          REFERENCES tenants (id),
    CONSTRAINT fk_swap_location        FOREIGN KEY (location_id)        REFERENCES locations (id),
    CONSTRAINT fk_swap_requester       FOREIGN KEY (requester_id)       REFERENCES users (id),
    CONSTRAINT fk_swap_requester_shift FOREIGN KEY (requester_shift_id) REFERENCES shifts (id),
    CONSTRAINT fk_swap_peer            FOREIGN KEY (peer_id)            REFERENCES users (id),
    CONSTRAINT fk_swap_peer_shift      FOREIGN KEY (peer_shift_id)      REFERENCES shifts (id),
    CONSTRAINT fk_swap_approver        FOREIGN KEY (approver_id)        REFERENCES users (id),
    CONSTRAINT fk_swap_created_by      FOREIGN KEY (created_by)         REFERENCES users (id),
    CONSTRAINT fk_swap_updated_by      FOREIGN KEY (updated_by)         REFERENCES users (id),
    CONSTRAINT ck_swap_status CHECK (status IN (
        'PENDING_PEER','PENDING_MANAGER','APPROVED',
        'PEER_REJECTED','MANAGER_REJECTED','EXPIRED','CANCELLED')),
    CONSTRAINT ck_swap_distinct CHECK (requester_id <> peer_id AND requester_shift_id <> peer_shift_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- BR-SCH-19: mỗi ca chỉ là đối tượng của TỐI ĐA 1 yêu cầu đang chờ xử lý.
-- MySQL không có partial index, nên dùng cột sinh: chỉ có giá trị khi yêu cầu còn
-- treo; mỗi NULL được coi là khác nhau nên yêu cầu đã đóng không chặn yêu cầu mới.
ALTER TABLE shift_swap_requests
    ADD COLUMN pending_requester_shift_id CHAR(36)
        GENERATED ALWAYS AS (
            CASE WHEN status IN ('PENDING_PEER','PENDING_MANAGER')
                 THEN requester_shift_id END) VIRTUAL,
    ADD COLUMN pending_peer_shift_id CHAR(36)
        GENERATED ALWAYS AS (
            CASE WHEN status IN ('PENDING_PEER','PENDING_MANAGER')
                 THEN peer_shift_id END) VIRTUAL;

ALTER TABLE shift_swap_requests
    ADD CONSTRAINT uk_swap_pending_requester_shift UNIQUE (pending_requester_shift_id),
    ADD CONSTRAINT uk_swap_pending_peer_shift      UNIQUE (pending_peer_shift_id);

-- ── transfer_requests — BR-TRF-01..07, DM-14 ─────────────────────────────────
CREATE TABLE transfer_requests (
    id                      CHAR(36)    NOT NULL,
    tenant_id               CHAR(36)    NOT NULL,
    target_user_id          CHAR(36)    NOT NULL,
    from_location_id        CHAR(36)    NOT NULL,
    to_location_id          CHAR(36)    NOT NULL,
    replacement_manager_id  CHAR(36)    NULL COMMENT 'BẮT BUỘC khi target là Manager — BR-TRF-03, DM-14',
    effective_date          DATE        NOT NULL COMMENT 'Phải ở tương lai — BR-TRF-02',
    status                  VARCHAR(20) NOT NULL COMMENT 'PENDING | APPROVED | REJECTED | EXECUTED | CANCELLED — BR-TRF-07',
    requester_id            CHAR(36)    NOT NULL,
    approver_id             CHAR(36)    NULL,
    decided_at              DATETIME    NULL COMMENT 'Mốc Giám đốc duyệt/từ chối',
    executed_at             DATETIME    NULL COMMENT 'Mốc job chạy tại effective date — BR-TRF-05 (idempotency)',
    created_at              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              CHAR(36)    NULL,
    updated_at              DATETIME    NULL,
    updated_by              CHAR(36)    NULL,
    CONSTRAINT pk_transfer_requests PRIMARY KEY (id),
    CONSTRAINT fk_transfer_tenant       FOREIGN KEY (tenant_id)              REFERENCES tenants (id),
    CONSTRAINT fk_transfer_target       FOREIGN KEY (target_user_id)         REFERENCES users (id),
    CONSTRAINT fk_transfer_from         FOREIGN KEY (from_location_id)       REFERENCES locations (id),
    CONSTRAINT fk_transfer_to           FOREIGN KEY (to_location_id)         REFERENCES locations (id),
    CONSTRAINT fk_transfer_replacement  FOREIGN KEY (replacement_manager_id) REFERENCES users (id),
    CONSTRAINT fk_transfer_requester    FOREIGN KEY (requester_id)           REFERENCES users (id),
    CONSTRAINT fk_transfer_approver     FOREIGN KEY (approver_id)            REFERENCES users (id),
    CONSTRAINT fk_transfer_created_by   FOREIGN KEY (created_by)             REFERENCES users (id),
    CONSTRAINT fk_transfer_updated_by   FOREIGN KEY (updated_by)             REFERENCES users (id),
    CONSTRAINT ck_transfer_status CHECK (status IN ('PENDING','APPROVED','REJECTED','EXECUTED','CANCELLED')),
    CONSTRAINT ck_transfer_locations CHECK (from_location_id <> to_location_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_transfer_requests_pending ON transfer_requests (status, effective_date);

-- =============================================================================
-- NHÓM 5 — Phòng và housekeeping (BR-ROOM-*, BR-HK-*)
-- =============================================================================

-- ── areas — BR-ORG-12, BR-ORG-13, BR-ORG-15 ──────────────────────────────────
-- Khác 3 danh mục kia: tạo ở cấp LOCATION vì là đặc thù vật lý từng khách sạn.
CREATE TABLE areas (
    id           CHAR(36)     NOT NULL,
    tenant_id    CHAR(36)     NOT NULL,
    location_id  CHAR(36)     NOT NULL,
    name         VARCHAR(100) NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   CHAR(36)     NULL,
    updated_at   DATETIME     NULL,
    updated_by   CHAR(36)     NULL,
    CONSTRAINT pk_areas PRIMARY KEY (id),
    -- BR-ORG-13: Area unique trong phạm vi LOCATION (khác 4 danh mục cấp Tenant).
    CONSTRAINT uk_areas_location_name UNIQUE (location_id, name),
    CONSTRAINT fk_areas_tenant     FOREIGN KEY (tenant_id)   REFERENCES tenants (id),
    CONSTRAINT fk_areas_location   FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_areas_created_by FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT fk_areas_updated_by FOREIGN KEY (updated_by)  REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ── rooms — BR-ROOM-01..10 ───────────────────────────────────────────────────
CREATE TABLE rooms (
    id                  CHAR(36)     NOT NULL,
    tenant_id           CHAR(36)     NOT NULL,
    location_id         CHAR(36)     NOT NULL,
    room_number         VARCHAR(20)  NOT NULL,
    floor               VARCHAR(10)  NOT NULL COMMENT 'Kiểu text — chấp nhận G/M/B1 (BR-ROOM-05)',
    room_type_id        CHAR(36)     NOT NULL,
    capacity            INT          NOT NULL,
    note                VARCHAR(500) NULL,
    status              VARCHAR(20)  NOT NULL COMMENT '7 trạng thái — BR-ROOM-01',
    unavailable_reason  VARCHAR(500) NULL     COMMENT 'Bắt buộc khi status = UNAVAILABLE — BR-ROOM-07',
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE COMMENT 'FALSE = đã xóa mềm — BR-ROOM-08',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          CHAR(36)     NULL,
    updated_at          DATETIME     NULL,
    updated_by          CHAR(36)     NULL,
    CONSTRAINT pk_rooms PRIMARY KEY (id),
    CONSTRAINT fk_rooms_tenant     FOREIGN KEY (tenant_id)    REFERENCES tenants (id),
    CONSTRAINT fk_rooms_location   FOREIGN KEY (location_id)  REFERENCES locations (id),
    CONSTRAINT fk_rooms_room_type  FOREIGN KEY (room_type_id) REFERENCES room_types (id),
    CONSTRAINT fk_rooms_created_by FOREIGN KEY (created_by)   REFERENCES users (id),
    CONSTRAINT fk_rooms_updated_by FOREIGN KEY (updated_by)   REFERENCES users (id),
    CONSTRAINT ck_rooms_status CHECK (status IN (
        'RESERVED','AVAILABLE','OCCUPIED','DIRTY','CLEANING','INSPECTION','UNAVAILABLE')),
    -- BR-ROOM-07: lý do bắt buộc khi và chỉ khi phòng đang Không khả dụng.
    CONSTRAINT ck_rooms_unavailable_reason CHECK (
        (status =  'UNAVAILABLE' AND unavailable_reason IS NOT NULL) OR
        (status <> 'UNAVAILABLE' AND unavailable_reason IS NULL)),
    CONSTRAINT ck_rooms_capacity CHECK (capacity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- BR-ROOM-05: số phòng duy nhất trong LOCATION (ERD đánh UK cột đơn = unique toàn
-- hệ thống, sai). Phòng đã xóa mềm không chiếm chỗ — cột sinh về NULL.
ALTER TABLE rooms
    ADD COLUMN active_room_number VARCHAR(20)
        GENERATED ALWAYS AS (CASE WHEN is_active THEN room_number END) VIRTUAL;

ALTER TABLE rooms
    ADD CONSTRAINT uk_rooms_location_active_number UNIQUE (location_id, active_room_number);

CREATE INDEX idx_rooms_location_status ON rooms (location_id, status);

-- ── housekeeping_tasks — BR-HK-01..12, DM-04, DM-05 ──────────────────────────
-- DM-05: CHECKOUT và STAYOVER dùng chung 1 bảng, phân biệt bằng task_type.
-- DM-04: gắn assigned_staff_id + assigned_date, KHÔNG gắn shift_id.
CREATE TABLE housekeeping_tasks (
    id                 CHAR(36)     NOT NULL,
    tenant_id          CHAR(36)     NOT NULL,
    location_id        CHAR(36)     NOT NULL,
    room_id            CHAR(36)     NOT NULL,
    task_type          VARCHAR(20)  NOT NULL COMMENT 'CHECKOUT | STAYOVER — BR-HK-05',
    status             VARCHAR(30)  NOT NULL COMMENT 'UNASSIGNED | IN_PROGRESS | PENDING_INSPECTION | COMPLETED | CANCELLED — BR-HK-06',
    assigned_staff_id  CHAR(36)     NULL,
    assigned_date      DATE         NULL     COMMENT 'Nhân viên phải có ca trong ngày này — BR-HK-03',
    assigned_by        CHAR(36)     NULL,
    assigned_at        DATETIME     NULL,
    completed_at       DATETIME     NULL,
    created_source     VARCHAR(30)  NOT NULL COMMENT 'CHECKOUT_AUTO | MANAGER_STAYOVER | INSPECTION_FAILED — BR-HK-12',
    parent_task_id     CHAR(36)     NULL     COMMENT 'Trỏ về task gốc khi sinh do kiểm tra không đạt — BR-HK-12',
    unassigned_reason  VARCHAR(30)  NULL     COMMENT 'Lý do gỡ NGƯỜI, task về UNASSIGNED — BR-HK-07',
    cancel_reason      VARCHAR(30)  NULL     COMMENT 'Lý do HỦY task — BR-HK-09 (phòng Không khả dụng), BR-HK-10 (khách check-out)',
    cancelled_at       DATETIME     NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         CHAR(36)     NULL,
    updated_at         DATETIME     NULL,
    updated_by         CHAR(36)     NULL,
    CONSTRAINT pk_housekeeping_tasks PRIMARY KEY (id),
    CONSTRAINT fk_hk_tenant      FOREIGN KEY (tenant_id)         REFERENCES tenants (id),
    CONSTRAINT fk_hk_location    FOREIGN KEY (location_id)       REFERENCES locations (id),
    CONSTRAINT fk_hk_room        FOREIGN KEY (room_id)           REFERENCES rooms (id),
    CONSTRAINT fk_hk_staff       FOREIGN KEY (assigned_staff_id) REFERENCES users (id),
    CONSTRAINT fk_hk_assigned_by FOREIGN KEY (assigned_by)       REFERENCES users (id),
    CONSTRAINT fk_hk_parent      FOREIGN KEY (parent_task_id)    REFERENCES housekeeping_tasks (id),
    CONSTRAINT fk_hk_created_by  FOREIGN KEY (created_by)        REFERENCES users (id),
    CONSTRAINT fk_hk_updated_by  FOREIGN KEY (updated_by)        REFERENCES users (id),
    CONSTRAINT ck_hk_task_type CHECK (task_type IN ('CHECKOUT','STAYOVER')),
    CONSTRAINT ck_hk_status CHECK (status IN (
        'UNASSIGNED','IN_PROGRESS','PENDING_INSPECTION','COMPLETED','CANCELLED')),
    CONSTRAINT ck_hk_created_source CHECK (created_source IN (
        'CHECKOUT_AUTO','MANAGER_STAYOVER','INSPECTION_FAILED')),
    CONSTRAINT ck_hk_unassigned_reason CHECK (
        unassigned_reason IS NULL OR
        unassigned_reason IN ('TRANSFER','TERMINATION','LEAVE_APPROVED','MANAGER_MANUAL')),
    CONSTRAINT ck_hk_cancel_reason CHECK (
        cancel_reason IS NULL OR
        cancel_reason IN ('ROOM_UNAVAILABLE','GUEST_CHECKED_OUT','MANAGER_MANUAL')),
    -- BR-HK-06: STAYOVER bỏ qua bước Chờ kiểm tra.
    CONSTRAINT ck_hk_stayover_no_inspection CHECK (
        NOT (task_type = 'STAYOVER' AND status = 'PENDING_INSPECTION')),
    -- Task chưa phân công thì không được có người làm, và ngược lại.
    CONSTRAINT ck_hk_assignment_pair CHECK (
        (status = 'UNASSIGNED' AND assigned_staff_id IS NULL) OR
        (status <> 'UNASSIGNED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- BR-HK-11: mỗi phòng tối đa 1 task ĐANG MỞ cho MỖI LOẠI task.
-- Task đã COMPLETED/CANCELLED không chiếm chỗ (cột sinh về NULL).
ALTER TABLE housekeeping_tasks
    ADD COLUMN open_task_key VARCHAR(80)
        GENERATED ALWAYS AS (
            CASE WHEN status IN ('UNASSIGNED','IN_PROGRESS','PENDING_INSPECTION')
                 THEN CONCAT(room_id, ':', task_type) END) VIRTUAL;

ALTER TABLE housekeeping_tasks
    ADD CONSTRAINT uk_hk_open_task_per_room_type UNIQUE (open_task_key);

CREATE INDEX idx_hk_location_status ON housekeeping_tasks (location_id, status);
CREATE INDEX idx_hk_staff_date      ON housekeeping_tasks (assigned_staff_id, assigned_date);

-- ── room_status_history — BR-ROOM-09, DM-06 ──────────────────────────────────
-- Mỗi lần đổi trạng thái sinh đúng 1 dòng. Lịch sử sử dụng phòng SUY RA từ chuỗi
-- này — không có entity RoomStay, không lưu thông tin khách.
CREATE TABLE room_status_history (
    id               CHAR(36)     NOT NULL,
    tenant_id        CHAR(36)     NOT NULL,
    room_id          CHAR(36)     NOT NULL,
    from_status      VARCHAR(20)  NULL COMMENT 'NULL ở bản ghi đầu tiên (phòng mới tạo)',
    to_status        VARCHAR(20)  NOT NULL,
    changed_by       CHAR(36)     NULL COMMENT 'NULL khi nguồn là SYSTEM',
    changed_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_source    VARCHAR(20)  NOT NULL COMMENT 'RECEPTION | HOUSEKEEPING | MANAGER | SYSTEM — cột "Người/nguồn" của BR-ROOM-02',
    reason           VARCHAR(500) NULL,
    related_task_id  CHAR(36)     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       CHAR(36)     NULL,
    updated_at       DATETIME     NULL,
    updated_by       CHAR(36)     NULL,
    CONSTRAINT pk_room_status_history PRIMARY KEY (id),
    CONSTRAINT fk_rsh_tenant       FOREIGN KEY (tenant_id)       REFERENCES tenants (id),
    CONSTRAINT fk_rsh_room         FOREIGN KEY (room_id)         REFERENCES rooms (id),
    CONSTRAINT fk_rsh_changed_by   FOREIGN KEY (changed_by)      REFERENCES users (id),
    CONSTRAINT fk_rsh_related_task FOREIGN KEY (related_task_id) REFERENCES housekeeping_tasks (id),
    CONSTRAINT fk_rsh_created_by   FOREIGN KEY (created_by)      REFERENCES users (id),
    CONSTRAINT fk_rsh_updated_by   FOREIGN KEY (updated_by)      REFERENCES users (id),
    CONSTRAINT ck_rsh_from_status CHECK (from_status IS NULL OR from_status IN (
        'RESERVED','AVAILABLE','OCCUPIED','DIRTY','CLEANING','INSPECTION','UNAVAILABLE')),
    CONSTRAINT ck_rsh_to_status CHECK (to_status IN (
        'RESERVED','AVAILABLE','OCCUPIED','DIRTY','CLEANING','INSPECTION','UNAVAILABLE')),
    CONSTRAINT ck_rsh_change_source CHECK (change_source IN (
        'RECEPTION','HOUSEKEEPING','MANAGER','SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_rsh_room_changed_at ON room_status_history (room_id, changed_at);

-- ── inspection_records — BR-HK-06, BR-HK-08, DM-06 ───────────────────────────
-- Kiểm tra KHÔNG đạt vẫn đưa task gốc sang COMPLETED; kết quả FAIL nằm ở đây và
-- next_task_id trỏ tới task tái kiểm tra vừa sinh (BR-HK-06, BR-HK-12).
CREATE TABLE inspection_records (
    id            CHAR(36)     NOT NULL,
    tenant_id     CHAR(36)     NOT NULL,
    task_id       CHAR(36)     NOT NULL,
    room_id       CHAR(36)     NOT NULL,
    inspector_id  CHAR(36)     NOT NULL COMMENT 'Manager — BR-ROOM-02',
    result        VARCHAR(10)  NOT NULL COMMENT 'PASS | FAIL',
    reason        VARCHAR(500) NULL     COMMENT 'Bắt buộc khi FAIL, text tự do — BR-HK-08',
    inspected_at  DATETIME     NOT NULL,
    next_task_id  CHAR(36)     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    CHAR(36)     NULL,
    updated_at    DATETIME     NULL,
    updated_by    CHAR(36)     NULL,
    CONSTRAINT pk_inspection_records PRIMARY KEY (id),
    CONSTRAINT fk_inspection_tenant     FOREIGN KEY (tenant_id)    REFERENCES tenants (id),
    CONSTRAINT fk_inspection_task       FOREIGN KEY (task_id)      REFERENCES housekeeping_tasks (id),
    CONSTRAINT fk_inspection_room       FOREIGN KEY (room_id)      REFERENCES rooms (id),
    CONSTRAINT fk_inspection_inspector  FOREIGN KEY (inspector_id) REFERENCES users (id),
    CONSTRAINT fk_inspection_next_task  FOREIGN KEY (next_task_id) REFERENCES housekeeping_tasks (id),
    CONSTRAINT fk_inspection_created_by FOREIGN KEY (created_by)   REFERENCES users (id),
    CONSTRAINT fk_inspection_updated_by FOREIGN KEY (updated_by)   REFERENCES users (id),
    CONSTRAINT ck_inspection_result CHECK (result IN ('PASS','FAIL')),
    -- BR-HK-08: kiểm tra không đạt BẮT BUỘC nhập lý do.
    CONSTRAINT ck_inspection_fail_reason CHECK (
        (result = 'FAIL' AND reason IS NOT NULL) OR result = 'PASS')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_inspection_task ON inspection_records (task_id);

-- =============================================================================
-- NHÓM 6 — Tài sản vật chất (BR-ASSET-*)
-- =============================================================================

-- ── fixed_assets — BR-ASSET-01..03, BR-ASSET-12..14, DM-10 ───────────────────
CREATE TABLE fixed_assets (
    id           CHAR(36)     NOT NULL,
    tenant_id    CHAR(36)     NOT NULL,
    location_id  CHAR(36)     NOT NULL COMMENT 'BR-ASSET-13 — không chuyển tài sản giữa các Location',
    category_id  CHAR(36)     NOT NULL,
    asset_code   VARCHAR(50)  NOT NULL COMMENT 'Hệ thống tự sinh, Manager sửa được — BR-ASSET-12',
    name         VARCHAR(255) NOT NULL,
    room_id      CHAR(36)     NULL,
    area_id      CHAR(36)     NULL,
    status       VARCHAR(20)  NOT NULL COMMENT 'GOOD | BROKEN | UNDER_REPAIR | DISPOSED — BR-ASSET-02',
    note         VARCHAR(500) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   CHAR(36)     NULL,
    updated_at   DATETIME     NULL,
    updated_by   CHAR(36)     NULL,
    CONSTRAINT pk_fixed_assets PRIMARY KEY (id),
    -- BR-ASSET-12: unique trong phạm vi LOCATION, KHÔNG phải toàn Tenant
    -- (ERD đánh UK cột đơn = unique toàn hệ thống, chặt hơn rule).
    CONSTRAINT uk_fixed_assets_location_code UNIQUE (location_id, asset_code),
    CONSTRAINT fk_fixed_assets_tenant     FOREIGN KEY (tenant_id)   REFERENCES tenants (id),
    CONSTRAINT fk_fixed_assets_location   FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_fixed_assets_category   FOREIGN KEY (category_id) REFERENCES asset_categories (id),
    CONSTRAINT fk_fixed_assets_room       FOREIGN KEY (room_id)     REFERENCES rooms (id),
    CONSTRAINT fk_fixed_assets_area       FOREIGN KEY (area_id)     REFERENCES areas (id),
    CONSTRAINT fk_fixed_assets_created_by FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT fk_fixed_assets_updated_by FOREIGN KEY (updated_by)  REFERENCES users (id),
    CONSTRAINT ck_fixed_assets_status CHECK (status IN ('GOOD','BROKEN','UNDER_REPAIR','DISPOSED')),
    -- BR-ASSET-03: gắn ĐÚNG 1 trong 2 — Phòng HOẶC Khu vực, không cả hai, không để trống.
    CONSTRAINT ck_fixed_assets_room_xor_area CHECK (
        (room_id IS NOT NULL AND area_id IS NULL) OR
        (room_id IS NULL     AND area_id IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_fixed_assets_location_status ON fixed_assets (location_id, status);
CREATE INDEX idx_fixed_assets_room            ON fixed_assets (room_id);
CREATE INDEX idx_fixed_assets_area            ON fixed_assets (area_id);

-- ── consumable_items — BR-ASSET-04, BR-ASSET-07, BR-ASSET-10, DM-11 ──────────
-- Tồn kho TĨNH, Manager sửa thẳng số lượng khi kiểm kê. Không có entity "Đợt kiểm
-- kê", không lưu lịch sử chênh lệch, không có ngưỡng cảnh báo (BR-ASSET-10).
CREATE TABLE consumable_items (
    id               CHAR(36)      NOT NULL,
    tenant_id        CHAR(36)      NOT NULL,
    location_id      CHAR(36)      NOT NULL COMMENT 'DM-11 — tài sản tiêu hao gắn ở cấp Location',
    category_id      CHAR(36)      NOT NULL,
    quantity         DECIMAL(12,2) NOT NULL DEFAULT 0,
    last_counted_at  DATETIME      NULL COMMENT 'Chỉ lưu mốc kiểm kê gần nhất — BR-ASSET-07',
    last_counted_by  CHAR(36)      NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       CHAR(36)      NULL,
    updated_at       DATETIME      NULL,
    updated_by       CHAR(36)      NULL,
    CONSTRAINT pk_consumable_items PRIMARY KEY (id),
    CONSTRAINT uk_consumable_items_location_category UNIQUE (location_id, category_id),
    CONSTRAINT fk_consumable_tenant     FOREIGN KEY (tenant_id)       REFERENCES tenants (id),
    CONSTRAINT fk_consumable_location   FOREIGN KEY (location_id)     REFERENCES locations (id),
    CONSTRAINT fk_consumable_category   FOREIGN KEY (category_id)     REFERENCES asset_categories (id),
    CONSTRAINT fk_consumable_counted_by FOREIGN KEY (last_counted_by) REFERENCES users (id),
    CONSTRAINT fk_consumable_created_by FOREIGN KEY (created_by)      REFERENCES users (id),
    CONSTRAINT fk_consumable_updated_by FOREIGN KEY (updated_by)      REFERENCES users (id),
    CONSTRAINT ck_consumable_quantity CHECK (quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ── damage_reports — BR-ASSET-05, BR-ASSET-06, BR-ASSET-11, DM-12 ────────────
-- DM-12: CHỈ áp dụng cho tài sản cố định, không đa hình sang phòng/khu vực/tiêu hao.
-- ERD chỉ có 5 cột (id, tenant_id, location_id, fixed_asset_id, reporter_id) —
-- thiếu status/description/reported_at nên BR-ASSET-11 và BR-ASSET-06 không chạy
-- được; bổ sung tại đây.
CREATE TABLE damage_reports (
    id              CHAR(36)     NOT NULL,
    tenant_id       CHAR(36)     NOT NULL,
    location_id     CHAR(36)     NOT NULL,
    fixed_asset_id  CHAR(36)     NOT NULL,
    reporter_id     CHAR(36)     NOT NULL COMMENT 'Lễ tân hoặc Dọn dẹp — BR-ASSET-05',
    description     VARCHAR(500) NOT NULL COMMENT 'Manager cần biết hỏng gì để quyết định — BR-ASSET-06',
    status          VARCHAR(20)  NOT NULL DEFAULT 'NEW' COMMENT 'NEW | RESOLVED — BR-ASSET-11',
    reported_at     DATETIME     NOT NULL,
    resolved_by     CHAR(36)     NULL,
    resolved_at     DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      CHAR(36)     NULL,
    updated_at      DATETIME     NULL,
    updated_by      CHAR(36)     NULL,
    CONSTRAINT pk_damage_reports PRIMARY KEY (id),
    CONSTRAINT fk_damage_tenant      FOREIGN KEY (tenant_id)      REFERENCES tenants (id),
    CONSTRAINT fk_damage_location    FOREIGN KEY (location_id)    REFERENCES locations (id),
    CONSTRAINT fk_damage_fixed_asset FOREIGN KEY (fixed_asset_id) REFERENCES fixed_assets (id),
    CONSTRAINT fk_damage_reporter    FOREIGN KEY (reporter_id)    REFERENCES users (id),
    CONSTRAINT fk_damage_resolved_by FOREIGN KEY (resolved_by)    REFERENCES users (id),
    CONSTRAINT fk_damage_created_by  FOREIGN KEY (created_by)     REFERENCES users (id),
    CONSTRAINT fk_damage_updated_by  FOREIGN KEY (updated_by)     REFERENCES users (id),
    CONSTRAINT ck_damage_status CHECK (status IN ('NEW','RESOLVED')),
    CONSTRAINT ck_damage_resolved_pair CHECK (
        (status = 'RESOLVED' AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL) OR
        (status = 'NEW'      AND resolved_by IS NULL     AND resolved_at IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- DM-16 thay thế entity Notification bằng "màn hình tự truy vấn danh sách đang chờ".
CREATE INDEX idx_damage_reports_location_status ON damage_reports (location_id, status);

-- =============================================================================
-- SEED — cấu hình cấp hệ thống (BR-SAAS-08, BR-SAAS-10, BR-SAAS-02)
-- =============================================================================

INSERT INTO system_config (id, trial_days, grace_period_days)
VALUES ('00000000-0000-0000-0000-000000000001', 30, 7);

INSERT INTO pricing_config (id, price_per_location, price_per_user, price_per_room, effective_from)
VALUES ('00000000-0000-0000-0000-000000000001', 500000, 50000, 20000, '2025-01-01');
