-- Màn «Việc của tôi» thành chi tiết phòng đang dọn (chốt 02/11/2026): người dọn ghi cho người kiểm tra lúc bấm
-- «Hoàn thành» (không bắt buộc) — ví dụ "thiếu 1 gối, đã báo kho". Ghi một lần cùng bước sang «Chờ kiểm tra»;
-- việc dọn lại sinh ra sau đó không chép theo.

ALTER TABLE housekeeping_tasks
    ADD COLUMN completion_note VARCHAR(500) NULL
        COMMENT 'Người dọn ghi cho người kiểm tra khi báo xong' AFTER completed_at;
