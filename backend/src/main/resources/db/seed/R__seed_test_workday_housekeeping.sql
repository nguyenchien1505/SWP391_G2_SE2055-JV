-- =============================================================================
-- DỮ LIỆU MẪU — luồng lịch dọn phòng (BR-HK-01..12, BR-ROOM-02, BR-SCH) tại "Khách sạn Test
-- Hà Nội": một ngày làm việc đang diễn ra, đủ mọi tình huống để thử ngay màn «Công việc dọn
-- phòng» (cả hai cách xem), «Việc của tôi», «Xếp lịch làm việc» và khối «Vận hành hôm nay».
-- CHỈ dùng cho dev/demo — cùng cơ chế với R__seed_test_accounts.sql.
--
-- NGÀY TƯƠNG ĐỐI: mọi ngày tính theo CURRENT_DATE lúc Flyway chạy file này (hôm qua / hôm nay /
-- ngày mai / ngày kia). File R__ chỉ chạy lại khi đổi nội dung, nên muốn dữ liệu khớp với hôm
-- nay thì xóa sạch database rồi chạy lại app.
--
-- Chạy SAU mọi file seed khác (Flyway xếp theo tên — "seed test workday housekeeping" đứng
-- cuối), nên tài khoản, phòng 101–203 và mẫu ca đã có sẵn.
--
-- Mọi INSERT bỏ qua khi trùng (email, số phòng đang dùng, nhân viên đã có ca ngày đó) và chỉ chèn
-- dòng phụ thuộc khi dòng cha có thật — chạy trên database đã có dữ liệu cũng không vỡ.
--
-- Tài khoản thêm — mật khẩu Test@123, vị trí Nhân viên buồng phòng, quyền Dọn dẹp:
--   dondep2@swp391.test   Nguyễn Thị Hoa
--   dondep3@swp391.test   Trần Văn Nam
--   dondep4@swp391.test   Lê Thị Mai       (nghỉ hôm nay — không có hàng trên bảng hôm nay)
--
-- Ca làm việc (Ca sáng 06–14, Ca chiều 14–22 — khớp Schedule Policy mặc định, BR-SCH-02/20). Mỗi ca
-- theo mẫu đã có người phải có ít nhất 1 lễ tân — Lễ tân Test hoặc Kiêm nhiệm Test (chốt 05/10/2026):
--   Hôm qua   Dọn dẹp Test, Lễ tân Test (sáng, đã chấm công vào/ra)
--   Hôm nay   Hoa, Nam, Lễ tân Test, Kỹ thuật Test (sáng) · Dọn dẹp Test, Kiêm nhiệm Test (chiều)
--   Ngày mai  Hoa, Mai, Lễ tân Test (sáng) · Dọn dẹp Test, Kiêm nhiệm Test (chiều)
--   Ngày kia  Hoa, Mai (sáng — THIẾU LỄ TÂN) · Nam, Kiêm nhiệm Test (chiều)
-- Thử quy tắc lễ tân:
--   · Ca sáng ngày kia cố ý thiếu lễ tân (như khi lễ tân vừa nghỉ việc): thêm Kỹ thuật Test → bị
--     chặn; thêm Lễ tân Test → được; gỡ Hoa hoặc Mai vẫn được.
--   · Gỡ Kiêm nhiệm Test khỏi Ca chiều hôm nay → bị chặn (lễ tân duy nhất của ca).
--
-- Phòng tầng 3–4 và việc dọn (cùng phòng 101–203 của R__seed_test_rooms.sql). Chỉ còn việc dọn
-- sau khi khách trả phòng, hệ thống tự sinh — đã bỏ dọn hằng ngày (chốt 05/10/2026):
--   Phòng Loại     Trạng thái    Việc dọn                                     Thử gì
--   301   Standard Đang sử dụng  —                                            Trả phòng → tự sinh việc dọn
--   302   Standard Đang sử dụng  —                                            —
--   303   Deluxe   Đang sử dụng  —                                            —
--   304   Standard Chờ dọn       Chờ giao                                     Giao cho một hoặc nhiều người
--   305   Deluxe   Đang dọn      Hoa + Nam cùng dọn                           Gỡ một người, thêm người
--   306   Standard Đang dọn      Nam đang làm                                 Gỡ Nam khỏi ca sáng → bị chặn
--   307   Standard Đang sử dụng  —                                            —
--   401   Standard Chờ kiểm tra  Hoa đã báo xong                              Kiểm tra (Manager)
--   402   Deluxe   Chờ kiểm tra  Nam đã báo xong                              Kiểm tra (Manager)
--   403   Standard Sẵn sàng      Nam dọn — kiểm tra ĐẠT                       —
--   404   Deluxe   Chờ dọn       Hoa dọn — KHÔNG ĐẠT → việc dọn lại chờ giao  Xem lý do, giao lại
--   405   Standard Đang dọn      Dọn dẹp Test nhận từ HÔM QUA, chưa xong       Tồn đọng (BR-HK-04)
--   406   Standard Đã đặt        —                                            —
--   407   Deluxe   Đang sử dụng  —                                            —
--
-- Mỗi phòng có vài dòng lịch sử trạng thái khớp với việc dọn của nó (BR-ROOM-09).
-- =============================================================================

