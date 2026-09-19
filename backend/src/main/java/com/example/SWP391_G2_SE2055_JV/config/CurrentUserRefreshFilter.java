package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.exception.ApiError;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Đồng bộ principal trong session với DB ở MỖI request đã đăng nhập.
 *
 * <p>Spring Security chỉ đọc DB lúc đăng nhập rồi giữ nguyên {@link CustomUserDetails}
 * trong session tới khi hết hạn (8 giờ). Thiếu filter này thì:
 * <ul>
 *   <li>Đổi mật khẩu xong, {@code /auth/me} vẫn báo {@code mustChangePassword = true}
 *       và frontend kẹt vòng lặp ở màn đổi mật khẩu.</li>
 *   <li>Nhân viên đã nghỉ việc / bị khóa (BR-USER-04) hoặc Tenant bị Suspended
 *       (BR-SAAS-11) vẫn dùng tiếp session đang mở.</li>
 *   <li>Đổi Position không có hiệu lực tới khi đăng nhập lại.</li>
 * </ul>
 *
 * <p>Đồng thời ép BR-USER-07 ở backend: còn mật khẩu tạm thì chỉ gọi được các endpoint
 * phục vụ việc đổi mật khẩu, không chỉ dựa vào frontend điều hướng.
 *
 * <p>Chi phí: 1–3 query mỗi request (user, position, tenant) — chấp nhận được ở quy mô
 * Milestone 1.
 *
 * <p>KHÔNG khai báo {@code @Component}: Spring Boot sẽ đăng ký nó thêm một lần làm
 * servlet filter nằm ngoài security chain. Filter được gắn thủ công trong {@link SecurityConfig}.
 */
@RequiredArgsConstructor
public class CurrentUserRefreshFilter extends OncePerRequestFilter {

    /** BR-USER-07: còn mật khẩu tạm thì chỉ được gọi các endpoint này. */
    private static final Set<String> ALLOWED_WHILE_MUST_CHANGE_PASSWORD =
        Set.of("/auth/me", "/auth/change-password", "/auth/logout");

    private final UserRepository         userRepository;
    private final UserDetailsServiceImpl userDetailsService;
    private final ObjectMapper           objectMapper;

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails current)) {
            chain.doFilter(request, response);
            return;
        }

        CustomUserDetails fresh = userRepository.findById(current.getId())
            .map(userDetailsService::toPrincipal)
            .orElse(null);

        // Đã nghỉ việc / bị khóa / Tenant Suspended: cắt luôn session đang mở.
        if (fresh == null || !fresh.isEnabled()) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            writeError(request, response, HttpStatus.UNAUTHORIZED,
                "Tài khoản không còn hoạt động hoặc Tenant đã bị tạm ngưng. Vui lòng đăng nhập lại.");
            return;
        }

        // Dữ liệu phân quyền đã đổi trong DB: thay principal và lưu lại vào session.
        if (!fresh.hasSameStateAs(current)) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                fresh, null, fresh.getAuthorities()));
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
        }

        if (fresh.isMustChangePassword()
                && !ALLOWED_WHILE_MUST_CHANGE_PASSWORD.contains(pathWithinApplication(request))) {
            writeError(request, response, HttpStatus.FORBIDDEN,
                "Bạn phải đổi mật khẩu tạm trước khi sử dụng hệ thống (BR-USER-07).");
            return;
        }

        chain.doFilter(request, response);
    }

    /** Bỏ context-path "/api" để so khớp giống cách khai báo rule trong SecurityConfig. */
    private static String pathWithinApplication(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** Cùng định dạng {@link ApiError} với GlobalExceptionHandler — advice không bắt được lỗi ở tầng filter. */
    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String message) throws IOException {
        ApiError body = ApiError.builder()
            .timestamp(LocalDateTime.now())
            .status(status.value())
            .error(status.getReasonPhrase())
            .message(message)
            .path(request.getRequestURI())
            .build();

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
