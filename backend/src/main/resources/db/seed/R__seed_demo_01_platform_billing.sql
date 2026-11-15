-- =============================================================================
-- DỮ LIỆU DEMO — tenant, gói dịch vụ, hóa đơn
-- CHỈ dùng cho dev/demo — cùng cơ chế với R__seed_test_accounts.sql. FILE SINH TỰ ĐỘNG, đừng sửa tay:
--   node backend/scripts/demo-seed/generate-demo-seed.js
-- NGÀY TƯƠNG ĐỐI: @d0 = CURRENT_DATE lúc Flyway chạy file này. Muốn dữ liệu khớp hôm nay thì xóa
-- database rồi chạy lại app — nên làm vào SÁNG NGÀY DEMO, sau 11:00 (mọi sự kiện "sáng nay" đều trước 11:00).
-- Mật khẩu mọi tài khoản: Test@123. Mọi INSERT bỏ qua dòng đã có (ON DUPLICATE KEY UPDATE id = id).
-- =============================================================================

SET NAMES utf8mb4;
SET @d0 = CURRENT_DATE;

--
-- Tenant (Platform Admin thấy đủ 4 trạng thái; Bảng giá có thêm 1 mốc giá cũ):
--   Tenant                    Giám đốc                       Trạng thái               Ghi chú
--   Chuỗi khách sạn Sen Việt  director.demo@swp391.test      ACTIVE                   Chuỗi chính 3 KS, 4 hóa đơn, hạn kế tiếp d+20
--   Khách sạn Biển Xanh       director.bienxanh@swp391.test  TRIAL                    Dùng thử còn 12 ngày
--   Hoàng Gia Resort          director.hoanggia@swp391.test  ACTIVE                   Sắp đến hạn gia hạn (d+5)
--   Nhà nghỉ Phố Cổ           director.phoco@swp391.test     PAYMENT_OVERDUE          Quá hạn 3 ngày — job khóa sau d+4
--   Khách sạn Đồi Thông       director.doithong@swp391.test  SUSPENDED/ADMIN_LOCKED   Admin khóa — Giám đốc bị chặn
--   Homestay Mây Trắng        director.maytrang@swp391.test  SUSPENDED/TRIAL_EXPIRED  Hết dùng thử — Giám đốc chỉ xem

