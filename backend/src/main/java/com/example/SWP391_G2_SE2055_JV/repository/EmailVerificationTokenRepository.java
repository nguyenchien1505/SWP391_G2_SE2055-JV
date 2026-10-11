package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByToken(String token);

    Optional<EmailVerificationToken> findByUserId(UUID userId);

    /**
     * Trong các Tenant được hỏi, Tenant nào có Giám đốc CHƯA xác thực email (còn mã chờ).
     * Một truy vấn cho cả trang danh sách, không gọi lặp từng dòng.
     */
    @Query("select u.tenantId from EmailVerificationToken t, User u "
        + "where t.userId = u.id and u.tenantId in :tenantIds")
    List<UUID> findTenantIdsAwaitingVerification(@Param("tenantIds") Collection<UUID> tenantIds);

    /** Mã tạo trước mốc {@code time} — đầu vào của job xóa đăng ký quá hạn không xác thực. */
    List<EmailVerificationToken> findByCreatedAtBefore(LocalDateTime time);
}
