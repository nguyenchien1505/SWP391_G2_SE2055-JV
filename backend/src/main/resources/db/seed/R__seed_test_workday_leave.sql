-- =============================================================================
-- DỮ LIỆU MẪU — Đơn xin nghỉ (BR Milestone 2: BR-SCH-06..08, BR-SCH-27..31) tại "Khách sạn Test Hà Nội".
-- CHỈ dùng cho dev/demo — cùng cơ chế với R__seed_test_accounts.sql.
--
-- Chạy SAU R__seed_test_workday_housekeeping.sql (Flyway xếp theo tên: "housekeeping" < "leave"), nên các
-- nhân viên dọn phòng (Hoa, Nam) đã có.
--
-- NGÀY TƯƠNG ĐỐI: tính theo CURRENT_DATE lúc Flyway chạy file này. File R__ chỉ chạy lại khi đổi nội dung,
-- nên muốn dữ liệu khớp với hôm nay thì xóa sạch database rồi chạy lại app.
--
-- Đơn nghỉ (mật khẩu mọi tài khoản Test@123):
--   Người gửi         Loại         Ngày nghỉ (so với hôm nay)   Trạng thái   Thử gì
--   Nguyễn Thị Hoa    Phép năm     +5 → +6 (2 ngày)             Chờ duyệt    Manager duyệt — quỹ phép trừ 2 ngày
--   Kiêm nhiệm Test   Không lương  +2 (1 ngày)                  Chờ duyệt    Duyệt → ca chiều +2 mất lễ tân duy nhất: phải
--                                                                           chọn lễ tân thay (Lễ tân Test) mới duyệt được
--                                                                           (BR-SCH-29 sửa 31/10/2026)
--   Lễ tân Test       Phép năm     −20 → −19 (2 ngày)           Đã duyệt     Quỹ phép của Lễ tân Test đã dùng 2 ngày
--   Quản lý Hà Nội    Phép năm     +10 → +11 (2 ngày)           Chờ duyệt    Giám đốc duyệt đơn của Manager (BR-SCH-08)
--   Trần Văn Nam      Không lương  +3 (1 ngày)                  Từ chối      dondep3 mở «Đơn xin nghỉ» đọc lý do từ chối
--                                                                           (bắt buộc từ 31/10/2026 — V13)
--
-- Ca chiều +2 cho Kiêm nhiệm Test (lễ tân) và Trần Văn Nam (không phải lễ tân): duyệt đơn của Kiêm nhiệm thì
-- ca đó còn người mà hết lễ tân. Bỏ qua người đã có ca ngày đó — database mới đã có sẵn hai ca này từ seed dọn
-- phòng; database cũ chạy seed dọn phòng từ hôm khác thì ngày +2 của nó đã qua, nên file này tự chèn.
--
-- Mọi INSERT bỏ qua khi trùng và chỉ chèn dòng phụ thuộc khi dòng cha có thật — chạy trên database đã có
-- dữ liệu cũng không vỡ.
-- =============================================================================

SET NAMES utf8mb4;

SET @tenant   = '10000000-0000-0000-0000-000000000001';
SET @hanoi    = '20000000-0000-0000-0000-000000000001';
SET @manager  = '50000000-0000-0000-0000-000000000003';
SET @letan    = '50000000-0000-0000-0000-000000000004';
SET @kiem     = '50000000-0000-0000-0000-000000000007';
SET @hoa      = '50000000-0000-0000-0000-000000000101';
SET @nam      = '50000000-0000-0000-0000-000000000102';
SET @ca_chieu = '70000000-0000-0000-0000-000000000002';

SET @d2 = CURRENT_DATE + INTERVAL 2 DAY;

-- ── Ca chiều +2 — để đơn của Kiêm nhiệm Test chạm đúng luật lễ tân (BR-SCH-29 sửa 31/10/2026) ─────
INSERT INTO shifts (id, tenant_id, location_id, staff_id, shift_date, start_time, end_time,
                    is_overnight, duration_hours, source_template_id, check_in_at, check_out_at)
SELECT s.id, @tenant, @hanoi, s.staff_id, @d2, TIME '14:00:00', TIME '22:00:00',
       FALSE, 8.00, @ca_chieu, NULL, NULL
