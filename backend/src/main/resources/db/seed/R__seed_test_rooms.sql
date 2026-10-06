-- =============================================================================
-- DỮ LIỆU TEST — loại phòng + phòng tại "Khách sạn Test Hà Nội" để làm Housekeeping
-- trong lúc module Quản lý phòng (BR-ROOM) chưa có API tạo phòng.
-- CHỈ dùng cho dev/demo — cùng cơ chế với R__seed_test_accounts.sql.
--
-- Flyway chạy các repeatable migration theo thứ tự mô tả: "seed test accounts" chạy
-- trước "seed test rooms", nên Tenant/Location đã có sẵn.
--
--   Phòng  Tầng  Loại      Trạng thái      Dùng để test
--   101    1     Standard  AVAILABLE       —
--   102    1     Standard  OCCUPIED        Khách trả phòng → tự sinh task CHECKOUT
--   103    1     Deluxe    OCCUPIED        Khách trả phòng → tự sinh task CHECKOUT
--   201    2     Standard  DIRTY           Có sẵn task CHECKOUT chưa phân công
--   202    2     Deluxe    DIRTY           Có sẵn task CHECKOUT chưa phân công
--   203    2     Standard  UNAVAILABLE     —
--
-- Chưa seed room_status_history — lịch sử trạng thái phòng thuộc module Quản lý phòng.
-- Cuối file: tài sản cố định mẫu gắn phòng 201 (chuyển từ R__seed_test_assets.sql).
-- =============================================================================

SET NAMES utf8mb4;

-- ── Loại phòng — danh mục cấp Tenant (BR-ORG-11) ──────────────────────────────
INSERT INTO room_types (id, tenant_id, name) VALUES
    ('60000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Standard'),
    ('60000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'Deluxe')
ON DUPLICATE KEY UPDATE id = id;

-- ── Phòng — Location Hà Nội ───────────────────────────────────────────────────
INSERT INTO rooms (id, tenant_id, location_id, room_number, floor, room_type_id,
                   capacity, status, unavailable_reason)
VALUES
    ('70000000-0000-0000-0000-000000000101', '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001', '101', '1',
     '60000000-0000-0000-0000-000000000001', 2, 'AVAILABLE', NULL),
    ('70000000-0000-0000-0000-000000000102', '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001', '102', '1',
     '60000000-0000-0000-0000-000000000001', 2, 'OCCUPIED', NULL),
    ('70000000-0000-0000-0000-000000000103', '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001', '103', '1',
     '60000000-0000-0000-0000-000000000002', 3, 'OCCUPIED', NULL),
    ('70000000-0000-0000-0000-000000000201', '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001', '201', '2',
     '60000000-0000-0000-0000-000000000001', 2, 'DIRTY', NULL),
    ('70000000-0000-0000-0000-000000000202', '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001', '202', '2',
     '60000000-0000-0000-0000-000000000002', 3, 'DIRTY', NULL),
    ('70000000-0000-0000-0000-000000000203', '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001', '203', '2',
     '60000000-0000-0000-0000-000000000001', 2, 'UNAVAILABLE', 'Hỏng điều hòa, chờ sửa')
ON DUPLICATE KEY UPDATE id = id;

-- ── Task CHECKOUT cho phòng đang Chờ dọn — BR-HK-01: phòng vào Chờ dọn thì phải có
-- task chưa phân công, seed phải giữ đúng bất biến này.
INSERT INTO housekeeping_tasks (id, tenant_id, location_id, room_id, task_type, status,
                                created_source)
VALUES
    ('80000000-0000-0000-0000-000000000201', '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001', '70000000-0000-0000-0000-000000000201',
     'CHECKOUT', 'UNASSIGNED', 'CHECKOUT_AUTO'),
    ('80000000-0000-0000-0000-000000000202', '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001', '70000000-0000-0000-0000-000000000202',
     'CHECKOUT', 'UNASSIGNED', 'CHECKOUT_AUTO')
ON DUPLICATE KEY UPDATE id = id;

-- ── Tài sản cố định gắn phòng 201 (chuyển từ R__seed_test_assets.sql) ─────────
-- Phải chạy SAU khi đã có phòng: Flyway chạy "seed test assets" trước "seed test rooms" (theo
-- tên), nên để ở file kia thì database mới không khởi động được. Danh mục 1111…/2222… do
-- R__seed_test_accounts.sql tạo — file đó chạy trước cả hai.
INSERT INTO fixed_assets (id, tenant_id, location_id, category_id, room_id, asset_code, name, status, created_at, updated_at)
VALUES
    ('aaaaaaaa-1111-1111-1111-111111111111', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     '22222222-2222-2222-2222-222222222222', '70000000-0000-0000-0000-000000000201', 'TS-CD-0001', 'Smart TV 50 inch LG', 'GOOD', NOW(), NOW()),
    ('aaaaaaaa-2222-2222-2222-222222222222', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     '22222222-2222-2222-2222-222222222222', '70000000-0000-0000-0000-000000000201', 'TS-CD-0002', 'Smart TV 50 inch Samsung', 'BROKEN', NOW(), NOW()),
    ('aaaaaaaa-3333-3333-3333-333333333333', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     '11111111-1111-1111-1111-111111111111', '70000000-0000-0000-0000-000000000201', 'TS-CD-0003', 'Tủ lạnh minibar Aqua', 'UNDER_REPAIR', NOW(), NOW()),
    ('aaaaaaaa-4444-4444-4444-444444444444', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     '11111111-1111-1111-1111-111111111111', '70000000-0000-0000-0000-000000000201', 'TS-CD-0004', 'Tủ lạnh minibar Electrolux', 'DISPOSED', NOW(), NOW())
ON DUPLICATE KEY UPDATE id = id;
