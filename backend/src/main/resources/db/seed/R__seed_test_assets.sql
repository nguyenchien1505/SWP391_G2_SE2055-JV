-- Seed Data cho Fixed Assets (Tài sản cố định) và Consumables (Vật tư tiêu hao)
-- Script này sẽ chèn một số tài sản mẫu để test hiển thị trên UI.
--
-- Tài sản cố định gắn phòng 201 nằm ở CUỐI R__seed_test_rooms.sql: Flyway chạy các file R__ theo
-- thứ tự tên ("seed test assets" trước "seed test rooms"), nên trên database mới lúc file này chạy
-- chưa có phòng nào để gắn — app dừng ngay khi khởi động.

-- Sử dụng các ID Category và Location đã được seed từ trước
SET @tenant_id = '10000000-0000-0000-0000-000000000001';
SET @location_id = '20000000-0000-0000-0000-000000000001'; -- Sao Mai Nha Trang

-- 1. Insert Asset Category cho Vật tư tiêu hao (Vì hiện tại seed test accounts chỉ có category cho FIXED)
INSERT INTO asset_categories (id, tenant_id, name, asset_kind, purpose, unit, is_active, created_at, updated_at)
VALUES 
    ('33333333-3333-3333-3333-333333333333', @tenant_id, 'Nước suối Aquafina', 'CONSUMABLE', 'GUEST_USE', 'Chai', TRUE, NOW(), NOW()),
    ('44444444-4444-4444-4444-444444444444', @tenant_id, 'Khăn tắm', 'CONSUMABLE', 'GUEST_USE', 'Cái', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE id = id;

-- 2. Insert Consumable Items (Vật tư tiêu hao)
INSERT INTO consumable_items (id, tenant_id, location_id, category_id, quantity, last_counted_at, created_at, updated_at)
VALUES
    ('bbbbbbbb-1111-1111-1111-111111111111', @tenant_id, @location_id, '33333333-3333-3333-3333-333333333333', 150.00, NOW(), NOW(), NOW()),
    ('bbbbbbbb-2222-2222-2222-222222222222', @tenant_id, @location_id, '44444444-4444-4444-4444-444444444444', 50.00, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE id = id;
