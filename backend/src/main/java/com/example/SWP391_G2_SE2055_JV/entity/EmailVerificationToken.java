package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Mã xác thực email của Giám đốc vừa tự đăng ký Tenant.
 *
 * <p>Mỗi tài khoản có tối đa 1 mã (unique {@code user_id}). Mã bị xóa ngay khi xác thực
 * thành công, nên còn dòng ở đây nghĩa là tài khoản chưa xác thực.
 */
@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class EmailVerificationToken extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", length = 36, nullable = false)
    private UUID userId;

    /**
     * UUID ngẫu nhiên gửi trong link — không đoán được nên không ai tự gõ link giả.
     * Cột là CHAR(36) (V4); không khai báo thì Hibernate hiểu String là VARCHAR và validate lỗi.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token", length = 36, nullable = false)
    private String token;
}
