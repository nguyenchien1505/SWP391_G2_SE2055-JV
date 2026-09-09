package com.example.SWP391_G2_SE2055_JV.config.oauth2;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.employee.entity.User;
import com.example.SWP391_G2_SE2055_JV.employee.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Called by Spring Security after Google successfully authenticates the user.
 *
 * Whitelist check:
 *   - Email exists in DB + enabled = true  → create session, redirect to frontend
 *   - Email not in DB or disabled           → reject, redirect to /auth/unauthorized
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    // Redirect URLs — adjust to match your frontend
    private static final String SUCCESS_REDIRECT     = "http://localhost:3000/dashboard";
    private static final String UNAUTHORIZED_REDIRECT = "http://localhost:3000/unauthorized";

    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String googleEmail = oauth2User.getAttribute("email");

        log.info("OAuth2 login attempt for email: {}", googleEmail);

        // Whitelist check — email must be pre-registered by HR
        User user = userRepository.findByEmail(googleEmail).orElse(null);

        if (user == null || !user.isEnabled()) {
            log.warn("OAuth2 login rejected for email: {} (not in whitelist or disabled)", googleEmail);
            // Clear the OAuth2 authentication — don't create a session
            SecurityContextHolder.clearContext();
            request.getSession().invalidate();
            response.sendRedirect(UNAUTHORIZED_REDIRECT);
            return;
        }

        // Replace the OAuth2 principal with our CustomUserDetails
        // so @AuthenticationPrincipal CustomUserDetails works everywhere
        CustomUserDetails userDetails = new CustomUserDetails(
            user.getId(),
            user.getEmail(),
            user.getPassword(),
            user.getRole(),
            user.isEnabled()
        );

        // Re-authenticate with our principal — session is already created by Spring Security
        var newAuth = new org.springframework.security.authentication
            .UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(newAuth);

        log.info("OAuth2 login success: {} (role: {})", googleEmail, user.getRole());
        response.sendRedirect(SUCCESS_REDIRECT);
    }
}
