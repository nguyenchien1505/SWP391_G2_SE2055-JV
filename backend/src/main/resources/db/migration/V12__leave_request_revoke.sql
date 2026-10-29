-- BR-SCH-16 (sửa, chốt 28/10/2026): người gửi vẫn không tự hủy đơn đã duyệt, nhưng NGƯỜI DUYỆT hủy được phần ngày
-- chưa tới, bắt buộc ghi lý do; quỹ phép hoàn lại đúng số ngày đó; ca đã gỡ không tự gán lại. Trước đây BR1 bảo
-- "muốn đi làm lại thì Manager xếp ca mới" mà BR-SCH-31 lại cấm xếp ca trong khoảng nghỉ đã duyệt.
--   · Đơn chưa tới ngày nghỉ → hủy cả đơn: CANCELLED, vẫn giữ người duyệt và thời điểm duyệt.
--   · Đơn đang nghỉ dở → rút ngắn tới hết hôm nay: to_date = hôm nay, original_to_date = ngày cũ.
-- Quỹ phép không lưu số dư mà cộng lại từ các đơn đã duyệt (LeaveQuota), nên tự hoàn khi đơn đổi.

ALTER TABLE leave_requests
    ADD COLUMN revoked_by       CHAR(36)     NULL
        COMMENT 'Người duyệt hủy phần ngày chưa tới — BR-SCH-16 bản sửa 28/10/2026' AFTER decided_at,
    ADD COLUMN revoked_at       DATETIME     NULL AFTER revoked_by,
    ADD COLUMN revoke_reason    VARCHAR(500) NULL COMMENT 'Bắt buộc khi hủy' AFTER revoked_at,
    ADD COLUMN original_to_date DATE         NULL
        COMMENT 'Ngày kết thúc trước khi rút ngắn; NULL nếu đơn không bị rút ngắn' AFTER revoke_reason,
    ADD CONSTRAINT fk_leave_requests_revoked_by FOREIGN KEY (revoked_by) REFERENCES users (id);

-- Đơn CANCELLED giờ có hai nguồn: người gửi tự hủy lúc chờ duyệt (không có người duyệt), hoặc người duyệt hủy
-- cả đơn đã duyệt (giữ người duyệt). Đơn APPROVED bị rút ngắn thì có người hủy và ngày kết thúc cũ.
ALTER TABLE leave_requests DROP CHECK ck_leave_requests_decision;

ALTER TABLE leave_requests
    ADD CONSTRAINT ck_leave_requests_decision CHECK (
        (status = 'PENDING' AND approver_id IS NULL AND decided_at IS NULL AND revoked_by IS NULL) OR
        (status = 'CANCELLED' AND approver_id IS NULL AND decided_at IS NULL AND revoked_by IS NULL) OR
        (status = 'CANCELLED' AND approver_id IS NOT NULL AND decided_at IS NOT NULL AND revoked_by IS NOT NULL
            AND original_to_date IS NULL) OR
        (status = 'APPROVED' AND approver_id IS NOT NULL AND decided_at IS NOT NULL
            AND (revoked_by IS NULL) = (original_to_date IS NULL)) OR
        (status = 'REJECTED' AND approver_id IS NOT NULL AND decided_at IS NOT NULL AND revoked_by IS NULL)),
    -- CHECK của MySQL cho qua khi biểu thức ra NULL, nên phải ghi rõ IS NOT NULL trước khi đo độ dài lý do.
    ADD CONSTRAINT ck_leave_requests_revoke CHECK (
        (revoked_by IS NULL AND revoked_at IS NULL AND revoke_reason IS NULL AND original_to_date IS NULL) OR
        (revoked_by IS NOT NULL AND revoked_at IS NOT NULL
            AND revoke_reason IS NOT NULL AND CHAR_LENGTH(TRIM(revoke_reason)) > 0
            AND revoked_by <> requester_id
            AND (original_to_date IS NULL OR original_to_date > to_date)));
