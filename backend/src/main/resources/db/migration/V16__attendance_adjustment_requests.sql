-- Chấm công M2 — đơn điều chỉnh công (chốt 01/11/2026): nhân viên quên bấm, mất mạng, bấm sai thì tự gửi đơn
-- kèm giờ đúng và lý do, thay vì báo miệng. Quản lý của khách sạn duyệt thì giờ được ghi như quản lý nhập
-- (một dòng MANAGER trong shift_attendance_events, lý do lấy từ đơn); từ chối thì ghi lý do từ chối.
-- Mỗi ca chỉ một đơn đang chờ. Đơn đã xử lý là lịch sử, không xóa.
CREATE TABLE attendance_adjustment_requests (
    id                      CHAR(36)     NOT NULL,
    tenant_id               CHAR(36)     NOT NULL,
    location_id             CHAR(36)     NOT NULL,
    shift_id                CHAR(36)     NOT NULL,
    requester_id            CHAR(36)     NOT NULL,
    requested_check_in_at   DATETIME     NULL     COMMENT 'Giờ vào đề nghị, giờ khách sạn; NULL = giữ giờ đang có',
    requested_check_out_at  DATETIME     NULL     COMMENT 'Giờ ra đề nghị, giờ khách sạn; NULL = giữ giờ đang có',
    reason                  VARCHAR(400) NOT NULL,
    status                  VARCHAR(20)  NOT NULL COMMENT 'PENDING | APPROVED | REJECTED | CANCELLED',
    reviewer_id             CHAR(36)     NULL,
    decided_at              DATETIME     NULL,
    review_note             VARCHAR(500) NULL     COMMENT 'Lý do từ chối (bắt buộc) / ghi chú khi duyệt',
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              CHAR(36)     NULL,
    updated_at              DATETIME     NULL,
    updated_by              CHAR(36)     NULL,
    CONSTRAINT pk_attendance_adjustment_requests PRIMARY KEY (id),
    CONSTRAINT fk_att_adj_tenant     FOREIGN KEY (tenant_id)    REFERENCES tenants (id),
    CONSTRAINT fk_att_adj_location   FOREIGN KEY (location_id)  REFERENCES locations (id),
    CONSTRAINT fk_att_adj_shift      FOREIGN KEY (shift_id)     REFERENCES shifts (id),
    CONSTRAINT fk_att_adj_requester  FOREIGN KEY (requester_id) REFERENCES users (id),
    CONSTRAINT fk_att_adj_reviewer   FOREIGN KEY (reviewer_id)  REFERENCES users (id),
    CONSTRAINT fk_att_adj_created_by FOREIGN KEY (created_by)   REFERENCES users (id),
    CONSTRAINT fk_att_adj_updated_by FOREIGN KEY (updated_by)   REFERENCES users (id),
    CONSTRAINT ck_att_adj_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_att_adj_has_time CHECK (requested_check_in_at IS NOT NULL OR requested_check_out_at IS NOT NULL),
    -- Lưới cuối: service đã cắt khoảng trắng và chặn lý do rỗng (400) trước khi tới đây.
    CONSTRAINT ck_att_adj_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_att_adj_shift_status    ON attendance_adjustment_requests (shift_id, status);
CREATE INDEX idx_att_adj_location_status ON attendance_adjustment_requests (location_id, status, created_at);
CREATE INDEX idx_att_adj_requester       ON attendance_adjustment_requests (requester_id, created_at);
