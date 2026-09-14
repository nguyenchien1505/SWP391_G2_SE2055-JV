package com.example.SWP391_G2_SE2055_JV.employee.controller;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.employee.dto.ChangePasswordRequest;
import com.example.SWP391_G2_SE2055_JV.employee.dto.ForgotPasswordRequest;
import com.example.SWP391_G2_SE2055_JV.employee.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Returns current authenticated user info — called by frontend after login. */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal CustomUserDetails user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id",         user.getId());
        body.put("email",      user.getUsername());
        body.put("role",       user.getRole().name());
        body.put("tenantId",   user.getTenantId());
        body.put("locationId", user.getLocationId());
        return ResponseEntity.ok(body);
    }

    /** Email not in system — shown after rejected OAuth2 login. */
    @GetMapping("/unauthorized")
    public ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
            "error",   "access_denied",
            "message", "Your Google account is not registered. Please contact your manager."
        ));
    }

    /** Request temporary password via email. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok().build();
    }

    /** Change own password — requires active session. */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.getUsername(), request);
        return ResponseEntity.ok().build();
    }
}