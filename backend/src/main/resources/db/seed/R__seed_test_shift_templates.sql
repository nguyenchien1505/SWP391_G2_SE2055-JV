-- =============================================================================
-- DỮ LIỆU TEST — mẫu ca của Tenant test để thử màn "Xếp lịch làm việc" ngay mà không
-- phải đăng nhập Giám đốc tạo mẫu trước.
-- CHỈ dùng cho dev/demo — cùng cơ chế với R__seed_test_accounts.sql.
--
-- Flyway chạy các repeatable migration theo thứ tự mô tả: "seed test accounts" chạy
-- trước "seed test shift templates", nên Tenant đã có sẵn.
--
--   Mẫu ca     Giờ            Ghi chú
--   Ca sáng    06:00 – 14:00  —
--   Ca chiều   14:00 – 22:00  —
--   Ca đêm     22:00 – 06:00  Qua đêm: 8 giờ tính trọn vào ngày bắt đầu (BR-SCH-03)
--
-- Cả ba là mẫu CHUNG (location_id NULL — dùng ở mọi chi nhánh). Mẫu riêng một chi nhánh thì
-- Giám đốc tự tạo ở màn "Quy tắc xếp lịch" (V5).
--
-- "ON DUPLICATE KEY UPDATE id = id" cũng bỏ qua khi Tenant đã tự tạo mẫu chung trùng tên
-- (unique tenant_id + scope_key + name — V5), không ghi đè mẫu của người dùng.
-- =============================================================================

SET NAMES utf8mb4;

-- ── Mẫu ca chung — Giám đốc quản lý (BR-SCH-04, BR-SCH-22) ───────────────────
INSERT INTO shift_templates (id, tenant_id, name, start_time, end_time, description) VALUES
    ('70000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'Ca sáng', '06:00:00', '14:00:00', 'Ca ban ngày, nhận bàn giao từ ca đêm'),
    ('70000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
     'Ca chiều', '14:00:00', '22:00:00', 'Khung giờ khách nhận phòng cao điểm'),
    ('70000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001',
     'Ca đêm', '22:00:00', '06:00:00', 'Qua đêm, tính vào ngày bắt đầu ca')
ON DUPLICATE KEY UPDATE id = id;
