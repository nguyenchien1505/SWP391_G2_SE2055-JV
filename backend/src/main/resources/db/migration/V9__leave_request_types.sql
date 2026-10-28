-- Đơn xin nghỉ (BR Milestone 2, chốt 19/10/2026):
--
--   BR-SCH-06 (sửa): 2 loại nghỉ — Nghỉ phép năm (có lương, trừ quỹ phép) và Nghỉ không lương.
--   Thêm các ràng buộc mà BR1 đã nêu nhưng DB chưa ép, để dữ liệu sai không lọt qua đường nào:
--     · lý do bắt buộc, không được chỉ toàn khoảng trắng (BR-SCH-16);
--     · đơn đã duyệt / từ chối mới có người duyệt và thời điểm quyết định (BR-SCH-08, BR-SCH-23);
--     · không tự duyệt đơn của chính mình (BR-SCH-08).
--
-- Trước bản này bảng chưa có luồng nào ghi, nên mặc định 'UNPAID' chỉ để thêm cột an toàn; sau đó bỏ
-- mặc định — mỗi đơn mới phải nói rõ loại.

ALTER TABLE leave_requests
    ADD COLUMN leave_type VARCHAR(20) NOT NULL DEFAULT 'UNPAID'
        COMMENT 'ANNUAL (phép năm, có lương, trừ quỹ) | UNPAID (không lương) — BR-SCH-06 bản sửa M2'
        AFTER requester_id;
ALTER TABLE leave_requests ALTER COLUMN leave_type DROP DEFAULT;

ALTER TABLE leave_requests
    ADD CONSTRAINT ck_leave_requests_type CHECK (leave_type IN ('ANNUAL', 'UNPAID')),
    ADD CONSTRAINT ck_leave_requests_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    ADD CONSTRAINT ck_leave_requests_decision CHECK (
        (status IN ('PENDING', 'CANCELLED') AND approver_id IS NULL AND decided_at IS NULL) OR
        (status IN ('APPROVED', 'REJECTED') AND approver_id IS NOT NULL AND decided_at IS NOT NULL)),
    ADD CONSTRAINT ck_leave_requests_not_self CHECK (approver_id IS NULL OR approver_id <> requester_id);

-- Kiểm đơn trùng ngày và cộng ngày phép theo năm của một người.
CREATE INDEX idx_leave_requests_requester_dates ON leave_requests (requester_id, from_date, to_date);