SET NAMES utf8mb4;

SET @tenant   = '10000000-0000-0000-0000-000000000001';
SET @hanoi    = '20000000-0000-0000-0000-000000000001';
SET @hk_pos   = '40000000-0000-0000-0000-000000000002';   -- Nhân viên buồng phòng
SET @manager  = '50000000-0000-0000-0000-000000000003';
SET @letan    = '50000000-0000-0000-0000-000000000004';
SET @dondep   = '50000000-0000-0000-0000-000000000005';
SET @kythuat  = '50000000-0000-0000-0000-000000000006';
SET @kiem     = '50000000-0000-0000-0000-000000000007';
SET @hoa      = '50000000-0000-0000-0000-000000000101';
SET @nam      = '50000000-0000-0000-0000-000000000102';
SET @mai      = '50000000-0000-0000-0000-000000000103';
SET @standard = '60000000-0000-0000-0000-000000000001';
SET @deluxe   = '60000000-0000-0000-0000-000000000002';
SET @ca_sang  = '70000000-0000-0000-0000-000000000001';
SET @ca_chieu = '70000000-0000-0000-0000-000000000002';
SET @hash     = '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq';   -- Test@123

SET @d_2 = CURRENT_DATE - INTERVAL 2 DAY;
SET @d_1 = CURRENT_DATE - INTERVAL 1 DAY;   -- hôm qua
SET @d0  = CURRENT_DATE;                    -- hôm nay
SET @d1  = CURRENT_DATE + INTERVAL 1 DAY;   -- ngày mai
SET @d2  = CURRENT_DATE + INTERVAL 2 DAY;

-- Việc dọn — id theo số phòng cho dễ tra (…3041 = phòng 304, việc thứ nhất).
SET @t304  = '80000000-0000-0000-0000-000000003041';
SET @t305  = '80000000-0000-0000-0000-000000003051';
SET @t306  = '80000000-0000-0000-0000-000000003061';
SET @t401  = '80000000-0000-0000-0000-000000004011';
SET @t402  = '80000000-0000-0000-0000-000000004021';
SET @t403  = '80000000-0000-0000-0000-000000004031';
SET @t404  = '80000000-0000-0000-0000-000000004041';   -- lần dọn đầu, kiểm tra không đạt
SET @t404b = '80000000-0000-0000-0000-000000004042';   -- việc dọn lại (BR-HK-12)
SET @t405  = '80000000-0000-0000-0000-000000004051';
SET @fail_404 = 'Còn tóc trên gối, chưa thay khăn tắm';

-- ── Nhân viên dọn phòng thêm — BR-USER-01; quyền tick riêng ở user_permissions ────────
INSERT INTO users (id, tenant_id, role, email, password_hash, must_change_password, status,
                   full_name, phone, location_id, position_id,
                   start_work_date, date_of_birth, gender, address, avatar_url)
SELECT u.id, @tenant, 'STAFF', u.email, @hash, FALSE, 'ACTIVE',
       u.full_name, u.phone, @hanoi, @hk_pos,
       u.start_work_date, u.date_of_birth, u.gender, u.address, u.avatar_url
