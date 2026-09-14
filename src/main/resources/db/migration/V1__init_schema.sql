-- Full schema for the hotel management SaaS platform, derived from docs/Hotel_Management_ERD.drawio.
-- Tables are created in dependency order. created_by/deleted_by audit columns on tenants,
-- locations and service_packages reference users(id) but users depends on tenants/locations
-- structurally, so those three FKs are added via ALTER TABLE right after `users` is created
-- to avoid a circular dependency at CREATE TABLE time.

-- ── tenants ──────────────────────────────────────────────────────────────────
CREATE TABLE tenants (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(255)  NOT NULL,
    contact_email  VARCHAR(255),
    contact_phone  VARCHAR(30),
    tax_code       VARCHAR(50),
    status         VARCHAR(20),
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     BIGINT,
    is_deleted     BIT(1)        NOT NULL DEFAULT b'0',
    deleted_at     DATETIME,
    deleted_by     BIGINT
);

-- ── service_packages ─────────────────────────────────────────────────────────
CREATE TABLE service_packages (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(50)  UNIQUE,
    name             VARCHAR(100) NOT NULL,
    description      TEXT,
    price            DECIMAL(12,2),
    currency         VARCHAR(10),
    billing_cycle    VARCHAR(20),
    duration_months  INT,
    max_locations    INT,
    max_users        INT,
    max_rooms        INT,
    status           VARCHAR(20),
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       BIGINT,
    is_deleted       BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at       DATETIME,
    deleted_by       BIGINT
);

-- ── locations ─────────────────────────────────────────────────────────────────
CREATE TABLE locations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    address     VARCHAR(500),
    phone       VARCHAR(30),
    status      VARCHAR(20),
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    is_deleted  BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at  DATETIME,
    deleted_by  BIGINT,
    CONSTRAINT fk_locations_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

-- ── users ─────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id      BIGINT,
    location_id    BIGINT,
    role           VARCHAR(20)  NOT NULL,
    full_name      VARCHAR(255),
    email          VARCHAR(255) UNIQUE,
    password_hash  VARCHAR(255),
    phone          VARCHAR(30),
    status         VARCHAR(20),
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     BIGINT,
    is_deleted     BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at     DATETIME,
    deleted_by     BIGINT,
    CONSTRAINT fk_users_tenant     FOREIGN KEY (tenant_id)   REFERENCES tenants (id),
    CONSTRAINT fk_users_location   FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT fk_users_deleted_by FOREIGN KEY (deleted_by)  REFERENCES users (id)
);

-- Deferred audit FKs (users didn't exist yet when these tables were created).
ALTER TABLE tenants
    ADD CONSTRAINT fk_tenants_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_tenants_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id);

ALTER TABLE service_packages
    ADD CONSTRAINT fk_service_packages_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_service_packages_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id);

ALTER TABLE locations
    ADD CONSTRAINT fk_locations_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT fk_locations_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id);

-- ── subscriptions ─────────────────────────────────────────────────────────────
CREATE TABLE subscriptions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT   NOT NULL,
    service_package_id  BIGINT   NOT NULL,
    start_date          DATE     NOT NULL,
    end_date            DATE     NOT NULL,
    auto_renew          BIT(1)   NOT NULL DEFAULT b'0',
    status              VARCHAR(20),
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT,
    is_deleted          BIT(1)   NOT NULL DEFAULT b'0',
    deleted_at          DATETIME,
    deleted_by          BIGINT,
    CONSTRAINT fk_subscriptions_tenant          FOREIGN KEY (tenant_id)          REFERENCES tenants (id),
    CONSTRAINT fk_subscriptions_service_package FOREIGN KEY (service_package_id) REFERENCES service_packages (id),
    CONSTRAINT fk_subscriptions_created_by      FOREIGN KEY (created_by)         REFERENCES users (id),
    CONSTRAINT fk_subscriptions_deleted_by      FOREIGN KEY (deleted_by)         REFERENCES users (id)
);

