package com.example.SWP391_G2_SE2055_JV.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật cơ chế chạy tác vụ định kỳ ({@code @Scheduled}) cho toàn ứng dụng. Khai báo trong một
 * class riêng thay vì gắn vào class Application chính để dễ tìm và dễ tắt.
 *
 * <p>Hiện chỉ có một tác vụ: {@code TenantTrialExpiryJob} (BR-SAAS-09).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
