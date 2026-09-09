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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService       userDetailsService;
    private final OAuth2LoginSuccessHandler successHandler;
    private final OAuth2LoginFailureHandler failureHandler;

    private static final String[] PUBLIC_ENDPOINTS = {
        "/auth/login/google",
        "/auth/unauthorized",
        "/auth/forgot-password",
        "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .maximumSessions(1)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                .requestMatchers("/analytics/**", "/dashboard/**")
                    .hasAnyRole("OWNER", "MANAGER")
                .requestMatchers("/recruitment/**")
                    .hasAnyRole("OWNER", "MANAGER", "DEPARTMENT_MANAGER", "HR")
                .requestMatchers(HttpMethod.GET, "/scheduling/**")
                    .hasAnyRole("OWNER", "MANAGER", "DEPARTMENT_MANAGER", "SUPERVISOR", "EMPLOYEE")
                .requestMatchers("/scheduling/**")
                    .hasAnyRole("OWNER", "MANAGER", "DEPARTMENT_MANAGER", "SUPERVISOR")
                .requestMatchers("/attendance/**").authenticated()
                .requestMatchers("/laborcost/**")
                    .hasAnyRole("OWNER", "MANAGER", "ACCOUNTANT")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            // ── Google OAuth2 Login ──────────────────────────────────────────
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(endpoint ->
                    endpoint.baseUri("/auth/login")   // frontend redirects to this
                )
                .redirectionEndpoint(endpoint ->
                    endpoint.baseUri("/auth/callback/google") // Google redirects back here
                )
                .successHandler(successHandler)
                .failureHandler(failureHandler)
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
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
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
