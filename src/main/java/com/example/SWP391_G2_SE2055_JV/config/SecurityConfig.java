package com.example.SWP391_G2_SE2055_JV.config;

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
import org.springframework.security.config.http.SessionCreationPolicy;
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

    private final UserDetailsService userDetailsService;

    private static final String[] PUBLIC_ENDPOINTS = {
        "/auth/login",
        "/auth/forgot-password",
        "/auth/reset-password",
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
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/users/**").hasAnyRole("OWNER", "MANAGER", "HR")
                .requestMatchers(HttpMethod.POST, "/users/**").hasAnyRole("OWNER", "MANAGER")
                .requestMatchers(HttpMethod.PUT, "/users/**").hasAnyRole("OWNER", "MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/users/**").hasRole("OWNER")
                .requestMatchers("/analytics/**", "/dashboard/**").hasAnyRole("OWNER", "MANAGER")
                .requestMatchers("/recruitment/**").hasAnyRole("OWNER", "MANAGER", "DEPARTMENT_MANAGER", "HR")
                .requestMatchers(HttpMethod.GET, "/scheduling/**")
                    .hasAnyRole("OWNER", "MANAGER", "DEPARTMENT_MANAGER", "SUPERVISOR", "EMPLOYEE")
                .requestMatchers("/scheduling/**")
                    .hasAnyRole("OWNER", "MANAGER", "DEPARTMENT_MANAGER", "SUPERVISOR")
                .requestMatchers("/attendance/**").authenticated()
                .requestMatchers("/laborcost/**").hasAnyRole("OWNER", "MANAGER", "ACCOUNTANT")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .formLogin(form -> form
                .loginProcessingUrl("/auth/login")
                .successHandler((req, res, auth) -> res.setStatus(HttpStatus.OK.value()))
                .failureHandler((req, res, ex) -> res.setStatus(HttpStatus.UNAUTHORIZED.value()))
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}