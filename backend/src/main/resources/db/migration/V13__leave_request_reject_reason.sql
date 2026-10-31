-- Từ chối đơn nghỉ phải kèm lý do (chốt 31/10/2026, bổ sung cho BR-SCH-08 / BR-SCH-23): người gửi đọc được vì sao
-- đơn không được duyệt, cùng cách với lý do hủy phần ngày chưa tới (V12). BR M2 chưa nói gì về lý do từ chối.

ALTER TABLE leave_requests
    ADD COLUMN reject_reason VARCHAR(500) NULL
        COMMENT 'Bắt buộc khi từ chối, người gửi đọc được — chốt 31/10/2026' AFTER decided_at;

-- Đơn đã từ chối trước bản này không có lý do. Ghi rõ điều đó thay vì để trống, để CHECK dưới áp được cho MỌI đơn
-- từ chối và người gửi không phải đoán vì sao ô lý do trống.
UPDATE leave_requests
SET reject_reason = 'Không ghi lý do — đơn bị từ chối trước khi hệ thống bắt buộc nhập lý do.'
WHERE status = 'REJECTED';

-- Lưới cuối: service đã cắt khoảng trắng và chặn lý do rỗng (400) trước khi tới đây. CHECK của MySQL cho qua khi biểu
-- thức ra NULL, nên phải ghi rõ IS NOT NULL trước khi đo độ dài.
ALTER TABLE leave_requests
    ADD CONSTRAINT ck_leave_requests_reject_reason CHECK (
        (status = 'REJECTED' AND reject_reason IS NOT NULL AND CHAR_LENGTH(TRIM(reject_reason)) > 0) OR
        (status <> 'REJECTED' AND reject_reason IS NULL));
