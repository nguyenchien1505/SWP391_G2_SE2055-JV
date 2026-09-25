-- Quyền nghiệp vụ theo TỪNG nhân viên: Manager tick khi tạo/sửa Staff (Lễ tân, Dọn dẹp, có thể
-- tick nhiều ô hoặc không tick ô nào). Thay cho cách cũ "quyền đi theo Loại Position" (BR-ORG-08
-- bản 20/09/2026). Position vẫn giữ position_type, chỉ dùng làm gợi ý tick sẵn khi chọn vị trí.
--
-- Mỗi nhân viên vẫn chỉ có MỘT Position (BR-USER-01), Department suy ra từ đó (BR-ORG-07).
-- Xem docs/THAY_DOI_BR.docx.
CREATE TABLE user_permissions (
    user_id     CHAR(36)    NOT NULL,
    permission  VARCHAR(30) NOT NULL COMMENT 'RECEPTION | HOUSEKEEPING — enum StaffPermission',
    CONSTRAINT pk_user_permissions PRIMARY KEY (user_id, permission),
    -- Quyền là một phần hồ sơ, không phải "dữ liệu phát sinh": xóa vĩnh viễn tài khoản thì xóa
    -- theo, không chặn như ca làm hay task (UserService.deleteUserPermanently).
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_permissions_value CHECK (permission IN ('RECEPTION', 'HOUSEKEEPING'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Nhân viên đã có: giữ đúng quyền đang dùng = Loại của Position đang giữ.
INSERT INTO user_permissions (user_id, permission)
SELECT u.id, p.position_type
FROM users u
JOIN positions p ON p.id = u.position_id
WHERE u.role = 'STAFF'
  AND p.position_type IN ('RECEPTION', 'HOUSEKEEPING');
