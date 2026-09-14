package com.example.SWP391_G2_SE2055_JV.config.oauth2;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.employee.entity.User;
import com.example.SWP391_G2_SE2055_JV.employee.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String SUCCESS_REDIRECT      = "http://localhost:3000/dashboard";
    private static final String UNAUTHORIZED_REDIRECT = "http://localhost:3000/unauthorized";

    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String googleEmail = oauth2User.getAttribute("email");

        log.info("OAuth2 login attempt: {}", googleEmail);

        User user = userRepository.findByEmail(googleEmail).orElse(null);

        if (user == null || user.isDeleted() || !user.isActive()) {
            log.warn("OAuth2 login rejected: {} (not registered or disabled)", googleEmail);
            SecurityContextHolder.clearContext();
            request.getSession().invalidate();
            response.sendRedirect(UNAUTHORIZED_REDIRECT);
            return;
        }

        CustomUserDetails userDetails = new CustomUserDetails(
            user.getId(),
            user.getEmail(),
            user.getPasswordHash(),
            user.getRole(),
            user.getTenantId(),
            user.getLocationId(),
            user.isActive()
        );

        UsernamePasswordAuthenticationToken newAuth =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(newAuth);

        log.info("OAuth2 login success: {} role={} tenantId={} locationId={}",
            googleEmail, user.getRole(), user.getTenantId(), user.getLocationId());
        response.sendRedirect(SUCCESS_REDIRECT);
    }
}
