package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.SuspendReason;
import com.example.SWP391_G2_SE2055_JV.exception.ApiError;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Xử lý đăng nhập bằng mật khẩu thất bại.
 *
 * <p>Mặc định trả 401 với body rỗng cho MỌI lỗi (sai mật khẩu, không có tài khoản, tài khoản bị
 * khóa). Riêng trường hợp tài khoản bị chặn vì TRẠNG THÁI CỦA TENANT thì trả 401 kèm thông báo
 * nêu rõ lý do, để người dùng biết vì sao không vào được.
 *
 * <p><b>Chỉ nói lý do khi đã nhập ĐÚNG mật khẩu.</b> Spring kiểm tra "tài khoản có bị khóa không"
 * TRƯỚC khi kiểm tra mật khẩu, nên nếu báo ngay thì bất kỳ ai biết một email cũng dò ra được email
 * đó thuộc Tenant nào đang bị khóa mà không cần mật khẩu. Vì vậy handler tự kiểm tra lại mật khẩu;
 * sai mật khẩu hoặc trường hợp khác đều giữ 401 rỗng như cũ.
 *
 * <p>KHÔNG khai báo {@code @Component}: được tạo thủ công trong {@link SecurityConfig} vì cần bean
 * {@code PasswordEncoder} nằm ngay trong class đó (khai báo bean sẽ tạo vòng phụ thuộc).
 */
@RequiredArgsConstructor
public class FormLoginFailureHandler implements AuthenticationFailureHandler {

    private final UserRepository      userRepository;
    private final PasswordEncoder     passwordEncoder;
    private final TenantAccessPolicy  tenantAccessPolicy;
    private final ObjectMapper        objectMapper;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String message = tenantBlockedMessage(request, exception);
        if (message == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());   // hành vi cũ
            return;
        }

        ApiError body = ApiError.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
            .message(message)
            .path(request.getRequestURI())
            .build();

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** @return thông báo nếu tài khoản bị chặn vì Tenant VÀ mật khẩu đúng; ngược lại {@code null}. */
    private String tenantBlockedMessage(HttpServletRequest request, AuthenticationException exception) {
        // DisabledException: Spring từ chối vì enabled = false (đã qua bước tìm user, chưa so mật khẩu).
        if (!(exception instanceof DisabledException)) {
            return null;
        }
        String email    = request.getParameter("username");
        String password = request.getParameter("password");
        if (email == null || password == null) {
            return null;
        }

        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null || !user.isActive()
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return null;
        }

        TenantAccessPolicy.Decision decision = tenantAccessPolicy.evaluate(user);
        if (decision.mode() != TenantAccessPolicy.Mode.BLOCKED) {
            return null;
        }
        return messageFor(decision.reason());
    }

    private static String messageFor(SuspendReason reason) {
        if (reason == SuspendReason.TRIAL_EXPIRED) {
            return "Tenant đã hết hạn dùng thử. Chỉ Giám đốc được đăng nhập để thanh toán; "
                + "vui lòng liên hệ Giám đốc của bạn.";
        }
        if (reason == SuspendReason.PAYMENT_FAILED) {
            return "Thanh toán của Tenant đã thất bại. Chỉ Giám đốc được đăng nhập để thanh toán; "
                + "vui lòng liên hệ Giám đốc của bạn.";
        }
        // ADMIN_LOCKED, thiếu lý do, hoặc Tenant không tồn tại.
        return "Tenant của bạn đã bị Admin Platform khóa. Vui lòng liên hệ quản trị nền tảng để được mở lại.";
    }
}
