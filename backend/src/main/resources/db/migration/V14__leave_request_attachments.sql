-- Ảnh minh chứng cho lý do xin nghỉ (chốt 01/11/2026 — thay BR-SCH-30 "đơn nghỉ không có đính kèm minh chứng trong
-- M2"): người gửi đính kèm ảnh khi đơn còn chờ duyệt, người duyệt xem lý do kèm ảnh.
--
-- Ảnh nằm ngay trong DB thay vì thư mục trên máy chủ: không phải cấu hình nơi lưu trên máy từng người trong nhóm,
-- sao lưu DB là có đủ ảnh, và ảnh lưu cùng giao dịch với đơn. Màn hình thu nhỏ ảnh (≤ 1600px) trước khi gửi nên
-- mỗi ảnh thường chỉ vài trăm KB; trần 5 MB/ảnh, tối đa 3 ảnh/đơn (service chặn). Danh sách đơn chỉ đọc thông tin
-- ảnh, không đọc cột data.
CREATE TABLE leave_request_attachments (
    id               CHAR(36)     NOT NULL,
    tenant_id        CHAR(36)     NOT NULL,
    leave_request_id CHAR(36)     NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(30)  NOT NULL COMMENT 'Đọc từ chính các byte đầu tệp, không tin tên tệp',
    size_bytes       INT          NOT NULL,
    data             MEDIUMBLOB   NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       CHAR(36)     NULL,
    updated_at       DATETIME     NULL,
    updated_by       CHAR(36)     NULL,
    CONSTRAINT pk_leave_attachments            PRIMARY KEY (id),
    CONSTRAINT fk_leave_attachments_tenant     FOREIGN KEY (tenant_id)        REFERENCES tenants (id),
    -- Đơn nghỉ không bao giờ bị xóa (hủy là đổi trạng thái), nên không CASCADE.
    CONSTRAINT fk_leave_attachments_request    FOREIGN KEY (leave_request_id) REFERENCES leave_requests (id),
    CONSTRAINT fk_leave_attachments_created_by FOREIGN KEY (created_by)       REFERENCES users (id),
    CONSTRAINT fk_leave_attachments_updated_by FOREIGN KEY (updated_by)       REFERENCES users (id),
    CONSTRAINT ck_leave_attachments_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp', 'image/gif')),
    CONSTRAINT ck_leave_attachments_size CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT ck_leave_attachments_name CHECK (CHAR_LENGTH(TRIM(file_name)) > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_leave_attachments_request ON leave_request_attachments (leave_request_id);
