-- =============================================================================
-- DỮ LIỆU DEMO — đơn nghỉ phép
-- CHỈ dùng cho dev/demo — cùng cơ chế với R__seed_test_accounts.sql. FILE SINH TỰ ĐỘNG, đừng sửa tay:
--   node backend/scripts/demo-seed/generate-demo-seed.js
-- NGÀY TƯƠNG ĐỐI: @d0 = CURRENT_DATE lúc Flyway chạy file này. Muốn dữ liệu khớp hôm nay thì xóa
-- database rồi chạy lại app — nên làm vào SÁNG NGÀY DEMO, sau 11:00 (mọi sự kiện "sáng nay" đều trước 11:00).
-- Mật khẩu mọi tài khoản: Test@123. Mọi INSERT bỏ qua dòng đã có (ON DUPLICATE KEY UPDATE id = id).
-- =============================================================================

SET NAMES utf8mb4;
SET @d0 = CURRENT_DATE;

--
-- Đơn nghỉ (chỉ nhân viên buồng phòng — duyệt không làm ca nào thiếu lễ tân):
--   Người gửi       Loại    Ngày        Trạng thái
--   buongphong.hn2  ANNUAL  d-12..d-11  APPROVED
--   buongphong.hn3  ANNUAL  d+3..d+4    APPROVED
--   buongphong.hn4  ANNUAL  d+5..d+6    PENDING
--   buongphong.hn6  UNPAID  d+1         PENDING
--   buongphong.hn1  ANNUAL  d+2         REJECTED
--   buongphong.dn2  UNPAID  d+6         CANCELLED (tự hủy)
--   buongphong.dn1  ANNUAL  d+4..d+5    CANCELLED (Manager thu hồi)
--   manager.dn      ANNUAL  d+10..d+11  PENDING
-- Đơn của Manager Đà Nẵng do Giám đốc duyệt. Đơn nghỉ ốm d+1 kèm 1 ảnh PNG.

