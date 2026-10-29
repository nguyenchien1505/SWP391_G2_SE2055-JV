-- =============================================================================
-- DỮ LIỆU MẪU — Giám sát vận hành (BR Milestone 2: BR-PERM-07, BR-ORG-08/09 bản sửa, BR-HK-13) tại
-- "Khách sạn Test Hà Nội". CHỈ dùng cho dev/demo — cùng cơ chế với R__seed_test_accounts.sql.
--
-- Chạy SAU R__seed_test_workday_housekeeping.sql (Flyway xếp theo tên: "housekeeping" < "supervisor"),
-- nên ca hôm nay của file đó — nếu chạy cùng ngày — đã có trước và file này không chèn trùng.
--
-- NGÀY TƯƠNG ĐỐI: ca "hôm nay" tính theo CURRENT_DATE lúc Flyway chạy file này. File R__ chỉ chạy lại
-- khi đổi nội dung, nên sang hôm sau muốn thử tiếp thì Quản lý xếp ca ở «Xếp lịch làm việc», hoặc xóa
-- sạch database rồi chạy lại app.
--
-- Tài khoản thêm — mật khẩu Test@123, vị trí "Giám sát vận hành" (phòng ban Vận hành):
--   giamsat@swp391.test    Giám sát Test           Giám sát              ca sáng hôm nay
--   giamsat2@swp391.test   Giám sát kiêm dọn Test  Giám sát + Dọn dẹp    ca sáng hôm nay (thử BR-HK-13)
--   giamsat3@swp391.test   Giám sát nghỉ Test      Giám sát              KHÔNG có ca (thử "ngoài ngày có ca")
--
-- Để có người nhận việc và đủ lễ tân cho ca sáng hôm nay (chốt 05/10/2026), file này cũng xếp ca sáng
-- hôm nay cho Lễ tân Test, Nguyễn Thị Hoa, Trần Văn Nam nếu họ chưa có ca hôm nay.
--
-- Một báo hỏng đang chờ xử lý: TV phòng 201 do Lễ tân Test báo — để Giám sát thử đóng phiếu.
--
-- Mọi INSERT bỏ qua khi trùng và chỉ chèn dòng phụ thuộc khi dòng cha có thật — chạy trên database
-- đã có dữ liệu cũng không vỡ.
-- =============================================================================

SET NAMES utf8mb4;

SET @tenant   = '10000000-0000-0000-0000-000000000001';
SET @hanoi    = '20000000-0000-0000-0000-000000000001';
SET @letan    = '50000000-0000-0000-0000-000000000004';
SET @hoa      = '50000000-0000-0000-0000-000000000101';
SET @nam      = '50000000-0000-0000-0000-000000000102';
SET @giamsat  = '50000000-0000-0000-0000-000000000201';
SET @giamsat2 = '50000000-0000-0000-0000-000000000202';
SET @giamsat3 = '50000000-0000-0000-0000-000000000203';
SET @ca_sang  = '70000000-0000-0000-0000-000000000001';
SET @tv_201   = 'aaaaaaaa-1111-1111-1111-111111111111';
SET @hash     = '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq';   -- Test@123
SET @d0       = CURRENT_DATE;

-- ── Phòng ban + vị trí loại Giám sát vận hành — BR-ORG-08 bản sửa M2 ────────────
-- Đã có phòng ban / vị trí cùng tên (tự tạo trên giao diện) thì dùng lại, không chèn mới.
INSERT INTO departments (id, tenant_id, name)
SELECT '30000000-0000-0000-0000-000000000004', @tenant, 'Vận hành'
FROM DUAL
WHERE EXISTS (SELECT 1 FROM tenants t WHERE t.id = @tenant)
ON DUPLICATE KEY UPDATE departments.id = departments.id;

SET @ops_dept = (SELECT id FROM departments WHERE tenant_id = @tenant AND name = 'Vận hành' LIMIT 1);

INSERT INTO positions (id, tenant_id, department_id, name, position_type)
SELECT '40000000-0000-0000-0000-000000000004', @tenant, @ops_dept, 'Giám sát vận hành', 'SUPERVISION'
FROM DUAL
WHERE @ops_dept IS NOT NULL
ON DUPLICATE KEY UPDATE positions.id = positions.id;

SET @sup_pos = (SELECT id FROM positions WHERE tenant_id = @tenant AND name = 'Giám sát vận hành' LIMIT 1);

-- ── Tài khoản Giám sát — BR-USER-01; quyền tick riêng ở user_permissions ──────────
INSERT INTO users (id, tenant_id, role, email, password_hash, must_change_password, status,
                   full_name, phone, location_id, position_id,
                   start_work_date, date_of_birth, gender, address, avatar_url)
