package com.example.SWP391_G2_SE2055_JV.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình Spring MVC.
 *
 * <p>CORS KHÔNG khai báo ở đây nữa mà nằm trong {@link SecurityConfig}
 * ({@code corsConfigurationSource}): cấu hình CORS của WebMvcConfigurer chỉ áp cho request
 * đi tới được tầng MVC, trong khi đăng nhập, đăng xuất và mọi lỗi 401/403 đều do filter của
 * Spring Security trả về trước đó — thiếu header CORS thì trình duyệt chặn response và
 * frontend báo "không kết nối được máy chủ".
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
