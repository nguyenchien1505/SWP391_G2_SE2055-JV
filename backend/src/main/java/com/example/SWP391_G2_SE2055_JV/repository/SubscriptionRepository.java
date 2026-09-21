package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gói dịch vụ hiện hành của Tenant — DM-09: mỗi Tenant đúng 1 bản ghi
 * (UNIQUE trên {@code tenant_id}), nên chỉ có tra cứu theo tenantId.
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantId(UUID tenantId);

    /**
     * Subscription còn ở chế độ dùng thử mà hạn dùng thử không muộn hơn {@code date} — đầu vào
     * của job hết hạn dùng thử (BR-SAAS-09). Spring sinh:
     * {@code WHERE is_trial = true AND trial_ends_at <= ?}.
     */
    List<Subscription> findByTrialTrueAndTrialEndsAtLessThanEqual(LocalDate date);
}
