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
--   102    1     Standard  OCCUPIED        Tạo task STAYOVER
--   103    1     Deluxe    OCCUPIED        Tạo task STAYOVER
--   201    2     Standard  DIRTY           Có sẵn task CHECKOUT chưa phân công
--   202    2     Deluxe    DIRTY           Có sẵn task CHECKOUT chưa phân công
--   203    2     Standard  UNAVAILABLE     —
--
-- Chưa seed room_status_history — lịch sử trạng thái phòng thuộc module Quản lý phòng.
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
