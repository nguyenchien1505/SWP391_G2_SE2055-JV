-- =============================================================================
-- DỮ LIỆU TEST — mỗi actor của BR-PERM-01..06 một tài khoản đăng nhập được ngay.
-- CHỈ dùng cho dev/demo: bỏ "classpath:db/seed" khỏi spring.flyway.locations
-- (application.yaml) khi deploy thật.
--
-- Mật khẩu chung: Test@123  (must_change_password = FALSE để vào thẳng hệ thống)
--
--   Email                   Role            Quyền nghiệp vụ         Ghi chú
--   admin@swp391.test       PLATFORM_ADMIN  -                       Đứng ngoài mọi Tenant
--   trantrungd83@gmail.com  DIRECTOR        -                       Dùng test Google OAuth
--   manager@swp391.test     MANAGER         -                       Quản lý Location Hà Nội
--   letan@swp391.test       STAFF           Lễ tân                  Vị trí Lễ tân
--   dondep@swp391.test      STAFF           Dọn dẹp                 Vị trí Buồng phòng
--   kiemnhiem@swp391.test   STAFF           Lễ tân + Dọn dẹp        Vị trí Lễ tân, tick thêm Dọn dẹp
--   kythuat@swp391.test     STAFF           (chỉ quyền chung)       Vị trí loại Khác
--
-- Repeatable migration (R__): chạy SAU các migration V*, và chạy lại mỗi khi file
-- này đổi nội dung. Mọi INSERT dùng "ON DUPLICATE KEY UPDATE id = id" — thêm dòng
-- còn thiếu, KHÔNG ghi đè dòng đã có (mật khẩu tester đã đổi được giữ nguyên).
-- Muốn áp lại toàn bộ giá trị ở đây thì xóa và tạo lại database.
-- =============================================================================

SET NAMES utf8mb4;

-- ── Tenant — BR-SAAS-13: email liên hệ = email đăng nhập của Giám đốc ─────────
INSERT INTO tenants (id, name, contact_email, contact_phone, status)
VALUES ('10000000-0000-0000-0000-000000000001', 'Chuỗi khách sạn Test SWP391',
        'trantrungd83@gmail.com', '0900000001', 'TRIAL')
ON DUPLICATE KEY UPDATE id = id;

-- Gói dùng thử 30 ngày, đơn giá snapshot theo pricing_config — BR-SAAS-05, BR-SAAS-08.
INSERT INTO subscriptions (id, tenant_id, quota_location, quota_user, quota_room,
                           price_per_location, price_per_user, price_per_room,
                           is_trial, trial_ends_at)
VALUES ('10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
        3, 20, 50, 500000, 50000, 20000,
        TRUE, DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY))
ON DUPLICATE KEY UPDATE id = id;

-- Policy mặc định — BR-SCH-20.
INSERT INTO schedule_policies (id, tenant_id)
VALUES ('10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001')
ON DUPLICATE KEY UPDATE id = id;

-- ── Location — đã có Manager nên OPERATIONAL (DM-13) ──────────────────────────
INSERT INTO locations (id, tenant_id, name, address, phone, star_rating, timezone, status)
VALUES ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
        'Khách sạn Test Hà Nội', '1 Tràng Tiền, Hoàn Kiếm, Hà Nội', '0900000002',
        3, 'Asia/Ho_Chi_Minh', 'OPERATIONAL')
ON DUPLICATE KEY UPDATE id = id;

