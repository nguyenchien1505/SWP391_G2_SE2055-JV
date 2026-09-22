package com.example.SWP391_G2_SE2055_JV.config.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    /** Lấy từ cấu hình như các redirect khác; trước đây hardcode nên deploy là sai đích. */
    @Value("${app.frontend.failure-redirect:http://localhost:3000/dang-nhap?error=oauth_failed}")
    private String failureRedirect;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.error("OAuth2 login failed: {}", exception.getMessage());
        response.sendRedirect(failureRedirect);
    }
}