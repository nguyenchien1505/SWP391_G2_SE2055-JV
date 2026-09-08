package com.example.SWP391_G2_SE2055_JV.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {
    // JPA auditing enabled — entities can use @CreatedDate, @LastModifiedDate
}