FROM (
    SELECT '94000000-0000-0000-0000-000000000001' AS id, @kiem AS staff_id
    UNION ALL SELECT '94000000-0000-0000-0000-000000000002', @nam
) AS s
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = s.staff_id AND u.status = 'ACTIVE' AND u.location_id = @hanoi)
  AND EXISTS (SELECT 1 FROM shift_templates t WHERE t.id = @ca_chieu)
  AND NOT EXISTS (SELECT 1 FROM shifts x WHERE x.staff_id = s.staff_id AND x.shift_date = @d2)
ON DUPLICATE KEY UPDATE shifts.id = shifts.id;

-- ── Đơn nghỉ — BR-SCH-06 bản sửa: phép năm / không lương ────────────────────────
-- Đơn đã duyệt / từ chối có người duyệt và thời điểm quyết định; đơn chờ duyệt thì chưa (ck_leave_requests_decision).
-- Đơn từ chối phải có lý do, đơn khác thì không (ck_leave_requests_reject_reason).
-- Ngày trong UNION tính thẳng từ CURRENT_DATE, không qua biến: biến @d giữ ngày dưới dạng chuỗi latin1, chung
-- một cột với biểu thức "@d + INTERVAL" (chuỗi utf8mb4) thì MySQL báo "Illegal mix of collations".
INSERT INTO leave_requests (id, tenant_id, location_id, requester_id, leave_type, from_date, to_date, reason,
                            status, approver_id, decided_at, reject_reason, created_at)
SELECT l.id, @tenant, @hanoi, l.requester_id, l.leave_type, l.from_date, l.to_date, l.reason,
       l.status, l.approver_id, l.decided_at, l.reject_reason, l.created_at
FROM (
    SELECT '93000000-0000-0000-0000-000000000001' AS id, @hoa AS requester_id, 'ANNUAL' AS leave_type,
           CURRENT_DATE + INTERVAL 5 DAY AS from_date, CURRENT_DATE + INTERVAL 6 DAY AS to_date,
           'Về quê dự đám cưới em gái' AS reason, 'PENDING' AS status,
           CAST(NULL AS CHAR(36)) AS approver_id, CAST(NULL AS DATETIME) AS decided_at,
           CAST(NULL AS CHAR(500)) AS reject_reason, TIMESTAMP(CURRENT_DATE, '08:15:00') AS created_at
    UNION ALL SELECT '93000000-0000-0000-0000-000000000002', @kiem, 'UNPAID',
           CURRENT_DATE + INTERVAL 2 DAY, CURRENT_DATE + INTERVAL 2 DAY,
           'Đưa con đi khám bệnh', 'PENDING', NULL, NULL, NULL, TIMESTAMP(CURRENT_DATE, '09:40:00')
    UNION ALL SELECT '93000000-0000-0000-0000-000000000003', @letan, 'ANNUAL',
           CURRENT_DATE - INTERVAL 20 DAY, CURRENT_DATE - INTERVAL 19 DAY, 'Nghỉ phép cùng gia đình', 'APPROVED',
           @manager, TIMESTAMP(CURRENT_DATE - INTERVAL 25 DAY, '10:00:00'), NULL,
           TIMESTAMP(CURRENT_DATE - INTERVAL 26 DAY, '16:30:00')
    UNION ALL SELECT '93000000-0000-0000-0000-000000000004', @manager, 'ANNUAL',
           CURRENT_DATE + INTERVAL 10 DAY, CURRENT_DATE + INTERVAL 11 DAY, 'Nghỉ phép năm, về thăm bố mẹ', 'PENDING',
           NULL, NULL, NULL, TIMESTAMP(CURRENT_DATE, '07:30:00')
    UNION ALL SELECT '93000000-0000-0000-0000-000000000005', @nam, 'UNPAID',
           CURRENT_DATE + INTERVAL 3 DAY, CURRENT_DATE + INTERVAL 3 DAY, 'Đi đám cưới bạn ở Hải Phòng', 'REJECTED',
           @manager, TIMESTAMP(CURRENT_DATE - INTERVAL 1 DAY, '17:20:00'),
           'Hôm đó đoàn khách nhận 20 phòng, bộ phận dọn phòng cần đủ người — bạn chọn ngày khác nhé.',
           TIMESTAMP(CURRENT_DATE - INTERVAL 2 DAY, '11:05:00')
) AS l
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = l.requester_id AND u.status = 'ACTIVE')
  AND (l.approver_id IS NULL OR EXISTS (SELECT 1 FROM users a WHERE a.id = l.approver_id))
ON DUPLICATE KEY UPDATE leave_requests.id = leave_requests.id;
