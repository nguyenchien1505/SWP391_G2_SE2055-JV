package com.example.SWP391_G2_SE2055_JV.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Bật {@code @Async} cho các việc không cần người dùng chờ — hiện là gửi email mật khẩu
 * tạm (xem {@code AccountEmailService}). Dùng thread pool mặc định Spring Boot tự cấu hình.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