FROM (
    SELECT @hoa AS id, 'dondep2@swp391.test' AS email, 'Nguyễn Thị Hoa' AS full_name, '0900000017' AS phone,
           DATE '2026-04-01' AS start_work_date, DATE '1995-06-18' AS date_of_birth, 'FEMALE' AS gender,
           '15 Phố Huế, Hai Bà Trưng, Hà Nội' AS address, 'https://ui-avatars.com/api/?name=Thi+Hoa' AS avatar_url
    UNION ALL
    SELECT @nam, 'dondep3@swp391.test', 'Trần Văn Nam', '0900000018',
           DATE '2026-04-15', DATE '1992-09-02', 'MALE',
           '42 Kim Mã, Ba Đình, Hà Nội', 'https://ui-avatars.com/api/?name=Van+Nam'
    UNION ALL
    SELECT @mai, 'dondep4@swp391.test', 'Lê Thị Mai', '0900000019',
           DATE '2026-05-01', DATE '1999-12-24', 'FEMALE',
           '7 Láng Hạ, Đống Đa, Hà Nội', 'https://ui-avatars.com/api/?name=Thi+Mai'
) AS u
WHERE NOT EXISTS (SELECT 1 FROM users x WHERE x.email = u.email)
ON DUPLICATE KEY UPDATE users.id = users.id;

-- Quyền Dọn dẹp — Manager tick cho từng người (BR-ORG-08 bản sửa).
INSERT INTO user_permissions (user_id, permission)
SELECT id, 'HOUSEKEEPING' FROM users
WHERE email IN ('dondep2@swp391.test', 'dondep3@swp391.test', 'dondep4@swp391.test')
ON DUPLICATE KEY UPDATE user_permissions.permission = user_permissions.permission;

-- ── Phòng tầng 3–4 — BR-ROOM-05; số phòng không trùng trong khách sạn ────────────
INSERT INTO rooms (id, tenant_id, location_id, room_number, floor, room_type_id, capacity, status, unavailable_reason)
SELECT r.id, @tenant, @hanoi, r.room_number, r.floor, r.room_type_id, r.capacity, r.status, NULL
FROM (
    SELECT '70000000-0000-0000-0000-000000000301' AS id, '301' AS room_number, '3' AS floor,
           @standard AS room_type_id, 2 AS capacity, 'OCCUPIED' AS status
    UNION ALL SELECT '70000000-0000-0000-0000-000000000302', '302', '3', @standard, 2, 'OCCUPIED'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000303', '303', '3', @deluxe,   3, 'OCCUPIED'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000304', '304', '3', @standard, 2, 'DIRTY'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000305', '305', '3', @deluxe,   3, 'CLEANING'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000306', '306', '3', @standard, 2, 'CLEANING'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000307', '307', '3', @standard, 2, 'OCCUPIED'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000401', '401', '4', @standard, 2, 'INSPECTION'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000402', '402', '4', @deluxe,   3, 'INSPECTION'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000403', '403', '4', @standard, 2, 'AVAILABLE'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000404', '404', '4', @deluxe,   3, 'DIRTY'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000405', '405', '4', @standard, 2, 'CLEANING'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000406', '406', '4', @standard, 2, 'RESERVED'
    UNION ALL SELECT '70000000-0000-0000-0000-000000000407', '407', '4', @deluxe,   3, 'OCCUPIED'
) AS r
WHERE NOT EXISTS (
    SELECT 1 FROM rooms x WHERE x.location_id = @hanoi AND x.room_number = r.room_number AND x.is_active)
ON DUPLICATE KEY UPDATE rooms.id = rooms.id;

-- ── Ca làm việc — BR-HK-03: chỉ giao việc dọn cho người CÓ CA ngày đó ─────────────
-- Bỏ qua nếu nhân viên đã có ca ngày đó (database đã có dữ liệu): người đó vẫn có ca, việc dọn
-- bên dưới vẫn đúng BR-HK-03.
INSERT INTO shifts (id, tenant_id, location_id, staff_id, shift_date, start_time, end_time,
                    is_overnight, duration_hours, source_template_id, check_in_at, check_out_at)
SELECT s.id, @tenant, @hanoi, s.staff_id, s.shift_date, s.start_time, s.end_time,
       FALSE, 8.00, s.template_id, s.check_in_at, s.check_out_at
