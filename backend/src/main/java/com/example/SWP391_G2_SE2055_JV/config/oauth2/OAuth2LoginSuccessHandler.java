package com.example.SWP391_G2_SE2055_JV.config.oauth2;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.config.UserDetailsServiceImpl;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Google OAuth2 KHÔNG tự tạo tài khoản: chỉ chấp nhận email đã tồn tại trong hệ thống
 * và đang ACTIVE. Tài khoản do Manager/Giám đốc tạo (BR-USER-03), không tự đăng ký.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository         userRepository;
    private final UserDetailsServiceImpl userDetailsService;

    @Value("${app.frontend.success-redirect:http://localhost:3000/dashboard}")
    private String successRedirect;

    @Value("${app.frontend.unauthorized-redirect:http://localhost:3000/unauthorized}")
    private String unauthorizedRedirect;

    @Value("${app.frontend.change-password-redirect:http://localhost:3000/change-password}")
    private String changePasswordRedirect;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String googleEmail = oauth2User.getAttribute("email");

        User user = userRepository.findByEmail(googleEmail).orElse(null);
        if (user == null) {
            log.warn("Từ chối đăng nhập Google: {} chưa có tài khoản trong hệ thống", googleEmail);
            reject(request, response);
            return;
        }

        CustomUserDetails principal = userDetailsService.toPrincipal(user);
        if (!principal.isEnabled()) {
            // Tài khoản không ACTIVE, đã nghỉ việc, hoặc Tenant bị khóa (BR-SAAS-11).
            log.warn("Từ chối đăng nhập Google: {} không ở trạng thái hoạt động", googleEmail);
            reject(request, response);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        log.info("Đăng nhập Google thành công: {} role={} tenantId={}",
            googleEmail, principal.getRole(), principal.getTenantId());

        // BR-USER-07: tài khoản còn mật khẩu tạm thì phải đổi trước khi vào hệ thống.
        response.sendRedirect(principal.isMustChangePassword() ? changePasswordRedirect : successRedirect);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        response.sendRedirect(unauthorizedRedirect);
    }
}