-- ── invoices ──────────────────────────────────────────────────────────────────
CREATE TABLE invoices (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL,
    subscription_id  BIGINT,
    invoice_no       VARCHAR(50)  UNIQUE,
    amount           DECIMAL(12,2),
    tax_amount       DECIMAL(12,2),
    total_amount     DECIMAL(12,2),
    currency         VARCHAR(10),
    status           VARCHAR(20),
    issued_at        DATETIME,
    due_date         DATE,
    paid_at          DATETIME,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       BIGINT,
    is_deleted       BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at       DATETIME,
    deleted_by       BIGINT,
    CONSTRAINT fk_invoices_tenant       FOREIGN KEY (tenant_id)       REFERENCES tenants (id),
    CONSTRAINT fk_invoices_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT fk_invoices_created_by   FOREIGN KEY (created_by)      REFERENCES users (id),
    CONSTRAINT fk_invoices_deleted_by   FOREIGN KEY (deleted_by)      REFERENCES users (id)
);

-- ── subscription_renewals ────────────────────────────────────────────────────
CREATE TABLE subscription_renewals (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id     BIGINT      NOT NULL,
    invoice_id          BIGINT,
    previous_end_date   DATE,
    new_end_date        DATE,
    renewal_type        VARCHAR(20),
    status              VARCHAR(20),
    renewed_by          BIGINT,
    renewed_at          DATETIME,
    CONSTRAINT fk_subscription_renewals_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT fk_subscription_renewals_invoice       FOREIGN KEY (invoice_id)      REFERENCES invoices (id),
    CONSTRAINT fk_subscription_renewals_renewed_by    FOREIGN KEY (renewed_by)      REFERENCES users (id)
);

-- ── payments ──────────────────────────────────────────────────────────────────
CREATE TABLE payments (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id       BIGINT       NOT NULL,
    payment_method   VARCHAR(30),
    gateway_code     VARCHAR(30),
    amount           DECIMAL(12,2),
    currency         VARCHAR(10),
    status           VARCHAR(20),
    paid_at          DATETIME,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       BIGINT,
    is_deleted       BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at       DATETIME,
    deleted_by       BIGINT,
    CONSTRAINT fk_payments_invoice    FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_payments_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id)
);

-- ── room_types ────────────────────────────────────────────────────────────────
CREATE TABLE room_types (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    description  TEXT,
    base_price   DECIMAL(10,2),
    capacity     INT,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   BIGINT,
    is_deleted   BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at   DATETIME,
    deleted_by   BIGINT,
    CONSTRAINT fk_room_types_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_room_types_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id)
);

-- ── room_statuses ─────────────────────────────────────────────────────────────
CREATE TABLE room_statuses (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(30)  UNIQUE,
    name           VARCHAR(50)  NOT NULL,
    description    VARCHAR(255),
    display_order  INT,
    is_active      BIT(1)       NOT NULL DEFAULT b'1',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     BIGINT,
    is_deleted     BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at     DATETIME,
    deleted_by     BIGINT,
    CONSTRAINT fk_room_statuses_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_room_statuses_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id)
);

-- ── location_room_types ───────────────────────────────────────────────────────
CREATE TABLE location_room_types (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    location_id     BIGINT NOT NULL,
    room_type_id    BIGINT NOT NULL,
    override_price  DECIMAL(10,2),
    status          VARCHAR(20),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    is_deleted      BIT(1)   NOT NULL DEFAULT b'0',
    CONSTRAINT fk_location_room_types_location  FOREIGN KEY (location_id)  REFERENCES locations (id),
    CONSTRAINT fk_location_room_types_room_type FOREIGN KEY (room_type_id) REFERENCES room_types (id),
    CONSTRAINT fk_location_room_types_created_by FOREIGN KEY (created_by)  REFERENCES users (id)
);