FROM (
    SELECT '90000000-0000-0000-0000-000000000001' AS id, @dondep AS staff_id, @d_1 AS shift_date,
           TIME '06:00:00' AS start_time, TIME '14:00:00' AS end_time, @ca_sang AS template_id,
           TIMESTAMP(@d_1, '06:02:00') AS check_in_at, TIMESTAMP(@d_1, '14:05:00') AS check_out_at
    UNION ALL SELECT '90000000-0000-0000-0000-000000000017', @letan,   @d_1, TIME '06:00:00', TIME '14:00:00', @ca_sang,  TIMESTAMP(@d_1, '05:57:00'), TIMESTAMP(@d_1, '14:02:00')
    UNION ALL SELECT '90000000-0000-0000-0000-000000000002', @hoa,     @d0, TIME '06:00:00', TIME '14:00:00', @ca_sang,  TIMESTAMP(@d0, '05:58:00'), NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000003', @nam,     @d0, TIME '06:00:00', TIME '14:00:00', @ca_sang,  TIMESTAMP(@d0, '06:03:00'), NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000004', @letan,   @d0, TIME '06:00:00', TIME '14:00:00', @ca_sang,  TIMESTAMP(@d0, '05:55:00'), NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000005', @kythuat, @d0, TIME '06:00:00', TIME '14:00:00', @ca_sang,  TIMESTAMP(@d0, '06:10:00'), NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000006', @dondep,  @d0, TIME '14:00:00', TIME '22:00:00', @ca_chieu, NULL, NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000007', @kiem,    @d0, TIME '14:00:00', TIME '22:00:00', @ca_chieu, NULL, NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000008', @hoa,     @d1, TIME '06:00:00', TIME '14:00:00', @ca_sang,  NULL, NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000009', @mai,     @d1, TIME '06:00:00', TIME '14:00:00', @ca_sang,  NULL, NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000010', @letan,   @d1, TIME '06:00:00', TIME '14:00:00', @ca_sang,  NULL, NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000011', @dondep,  @d1, TIME '14:00:00', TIME '22:00:00', @ca_chieu, NULL, NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000012', @kiem,    @d1, TIME '14:00:00', TIME '22:00:00', @ca_chieu, NULL, NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000013', @hoa,     @d2, TIME '06:00:00', TIME '14:00:00', @ca_sang,  NULL, NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000014', @mai,     @d2, TIME '06:00:00', TIME '14:00:00', @ca_sang,  NULL, NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000015', @nam,     @d2, TIME '14:00:00', TIME '22:00:00', @ca_chieu, NULL, NULL
    UNION ALL SELECT '90000000-0000-0000-0000-000000000018', @kiem,    @d2, TIME '14:00:00', TIME '22:00:00', @ca_chieu, NULL, NULL
) AS s
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = s.staff_id AND u.status = 'ACTIVE')
  AND NOT EXISTS (SELECT 1 FROM shifts x WHERE x.staff_id = s.staff_id AND x.shift_date = s.shift_date)
ON DUPLICATE KEY UPDATE shifts.id = shifts.id;

-- ── Việc dọn — BR-HK-06/11/12; mỗi phòng tối đa 1 việc đang mở ─────────────────
-- Việc «Chờ kiểm tra» chưa có completed_at: app chỉ điền khi Manager kiểm tra xong (BR-HK-06).
-- Người dọn nằm ở bảng housekeeping_task_assignees bên dưới (một việc có thể nhiều người — V4).
INSERT INTO housekeeping_tasks (id, tenant_id, location_id, room_id, task_type, status,
                                assigned_date, assigned_by, assigned_at,
                                completed_at, created_source, created_at)
SELECT t.id, @tenant, @hanoi, t.room_id, 'CHECKOUT', t.status,
       t.assigned_date, IF(t.assigned_date IS NULL, NULL, @manager), t.assigned_at,
       t.completed_at, 'CHECKOUT_AUTO', t.created_at
