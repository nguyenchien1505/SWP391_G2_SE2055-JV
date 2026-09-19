package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.dto.ChangePasswordRequest;
import com.example.SWP391_G2_SE2055_JV.service.AuthService;
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

    /**
     * Thông tin người đang đăng nhập — frontend gọi ngay sau khi login.
     *
     * <p>Trả cả {@code positionType} vì Lễ tân / Dọn dẹp không phải role mà là Loại
     * Position (BR-ORG-08); frontend dựa vào đây để bật/tắt màn hình nghiệp vụ đặc thù.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal CustomUserDetails user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id",                 user.getId());
        body.put("email",              user.getUsername());
        body.put("role",               user.getRole().name());
        body.put("tenantId",           user.getTenantId());
        body.put("locationId",         user.getLocationId());
        body.put("positionId",         user.getPositionId());
        body.put("positionType",       user.getPositionType() == null ? null : user.getPositionType().name());
        // BR-USER-07: frontend phải ép về màn hình đổi mật khẩu khi cờ này bật.
        body.put("mustChangePassword", user.isMustChangePassword());
        return ResponseEntity.ok(body);
    }

    /** Email chưa có trong hệ thống — hiển thị sau khi đăng nhập Google bị từ chối. */
    @GetMapping("/unauthorized")
    public ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
            "error",   "access_denied",
            "message", "Tài khoản Google của bạn chưa được đăng ký. Vui lòng liên hệ quản lý."
        ));
    }

    /** Tự đổi mật khẩu — bắt buộc ở lần đăng nhập đầu tiên (BR-USER-07). */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.getUsername(), request);
        return ResponseEntity.ok().build();
    }
}
