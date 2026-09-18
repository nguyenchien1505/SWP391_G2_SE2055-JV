-- Room Management (Milestone 1 — Mục 6). Bổ sung trên nền V1__init_schema.sql.
-- Đối chiếu personal docs/room-management (03-data-model.md, mục B).
-- MySQL 8. Các bảng rooms / room_statuses / cleaning_assignments đã tạo ở V1;
-- migration này chỉ THÊM cột / ràng buộc / bảng lịch sử / seed trạng thái.

-- ── 1) rooms: bổ sung Sức chứa + Ghi chú (BR-51; Manager sửa vận hành — BR-50) ──
ALTER TABLE rooms
    ADD COLUMN capacity INT          NULL AFTER room_number,
    ADD COLUMN note     VARCHAR(500) NULL AFTER floor;

-- ── 2) rooms: Số phòng duy nhất trong Location (BR-51) ─────────────────────────
-- Soft-delete: chỉ ép unique với phòng còn sống. Cột sinh ra = room_number khi
-- is_deleted=0, = NULL khi đã xóa mềm; MySQL coi mỗi NULL là khác nhau nên phòng
-- đã xóa không chặn việc tái sử dụng số phòng.
ALTER TABLE rooms
    ADD COLUMN active_room_number VARCHAR(20)
        GENERATED ALWAYS AS (IF(is_deleted = 0, room_number, NULL)) VIRTUAL;

ALTER TABLE rooms
    ADD CONSTRAINT uk_rooms_location_active_number UNIQUE (location_id, active_room_number);

-- ── 3) room_status_history: lịch sử trạng thái + lý do (kiểm tra không đạt...) ──
-- Phục vụ "lịch sử sử dụng & dọn dẹp" và yêu cầu lưu lý do lần kiểm tra KHÔNG đạt
-- (BR spec 6.2 — ma trận chuyển trạng thái). Mỗi transition ghi 1 dòng.
CREATE TABLE room_status_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id         BIGINT       NOT NULL,
    from_status_id  BIGINT,                       -- NULL nếu là bản ghi đầu tiên
    to_status_id    BIGINT       NOT NULL,
    changed_by      BIGINT,                       -- người thực hiện (NULL nếu hệ thống)
    changed_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason          VARCHAR(500),                 -- lý do bảo trì/khóa, kết quả kiểm tra...
    CONSTRAINT fk_room_status_history_room        FOREIGN KEY (room_id)        REFERENCES rooms (id),
    CONSTRAINT fk_room_status_history_from_status FOREIGN KEY (from_status_id) REFERENCES room_statuses (id),
    CONSTRAINT fk_room_status_history_to_status   FOREIGN KEY (to_status_id)   REFERENCES room_statuses (id),
    CONSTRAINT fk_room_status_history_changed_by  FOREIGN KEY (changed_by)     REFERENCES users (id)
);

CREATE INDEX idx_room_status_history_room ON room_status_history (room_id, changed_at);

-- ── 4) cleaning_assignments: phân biệt loại task dọn (BR-61) ────────────────────
-- POST_CHECKOUT: dọn sau khi khách check-out (luồng Chờ dọn -> Đang dọn).
-- STAYOVER     : dọn hằng ngày khi phòng vẫn Đang sử dụng (phòng không đổi trạng thái).
ALTER TABLE cleaning_assignments
    ADD COLUMN task_type VARCHAR(20) NOT NULL DEFAULT 'POST_CHECKOUT' AFTER room_id;

-- ── 5) Seed danh mục trạng thái phòng (7 trạng thái — BR spec 6.2) ──────────────
-- code dùng để enforce state machine ở service layer (01-state-machine.md).
INSERT INTO room_statuses (code, name, description, display_order, is_active) VALUES
    ('AVAILABLE',   'Trống / Sẵn sàng', 'Phòng sạch, có thể xếp khách',                 1, b'1'),
    ('RESERVED',    'Đã đặt',           'Khách đã đặt — cờ thủ công do Lễ tân bật',      2, b'1'),
    ('OCCUPIED',    'Đang sử dụng',     'Có khách đang ở',                               3, b'1'),
    ('DIRTY',       'Chờ dọn',          'Cần dọn, chưa phân công nhân viên',             4, b'1'),
    ('CLEANING',    'Đang dọn',         'Đã phân công, nhân viên đang thực hiện',        5, b'1'),
    ('INSPECTION',  'Chờ kiểm tra',     'Nhân viên dọn xong, chờ Manager kiểm tra',      6, b'1'),
    ('UNAVAILABLE', 'Không khả dụng',   'Gộp Bảo trì + Khóa phòng, kèm lý do',           7, b'1');