FROM (
    SELECT @t304 AS id, '70000000-0000-0000-0000-000000000304' AS room_id, 'UNASSIGNED' AS status,
           NULL AS assigned_date, NULL AS assigned_at, NULL AS completed_at, TIMESTAMP(@d0, '11:20:00') AS created_at
    UNION ALL SELECT @t305, '70000000-0000-0000-0000-000000000305', 'IN_PROGRESS', @d0,
           TIMESTAMP(@d0, '08:05:00'), NULL, TIMESTAMP(@d0, '07:30:00')
    UNION ALL SELECT @t306, '70000000-0000-0000-0000-000000000306', 'IN_PROGRESS', @d0,
           TIMESTAMP(@d0, '08:10:00'), NULL, TIMESTAMP(@d0, '07:45:00')
    UNION ALL SELECT @t401, '70000000-0000-0000-0000-000000000401', 'PENDING_INSPECTION', @d0,
           TIMESTAMP(@d0, '07:00:00'), NULL, TIMESTAMP(@d0, '06:40:00')
    UNION ALL SELECT @t402, '70000000-0000-0000-0000-000000000402', 'PENDING_INSPECTION', @d0,
           TIMESTAMP(@d0, '07:05:00'), NULL, TIMESTAMP(@d0, '06:50:00')
    UNION ALL SELECT @t403, '70000000-0000-0000-0000-000000000403', 'COMPLETED', @d0,
           TIMESTAMP(@d0, '06:45:00'), TIMESTAMP(@d0, '08:00:00'), TIMESTAMP(@d0, '06:30:00')
    UNION ALL SELECT @t404, '70000000-0000-0000-0000-000000000404', 'COMPLETED', @d0,
           TIMESTAMP(@d0, '06:50:00'), TIMESTAMP(@d0, '08:05:00'), TIMESTAMP(@d0, '06:35:00')
    UNION ALL SELECT @t405, '70000000-0000-0000-0000-000000000405', 'IN_PROGRESS', @d_1,
           TIMESTAMP(@d_1, '12:30:00'), NULL, TIMESTAMP(@d_1, '11:00:00')
) AS t
WHERE EXISTS (SELECT 1 FROM rooms r WHERE r.id = t.room_id)
ON DUPLICATE KEY UPDATE housekeeping_tasks.id = housekeeping_tasks.id;

-- Người dọn của từng việc — phòng 305 có hai người cùng dọn. Chỉ gắn vào việc ĐÃ giao: việc chờ
-- giao thì không có ai (service giữ bất biến này).
INSERT INTO housekeeping_task_assignees (task_id, staff_id)
SELECT a.task_id, a.staff_id
FROM (
    SELECT @t305 AS task_id, @hoa AS staff_id
    UNION ALL SELECT @t305, @nam
    UNION ALL SELECT @t306, @nam
    UNION ALL SELECT @t401, @hoa
    UNION ALL SELECT @t402, @nam
    UNION ALL SELECT @t403, @nam
    UNION ALL SELECT @t404, @hoa
    UNION ALL SELECT @t405, @dondep
) AS a
WHERE EXISTS (SELECT 1 FROM housekeeping_tasks t WHERE t.id = a.task_id AND t.status <> 'UNASSIGNED')
  AND EXISTS (SELECT 1 FROM users u WHERE u.id = a.staff_id)
ON DUPLICATE KEY UPDATE housekeeping_task_assignees.task_id = housekeeping_task_assignees.task_id;

-- Việc dọn lại của phòng 404 — chèn RIÊNG, sau việc gốc, vì trỏ về việc gốc (BR-HK-12).
INSERT INTO housekeeping_tasks (id, tenant_id, location_id, room_id, task_type, status,
                                created_source, parent_task_id, created_at)
SELECT @t404b, @tenant, @hanoi, '70000000-0000-0000-0000-000000000404', 'CHECKOUT', 'UNASSIGNED',
       'INSPECTION_FAILED', @t404, TIMESTAMP(@d0, '08:05:00')
FROM DUAL
WHERE EXISTS (SELECT 1 FROM housekeeping_tasks x WHERE x.id = @t404)
ON DUPLICATE KEY UPDATE housekeeping_tasks.id = housekeeping_tasks.id;

-- ── Biên bản kiểm tra — BR-HK-06/08: không đạt thì bắt buộc lý do, trỏ sang việc dọn lại ──
INSERT INTO inspection_records (id, tenant_id, task_id, room_id, inspector_id, result, reason,
                                inspected_at, next_task_id)
