-- Xác thực email khi Giám đốc tự đăng ký Tenant (bổ sung cho BR-SAAS-13).
--
-- Đăng ký xong, tài khoản Giám đốc ở trạng thái INACTIVE (chưa đăng nhập được) và có đúng
-- 1 mã xác thực ở bảng này. Giám đốc bấm nút trong email → trang xác thực → backend tìm mã,
-- chuyển tài khoản sang ACTIVE rồi XÓA mã (mã chỉ dùng một lần).
--
-- Lưu ở DB chứ không giữ trong bộ nhớ để restart backend không làm mất mã, khiến tài khoản
-- kẹt ở INACTIVE. Tài khoản không có dòng nào ở đây (Admin, Manager, Staff, seed, Giám đốc đã
-- xác thực) không bị ảnh hưởng.
CREATE TABLE email_verification_tokens (
    id          CHAR(36) NOT NULL,
    user_id     CHAR(36) NOT NULL COMMENT 'Tài khoản Giám đốc chờ xác thực',
    token       CHAR(36) NOT NULL COMMENT 'UUID ngẫu nhiên gửi trong link email',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  CHAR(36) NULL,
    updated_at  DATETIME NULL,
    updated_by  CHAR(36) NULL,
    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_tokens_user  UNIQUE (user_id),
    CONSTRAINT uk_email_verification_tokens_token UNIQUE (token),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