INSERT INTO leave_requests (id, tenant_id, location_id, requester_id, leave_type, from_date, to_date, reason, status, approver_id, decided_at, reject_reason, revoked_by, revoked_at, revoke_reason, original_to_date, created_at, created_by, updated_at) VALUES
    ('d9000000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'd5000000-0000-4000-8000-000000000019', 'ANNUAL', (@d0 + INTERVAL -12 DAY), (@d0 + INTERVAL -11 DAY), 'Về quê dự đám cưới em gái.', 'APPROVED', 'd5000000-0000-4000-8000-000000000007', TIMESTAMP((@d0 + INTERVAL -19 DAY), '08:30:00'), NULL, NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -20 DAY), '09:10:00'), 'd5000000-0000-4000-8000-000000000019', NULL),
    ('d9000000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'd5000000-0000-4000-8000-000000000020', 'ANNUAL', (@d0 + INTERVAL 3 DAY), (@d0 + INTERVAL 4 DAY), 'Đưa con đi khám định kỳ ở bệnh viện Nhi Trung ương.', 'APPROVED', 'd5000000-0000-4000-8000-000000000007', TIMESTAMP((@d0 + INTERVAL -5 DAY), '09:00:00'), NULL, NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -6 DAY), '13:20:00'), 'd5000000-0000-4000-8000-000000000020', NULL),
    ('d9000000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'd5000000-0000-4000-8000-000000000021', 'ANNUAL', (@d0 + INTERVAL 5 DAY), (@d0 + INTERVAL 6 DAY), 'Nghỉ phép năm, đi du lịch cùng gia đình.', 'PENDING', NULL, NULL, NULL, NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -1 DAY), '16:45:00'), 'd5000000-0000-4000-8000-000000000021', NULL),
    ('d9000000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'd5000000-0000-4000-8000-000000000023', 'UNPAID', (@d0 + INTERVAL 1 DAY), (@d0 + INTERVAL 1 DAY), 'Bị sốt, bác sĩ cho nghỉ 1 ngày — có giấy khám bệnh đính kèm.', 'PENDING', NULL, NULL, NULL, NULL, NULL, NULL, NULL, TIMESTAMP(@d0, '06:40:00'), 'd5000000-0000-4000-8000-000000000023', NULL),
    ('d9000000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'd5000000-0000-4000-8000-000000000018', 'ANNUAL', (@d0 + INTERVAL 2 DAY), (@d0 + INTERVAL 2 DAY), 'Việc gia đình.', 'REJECTED', 'd5000000-0000-4000-8000-000000000007', TIMESTAMP((@d0 + INTERVAL -4 DAY), '08:15:00'), 'Ngày này khách sạn đón đoàn 40 khách, thiếu người dọn phòng. Bạn chọn ngày khác giúp chị nhé.', NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -5 DAY), '10:00:00'), 'd5000000-0000-4000-8000-000000000018', NULL),
    ('d9000000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002', 'd5000000-0000-4000-8000-000000000036', 'UNPAID', (@d0 + INTERVAL 6 DAY), (@d0 + INTERVAL 6 DAY), 'Đi làm giấy tờ nhà đất.', 'CANCELLED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -3 DAY), '11:30:00'), 'd5000000-0000-4000-8000-000000000036', TIMESTAMP((@d0 + INTERVAL -2 DAY), '08:00:00')),
    ('d9000000-0000-4000-8000-000000000007', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002', 'd5000000-0000-4000-8000-000000000035', 'ANNUAL', (@d0 + INTERVAL 4 DAY), (@d0 + INTERVAL 5 DAY), 'Về quê thăm bố mẹ.', 'CANCELLED', 'd5000000-0000-4000-8000-000000000008', TIMESTAMP((@d0 + INTERVAL -7 DAY), '10:30:00'), NULL, 'd5000000-0000-4000-8000-000000000008', TIMESTAMP((@d0 + INTERVAL -1 DAY), '17:20:00'), 'Khách đoàn đặt kín phòng hai ngày này; đã trao đổi, nhân viên đồng ý dời phép sang tuần sau.', NULL, TIMESTAMP((@d0 + INTERVAL -8 DAY), '09:00:00'), 'd5000000-0000-4000-8000-000000000035', NULL),
    ('d9000000-0000-4000-8000-000000000008', 'd1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002', 'd5000000-0000-4000-8000-000000000008', 'ANNUAL', (@d0 + INTERVAL 10 DAY), (@d0 + INTERVAL 11 DAY), 'Tham dự lễ tốt nghiệp của con tại TP.HCM.', 'PENDING', NULL, NULL, NULL, NULL, NULL, NULL, NULL, TIMESTAMP((@d0 + INTERVAL -2 DAY), '15:00:00'), 'd5000000-0000-4000-8000-000000000008', NULL)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO leave_request_attachments (id, tenant_id, leave_request_id, file_name, content_type, size_bytes, data, created_at, created_by) VALUES
    ('d9100000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'd9000000-0000-4000-8000-000000000004', 'giay-kham-benh.png', 'image/png', 278, X'89504e470d0a1a0a0000000d49484452000000a00000006e080000000085464735000000dd49444154789cedd4c109c34014035117964ad27f0bb907725dec7819f89660a682870e3adee11d4f03fe2590f6037e4213481348ab03be26cb058205e3124813481348ab03ce1cf5522e707bc1b804d204d204d2ea80d347bd940bbcb9605c02690269026975c0f17f3e2f1778b9605c02690269026975c0e71e390a08168c4b204d204d20ad0e3873d44bb9c0ed05e3124813481348ab034e1ff5522ef0e6827109a409a409a4d501c7fff9bc5ce0e5827109a409a409a4d5019f7be4282058302e8134813481b43ae0cc512fe502b7178c4b20ad07989c405a3cf00b74df29293aa85d7d0000000049454e44ae426082', TIMESTAMP(@d0, '06:40:00'), 'd5000000-0000-4000-8000-000000000023')
ON DUPLICATE KEY UPDATE id = id;

