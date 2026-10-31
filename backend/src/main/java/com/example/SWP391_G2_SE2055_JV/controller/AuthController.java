package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.dto.ChangePasswordRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ForgotPasswordRequest;
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
     * <p>Trả cả {@code permissions} vì Lễ tân / Dọn dẹp không phải role mà là quyền nghiệp vụ
     * Manager tick cho từng nhân viên; frontend dựa vào đây để bật/tắt màn hình nghiệp vụ đặc thù.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal CustomUserDetails user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id",                 user.getId());
        body.put("email",              user.getUsername());
        body.put("role",               user.getRole().name());
        body.put("tenantId",           user.getTenantId());
        body.put("locationId",         user.getLocationId());
        // Tên khách sạn cho thanh tiêu đề — Staff không gọi được GET /locations.
        body.put("locationName",       authService.findLocationName(user.getLocationId(), user.getTenantId()));
        // Email Quản lý của khách sạn đó — nhân viên biết liên hệ ai.
        body.put("locationManagerEmail", authService.findLocationManagerEmail(user.getLocationId()));
        body.put("positionId",         user.getPositionId());
        // Quyền nghiệp vụ đã được tick — ví dụ ["RECEPTION", "HOUSEKEEPING"]; rỗng = quyền chung.
        body.put("permissions",        user.getPermissions().stream().map(Enum::name).sorted().toList());
        // BR-USER-07: frontend phải ép về màn hình đổi mật khẩu khi cờ này bật.
        body.put("mustChangePassword", user.isMustChangePassword());
        // Tenant hết hạn dùng thử / thanh toán thất bại: Giám đốc chỉ được xem, frontend hiển thị
        // thông báo và ẩn các nút thao tác ghi.
        body.put("readOnly",           user.isReadOnly());
        // Tenant mới chưa chọn Dùng thử / Mua gói: frontend chỉ mở trang Gói dịch vụ.
        body.put("planRequired",       user.isPlanRequired());
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

    /**
     * Nhân viên quên mật khẩu: email cho Quản lý khách sạn của họ để Quản lý cấp mật khẩu tạm. Công khai (chưa
     * đăng nhập được) và luôn trả 204 dù email có tài khoản hay không — không cho dò tài khoản.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.noContent().build();
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
