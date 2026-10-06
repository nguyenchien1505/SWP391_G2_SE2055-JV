-- Bộ mẫu ca theo từng chi nhánh (chốt 06/10/2026 — ghi vào docs/THAY_DOI_BR.docx). Thay cách chia
-- mẫu của V5: chi nhánh dùng ĐÚNG MỘT bộ mẫu, không cộng dồn mẫu chung với mẫu riêng.
--
--   own_shift_templates = FALSE (mặc định): chi nhánh dùng bộ mẫu chung của chuỗi (location_id NULL).
--   own_shift_templates = TRUE           : chi nhánh chỉ dùng bộ mẫu riêng của nó (location_id = chi nhánh).
--
-- Giám đốc bật / tắt chủ động — thêm mẫu riêng KHÔNG tự đổi bộ, nên soạn sẵn bộ riêng rồi mới bật được.
-- Ca đã xếp giữ nguyên mẫu của nó khi chi nhánh đổi bộ. Mọi chi nhánh hiện có giữ bộ mẫu chung.
ALTER TABLE locations
    ADD COLUMN own_shift_templates BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'TRUE = chi nhánh dùng bộ mẫu ca riêng; FALSE = dùng bộ mẫu chung của chuỗi';
