package com.example.SWP391_G2_SE2055_JV.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật cơ chế chạy tác vụ định kỳ ({@code @Scheduled}) cho toàn ứng dụng. Khai báo trong một
 * class riêng thay vì gắn vào class Application chính để dễ tìm và dễ tắt.
 *
 * <p>Các tác vụ: {@code TenantTrialExpiryJob} (BR-SAAS-09) và {@code LocationActivationJob} (bật chi nhánh
 * tới ngày quản lý bắt đầu làm — chốt 06/10/2026).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
