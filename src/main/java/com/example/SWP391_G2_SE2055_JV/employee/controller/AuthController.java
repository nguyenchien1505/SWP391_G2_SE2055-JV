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

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Returns info of the currently logged-in user.
     * Frontend calls this after OAuth2 redirect to know the role.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(Map.of(
            "id",    user.getId(),
            "email", user.getUsername(),
            "role",  user.getRole().name()
        ));
    }

    /**
     * Called when Google login succeeds but the email is not whitelisted.
     * Returns 401 with a descriptive message.
     */
    @GetMapping("/unauthorized")
    public ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
            "error",   "access_denied",
            "message", "Your Google account is not registered in this system. Please contact HR."
        ));
    }

    /** Sends a temporary password to the given email (admin use). */
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
