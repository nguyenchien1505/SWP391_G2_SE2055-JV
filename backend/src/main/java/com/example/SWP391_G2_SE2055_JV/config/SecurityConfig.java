package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.config.oauth2.OAuth2LoginFailureHandler;
import com.example.SWP391_G2_SE2055_JV.config.oauth2.OAuth2LoginSuccessHandler;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Xác thực bằng session + Google OAuth2, phân quyền theo HAI trục — BR-PERM-01..06.
 *
 * <p>Trục 1 — {@code ROLE_*} (4 giá trị của DM-01):
 * <pre>
 *   PLATFORM_ADMIN : quản lý Tenant, gói dịch vụ, cấu hình chung của hệ thống
 *   DIRECTOR       : phạm vi toàn Tenant — danh mục, Location, Phòng, Policy, duyệt điều chuyển
 *   MANAGER        : vận hành 1 Location — Staff, lịch, task dọn, trạng thái phòng, tài sản
 *   STAFF          : quyền chung của người lao động
 * </pre>
 *
 * <p>Trục 2 — {@code POSITION_*}: Lễ tân và Dọn dẹp KHÔNG phải role mà là Loại Position
 * (BR-ORG-08). Quyền nghiệp vụ đặc thù của hai nhóm này gắn vào authority
 * {@code POSITION_RECEPTION} / {@code POSITION_HOUSEKEEPING}. Position loại OTHER chỉ có
 * quyền chung, không nhận authority đặc thù nào (BR-ORG-09, BR-PERM-06).
 *
 * <p>Rule ở đây là lớp chặn thô theo URL; kiểm tra quyền sở hữu (Staff chỉ xem ca của
 * mình, Manager chỉ thao tác trong Location của mình) nằm ở tầng service.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl    userDetailsService;
    private final UserRepository            userRepository;
    private final ObjectMapper              objectMapper;
    private final OAuth2LoginSuccessHandler successHandler;
    private final OAuth2LoginFailureHandler failureHandler;
    private final TenantAccessPolicy        tenantAccessPolicy;

    // "/auth/login" công khai cho CẢ hai luồng:
    //   - formLogin  POST /auth/login
    //   - oauth2Login GET /auth/login/google  (Spring nối thêm /{registrationId})
    private static final String[] PUBLIC_ENDPOINTS = {
        "/auth/login",
        "/auth/login/**",
        "/auth/callback/**",
        "/auth/unauthorized",
        // BR-SAAS-13: Tenant tự đăng ký, chưa có tài khoản nên phải công khai.
        // (Endpoint chưa hiện thực — khai báo sẵn để rule không bị bỏ sót khi làm.)
        "/auth/register-tenant"
    };

    private static final String ADMIN    = "PLATFORM_ADMIN";
    private static final String DIRECTOR = "DIRECTOR";
    private static final String MANAGER  = "MANAGER";
    private static final String STAFF    = "STAFF";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Dựng provider inline — KHÔNG để @Bean, tránh xung đột với auto-config.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());

        http
            // Áp cấu hình CORS của WebConfig cho CẢ các phản hồi do Spring Security tạo (đăng nhập,
            // đăng xuất, 401/403). Thiếu dòng này, CORS chỉ có hiệu lực với phản hồi đi qua Spring
            // MVC: trình duyệt gọi từ http://localhost:3000 nhận được phản hồi đăng nhập/401 không
            // có header CORS nên bị chặn và báo "Network Error".
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .authenticationProvider(provider)
            .sessionManagement(session -> session
                .maximumSessions(1)
                // Phiên cũ bị đẩy ra khi đăng nhập nơi khác: trả 401 để frontend về trang
                // login (mặc định Spring chỉ ghi một câu text, không đặt mã lỗi).
                .expiredSessionStrategy(event ->
                    event.getResponse().setStatus(HttpStatus.UNAUTHORIZED.value())))

            // Đọc lại user từ DB mỗi request — xem CurrentUserRefreshFilter. Đặt TRƯỚC
            // AuthorizationFilter để rule URL phân quyền dựa trên dữ liệu mới nhất.
            .addFilterBefore(
                new CurrentUserRefreshFilter(userRepository, userDetailsService, objectMapper),
                AuthorizationFilter.class)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ── Nền tảng SaaS — chỉ Admin Platform (BR-PERM-01) ──────────
                .requestMatchers("/platform/**").hasRole(ADMIN)

                // ── Gói dịch vụ của chính Tenant — Giám đốc tự custom (BR-SAAS-04) ──
                .requestMatchers("/billing/**").hasAnyRole(ADMIN, DIRECTOR)

                // ── Location: chỉ Giám đốc CRUD; Manager chỉ sửa thông tin vận hành (BR-ORG-03) ──
                .requestMatchers(HttpMethod.GET, "/locations/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER)
                .requestMatchers(HttpMethod.PATCH, "/locations/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER)
                .requestMatchers("/locations/**")
                    .hasAnyRole(ADMIN, DIRECTOR)

                // ── Danh mục cấp Tenant: chỉ Giám đốc tạo (BR-ORG-06, BR-ORG-11, BR-ASSET-09) ──
                .requestMatchers(HttpMethod.GET, "/organization/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER)
                .requestMatchers("/organization/**")
                    .hasAnyRole(ADMIN, DIRECTOR)

                // ── Nhân sự: Manager CRUD Staff trong Location, Giám đốc CRUD Manager ──
                .requestMatchers(HttpMethod.GET, "/users/me/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/users/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER)
                .requestMatchers("/users/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER)

                // ── Điều chuyển: Manager tạo, Giám đốc duyệt (BR-TRF-02) ─────
                .requestMatchers("/transfers/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER)

                // ── Phòng ────────────────────────────────────────────────────
                // Chỉ Giám đốc CRUD Phòng (BR-ROOM-04).
                .requestMatchers(HttpMethod.GET, "/rooms/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER, STAFF)
                // Lễ tân đổi trạng thái khi khách đặt/check-in/check-out (BR-PERM-04).
                .requestMatchers(HttpMethod.PATCH, "/rooms/*/status")
                    .access((authn, ctx) -> new org.springframework.security.authorization.AuthorizationDecision(
                        hasAnyAuthority(authn, "ROLE_" + ADMIN, "ROLE_" + MANAGER, "POSITION_RECEPTION")))
                .requestMatchers(HttpMethod.POST, "/rooms/**", "/rooms")
                    .hasAnyRole(ADMIN, DIRECTOR)
                .requestMatchers(HttpMethod.DELETE, "/rooms/**")
                    .hasAnyRole(ADMIN, DIRECTOR)
                .requestMatchers("/rooms/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER)

                // ── Housekeeping ─────────────────────────────────────────────
                // Nhân viên dọn bấm hoàn thành task của chính mình (BR-PERM-05);
                // quyền sở hữu kiểm tra ở service.
                .requestMatchers(HttpMethod.PATCH, "/housekeeping/tasks/*/complete")
                    .access((authn, ctx) -> new org.springframework.security.authorization.AuthorizationDecision(
                        hasAnyAuthority(authn, "ROLE_" + ADMIN, "ROLE_" + MANAGER, "POSITION_HOUSEKEEPING")))
                .requestMatchers(HttpMethod.GET, "/housekeeping/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER, STAFF)
                .requestMatchers("/housekeeping/**")
                    .hasAnyRole(ADMIN, MANAGER)

                // ── Lịch làm việc ────────────────────────────────────────────
                // Schedule Policy và Shift Template do Giám đốc quản lý (BR-SCH-01, BR-SCH-04),
                // nhưng Manager phải ĐỌC được: xem policy để hiểu vì sao ca bị chặn, và chọn
                // template khi xếp ca. Rule GET này phải đứng TRƯỚC rule chặn bên dưới vì rule
                // khớp đầu tiên thắng ("/scheduling/policy/**" khớp cả "/scheduling/policy").
                .requestMatchers(HttpMethod.GET, "/scheduling/policy/**", "/scheduling/shift-templates/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER)
                .requestMatchers("/scheduling/policy/**", "/scheduling/shift-templates/**")
                    .hasAnyRole(ADMIN, DIRECTOR)
                // Mọi người lao động đều xin nghỉ / đổi ca / check-in-out được (BR-PERM-06).
                .requestMatchers("/scheduling/leave-requests/**", "/scheduling/shift-swap-requests/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER, STAFF)
                .requestMatchers(HttpMethod.POST, "/scheduling/shifts/*/check-in", "/scheduling/shifts/*/check-out")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER, STAFF)
                .requestMatchers(HttpMethod.GET, "/scheduling/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER, STAFF)
                .requestMatchers("/scheduling/**")
                    .hasAnyRole(ADMIN, MANAGER)

                // ── Tài sản ──────────────────────────────────────────────────
                // Cả Lễ tân và Dọn dẹp đều báo hỏng được (BR-ASSET-05).
                .requestMatchers(HttpMethod.POST, "/assets/damage-reports")
                    .access((authn, ctx) -> new org.springframework.security.authorization.AuthorizationDecision(
                        hasAnyAuthority(authn, "ROLE_" + ADMIN, "ROLE_" + MANAGER,
                            "POSITION_RECEPTION", "POSITION_HOUSEKEEPING")))
                .requestMatchers(HttpMethod.GET, "/assets/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER, STAFF)
                .requestMatchers("/assets/**")
                    .hasAnyRole(ADMIN, DIRECTOR, MANAGER)

                // ── Dashboard (BR-DASH-02, BR-DASH-03) ───────────────────────
                .requestMatchers("/dashboard/**").hasAnyRole(ADMIN, DIRECTOR, MANAGER)

                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )

            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(e -> e.baseUri("/auth/login"))
                .redirectionEndpoint(e -> e.baseUri("/auth/callback/google"))
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            )

            .formLogin(form -> form
                .loginProcessingUrl("/auth/login")
                .successHandler((req, res, auth) -> res.setStatus(HttpStatus.OK.value()))
                // 401; riêng tài khoản bị chặn vì trạng thái Tenant (và đã nhập đúng mật khẩu) thì
                // kèm thông báo nêu lý do — xem FormLoginFailureHandler.
                .failureHandler(new FormLoginFailureHandler(
                    userRepository, passwordEncoder(), tenantAccessPolicy, objectMapper))
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.OK.value()))
            );

        return http.build();
    }

    private static boolean hasAnyAuthority(
            java.util.function.Supplier<org.springframework.security.core.Authentication> authn,
            String... required) {
        org.springframework.security.core.Authentication auth = authn.get();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (var granted : auth.getAuthorities()) {
            for (String r : required) {
                if (r.equals(granted.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Báo cho SessionRegistry của {@code maximumSessions(1)} biết khi session bị hủy
     * (logout, hết hạn). Thiếu bean này registry giữ mãi session đã chết.
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