-- ── Department / Position — mỗi Loại Position một chức danh (BR-ORG-08) ───────
INSERT INTO departments (id, tenant_id, name) VALUES
    ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Lễ tân'),
    ('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'Buồng phòng'),
    ('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'Kỹ thuật')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO positions (id, tenant_id, department_id, name, position_type) VALUES
    ('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     '30000000-0000-0000-0000-000000000001', 'Nhân viên lễ tân', 'RECEPTION'),
    ('40000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
     '30000000-0000-0000-0000-000000000002', 'Nhân viên buồng phòng', 'HOUSEKEEPING'),
    ('40000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001',
     '30000000-0000-0000-0000-000000000003', 'Nhân viên kỹ thuật', 'OTHER')
ON DUPLICATE KEY UPDATE id = id;

-- ── Tài khoản — hash BCrypt của "Test@123" ────────────────────────────────────
-- Trường bắt buộc theo vai trò: BR-USER-01 (Staff), BR-USER-05 (Manager, Giám đốc).
INSERT INTO users (id, tenant_id, role, email, password_hash, must_change_password, status,
                   full_name, phone, location_id, position_id,
                   start_work_date, date_of_birth, gender, address, avatar_url)
VALUES
    ('50000000-0000-0000-0000-000000000001', NULL, 'PLATFORM_ADMIN', 'admin@swp391.test',
     '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE',
     'Admin Platform', '0900000010', NULL, NULL,
     NULL, NULL, NULL, NULL, NULL),

    ('50000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'DIRECTOR',
     'trantrungd83@gmail.com',
     '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE',
     'Giám đốc', '0900000011', NULL, NULL,
     NULL, NULL, NULL, NULL, NULL),

    ('50000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'MANAGER',
     'manager@swp391.test',
     '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE',
     'Quản lý Hà Nội', '0900000012', '20000000-0000-0000-0000-000000000001', NULL,
     '2026-01-01', '1990-03-15', 'MALE', '10 Hàng Bài, Hoàn Kiếm, Hà Nội',
     'https://ui-avatars.com/api/?name=Manager'),

    ('50000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', 'STAFF',
     'letan@swp391.test',
     '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE',
     'Lễ tân Test', '0900000013', '20000000-0000-0000-0000-000000000001',
     '40000000-0000-0000-0000-000000000001',
     '2026-02-01', '1998-07-20', 'FEMALE', '25 Lý Thường Kiệt, Hoàn Kiếm, Hà Nội',
     'https://ui-avatars.com/api/?name=Le+Tan'),

    ('50000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', 'STAFF',
     'dondep@swp391.test',
     '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE',
     'Dọn dẹp Test', '0900000014', '20000000-0000-0000-0000-000000000001',
     '40000000-0000-0000-0000-000000000002',
     '2026-02-01', '1996-11-05', 'FEMALE', '8 Bà Triệu, Hai Bà Trưng, Hà Nội',
     'https://ui-avatars.com/api/?name=Don+Dep'),

    ('50000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000001', 'STAFF',
     'kythuat@swp391.test',
     '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE',
     'Kỹ thuật Test', '0900000015', '20000000-0000-0000-0000-000000000001',
     '40000000-0000-0000-0000-000000000003',
     '2026-03-01', '1993-01-30', 'MALE', '50 Tôn Đức Thắng, Đống Đa, Hà Nội',
     'https://ui-avatars.com/api/?name=Ky+Thuat'),

    ('50000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000001', 'STAFF',
     'kiemnhiem@swp391.test',
     '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE',
     'Kiêm nhiệm Test', '0900000016', '20000000-0000-0000-0000-000000000001',
     '40000000-0000-0000-0000-000000000001',
     '2026-03-15', '1997-04-12', 'FEMALE', '12 Hai Bà Trưng, Hoàn Kiếm, Hà Nội',
     'https://ui-avatars.com/api/?name=Kiem+Nhiem')
ON DUPLICATE KEY UPDATE id = id;

-- ── Quyền nghiệp vụ Manager tick cho từng nhân viên (bảng user_permissions — V2) ──
-- "Kỹ thuật" không có dòng nào: chỉ quyền chung (BR-PERM-06).
INSERT INTO user_permissions (user_id, permission) VALUES
    ('50000000-0000-0000-0000-000000000004', 'RECEPTION'),
    ('50000000-0000-0000-0000-000000000005', 'HOUSEKEEPING'),
    ('50000000-0000-0000-0000-000000000007', 'RECEPTION'),
    ('50000000-0000-0000-0000-000000000007', 'HOUSEKEEPING')
ON DUPLICATE KEY UPDATE permission = permission;

-- ── Dữ liệu test cho Asset Categories ──────────────────────────────────────
INSERT INTO asset_categories (id, tenant_id, name, asset_kind, purpose, unit, is_active, created_at, updated_at)
VALUES 
    ('11111111-1111-1111-1111-111111111111', '10000000-0000-0000-0000-000000000001', 'TS-CD-01 (Tủ lạnh mini bar)', 'FIXED', 'GUEST_USE', NULL, TRUE, NOW(), NOW()),
    ('22222222-2222-2222-2222-222222222222', '10000000-0000-0000-0000-000000000001', 'TS-CD-02 (Smart TV 50 inch)', 'FIXED', 'GUEST_USE', NULL, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE id = id;