-- ── rooms ─────────────────────────────────────────────────────────────────────
CREATE TABLE rooms (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    location_id             BIGINT      NOT NULL,
    location_room_type_id   BIGINT      NOT NULL,
    current_status_id       BIGINT      NOT NULL,
    room_number             VARCHAR(20) NOT NULL,
    floor                   VARCHAR(10),
    created_at              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              BIGINT,
    is_deleted              BIT(1)      NOT NULL DEFAULT b'0',
    deleted_at              DATETIME,
    deleted_by              BIGINT,
    CONSTRAINT fk_rooms_location           FOREIGN KEY (location_id)           REFERENCES locations (id),
    CONSTRAINT fk_rooms_location_room_type FOREIGN KEY (location_room_type_id) REFERENCES location_room_types (id),
    CONSTRAINT fk_rooms_current_status     FOREIGN KEY (current_status_id)     REFERENCES room_statuses (id),
    CONSTRAINT fk_rooms_created_by         FOREIGN KEY (created_by)            REFERENCES users (id),
    CONSTRAINT fk_rooms_deleted_by         FOREIGN KEY (deleted_by)            REFERENCES users (id)
);

-- ── work_schedule_policies ────────────────────────────────────────────────────
CREATE TABLE work_schedule_policies (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    location_id           BIGINT       NOT NULL,
    name                  VARCHAR(100),
    max_shifts_per_week   INT,
    max_hours_per_week    INT,
    min_rest_hours        INT,
    effective_from        DATE,
    effective_to          DATE,
    status                VARCHAR(20),
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            BIGINT,
    is_deleted            BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at            DATETIME,
    deleted_by            BIGINT,
    CONSTRAINT fk_work_schedule_policies_location   FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_work_schedule_policies_created_by FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT fk_work_schedule_policies_deleted_by FOREIGN KEY (deleted_by)  REFERENCES users (id)
);

-- ── shifts ────────────────────────────────────────────────────────────────────
CREATE TABLE shifts (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT   NOT NULL,
    shift_type  VARCHAR(20),
    shift_date  DATE     NOT NULL,
    start_time  TIME,
    end_time    TIME,
    status      VARCHAR(20),
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    is_deleted  BIT(1)   NOT NULL DEFAULT b'0',
    deleted_at  DATETIME,
    deleted_by  BIGINT,
    CONSTRAINT fk_shifts_user       FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT fk_shifts_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_shifts_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id)
);

-- ── attendance_logs ───────────────────────────────────────────────────────────
CREATE TABLE attendance_logs (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_id       BIGINT   NOT NULL,
    user_id        BIGINT   NOT NULL,
    check_in_at    DATETIME,
    check_out_at   DATETIME,
    check_method   VARCHAR(20),
    is_late        BIT(1)   NOT NULL DEFAULT b'0',
    note           VARCHAR(255),
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_logs_shift FOREIGN KEY (shift_id) REFERENCES shifts (id),
    CONSTRAINT fk_attendance_logs_user  FOREIGN KEY (user_id)  REFERENCES users (id)
);

