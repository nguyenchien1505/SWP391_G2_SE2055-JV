-- BR-HK-17 (BR Milestone 2, chốt 19/10/2026): người TỰ NHẬN được trả lại việc khi chưa bấm hoàn thành,
-- BẮT BUỘC nhập lý do. Nhóm hết người thì việc về Chưa phân công và phòng về Chờ dọn (lý do nằm cả ở
-- room_status_history). Nhóm còn người khác thì chỉ rút tên người trả — phòng không đổi trạng thái nên lý
-- do KHÔNG có chỗ nào khác để nằm. Bảng này giữ MỌI lần trả (chốt 28/10/2026). Chỉ thêm, không sửa.
CREATE TABLE housekeeping_task_returns (
    id             CHAR(36)     NOT NULL,
    tenant_id      CHAR(36)     NOT NULL,
    task_id        CHAR(36)     NOT NULL,
    staff_id       CHAR(36)     NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    returned_at    DATETIME     NOT NULL,
    task_released  BOOLEAN      NOT NULL COMMENT 'TRUE = nhóm hết người: việc về Chưa phân công, phòng về Chờ dọn',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     CHAR(36)     NULL,
    updated_at     DATETIME     NULL,
    updated_by     CHAR(36)     NULL,
    CONSTRAINT pk_hk_task_returns       PRIMARY KEY (id),
    CONSTRAINT fk_hk_returns_tenant     FOREIGN KEY (tenant_id)  REFERENCES tenants (id),
    CONSTRAINT fk_hk_returns_task       FOREIGN KEY (task_id)    REFERENCES housekeeping_tasks (id),
    -- Không CASCADE: người từng trả việc là đã phát sinh dữ liệu, không xóa vĩnh viễn được (như V5).
    CONSTRAINT fk_hk_returns_staff      FOREIGN KEY (staff_id)   REFERENCES users (id),
    CONSTRAINT fk_hk_returns_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_hk_returns_updated_by FOREIGN KEY (updated_by) REFERENCES users (id),
    -- Lưới cuối: service đã cắt khoảng trắng và chặn lý do rỗng (400) trước khi tới đây.
    CONSTRAINT ck_hk_returns_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_hk_returns_task ON housekeeping_task_returns (task_id);

-- Lý do gỡ thứ 5, CHỈ việc dọn dùng: người tự nhận trả lại và nhóm hết người. CHECK của bảng shifts giữ
-- nguyên 4 giá trị (BR-SCH-24) — ca không bao giờ nhận giá trị này.
ALTER TABLE housekeeping_tasks DROP CHECK ck_hk_unassigned_reason;
ALTER TABLE housekeeping_tasks
    MODIFY COLUMN unassigned_reason VARCHAR(30) NULL
        COMMENT 'TRANSFER | TERMINATION | LEAVE_APPROVED | MANAGER_MANUAL | SELF_RETURNED — BR-HK-07, BR-HK-17',
    ADD CONSTRAINT ck_hk_unassigned_reason CHECK (unassigned_reason IS NULL OR unassigned_reason IN (
        'TRANSFER', 'TERMINATION', 'LEAVE_APPROVED', 'MANAGER_MANUAL', 'SELF_RETURNED'));
