-- Mẫu ca chung + mẫu ca riêng từng chi nhánh (chốt 05/10/2026 — ghi vào docs/THAY_DOI_BR.docx).
--
-- BR-SCH-04 cũ: mẫu ca chỉ có ở cấp Tenant. Nay Giám đốc tạo được thêm mẫu RIÊNG cho một chi
-- nhánh; Manager của chi nhánh đó thấy mẫu chung + mẫu riêng của mình. location_id NULL = mẫu chung
-- (mọi mẫu đang có giữ nguyên là mẫu chung). Phạm vi cố định từ lúc tạo, không đổi được.
--
-- Schedule Policy vẫn một bộ cho cả Tenant (BR-SCH-01) — không đổi.

ALTER TABLE shift_templates
    ADD COLUMN location_id CHAR(36) NULL
        COMMENT 'NULL = mẫu chung toàn chuỗi; có giá trị = mẫu riêng của chi nhánh đó' AFTER tenant_id;

-- Xóa chi nhánh (BR-ORG-05) thì mẫu riêng của nó đi theo: mẫu riêng chỉ dùng được ở chính chi
-- nhánh đó, và chi nhánh còn ca thì đã bị khóa ngoại của bảng shifts chặn xóa từ trước.
ALTER TABLE shift_templates
    ADD CONSTRAINT fk_shift_templates_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE;

-- Tên duy nhất trong từng phạm vi. MySQL coi mỗi NULL là khác nhau nên không đặt UNIQUE thẳng
-- trên location_id được — dùng cột sinh: mẫu chung về 'ALL'. Service chặn thêm trường hợp mẫu
-- riêng trùng tên mẫu chung, để Manager không thấy hai "Ca sáng" khi chọn mẫu.
ALTER TABLE shift_templates
    ADD COLUMN scope_key CHAR(36) GENERATED ALWAYS AS (COALESCE(location_id, 'ALL')) VIRTUAL;

-- Thêm unique mới TRƯỚC khi bỏ cái cũ: khóa ngoại tenant_id cần một index bắt đầu bằng tenant_id.
ALTER TABLE shift_templates
    ADD CONSTRAINT uk_shift_templates_scope_name UNIQUE (tenant_id, scope_key, name);

ALTER TABLE shift_templates DROP INDEX uk_shift_templates_tenant_name;
