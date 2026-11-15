-- =============================================================================
-- DỮ LIỆU DEMO — khách sạn, danh mục, tài khoản chuỗi "Sen Việt" và tenant phụ
-- CHỈ dùng cho dev/demo — cùng cơ chế với R__seed_test_accounts.sql. FILE SINH TỰ ĐỘNG, đừng sửa tay:
--   node backend/scripts/demo-seed/generate-demo-seed.js
-- NGÀY TƯƠNG ĐỐI: @d0 = CURRENT_DATE lúc Flyway chạy file này. Muốn dữ liệu khớp hôm nay thì xóa
-- database rồi chạy lại app — nên làm vào SÁNG NGÀY DEMO, sau 11:00 (mọi sự kiện "sáng nay" đều trước 11:00).
-- Mật khẩu mọi tài khoản: Test@123. Mọi INSERT bỏ qua dòng đã có (ON DUPLICATE KEY UPDATE id = id).
-- =============================================================================

SET NAMES utf8mb4;
SET @d0 = CURRENT_DATE;

--
-- Tài khoản chuỗi Sen Việt (đuôi @swp391.test, mật khẩu Test@123):
--   Email                        Vai trò / quyền  KS  Ca     Ghi chú
--   director.demo@swp391.test    DIRECTOR
--   manager.hn@swp391.test       MANAGER          HN
--   manager.dn@swp391.test       MANAGER          DN
--   manager.hl@swp391.test       MANAGER          HL         vào làm d+7 → KS Hạ Long chưa hoạt động
--   manager.duphong@swp391.test  MANAGER                     Manager dự bị, chưa gán KS
--   letan.hn1@swp391.test        Lễ tân           HN  Sáng
--   letan.hn2@swp391.test        Lễ tân           HN  Sáng
--   letan.hn3@swp391.test        Lễ tân           HN  Chiều
--   letan.hn4@swp391.test        Lễ tân+Dọn       HN  Chiều
--   letan.hn5@swp391.test        Lễ tân           HN  Đêm
--   buongphong.hn1@swp391.test   Dọn              HN  Sáng
--   buongphong.hn2@swp391.test   Dọn              HN  Sáng
--   buongphong.hn3@swp391.test   Dọn              HN  Sáng
--   buongphong.hn4@swp391.test   Dọn              HN  Sáng
--   buongphong.hn5@swp391.test   Dọn              HN  Chiều
--   buongphong.hn6@swp391.test   Dọn              HN  Chiều
--   giamsat.hn1@swp391.test      Giám sát         HN  Sáng
--   giamsat.hn2@swp391.test      Giám sát+Dọn     HN  Chiều
--   kythuat.hn1@swp391.test      Quyền chung      HN  Sáng
--   kythuat.hn2@swp391.test      Quyền chung      HN  Đêm
--   buongphong.hn7@swp391.test   Dọn              HN  Sáng   INACTIVE
--   buongphong.hn8@swp391.test   Dọn              HN  Sáng   TERMINATED
--   letan.dn1@swp391.test        Lễ tân           DN  Sáng
--   letan.dn2@swp391.test        Lễ tân           DN  Sáng
--   letan.dn3@swp391.test        Lễ tân           DN  Chiều
--   letan.dn4@swp391.test        Lễ tân           DN  Chiều
--   letan.dn5@swp391.test        Lễ tân           DN  Đêm
--   buongphong.dn1@swp391.test   Dọn              DN  Sáng
--   buongphong.dn2@swp391.test   Dọn              DN  Sáng
--   buongphong.dn3@swp391.test   Dọn              DN  Chiều
--   buongphong.dn4@swp391.test   Dọn              DN  Chiều
--   giamsat.dn1@swp391.test      Giám sát+Dọn     DN  Sáng
--   kythuat.dn1@swp391.test      Quyền chung      DN  Sáng
--   kythuat.dn2@swp391.test      Quyền chung      DN  Đêm
--   buongphong.dn5@swp391.test   Dọn              DN  Sáng   phải đổi mật khẩu lần đầu
-- Tenant phụ: director.bienxanh@swp391.test, director.hoanggia@swp391.test, director.phoco@swp391.test, director.doithong@swp391.test, director.maytrang@swp391.test, manager.hoanggia@swp391.test, manager.phoco@swp391.test, letan.hoanggia@swp391.test, buongphong.hoanggia@swp391.test, letan.phoco@swp391.test, buongphong.phoco@swp391.test
--
-- Hà Nội chấm công theo IP chế độ CẢNH BÁO: có mạng 113.160.92.0/24 và 127.0.0.1 / ::1 (máy chạy demo).
-- Đà Nẵng dùng bộ mẫu ca riêng 07–15 / 15–23 / 23–07. Danh mục có 1 phòng ban, 1 vị trí, 1 loại phòng đã ẩn.