SELECT i.id, @tenant, i.task_id, i.room_id, @manager, i.result, i.reason, i.inspected_at, i.next_task_id
FROM (
    SELECT '81000000-0000-0000-0000-000000004031' AS id, @t403 AS task_id,
           '70000000-0000-0000-0000-000000000403' AS room_id, 'PASS' AS result, NULL AS reason,
           TIMESTAMP(@d0, '08:00:00') AS inspected_at, NULL AS next_task_id
    UNION ALL SELECT '81000000-0000-0000-0000-000000004041', @t404,
           '70000000-0000-0000-0000-000000000404', 'FAIL', @fail_404, TIMESTAMP(@d0, '08:05:00'), @t404b
) AS i
WHERE EXISTS (SELECT 1 FROM housekeeping_tasks x WHERE x.id = i.task_id)
  AND (i.next_task_id IS NULL OR EXISTS (SELECT 1 FROM housekeeping_tasks y WHERE y.id = i.next_task_id))
ON DUPLICATE KEY UPDATE inspection_records.id = inspection_records.id;

-- ── Lịch sử trạng thái phòng — BR-ROOM-02/09 ─────────────────────────────────────
-- Giống cách app ghi: bước của Hệ thống không có người đổi; bước do việc dọn kéo theo trỏ về việc đó.
INSERT INTO room_status_history (id, tenant_id, room_id, from_status, to_status, changed_by,
                                 changed_at, change_source, reason, related_task_id, created_at)
SELECT h.id, @tenant, h.room_id, h.from_status, h.to_status, h.changed_by,
       h.changed_at, h.change_source, h.reason, h.task_id, h.changed_at
