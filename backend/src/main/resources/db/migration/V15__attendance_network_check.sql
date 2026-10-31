-- Chấm công M2 — xác thực theo IP mạng khách sạn (chốt 01/11/2026). Manager khai IP / dải IP mạng của khách
-- sạn mình và chọn chế độ kiểm:
--   OFF     — không kiểm (mặc định: khách sạn chưa cấu hình chạy y như trước);
--   WARN    — vẫn cho vào / ra ca nhưng đánh dấu «ngoài mạng khách sạn» cho quản lý xem;
--   ENFORCE — sai mạng thì không cho vào / ra ca.
-- Manager nhập bù / sửa giờ không bị kiểm (đã có lý do bắt buộc và người nhập).
ALTER TABLE locations
    ADD COLUMN attendance_ip_mode VARCHAR(10) NOT NULL DEFAULT 'OFF'
        COMMENT 'OFF | WARN | ENFORCE — kiểm IP khi nhân viên tự vào / ra ca',
    ADD CONSTRAINT ck_locations_attendance_ip_mode CHECK (attendance_ip_mode IN ('OFF', 'WARN', 'ENFORCE'));

CREATE TABLE location_attendance_networks (
    id          CHAR(36)     NOT NULL,
    tenant_id   CHAR(36)     NOT NULL,
    location_id CHAR(36)     NOT NULL,
    cidr        VARCHAR(50)  NOT NULL COMMENT 'Một IP (IPv4 / IPv6) hoặc một dải dạng IP/độ dài tiền tố, đã chuẩn hóa',
    label       VARCHAR(100) NULL     COMMENT 'Ghi chú, ví dụ «Wifi tầng 1»',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  CHAR(36)     NULL,
    updated_at  DATETIME     NULL,
    updated_by  CHAR(36)     NULL,
    CONSTRAINT pk_location_attendance_networks PRIMARY KEY (id),
    CONSTRAINT fk_att_networks_tenant     FOREIGN KEY (tenant_id)   REFERENCES tenants (id),
    CONSTRAINT fk_att_networks_location   FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_att_networks_created_by FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT fk_att_networks_updated_by FOREIGN KEY (updated_by)  REFERENCES users (id),
    CONSTRAINT uk_att_networks_location_cidr UNIQUE (location_id, cidr)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Kết quả kiểm mạng của từng lần ghi — truy được ca nào chấm ngoài mạng khách sạn.
ALTER TABLE shift_attendance_events
    ADD COLUMN network_check VARCHAR(15) NOT NULL DEFAULT 'NOT_CHECKED'
        COMMENT 'NOT_CHECKED | MATCHED | OUTSIDE',
    ADD COLUMN matched_cidr VARCHAR(50) NULL COMMENT 'IP / dải IP đã khớp khi MATCHED',
    ADD CONSTRAINT ck_att_events_network_check CHECK (network_check IN ('NOT_CHECKED', 'MATCHED', 'OUTSIDE'));
