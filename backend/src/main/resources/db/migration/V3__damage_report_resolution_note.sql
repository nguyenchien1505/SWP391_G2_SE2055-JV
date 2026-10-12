-- Ghi chú xử lý của Manager khi đóng báo hỏng (không bắt buộc). BR-ASSET-11 chỉ quy định
-- 2 trạng thái Mới/Đã xử lý; nhóm chốt bổ sung ghi chú để nhân viên báo hỏng biết Manager
-- đã xử lý thế nào. Xem docs/THAY_DOI_BR.docx.
--
-- Chỉ có giá trị khi status = RESOLVED, nhưng không ép bằng CHECK: Manager được đóng phiếu
-- mà không ghi gì (ví dụ báo nhầm).
ALTER TABLE damage_reports
    ADD COLUMN resolution_note VARCHAR(500) NULL
        COMMENT 'Manager ghi khi đóng phiếu, không bắt buộc' AFTER resolved_at;
