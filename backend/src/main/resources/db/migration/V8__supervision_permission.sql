-- Giám sát vận hành (BR Milestone 2, chốt 19/10/2026):
--
--   BR-ORG-08 (sửa): Loại Position thứ 4 — Lễ tân / Dọn dẹp / Giám sát vận hành / Khác.
--   BR-ORG-09 (sửa): quyền nghiệp vụ thứ 3 — Giám sát, Manager tick theo từng người, tick kèm được
--                    Lễ tân và / hoặc Dọn dẹp.
--   BR-ROOM-02 (sửa): Chờ kiểm tra → Trống / Chờ dọn do "Manager hoặc Giám sát" thực hiện, nên lịch
--                    sử trạng thái phòng có thêm nguồn SUPERVISION.
--
-- Chỉ nới CHECK (kèm sửa COMMENT cho khớp), không đổi dữ liệu đang có.

ALTER TABLE positions DROP CHECK ck_positions_type;
ALTER TABLE user_permissions DROP CHECK ck_user_permissions_value;
ALTER TABLE room_status_history DROP CHECK ck_rsh_change_source;

ALTER TABLE positions
    MODIFY COLUMN position_type VARCHAR(20) NOT NULL
        COMMENT 'RECEPTION | HOUSEKEEPING | SUPERVISION | OTHER — chỉ dùng để tick sẵn quyền (BR-ORG-08)';
ALTER TABLE user_permissions
    MODIFY COLUMN permission VARCHAR(30) NOT NULL
        COMMENT 'RECEPTION | HOUSEKEEPING | SUPERVISION — enum StaffPermission';
ALTER TABLE room_status_history
    MODIFY COLUMN change_source VARCHAR(20) NOT NULL
        COMMENT 'RECEPTION | HOUSEKEEPING | MANAGER | SUPERVISION | SYSTEM — cột "Người/nguồn" của BR-ROOM-02';

ALTER TABLE positions
    ADD CONSTRAINT ck_positions_type
        CHECK (position_type IN ('RECEPTION', 'HOUSEKEEPING', 'SUPERVISION', 'OTHER'));
ALTER TABLE user_permissions
    ADD CONSTRAINT ck_user_permissions_value
        CHECK (permission IN ('RECEPTION', 'HOUSEKEEPING', 'SUPERVISION'));
ALTER TABLE room_status_history
    ADD CONSTRAINT ck_rsh_change_source
        CHECK (change_source IN ('RECEPTION', 'HOUSEKEEPING', 'MANAGER', 'SUPERVISION', 'SYSTEM'));