INSERT INTO locations (id, tenant_id, name, address, phone, star_rating, timezone, status, own_shift_templates, attendance_ip_mode, created_at) VALUES
    ('d2000000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'Sen Việt Hà Nội – Hoàn Kiếm', '18 Hàng Bè, Hoàn Kiếm, Hà Nội', '02439260001', 3, 'Asia/Ho_Chi_Minh', 'OPERATIONAL', FALSE, 'WARN', TIMESTAMP((@d0 + INTERVAL -74 DAY), '09:30:00')),
    ('d2000000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000001', 'Sen Việt Đà Nẵng – Mỹ Khê', '96 Võ Nguyên Giáp, Sơn Trà, Đà Nẵng', '02363950002', 3, 'Asia/Ho_Chi_Minh', 'OPERATIONAL', TRUE, 'OFF', TIMESTAMP((@d0 + INTERVAL -72 DAY), '10:00:00')),
    ('d2000000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000001', 'Sen Việt Hạ Long – Bãi Cháy', '25 Hạ Long, Bãi Cháy, Hạ Long, Quảng Ninh', '02033840003', 2, 'Asia/Ho_Chi_Minh', 'NOT_OPERATIONAL', FALSE, 'OFF', TIMESTAMP((@d0 + INTERVAL -20 DAY), '11:00:00')),
    ('d2000000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000002', 'Biển Xanh Nha Trang', '40 Trần Phú, Lộc Thọ, Nha Trang', '02583520004', 2, 'Asia/Ho_Chi_Minh', 'NOT_OPERATIONAL', FALSE, 'OFF', TIMESTAMP((@d0 + INTERVAL -17 DAY), '09:00:00')),
    ('d2000000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000003', 'Hoàng Gia Resort Phú Quốc', '12 Trần Hưng Đạo, Dương Đông, Phú Quốc', '02973980005', 3, 'Asia/Ho_Chi_Minh', 'OPERATIONAL', FALSE, 'OFF', TIMESTAMP((@d0 + INTERVAL -59 DAY), '09:00:00')),
    ('d2000000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000004', 'Nhà nghỉ Phố Cổ Hội An', '7 Nguyễn Thái Học, Minh An, Hội An', '02353860006', 1, 'Asia/Ho_Chi_Minh', 'OPERATIONAL', FALSE, 'OFF', TIMESTAMP((@d0 + INTERVAL -67 DAY), '09:00:00')),
    ('d2000000-0000-4000-8000-000000000007', 'd1000000-0000-4000-8000-000000000005', 'Đồi Thông Đà Lạt', '3 Hồ Tùng Mậu, Phường 3, Đà Lạt', '02633820007', 2, 'Asia/Ho_Chi_Minh', 'NOT_OPERATIONAL', FALSE, 'OFF', TIMESTAMP((@d0 + INTERVAL -29 DAY), '09:00:00')),
    ('d2000000-0000-4000-8000-000000000008', 'd1000000-0000-4000-8000-000000000006', 'Mây Trắng Sa Pa', '15 Thác Bạc, Sa Pa, Lào Cai', '02143870008', 1, 'Asia/Ho_Chi_Minh', 'NOT_OPERATIONAL', FALSE, 'OFF', TIMESTAMP((@d0 + INTERVAL -35 DAY), '09:00:00'))
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO departments (id, tenant_id, name, is_active) VALUES
    ('d3000000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'Lễ tân', TRUE),
    ('d3000000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000001', 'Buồng phòng', TRUE),
    ('d3000000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000001', 'Giám sát vận hành', TRUE),
    ('d3000000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000001', 'Kỹ thuật – Bảo trì', TRUE),
    ('d3000000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000001', 'Hành chính', FALSE),
    ('d3000000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000003', 'Vận hành', TRUE),
    ('d3000000-0000-4000-8000-000000000007', 'd1000000-0000-4000-8000-000000000004', 'Vận hành', TRUE)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO positions (id, tenant_id, department_id, name, position_type, is_active) VALUES
    ('d4000000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000001', 'Trưởng ca lễ tân', 'RECEPTION', TRUE),
    ('d4000000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000001', 'Nhân viên lễ tân', 'RECEPTION', TRUE),
    ('d4000000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000002', 'Nhân viên buồng phòng', 'HOUSEKEEPING', TRUE),
    ('d4000000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000003', 'Giám sát buồng phòng', 'SUPERVISION', TRUE),
    ('d4000000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000004', 'Kỹ thuật viên', 'OTHER', TRUE),
    ('d4000000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000005', 'Nhân viên văn thư', 'OTHER', FALSE),
    ('d4000000-0000-4000-8000-000000000007', 'd1000000-0000-4000-8000-000000000003', 'd3000000-0000-4000-8000-000000000006', 'Lễ tân', 'RECEPTION', TRUE),
    ('d4000000-0000-4000-8000-000000000008', 'd1000000-0000-4000-8000-000000000003', 'd3000000-0000-4000-8000-000000000006', 'Buồng phòng', 'HOUSEKEEPING', TRUE),
    ('d4000000-0000-4000-8000-000000000009', 'd1000000-0000-4000-8000-000000000004', 'd3000000-0000-4000-8000-000000000007', 'Lễ tân', 'RECEPTION', TRUE),
    ('d4000000-0000-4000-8000-000000000010', 'd1000000-0000-4000-8000-000000000004', 'd3000000-0000-4000-8000-000000000007', 'Buồng phòng', 'HOUSEKEEPING', TRUE)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO room_types (id, tenant_id, name, is_active) VALUES
    ('d6000000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'Standard', TRUE),
    ('d6000000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000001', 'Superior', TRUE),
    ('d6000000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000001', 'Deluxe', TRUE),
    ('d6000000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000001', 'Suite', TRUE),
    ('d6000000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000001', 'Family', TRUE),
    ('d6000000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000001', 'Phòng tập thể (ngừng dùng)', FALSE),
    ('d6000000-0000-4000-8000-000000000007', 'd1000000-0000-4000-8000-000000000003', 'Bungalow', TRUE),
    ('d6000000-0000-4000-8000-000000000008', 'd1000000-0000-4000-8000-000000000004', 'Phòng đôi', TRUE)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO areas (id, tenant_id, location_id, name) VALUES
    ('d2200000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'Sảnh lễ tân'),
    ('d2200000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'Nhà hàng tầng 1'),
    ('d2200000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'Phòng gym'),
    ('d2200000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'Kho buồng phòng'),
    ('d2200000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'Khu giặt là'),
    ('d2200000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002', 'Sảnh lễ tân'),
    ('d2200000-0000-4000-8000-000000000007', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002', 'Nhà hàng hướng biển'),
    ('d2200000-0000-4000-8000-000000000008', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002', 'Bể bơi tầng thượng'),
    ('d2200000-0000-4000-8000-000000000009', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002', 'Kho buồng phòng'),
    ('d2200000-0000-4000-8000-000000000010', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000003', 'Sảnh lễ tân')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO shift_templates (id, tenant_id, location_id, name, start_time, end_time, description, is_active) VALUES
    ('d7000000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', NULL, 'Ca sáng', '06:00:00', '14:00:00', 'Dùng chung toàn chuỗi', TRUE),
    ('d7000000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000001', NULL, 'Ca chiều', '14:00:00', '22:00:00', 'Dùng chung toàn chuỗi', TRUE),
    ('d7000000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000001', NULL, 'Ca đêm', '22:00:00', '06:00:00', 'Qua đêm, tính vào ngày bắt đầu', TRUE),
    ('d7000000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002', 'Ca sáng', '07:00:00', '15:00:00', 'Riêng Đà Nẵng — lệch 1 giờ theo giờ đón khách biển', TRUE),
    ('d7000000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002', 'Ca chiều', '15:00:00', '23:00:00', 'Riêng Đà Nẵng', TRUE),
    ('d7000000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002', 'Ca đêm', '23:00:00', '07:00:00', 'Riêng Đà Nẵng, qua đêm', TRUE)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO users (id, tenant_id, role, email, password_hash, must_change_password, status, full_name, phone, location_id, position_id, start_work_date, date_of_birth, gender, address, avatar_url, terminated_at, terminated_by, created_at) VALUES
    ('d5000000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'DIRECTOR', 'director.demo@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Nguyễn Minh Khôi', '0912345001', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -75 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000002', 'DIRECTOR', 'director.bienxanh@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Trần Thu Hà', '0912345002', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -18 DAY), '15:20:00')),
    ('d5000000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000003', 'DIRECTOR', 'director.hoanggia@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Lê Hoàng Long', '0912345003', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -60 DAY), '10:00:00')),
    ('d5000000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000004', 'DIRECTOR', 'director.phoco@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Phạm Văn Đức', '0912345004', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -68 DAY), '08:30:00')),
    ('d5000000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000005', 'DIRECTOR', 'director.doithong@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Vũ Thị Lan', '0912345005', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -30 DAY), '13:00:00')),
    ('d5000000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000006', 'DIRECTOR', 'director.maytrang@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Đỗ Quang Huy', '0912345006', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -36 DAY), '20:00:00')),
    ('d5000000-0000-4000-8000-000000000007', 'd1000000-0000-4000-8000-000000000001', 'MANAGER', 'manager.hn@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Hoàng Thị Mai Anh', '0981007919', 'd2000000-0000-4000-8000-000000000001', NULL, (@d0 + INTERVAL -330 DAY), '1988-04-12', 'FEMALE', '45 Lý Thái Tổ, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=Ho%C3%A0ng%20Th%E1%BB%8B%20Mai%20Anh&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -331 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000008', 'd1000000-0000-4000-8000-000000000001', 'MANAGER', 'manager.dn@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Ngô Văn Bảo', '0981015838', 'd2000000-0000-4000-8000-000000000002', NULL, (@d0 + INTERVAL -320 DAY), '1986-09-03', 'MALE', '12 Phạm Văn Đồng, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=Ng%C3%B4%20V%C4%83n%20B%E1%BA%A3o&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -321 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000009', 'd1000000-0000-4000-8000-000000000001', 'MANAGER', 'manager.hl@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Đặng Thu Trang', '0981023757', 'd2000000-0000-4000-8000-000000000003', NULL, (@d0 + INTERVAL 7 DAY), '1991-12-20', 'FEMALE', '8 Cái Lân, Bãi Cháy, Hạ Long', 'https://ui-avatars.com/api/?name=%C4%90%E1%BA%B7ng%20Thu%20Trang&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -1 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000010', 'd1000000-0000-4000-8000-000000000001', 'MANAGER', 'manager.duphong@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Bùi Đức Thịnh', '0981031676', NULL, NULL, (@d0 + INTERVAL -30 DAY), '1990-06-08', 'MALE', '77 Nguyễn Trãi, Thanh Xuân, Hà Nội', 'https://ui-avatars.com/api/?name=B%C3%B9i%20%C4%90%E1%BB%A9c%20Th%E1%BB%8Bnh&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -31 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000011', 'd1000000-0000-4000-8000-000000000003', 'MANAGER', 'manager.hoanggia@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Trịnh Văn Tâm', '0981039595', 'd2000000-0000-4000-8000-000000000005', NULL, (@d0 + INTERVAL -58 DAY), '1987-02-14', 'MALE', '5 Bạch Đằng, Dương Đông, Phú Quốc', 'https://ui-avatars.com/api/?name=Tr%E1%BB%8Bnh%20V%C4%83n%20T%C3%A2m&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -59 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000012', 'd1000000-0000-4000-8000-000000000004', 'MANAGER', 'manager.phoco@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Lâm Thị Hồng', '0981047514', 'd2000000-0000-4000-8000-000000000006', NULL, (@d0 + INTERVAL -66 DAY), '1985-11-30', 'FEMALE', '22 Trần Phú, Minh An, Hội An', 'https://ui-avatars.com/api/?name=L%C3%A2m%20Th%E1%BB%8B%20H%E1%BB%93ng&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -67 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000013', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'letan.hn1@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Nguyễn Thị Thu Hằng', '0981055433', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000001', (@d0 + INTERVAL -300 DAY), '1994-03-08', 'FEMALE', '10 Hàng Gai, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=Nguy%E1%BB%85n%20Th%E1%BB%8B%20Thu%20H%E1%BA%B1ng&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -301 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000014', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'letan.hn2@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Phạm Quang Vinh', '0981063352', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000002', (@d0 + INTERVAL -290 DAY), '1997-07-21', 'MALE', '13 Lê Duẩn, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=Ph%E1%BA%A1m%20Quang%20Vinh&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -291 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000015', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'letan.hn3@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Lê Ngọc Ánh', '0981071271', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000002', (@d0 + INTERVAL -280 DAY), '1999-01-15', 'FEMALE', '16 Ngô Quyền, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=L%C3%AA%20Ng%E1%BB%8Dc%20%C3%81nh&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -281 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000016', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'letan.hn4@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Trần Đức Anh', '0981079190', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000002', (@d0 + INTERVAL -270 DAY), '1996-10-02', 'MALE', '19 Bạch Đằng, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=Tr%E1%BA%A7n%20%C4%90%E1%BB%A9c%20Anh&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -271 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000017', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'letan.hn5@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Vũ Thị Hương', '0981087109', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000002', (@d0 + INTERVAL -260 DAY), '1993-05-27', 'FEMALE', '22 Trần Phú, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=V%C5%A9%20Th%E1%BB%8B%20H%C6%B0%C6%A1ng&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -261 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000018', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.hn1@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Đinh Thị Hoa', '0981095028', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -305 DAY), '1990-08-19', 'FEMALE', '25 Hàng Gai, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=%C4%90inh%20Th%E1%BB%8B%20Hoa&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -306 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000019', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.hn2@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Nguyễn Văn Nam', '0981102947', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -295 DAY), '1995-12-01', 'MALE', '28 Lê Duẩn, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=Nguy%E1%BB%85n%20V%C4%83n%20Nam&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -296 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000020', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.hn3@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Lê Thị Mai', '0981110866', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -285 DAY), '1992-02-11', 'FEMALE', '31 Ngô Quyền, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=L%C3%AA%20Th%E1%BB%8B%20Mai&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -286 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000021', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.hn4@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Hà Thị Ngọc', '0981118785', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -275 DAY), '1998-06-30', 'FEMALE', '34 Bạch Đằng, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=H%C3%A0%20Th%E1%BB%8B%20Ng%E1%BB%8Dc&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -276 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000022', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.hn5@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Phan Văn Lực', '0981126704', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -265 DAY), '1991-09-09', 'MALE', '37 Trần Phú, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=Phan%20V%C4%83n%20L%E1%BB%B1c&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -266 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000023', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.hn6@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Cao Thị Yến', '0981134623', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -255 DAY), '2000-04-04', 'FEMALE', '40 Hàng Gai, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=Cao%20Th%E1%BB%8B%20Y%E1%BA%BFn&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -256 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000024', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'giamsat.hn1@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Trương Minh Tuấn', '0981142542', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000004', (@d0 + INTERVAL -310 DAY), '1989-11-23', 'MALE', '43 Lê Duẩn, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=Tr%C6%B0%C6%A1ng%20Minh%20Tu%E1%BA%A5n&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -311 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000025', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'giamsat.hn2@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Lý Thị Kim Oanh', '0981150461', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000004', (@d0 + INTERVAL -300 DAY), '1990-01-31', 'FEMALE', '46 Ngô Quyền, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=L%C3%BD%20Th%E1%BB%8B%20Kim%20Oanh&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -301 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000026', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'kythuat.hn1@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Đoàn Văn Hùng', '0981158380', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000005', (@d0 + INTERVAL -300 DAY), '1987-07-07', 'MALE', '49 Bạch Đằng, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=%C4%90o%C3%A0n%20V%C4%83n%20H%C3%B9ng&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -301 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000027', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'kythuat.hn2@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Mạc Văn Tiến', '0981166299', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000005', (@d0 + INTERVAL -240 DAY), '1994-03-18', 'MALE', '52 Trần Phú, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=M%E1%BA%A1c%20V%C4%83n%20Ti%E1%BA%BFn&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -241 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000028', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.hn7@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'INACTIVE', 'Kiều Thị Dung', '0981174218', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -230 DAY), '1996-05-05', 'FEMALE', '55 Hàng Gai, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=Ki%E1%BB%81u%20Th%E1%BB%8B%20Dung&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -231 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000029', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.hn8@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'TERMINATED', 'Tô Văn Sơn', '0981182137', 'd2000000-0000-4000-8000-000000000001', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -220 DAY), '1993-08-08', 'MALE', '58 Lê Duẩn, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=T%C3%B4%20V%C4%83n%20S%C6%A1n&background=random', TIMESTAMP((@d0 + INTERVAL -3 DAY), '17:00:00'), 'd5000000-0000-4000-8000-000000000007', TIMESTAMP((@d0 + INTERVAL -221 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000030', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'letan.dn1@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Huỳnh Thị Bích Ngân', '0981190056', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000001', (@d0 + INTERVAL -300 DAY), '1993-02-02', 'FEMALE', '61 Ngô Quyền, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=Hu%E1%BB%B3nh%20Th%E1%BB%8B%20B%C3%ADch%20Ng%C3%A2n&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -301 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000031', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'letan.dn2@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Võ Minh Quân', '0981197975', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000002', (@d0 + INTERVAL -290 DAY), '1998-12-12', 'MALE', '64 Bạch Đằng, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=V%C3%B5%20Minh%20Qu%C3%A2n&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -291 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000032', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'letan.dn3@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Nguyễn Hoài Thương', '0981205894', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000002', (@d0 + INTERVAL -280 DAY), '1999-09-19', 'FEMALE', '67 Trần Phú, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=Nguy%E1%BB%85n%20Ho%C3%A0i%20Th%C6%B0%C6%A1ng&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -281 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000033', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'letan.dn4@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Trần Quốc Bảo', '0981213813', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000002', (@d0 + INTERVAL -270 DAY), '1997-04-24', 'MALE', '70 Hàng Gai, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=Tr%E1%BA%A7n%20Qu%E1%BB%91c%20B%E1%BA%A3o&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -271 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000034', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'letan.dn5@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Lê Văn Phúc', '0981221732', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000002', (@d0 + INTERVAL -260 DAY), '1995-06-16', 'MALE', '73 Lê Duẩn, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=L%C3%AA%20V%C4%83n%20Ph%C3%BAc&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -261 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000035', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.dn1@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Phạm Thị Lệ', '0981229651', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -300 DAY), '1991-10-10', 'FEMALE', '76 Ngô Quyền, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=Ph%E1%BA%A1m%20Th%E1%BB%8B%20L%E1%BB%87&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -301 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000036', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.dn2@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Hồ Thị Thắm', '0981237570', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -290 DAY), '1994-11-11', 'FEMALE', '79 Bạch Đằng, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=H%E1%BB%93%20Th%E1%BB%8B%20Th%E1%BA%AFm&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -291 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000037', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.dn3@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Dương Văn Khánh', '0981245489', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -280 DAY), '1996-03-03', 'MALE', '82 Trần Phú, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=D%C6%B0%C6%A1ng%20V%C4%83n%20Kh%C3%A1nh&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -281 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000038', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.dn4@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Lưu Thị Nhàn', '0981253408', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000003', (@d0 + INTERVAL -270 DAY), '1992-07-17', 'FEMALE', '85 Hàng Gai, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=L%C6%B0u%20Th%E1%BB%8B%20Nh%C3%A0n&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -271 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000039', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'giamsat.dn1@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Châu Văn Thành', '0981261327', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000004', (@d0 + INTERVAL -310 DAY), '1988-08-28', 'MALE', '88 Lê Duẩn, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=Ch%C3%A2u%20V%C4%83n%20Th%C3%A0nh&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -311 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000040', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'kythuat.dn1@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Tạ Văn Hiếu', '0981269246', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000005', (@d0 + INTERVAL -300 DAY), '1990-12-25', 'MALE', '91 Ngô Quyền, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=T%E1%BA%A1%20V%C4%83n%20Hi%E1%BA%BFu&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -301 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000041', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'kythuat.dn2@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Quách Văn Lâm', '0981277165', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000005', (@d0 + INTERVAL -250 DAY), '1993-01-09', 'MALE', '94 Bạch Đằng, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=Qu%C3%A1ch%20V%C4%83n%20L%C3%A2m&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -251 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000042', 'd1000000-0000-4000-8000-000000000001', 'STAFF', 'buongphong.dn5@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', TRUE, 'ACTIVE', 'Mai Thị Thu', '0981285084', 'd2000000-0000-4000-8000-000000000002', 'd4000000-0000-4000-8000-000000000003', @d0, '2001-05-15', 'FEMALE', '97 Trần Phú, Sơn Trà, Đà Nẵng', 'https://ui-avatars.com/api/?name=Mai%20Th%E1%BB%8B%20Thu&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -1 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000043', 'd1000000-0000-4000-8000-000000000003', 'STAFF', 'letan.hoanggia@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Phan Thị Ngọc Diệp', '0981293003', 'd2000000-0000-4000-8000-000000000005', 'd4000000-0000-4000-8000-000000000007', (@d0 + INTERVAL -57 DAY), '1992-03-10', 'FEMALE', '12 Trần Hưng Đạo, Dương Đông, Phú Quốc', 'https://ui-avatars.com/api/?name=Phan%20Th%E1%BB%8B%20Ng%E1%BB%8Dc%20Di%E1%BB%87p&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -58 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000044', 'd1000000-0000-4000-8000-000000000003', 'STAFF', 'buongphong.hoanggia@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Nguyễn Văn Toàn', '0981300922', 'd2000000-0000-4000-8000-000000000005', 'd4000000-0000-4000-8000-000000000008', (@d0 + INTERVAL -57 DAY), '1993-04-11', 'MALE', '12 Trần Hưng Đạo, Dương Đông, Phú Quốc', 'https://ui-avatars.com/api/?name=Nguy%E1%BB%85n%20V%C4%83n%20To%C3%A0n&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -58 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000045', 'd1000000-0000-4000-8000-000000000004', 'STAFF', 'letan.phoco@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Trần Thị Thanh Tâm', '0981308841', 'd2000000-0000-4000-8000-000000000006', 'd4000000-0000-4000-8000-000000000009', (@d0 + INTERVAL -65 DAY), '1994-05-12', 'FEMALE', '7 Nguyễn Thái Học, Minh An, Hội An', 'https://ui-avatars.com/api/?name=Tr%E1%BA%A7n%20Th%E1%BB%8B%20Thanh%20T%C3%A2m&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -66 DAY), '09:00:00')),
    ('d5000000-0000-4000-8000-000000000046', 'd1000000-0000-4000-8000-000000000004', 'STAFF', 'buongphong.phoco@swp391.test', '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq', FALSE, 'ACTIVE', 'Lê Văn Mạnh', '0981316760', 'd2000000-0000-4000-8000-000000000006', 'd4000000-0000-4000-8000-000000000010', (@d0 + INTERVAL -65 DAY), '1995-06-13', 'MALE', '7 Nguyễn Thái Học, Minh An, Hội An', 'https://ui-avatars.com/api/?name=L%C3%AA%20V%C4%83n%20M%E1%BA%A1nh&background=random', NULL, NULL, TIMESTAMP((@d0 + INTERVAL -66 DAY), '09:00:00'))
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO user_permissions (user_id, permission) VALUES
    ('d5000000-0000-4000-8000-000000000013', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000014', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000015', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000016', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000016', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000017', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000018', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000019', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000020', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000021', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000022', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000023', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000024', 'SUPERVISION'),
    ('d5000000-0000-4000-8000-000000000025', 'SUPERVISION'),
    ('d5000000-0000-4000-8000-000000000025', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000028', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000029', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000030', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000031', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000032', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000033', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000034', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000035', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000036', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000037', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000038', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000039', 'SUPERVISION'),
    ('d5000000-0000-4000-8000-000000000039', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000042', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000043', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000044', 'HOUSEKEEPING'),
    ('d5000000-0000-4000-8000-000000000045', 'RECEPTION'),
    ('d5000000-0000-4000-8000-000000000046', 'HOUSEKEEPING')
ON DUPLICATE KEY UPDATE permission = permission;

INSERT INTO location_attendance_networks (id, tenant_id, location_id, cidr, label) VALUES
    ('d2100000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', '113.160.92.0/24', 'Wifi khách sạn (VNPT)'),
    ('d2100000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', '127.0.0.1', 'Máy demo (localhost)'),
    ('d2100000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', '::1', 'Máy demo (localhost IPv6)')
ON DUPLICATE KEY UPDATE id = id;

