package com.example.SWP391_G2_SE2055_JV.employee.controller;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.employee.dto.ChangePasswordRequest;
import com.example.SWP391_G2_SE2055_JV.employee.dto.ForgotPasswordRequest;
import com.example.SWP391_G2_SE2055_JV.employee.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Handles authentication-adjacent actions.
 *
 * Actual login is processed by Spring Security's UsernamePasswordAuthenticationFilter
 * at POST /auth/login (configured in SecurityConfig).
 * Logout is at POST /auth/logout (also handled by Spring Security).
 *
 * This controller handles:
 *  - GET  /auth/me              — current user info
 *  - POST /auth/forgot-password — request temporary password via email
 *  - POST /auth/change-password — change own password (authenticated)
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Returns current authenticated user details. */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(Map.of(
            "id",       user.getId(),
            "username", user.getUsername(),
            "role",     user.getRole().name()
        ));
    }

    /**
     * Sends a temporary password to the account's registered email.
     * Per project note: "quên pass user tự lấy pass tạm rồi tự đổi mk"
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok().build();
    }

    /** Change own password — requires current session. */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.getUsername(), request);
        return ResponseEntity.ok().build();
    }
}
