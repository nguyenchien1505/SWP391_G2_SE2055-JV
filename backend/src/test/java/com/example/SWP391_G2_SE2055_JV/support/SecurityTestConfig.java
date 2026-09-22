package com.example.SWP391_G2_SE2055_JV.support;

import com.example.SWP391_G2_SE2055_JV.config.SecurityConfig;
import com.example.SWP391_G2_SE2055_JV.config.TenantAccessPolicy;
import com.example.SWP391_G2_SE2055_JV.config.UserDetailsServiceImpl;
import com.example.SWP391_G2_SE2055_JV.config.oauth2.OAuth2LoginFailureHandler;
import com.example.SWP391_G2_SE2055_JV.config.oauth2.OAuth2LoginSuccessHandler;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

/**
 * Nạp {@link SecurityConfig} THẬT vào test controller để kiểm tra được cả rule URL lẫn
 * {@code @PreAuthorize}, đồng thời mock 5 bean mà SecurityConfig cần — test controller
 * không phải chạm DB.
 *
 * <p>Không dùng trực tiếp; đi qua {@link SecuredWebMvcTest}.
 */
@TestConfiguration
@Import(SecurityConfig.class)
public class SecurityTestConfig {

    @MockBean UserDetailsServiceImpl    userDetailsService;
    @MockBean UserRepository            userRepository;
    @MockBean OAuth2LoginSuccessHandler oauth2SuccessHandler;
    @MockBean OAuth2LoginFailureHandler oauth2FailureHandler;
    @MockBean TenantAccessPolicy        tenantAccessPolicy;
}