INSERT INTO pricing_config (id, price_per_location, price_per_user, price_per_room, effective_from, created_at) VALUES
    ('d1400000-0000-4000-8000-000000000001', 400000, 40000, 15000, '2024-06-01', '2024-05-20 09:00:00')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO tenants (id, name, contact_email, contact_phone, status, suspend_reason, suspended_at, created_at) VALUES
    ('d1000000-0000-4000-8000-000000000001', 'Chuỗi khách sạn Sen Việt', 'director.demo@swp391.test', '0912345001', 'ACTIVE', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -75 DAY), '09:00:00')),
    ('d1000000-0000-4000-8000-000000000002', 'Khách sạn Biển Xanh', 'director.bienxanh@swp391.test', '0912345002', 'TRIAL', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -18 DAY), '15:20:00')),
    ('d1000000-0000-4000-8000-000000000003', 'Hoàng Gia Resort', 'director.hoanggia@swp391.test', '0912345003', 'ACTIVE', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -60 DAY), '10:00:00')),
    ('d1000000-0000-4000-8000-000000000004', 'Nhà nghỉ Phố Cổ', 'director.phoco@swp391.test', '0912345004', 'PAYMENT_OVERDUE', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -68 DAY), '08:30:00')),
    ('d1000000-0000-4000-8000-000000000005', 'Khách sạn Đồi Thông', 'director.doithong@swp391.test', '0912345005', 'SUSPENDED', 'ADMIN_LOCKED', TIMESTAMP((@d0 + INTERVAL -2 DAY), '10:15:00'), TIMESTAMP((@d0 + INTERVAL -30 DAY), '13:00:00')),
    ('d1000000-0000-4000-8000-000000000006', 'Homestay Mây Trắng', 'director.maytrang@swp391.test', '0912345006', 'SUSPENDED', 'TRIAL_EXPIRED', TIMESTAMP((@d0 + INTERVAL -5 DAY), '00:00:00'), TIMESTAMP((@d0 + INTERVAL -36 DAY), '20:00:00'))
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO subscriptions (id, tenant_id, quota_location, quota_user, quota_room, price_per_location, price_per_user, price_per_room, is_trial, trial_ends_at, current_period_start, next_billing_date, created_at) VALUES
    ('d1100000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 3, 40, 80, 500000, 50000, 20000, FALSE, (@d0 + INTERVAL -45 DAY), (@d0 + INTERVAL -10 DAY), (@d0 + INTERVAL 20 DAY), TIMESTAMP((@d0 + INTERVAL -75 DAY), '09:00:00')),
    ('d1100000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000002', 1, 20, 50, 500000, 50000, 20000, TRUE, (@d0 + INTERVAL 12 DAY), NULL, NULL, TIMESTAMP((@d0 + INTERVAL -18 DAY), '15:20:00')),
    ('d1100000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000003', 1, 10, 20, 500000, 50000, 20000, FALSE, (@d0 + INTERVAL -25 DAY), (@d0 + INTERVAL -25 DAY), (@d0 + INTERVAL 5 DAY), TIMESTAMP((@d0 + INTERVAL -60 DAY), '10:00:00')),
    ('d1100000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000004', 1, 5, 10, 500000, 50000, 20000, FALSE, (@d0 + INTERVAL -33 DAY), (@d0 + INTERVAL -33 DAY), (@d0 + INTERVAL -3 DAY), TIMESTAMP((@d0 + INTERVAL -68 DAY), '08:30:00')),
    ('d1100000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000005', 1, 10, 20, 500000, 50000, 20000, FALSE, (@d0 + INTERVAL -15 DAY), (@d0 + INTERVAL -15 DAY), (@d0 + INTERVAL 15 DAY), TIMESTAMP((@d0 + INTERVAL -30 DAY), '13:00:00')),
    ('d1100000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000006', 1, 20, 50, 500000, 50000, 20000, TRUE, (@d0 + INTERVAL -5 DAY), NULL, NULL, TIMESTAMP((@d0 + INTERVAL -36 DAY), '20:00:00'))
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO schedule_policies (id, tenant_id) VALUES
    ('d1200000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001'),
    ('d1200000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000002'),
    ('d1200000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000003'),
    ('d1200000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000004'),
    ('d1200000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000005'),
    ('d1200000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000006')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO invoices (id, tenant_id, subscription_id, invoice_type, period_start, period_end, quota_location_snapshot, quota_user_snapshot, quota_room_snapshot, amount, status, issued_at, paid_at, created_at) VALUES
    ('d1300000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'd1100000-0000-4000-8000-000000000001', 'PERIODIC', (@d0 + INTERVAL -70 DAY), (@d0 + INTERVAL -41 DAY), 2, 25, 50, 3250000, 'PAID', TIMESTAMP((@d0 + INTERVAL -70 DAY), '10:12:00'), TIMESTAMP((@d0 + INTERVAL -70 DAY), '10:15:00'), TIMESTAMP((@d0 + INTERVAL -70 DAY), '10:12:00')),
    ('d1300000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000001', 'd1100000-0000-4000-8000-000000000001', 'PERIODIC', (@d0 + INTERVAL -40 DAY), (@d0 + INTERVAL -11 DAY), 2, 25, 50, 3250000, 'PAID', TIMESTAMP((@d0 + INTERVAL -42 DAY), '20:05:00'), TIMESTAMP((@d0 + INTERVAL -42 DAY), '20:08:00'), TIMESTAMP((@d0 + INTERVAL -42 DAY), '20:05:00')),
    ('d1300000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000001', 'd1100000-0000-4000-8000-000000000001', 'UPGRADE_DIFF', (@d0 + INTERVAL -25 DAY), (@d0 + INTERVAL -11 DAY), 3, 40, 80, 925000, 'PAID', TIMESTAMP((@d0 + INTERVAL -25 DAY), '14:40:00'), TIMESTAMP((@d0 + INTERVAL -25 DAY), '14:43:00'), TIMESTAMP((@d0 + INTERVAL -25 DAY), '14:40:00')),
    ('d1300000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000001', 'd1100000-0000-4000-8000-000000000001', 'PERIODIC', (@d0 + INTERVAL -10 DAY), (@d0 + INTERVAL 19 DAY), 3, 40, 80, 5100000, 'PAID', TIMESTAMP((@d0 + INTERVAL -12 DAY), '21:30:00'), TIMESTAMP((@d0 + INTERVAL -12 DAY), '21:33:00'), TIMESTAMP((@d0 + INTERVAL -12 DAY), '21:30:00')),
    ('d1300000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000003', 'd1100000-0000-4000-8000-000000000003', 'PERIODIC', (@d0 + INTERVAL -55 DAY), (@d0 + INTERVAL -26 DAY), 1, 10, 20, 1400000, 'PAID', TIMESTAMP((@d0 + INTERVAL -55 DAY), '11:02:00'), TIMESTAMP((@d0 + INTERVAL -55 DAY), '11:05:00'), TIMESTAMP((@d0 + INTERVAL -55 DAY), '11:02:00')),
    ('d1300000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000003', 'd1100000-0000-4000-8000-000000000003', 'PERIODIC', (@d0 + INTERVAL -25 DAY), (@d0 + INTERVAL 4 DAY), 1, 10, 20, 1400000, 'PAID', TIMESTAMP((@d0 + INTERVAL -27 DAY), '19:45:00'), TIMESTAMP((@d0 + INTERVAL -27 DAY), '19:48:00'), TIMESTAMP((@d0 + INTERVAL -27 DAY), '19:45:00')),
    ('d1300000-0000-4000-8000-000000000007', 'd1000000-0000-4000-8000-000000000004', 'd1100000-0000-4000-8000-000000000004', 'PERIODIC', (@d0 + INTERVAL -63 DAY), (@d0 + INTERVAL -34 DAY), 1, 5, 10, 950000, 'PAID', TIMESTAMP((@d0 + INTERVAL -63 DAY), '09:15:00'), TIMESTAMP((@d0 + INTERVAL -63 DAY), '09:18:00'), TIMESTAMP((@d0 + INTERVAL -63 DAY), '09:15:00')),
    ('d1300000-0000-4000-8000-000000000008', 'd1000000-0000-4000-8000-000000000004', 'd1100000-0000-4000-8000-000000000004', 'PERIODIC', (@d0 + INTERVAL -33 DAY), (@d0 + INTERVAL -4 DAY), 1, 5, 10, 950000, 'PAID', TIMESTAMP((@d0 + INTERVAL -34 DAY), '22:10:00'), TIMESTAMP((@d0 + INTERVAL -34 DAY), '22:13:00'), TIMESTAMP((@d0 + INTERVAL -34 DAY), '22:10:00')),
    ('d1300000-0000-4000-8000-000000000009', 'd1000000-0000-4000-8000-000000000005', 'd1100000-0000-4000-8000-000000000005', 'PERIODIC', (@d0 + INTERVAL -15 DAY), (@d0 + INTERVAL 14 DAY), 1, 10, 20, 1400000, 'PAID', TIMESTAMP((@d0 + INTERVAL -15 DAY), '16:20:00'), TIMESTAMP((@d0 + INTERVAL -15 DAY), '16:23:00'), TIMESTAMP((@d0 + INTERVAL -15 DAY), '16:20:00'))
ON DUPLICATE KEY UPDATE id = id;

