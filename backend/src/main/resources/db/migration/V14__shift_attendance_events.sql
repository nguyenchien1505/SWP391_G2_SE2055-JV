-- Chấm công (BR Milestone 2, chốt 31/10/2026) — sửa DM-15: giờ vào / ra HIỆU LỰC vẫn nằm trên
-- shifts.check_in_at / check_out_at (mọi luồng đang đọc hai cột đó không đổi), nhưng MỖI lần ghi — nhân viên
-- tự bấm hay Manager nhập bù / sửa giờ — thêm một dòng vào bảng này, kèm IP, trình duyệt, mã thiết bị và cách
-- xác thực. Manager sửa giờ nhiều lần vẫn còn đủ dấu vết từng lần. Chỉ thêm, không sửa / xóa.
CREATE TABLE shift_attendance_events (
    id                 CHAR(36)     NOT NULL,
    tenant_id          CHAR(36)     NOT NULL,
    shift_id           CHAR(36)     NOT NULL,
    staff_id           CHAR(36)     NOT NULL COMMENT 'Người làm ca lúc ghi — không đổi theo ca về sau',
    event_type         VARCHAR(10)  NOT NULL COMMENT 'CHECK_IN | CHECK_OUT',
    occurred_at        DATETIME     NOT NULL COMMENT 'Giờ vào / ra hiệu lực, giờ khách sạn',
    recorded_at        DATETIME     NOT NULL COMMENT 'Lúc ghi dòng này, giờ khách sạn',
    method             VARCHAR(10)  NOT NULL COMMENT 'SELF = nhân viên tự bấm | MANAGER = Manager nhập',
    recorded_by        CHAR(36)     NOT NULL,
    ip_address         VARCHAR(45)  NULL     COMMENT 'Đủ dài cho IPv6',
    user_agent         VARCHAR(512) NULL,
    device_id          VARCHAR(64)  NULL     COMMENT 'Mã trình duyệt tự sinh (header X-Device-Id) — chỉ để tham khảo',
    verification_type  VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE ở M2; QR / IP sau này',
    note               VARCHAR(500) NULL     COMMENT 'Lý do — bắt buộc khi Manager nhập',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         CHAR(36)     NULL,
    updated_at         DATETIME     NULL,
    updated_by         CHAR(36)     NULL,
    CONSTRAINT pk_shift_attendance_events PRIMARY KEY (id),
    CONSTRAINT fk_att_events_tenant      FOREIGN KEY (tenant_id)   REFERENCES tenants (id),
    CONSTRAINT fk_att_events_shift       FOREIGN KEY (shift_id)    REFERENCES shifts (id),
    CONSTRAINT fk_att_events_staff       FOREIGN KEY (staff_id)    REFERENCES users (id),
    CONSTRAINT fk_att_events_recorded_by FOREIGN KEY (recorded_by) REFERENCES users (id),
    CONSTRAINT fk_att_events_created_by  FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT fk_att_events_updated_by  FOREIGN KEY (updated_by)  REFERENCES users (id),
    CONSTRAINT ck_att_events_type   CHECK (event_type IN ('CHECK_IN', 'CHECK_OUT')),
    CONSTRAINT ck_att_events_method CHECK (method IN ('SELF', 'MANAGER')),
    -- Lưới cuối: service đã cắt khoảng trắng và chặn lý do rỗng (400) trước khi tới đây.
    CONSTRAINT ck_att_events_manager_note CHECK (method <> 'MANAGER' OR CHAR_LENGTH(TRIM(note)) > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_att_events_shift ON shift_attendance_events (shift_id, recorded_at);
