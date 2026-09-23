-- Seed Data cho Fixed Assets (Tài sản cố định) và Consumables (Vật tư tiêu hao)
-- Script này sẽ chèn một số tài sản mẫu để test hiển thị trên UI.

-- Sử dụng các ID Category và Location đã được seed từ trước
SET @tenant_id = '10000000-0000-0000-0000-000000000001';
SET @location_id = '20000000-0000-0000-0000-000000000001'; -- Sao Mai Nha Trang
SET @category_tv = '22222222-2222-2222-2222-222222222222';
SET @category_fridge = '11111111-1111-1111-1111-111111111111';
SET @room_201 = '70000000-0000-0000-0000-000000000201';

-- 1. Insert Fixed Assets (Tài sản cố định)
INSERT INTO fixed_assets (id, tenant_id, location_id, category_id, room_id, asset_code, name, status, created_at, updated_at)
VALUES
    ('aaaaaaaa-1111-1111-1111-111111111111', @tenant_id, @location_id, @category_tv, @room_201, 'TS-CD-0001', 'Smart TV 50 inch LG', 'GOOD', NOW(), NOW()),
    ('aaaaaaaa-2222-2222-2222-222222222222', @tenant_id, @location_id, @category_tv, @room_201, 'TS-CD-0002', 'Smart TV 50 inch Samsung', 'BROKEN', NOW(), NOW()),
    ('aaaaaaaa-3333-3333-3333-333333333333', @tenant_id, @location_id, @category_fridge, @room_201, 'TS-CD-0003', 'Tủ lạnh minibar Aqua', 'UNDER_REPAIR', NOW(), NOW()),
    ('aaaaaaaa-4444-4444-4444-444444444444', @tenant_id, @location_id, @category_fridge, @room_201, 'TS-CD-0004', 'Tủ lạnh minibar Electrolux', 'DISPOSED', NOW(), NOW())
ON DUPLICATE KEY UPDATE id = id;

-- 2. Insert Asset Category cho Vật tư tiêu hao (Vì hiện tại seed test accounts chỉ có category cho FIXED)
INSERT INTO asset_categories (id, tenant_id, name, asset_kind, purpose, unit, is_active, created_at, updated_at)
VALUES 
    ('33333333-3333-3333-3333-333333333333', @tenant_id, 'Nước suối Aquafina', 'CONSUMABLE', 'GUEST_USE', 'Chai', TRUE, NOW(), NOW()),
    ('44444444-4444-4444-4444-444444444444', @tenant_id, 'Khăn tắm', 'CONSUMABLE', 'GUEST_USE', 'Cái', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE id = id;

-- 3. Insert Consumable Items (Vật tư tiêu hao)
INSERT INTO consumable_items (id, tenant_id, location_id, category_id, quantity, last_counted_at, created_at, updated_at)
VALUES
    ('bbbbbbbb-1111-1111-1111-111111111111', @tenant_id, @location_id, '33333333-3333-3333-3333-333333333333', 150.00, NOW(), NOW(), NOW()),
    ('bbbbbbbb-2222-2222-2222-222222222222', @tenant_id, @location_id, '44444444-4444-4444-4444-444444444444', 50.00, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE id = id;
