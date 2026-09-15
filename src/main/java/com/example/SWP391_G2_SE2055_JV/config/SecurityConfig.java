package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.config.oauth2.OAuth2LoginFailureHandler;
import com.example.SWP391_G2_SE2055_JV.config.oauth2.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Security configuration — session-based auth + Google OAuth2 + RBAC
 *
 * Roles (Milestone 1):
 *   ADMIN_PLATFORM — SaaS platform admin
 *   DIRECTOR       — hotel director, read-only
 *   MANAGER        — hotel operations manager
 *   RECEPTIONIST   — front desk
 *   HOUSEKEEPING   — housekeeping staff
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService        userDetailsService;
    private final OAuth2LoginSuccessHandler successHandler;
    private final OAuth2LoginFailureHandler failureHandler;

    // NOTE: "/auth/login" is public for BOTH:
    //   - formLogin POST /auth/login          (local username/password)
    //   - oauth2Login GET /auth/login/google  (Spring appends /{registrationId})
    private static final String[] PUBLIC_ENDPOINTS = {
        "/auth/login",
        "/auth/login/**",
        "/auth/callback/**",
        "/auth/unauthorized",
        "/auth/forgot-password"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Build provider inline — NOT a @Bean, avoids Spring Boot auto-config conflict
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());

        http
            .csrf(AbstractHttpConfigurer::disable)
            .authenticationProvider(provider)
            .sessionManagement(session -> session.maximumSessions(1))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ── Platform admin only ──────────────────────────────────────
                .requestMatchers("/platform/**")
                    .hasRole("ADMIN_PLATFORM")

                // ── Hotel management — manager + admin ───────────────────────
                .requestMatchers("/hotels/**")
                    .hasAnyRole("ADMIN_PLATFORM", "DIRECTOR", "MANAGER")

                // ── Users/staff management ───────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/users/**")
                    .hasAnyRole("ADMIN_PLATFORM", "MANAGER", "DIRECTOR")
                .requestMatchers("/users/**")
                    .hasAnyRole("ADMIN_PLATFORM", "MANAGER")

                // ── Organization (departments, positions) ────────────────────
                .requestMatchers(HttpMethod.GET, "/organization/**")
                    .hasAnyRole("ADMIN_PLATFORM", "DIRECTOR", "MANAGER", "RECEPTIONIST")
                .requestMatchers("/organization/**")
                    .hasAnyRole("ADMIN_PLATFORM", "MANAGER")

                // ── Room management ──────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/rooms/**")
                    .hasAnyRole("ADMIN_PLATFORM", "DIRECTOR", "MANAGER", "RECEPTIONIST", "HOUSEKEEPING")
                .requestMatchers("/rooms/**")
                    .hasAnyRole("ADMIN_PLATFORM", "MANAGER", "RECEPTIONIST")

                // ── Scheduling (work-schedule policy, shifts, shift-change requests, housekeeping) ──
                // Housekeeping may complete their own cleaning assignment (self-ownership
                // checked in CleaningAssignmentService), but nothing else under /scheduling/**.
                .requestMatchers(HttpMethod.PATCH, "/scheduling/cleaning-assignments/*/status")
                    .hasAnyRole("ADMIN_PLATFORM", "MANAGER", "HOUSEKEEPING")
                // Any staff role may submit a shift-change request for their own shift
                // (self-ownership checked in ShiftChangeRequestService).
                .requestMatchers(HttpMethod.POST, "/scheduling/shift-change-requests")
                    .hasAnyRole("ADMIN_PLATFORM", "MANAGER", "RECEPTIONIST", "HOUSEKEEPING")
                .requestMatchers(HttpMethod.GET, "/scheduling/**")
                    .hasAnyRole("ADMIN_PLATFORM", "DIRECTOR", "MANAGER", "RECEPTIONIST", "HOUSEKEEPING")
                // Director sets work-schedule policy (Milestone-2 addition — previously
                // policy writes were Manager-only, which didn't match the intended workflow).
                .requestMatchers("/scheduling/policies/**")
                    .hasAnyRole("ADMIN_PLATFORM", "DIRECTOR", "MANAGER")
                .requestMatchers("/scheduling/**")
                    .hasAnyRole("ADMIN_PLATFORM", "MANAGER")

                // ── Assets ───────────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/assets/**")
                    .hasAnyRole("ADMIN_PLATFORM", "DIRECTOR", "MANAGER", "RECEPTIONIST", "HOUSEKEEPING")
                .requestMatchers("/assets/**")
                    .hasAnyRole("ADMIN_PLATFORM", "MANAGER")

                // ── Reports / dashboard ──────────────────────────────────────
                .requestMatchers("/dashboard/**", "/analytics/**")
                    .hasAnyRole("ADMIN_PLATFORM", "DIRECTOR", "MANAGER")

                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )

            // ── Google OAuth2 ────────────────────────────────────────────────
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(e -> e.baseUri("/auth/login"))
                .redirectionEndpoint(e -> e.baseUri("/auth/callback/google"))
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            )

            // ── Local username/password login ───────────────────────────────
            .formLogin(form -> form
                .loginProcessingUrl("/auth/login")
                .successHandler((req, res, auth) -> res.setStatus(HttpStatus.OK.value()))
                .failureHandler((req, res, ex) -> res.setStatus(HttpStatus.UNAUTHORIZED.value()))
                .permitAll()
            )

            // ── Logout ───────────────────────────────────────────────────────
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.OK.value()))
            );

        return http.build();
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