-- Schedule Policy M2 (chốt 19/10/2026) — thêm 3 tham số Giám đốc cấu hình cho cả chuỗi:
--   · annual_leave_days_per_year   — quỹ phép năm, mặc định 12 ngày (BR-SCH-27);
--   · annual_leave_notice_days     — phép năm phải gửi trước tối thiểu bấy nhiêu ngày, mặc định 3
--                                    (BR-SCH-07 bản sửa; nghỉ không lương không áp);
--   · late_early_threshold_minutes — vào trễ / ra sớm QUÁ số phút này mới tính đi muộn / về sớm,
--                                    mặc định 5 phút (BR-ATT-02).
-- Policy đã có (kể cả của seed) nhận luôn giá trị mặc định nhờ DEFAULT.
ALTER TABLE schedule_policies
    ADD COLUMN annual_leave_days_per_year   INT NOT NULL DEFAULT 12
        COMMENT 'Quỹ phép năm, tích lũy theo tháng làm việc — BR-SCH-27' AFTER swap_response_timeout_hours,
    ADD COLUMN annual_leave_notice_days     INT NOT NULL DEFAULT 3
        COMMENT 'Số ngày phải báo trước khi xin phép năm — BR-SCH-07 bản sửa' AFTER annual_leave_days_per_year,
    ADD COLUMN late_early_threshold_minutes INT NOT NULL DEFAULT 5
        COMMENT 'Ngưỡng đi muộn / về sớm (phút) — BR-ATT-02' AFTER annual_leave_notice_days,
    ADD CONSTRAINT ck_schedule_policies_leave_attendance CHECK (
        annual_leave_days_per_year   BETWEEN 0 AND 60 AND
        annual_leave_notice_days     BETWEEN 0 AND 30 AND
        late_early_threshold_minutes BETWEEN 0 AND 60);