SELECT u.id, @tenant, 'STAFF', u.email, @hash, FALSE, 'ACTIVE',
       u.full_name, u.phone, @hanoi, @sup_pos,
       u.start_work_date, u.date_of_birth, u.gender, u.address, u.avatar_url
FROM (
    SELECT @giamsat AS id, 'giamsat@swp391.test' AS email, 'Giám sát Test' AS full_name, '0900000020' AS phone,
           DATE '2026-03-01' AS start_work_date, DATE '1991-08-08' AS date_of_birth, 'MALE' AS gender,
           '20 Lý Thái Tổ, Hoàn Kiếm, Hà Nội' AS address, 'https://ui-avatars.com/api/?name=Giam+Sat' AS avatar_url
    UNION ALL
    SELECT @giamsat2, 'giamsat2@swp391.test', 'Giám sát kiêm dọn Test', '0900000021',
           DATE '2026-04-01', DATE '1994-02-14', 'FEMALE',
           '3 Hàng Khay, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=Giam+Sat+Kiem'
    UNION ALL
    SELECT @giamsat3, 'giamsat3@swp391.test', 'Giám sát nghỉ Test', '0900000022',
           DATE '2026-05-01', DATE '1996-10-10', 'MALE',
           '9 Đinh Tiên Hoàng, Hoàn Kiếm, Hà Nội', 'https://ui-avatars.com/api/?name=Giam+Sat+Nghi'
) AS u
WHERE @sup_pos IS NOT NULL
  AND EXISTS (SELECT 1 FROM locations l WHERE l.id = @hanoi)
  AND NOT EXISTS (SELECT 1 FROM users x WHERE x.email = u.email)
ON DUPLICATE KEY UPDATE users.id = users.id;

-- BR-ORG-09 bản sửa M2: Giám sát tick kèm được Dọn dẹp.
INSERT INTO user_permissions (user_id, permission)
SELECT p.user_id, p.permission
FROM (
    SELECT @giamsat AS user_id, 'SUPERVISION' AS permission
    UNION ALL SELECT @giamsat2, 'SUPERVISION'
    UNION ALL SELECT @giamsat2, 'HOUSEKEEPING'
    UNION ALL SELECT @giamsat3, 'SUPERVISION'
) AS p
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = p.user_id)
ON DUPLICATE KEY UPDATE user_permissions.permission = user_permissions.permission;

-- ── Ca sáng hôm nay — BR-PERM-07 (tiếp): Giám sát chỉ thao tác trong ngày có ca ───
-- Bỏ qua người đã có ca hôm nay. Giám sát nghỉ Test cố ý không có ca.
INSERT INTO shifts (id, tenant_id, location_id, staff_id, shift_date, start_time, end_time,
                    is_overnight, duration_hours, source_template_id, check_in_at, check_out_at)
SELECT s.id, @tenant, @hanoi, s.staff_id, @d0, TIME '06:00:00', TIME '14:00:00',
       FALSE, 8.00, @ca_sang, NULL, NULL
FROM (
    SELECT '91000000-0000-0000-0000-000000000001' AS id, @giamsat AS staff_id
    UNION ALL SELECT '91000000-0000-0000-0000-000000000002', @giamsat2
    UNION ALL SELECT '91000000-0000-0000-0000-000000000003', @letan
    UNION ALL SELECT '91000000-0000-0000-0000-000000000004', @hoa
    UNION ALL SELECT '91000000-0000-0000-0000-000000000005', @nam
) AS s
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = s.staff_id AND u.status = 'ACTIVE' AND u.location_id = @hanoi)
  AND EXISTS (SELECT 1 FROM shift_templates t WHERE t.id = @ca_sang)
  AND NOT EXISTS (SELECT 1 FROM shifts x WHERE x.staff_id = s.staff_id AND x.shift_date = @d0)
ON DUPLICATE KEY UPDATE shifts.id = shifts.id;

-- ── Báo hỏng chờ xử lý — Giám sát xem và đóng thay Manager (BR-PERM-07) ──────────
INSERT INTO damage_reports (id, tenant_id, location_id, fixed_asset_id, reporter_id, description,
                            status, reported_at)
SELECT '92000000-0000-0000-0000-000000000001', @tenant, @hanoi, a.id, @letan,
       'Tivi bật không lên hình, đèn nguồn vẫn sáng', 'NEW', TIMESTAMP(@d0, '07:15:00')
FROM fixed_assets a
WHERE a.id = @tv_201
  AND a.location_id = @hanoi
  AND a.status <> 'DISPOSED'
  AND EXISTS (SELECT 1 FROM users u WHERE u.id = @letan)
ON DUPLICATE KEY UPDATE damage_reports.id = damage_reports.id;
