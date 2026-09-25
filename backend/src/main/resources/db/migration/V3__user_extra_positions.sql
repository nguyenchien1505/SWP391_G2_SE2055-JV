-- Nhân viên đa nhiệm: ngoài Position chính (users.position_id — Department suy ra từ đây,
-- BR-ORG-07), một STAFF có thể KIÊM NHIỆM thêm các Position khác. Quyền nghiệp vụ là hợp các
-- Loại Position người đó giữ (BR-ORG-08), ví dụ Lễ tân kiêm Dọn dẹp.
--
-- Bảng chỉ chứa vị trí kiêm nhiệm, KHÔNG lặp lại vị trí chính — nên dữ liệu cũ và seed không
-- cần chuyển đổi gì.
CREATE TABLE user_extra_positions (
    user_id      CHAR(36) NOT NULL,
    position_id  CHAR(36) NOT NULL,
    CONSTRAINT pk_user_extra_positions PRIMARY KEY (user_id, position_id),
    -- Kiêm nhiệm là một phần hồ sơ, không phải "dữ liệu phát sinh": xóa vĩnh viễn tài khoản thì
    -- xóa theo, không chặn như ca làm hay task (UserService.deleteUserPermanently).
    CONSTRAINT fk_user_extra_positions_user     FOREIGN KEY (user_id)     REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_extra_positions_position FOREIGN KEY (position_id) REFERENCES positions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
