package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

/**
 * Bật auditing cho bộ 4 cột created_at/created_by/updated_at/updated_by trên mọi
 * entity kế thừa {@code AuditableEntity} (DM-17).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaConfig {

    /**
     * Người thực hiện thao tác hiện tại. Rỗng với các luồng chạy ngoài ngữ cảnh
     * đăng nhập (scheduled job, Tenant tự đăng ký) — khi đó created_by để NULL.
     */
    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.ofNullable(SecurityUtils.getCurrentUserIdOrNull());
    }
}