-- ── cleaning_assignments ──────────────────────────────────────────────────────
CREATE TABLE cleaning_assignments (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_id       BIGINT   NOT NULL,
    room_id        BIGINT   NOT NULL,
    status         VARCHAR(20),
    assigned_at    DATETIME,
    completed_at   DATETIME,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     BIGINT,
    is_deleted     BIT(1)   NOT NULL DEFAULT b'0',
    CONSTRAINT fk_cleaning_assignments_shift      FOREIGN KEY (shift_id)   REFERENCES shifts (id),
    CONSTRAINT fk_cleaning_assignments_room       FOREIGN KEY (room_id)    REFERENCES rooms (id),
    CONSTRAINT fk_cleaning_assignments_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

-- ── asset_categories ──────────────────────────────────────────────────────────
CREATE TABLE asset_categories (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    location_id  BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    type         VARCHAR(20),
    description  VARCHAR(255),
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   BIGINT,
    is_deleted   BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at   DATETIME,
    deleted_by   BIGINT,
    CONSTRAINT fk_asset_categories_location   FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_asset_categories_created_by FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT fk_asset_categories_deleted_by FOREIGN KEY (deleted_by)  REFERENCES users (id)
);

-- ── assets ────────────────────────────────────────────────────────────────────
CREATE TABLE assets (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    location_id      BIGINT       NOT NULL,
    room_id          BIGINT,
    code             VARCHAR(50),
    name             VARCHAR(255) NOT NULL,
    is_consumable    BIT(1)       NOT NULL DEFAULT b'0',
    quantity         INT,
    unit             VARCHAR(20),
    asset_condition  VARCHAR(20),
    purchase_date    DATE,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       BIGINT,
    is_deleted       BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at       DATETIME,
    deleted_by       BIGINT,
    CONSTRAINT fk_assets_location   FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_assets_room       FOREIGN KEY (room_id)     REFERENCES rooms (id),
    CONSTRAINT fk_assets_created_by FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT fk_assets_deleted_by FOREIGN KEY (deleted_by)  REFERENCES users (id)
);

-- ── asset_category_mappings ───────────────────────────────────────────────────
CREATE TABLE asset_category_mappings (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id            BIGINT   NOT NULL,
    asset_category_id   BIGINT   NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT,
    is_deleted          BIT(1)   NOT NULL DEFAULT b'0',
    CONSTRAINT fk_asset_category_mappings_asset          FOREIGN KEY (asset_id)          REFERENCES assets (id),
    CONSTRAINT fk_asset_category_mappings_asset_category FOREIGN KEY (asset_category_id) REFERENCES asset_categories (id),
    CONSTRAINT fk_asset_category_mappings_created_by     FOREIGN KEY (created_by)         REFERENCES users (id)
);

-- ── asset_issue_reports ───────────────────────────────────────────────────────
CREATE TABLE asset_issue_reports (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id       BIGINT   NOT NULL,
    room_id        BIGINT,
    reported_by    BIGINT   NOT NULL,
    issue_type     VARCHAR(20),
    description    TEXT,
    status         VARCHAR(20),
    reported_at    DATETIME,
    resolved_by    BIGINT,
    resolved_at    DATETIME,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     BIGINT,
    is_deleted     BIT(1)   NOT NULL DEFAULT b'0',
    deleted_at     DATETIME,
    deleted_by     BIGINT,
    CONSTRAINT fk_asset_issue_reports_asset       FOREIGN KEY (asset_id)     REFERENCES assets (id),
    CONSTRAINT fk_asset_issue_reports_room        FOREIGN KEY (room_id)      REFERENCES rooms (id),
    CONSTRAINT fk_asset_issue_reports_reported_by FOREIGN KEY (reported_by)  REFERENCES users (id),
    CONSTRAINT fk_asset_issue_reports_resolved_by FOREIGN KEY (resolved_by)  REFERENCES users (id),
    CONSTRAINT fk_asset_issue_reports_created_by  FOREIGN KEY (created_by)   REFERENCES users (id),
    CONSTRAINT fk_asset_issue_reports_deleted_by  FOREIGN KEY (deleted_by)   REFERENCES users (id)
);

-- ── asset_inventory_checks ────────────────────────────────────────────────────
CREATE TABLE asset_inventory_checks (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id             BIGINT   NOT NULL,
    asset_id            BIGINT   NOT NULL,
    expected_quantity   INT,
    actual_quantity     INT,
    difference          INT,
    note                VARCHAR(255),
    checked_by          BIGINT,
    checked_at          DATETIME,
    CONSTRAINT fk_asset_inventory_checks_room       FOREIGN KEY (room_id)    REFERENCES rooms (id),
    CONSTRAINT fk_asset_inventory_checks_asset      FOREIGN KEY (asset_id)   REFERENCES assets (id),
    CONSTRAINT fk_asset_inventory_checks_checked_by FOREIGN KEY (checked_by) REFERENCES users (id)
);