FROM (
    SELECT '82000000-0000-0000-0000-000000003011' AS id, '70000000-0000-0000-0000-000000000301' AS room_id,
           'AVAILABLE' AS from_status, 'OCCUPIED' AS to_status, @letan AS changed_by,
           TIMESTAMP(@d_2, '14:10:00') AS changed_at, 'RECEPTION' AS change_source, NULL AS reason, NULL AS task_id
    UNION ALL SELECT '82000000-0000-0000-0000-000000003021', '70000000-0000-0000-0000-000000000302',
           'AVAILABLE', 'OCCUPIED', @letan, TIMESTAMP(@d_1, '15:00:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000003031', '70000000-0000-0000-0000-000000000303',
           'AVAILABLE', 'OCCUPIED', @letan, TIMESTAMP(@d_2, '13:30:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000003041', '70000000-0000-0000-0000-000000000304',
           'OCCUPIED', 'DIRTY', @letan, TIMESTAMP(@d0, '11:20:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000003051', '70000000-0000-0000-0000-000000000305',
           'OCCUPIED', 'DIRTY', @letan, TIMESTAMP(@d0, '07:30:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000003052', '70000000-0000-0000-0000-000000000305',
           'DIRTY', 'CLEANING', NULL, TIMESTAMP(@d0, '08:05:00'), 'SYSTEM', NULL, @t305
    UNION ALL SELECT '82000000-0000-0000-0000-000000003061', '70000000-0000-0000-0000-000000000306',
           'OCCUPIED', 'DIRTY', @letan, TIMESTAMP(@d0, '07:45:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000003062', '70000000-0000-0000-0000-000000000306',
           'DIRTY', 'CLEANING', NULL, TIMESTAMP(@d0, '08:10:00'), 'SYSTEM', NULL, @t306
    UNION ALL SELECT '82000000-0000-0000-0000-000000003071', '70000000-0000-0000-0000-000000000307',
           'AVAILABLE', 'OCCUPIED', @letan, TIMESTAMP(@d_1, '16:20:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000004011', '70000000-0000-0000-0000-000000000401',
           'OCCUPIED', 'DIRTY', @letan, TIMESTAMP(@d0, '06:40:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000004012', '70000000-0000-0000-0000-000000000401',
           'DIRTY', 'CLEANING', NULL, TIMESTAMP(@d0, '07:00:00'), 'SYSTEM', NULL, @t401
    UNION ALL SELECT '82000000-0000-0000-0000-000000004013', '70000000-0000-0000-0000-000000000401',
           'CLEANING', 'INSPECTION', @hoa, TIMESTAMP(@d0, '07:50:00'), 'HOUSEKEEPING', NULL, @t401
    UNION ALL SELECT '82000000-0000-0000-0000-000000004021', '70000000-0000-0000-0000-000000000402',
           'OCCUPIED', 'DIRTY', @letan, TIMESTAMP(@d0, '06:50:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000004022', '70000000-0000-0000-0000-000000000402',
           'DIRTY', 'CLEANING', NULL, TIMESTAMP(@d0, '07:05:00'), 'SYSTEM', NULL, @t402
    UNION ALL SELECT '82000000-0000-0000-0000-000000004023', '70000000-0000-0000-0000-000000000402',
           'CLEANING', 'INSPECTION', @nam, TIMESTAMP(@d0, '08:30:00'), 'HOUSEKEEPING', NULL, @t402
    UNION ALL SELECT '82000000-0000-0000-0000-000000004031', '70000000-0000-0000-0000-000000000403',
           'OCCUPIED', 'DIRTY', @letan, TIMESTAMP(@d0, '06:30:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000004032', '70000000-0000-0000-0000-000000000403',
           'DIRTY', 'CLEANING', NULL, TIMESTAMP(@d0, '06:45:00'), 'SYSTEM', NULL, @t403
    UNION ALL SELECT '82000000-0000-0000-0000-000000004033', '70000000-0000-0000-0000-000000000403',
           'CLEANING', 'INSPECTION', @nam, TIMESTAMP(@d0, '07:40:00'), 'HOUSEKEEPING', NULL, @t403
    UNION ALL SELECT '82000000-0000-0000-0000-000000004034', '70000000-0000-0000-0000-000000000403',
           'INSPECTION', 'AVAILABLE', @manager, TIMESTAMP(@d0, '08:00:00'), 'MANAGER', NULL, @t403
    UNION ALL SELECT '82000000-0000-0000-0000-000000004041', '70000000-0000-0000-0000-000000000404',
           'OCCUPIED', 'DIRTY', @letan, TIMESTAMP(@d0, '06:35:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000004042', '70000000-0000-0000-0000-000000000404',
           'DIRTY', 'CLEANING', NULL, TIMESTAMP(@d0, '06:50:00'), 'SYSTEM', NULL, @t404
    UNION ALL SELECT '82000000-0000-0000-0000-000000004043', '70000000-0000-0000-0000-000000000404',
           'CLEANING', 'INSPECTION', @hoa, TIMESTAMP(@d0, '07:35:00'), 'HOUSEKEEPING', NULL, @t404
    UNION ALL SELECT '82000000-0000-0000-0000-000000004044', '70000000-0000-0000-0000-000000000404',
           'INSPECTION', 'DIRTY', @manager, TIMESTAMP(@d0, '08:05:00'), 'MANAGER', @fail_404, @t404
    UNION ALL SELECT '82000000-0000-0000-0000-000000004051', '70000000-0000-0000-0000-000000000405',
           'OCCUPIED', 'DIRTY', @letan, TIMESTAMP(@d_1, '11:00:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000004052', '70000000-0000-0000-0000-000000000405',
           'DIRTY', 'CLEANING', NULL, TIMESTAMP(@d_1, '12:30:00'), 'SYSTEM', NULL, @t405
    UNION ALL SELECT '82000000-0000-0000-0000-000000004061', '70000000-0000-0000-0000-000000000406',
           'AVAILABLE', 'RESERVED', @letan, TIMESTAMP(@d0, '09:00:00'), 'RECEPTION', NULL, NULL
    UNION ALL SELECT '82000000-0000-0000-0000-000000004071', '70000000-0000-0000-0000-000000000407',
           'AVAILABLE', 'OCCUPIED', @letan, TIMESTAMP(@d_1, '14:00:00'), 'RECEPTION', NULL, NULL
) AS h
WHERE EXISTS (SELECT 1 FROM rooms r WHERE r.id = h.room_id)
  AND (h.task_id IS NULL OR EXISTS (SELECT 1 FROM housekeeping_tasks x WHERE x.id = h.task_id))
  AND (h.changed_by IS NULL OR EXISTS (SELECT 1 FROM users u WHERE u.id = h.changed_by))
ON DUPLICATE KEY UPDATE room_status_history.id = room_status_history.id;
