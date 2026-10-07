package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.EmailVerificationToken;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.EmailVerificationTokenRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Xác thực email của Giám đốc tự đăng ký Tenant (bổ sung cho BR-SAAS-13).
 *
 * <pre>
 *   Đăng ký  → Giám đốc INACTIVE + {@link #issue} lưu 1 mã ngẫu nhiên
 *            → sau commit: gửi email có nút dẫn tới trang xác thực của FE
 *   Xác thực → {@link #verify}: tìm mã → Giám đốc ACTIVE → xóa mã (dùng một lần)
 * </pre>
 *
 * <p>Việc CHẶN đăng nhập khi chưa xác thực không nằm ở đây: luồng đăng nhập có sẵn đã từ chối
 * mọi tài khoản không ACTIVE ({@code UserDetailsServiceImpl}, {@code CurrentUserRefreshFilter}).
 *
 * <p>Gửi email dùng chung công tắc {@code app.mail.enabled} với email mật khẩu tạm. Khi tắt
 * (mặc định lúc dev), link xác thực được ghi ra log để test mà không cần cấu hình Gmail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String FROM_NAME = "SAO MAI HOSPITALITY OPS";

    /** Khoảng cách tối thiểu giữa hai lần gửi email xác thực cho cùng một tài khoản. */
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository                   userRepository;
    private final TenantRepository                 tenantRepository;
    private final ApplicationEventPublisher        eventPublisher;
    private final ObjectProvider<JavaMailSender>   mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    /** Gmail chỉ cho gửi với địa chỉ người gửi là chính tài khoản SMTP. */
    @Value("${spring.mail.username:}")
    private String from;

    /** Trang xác thực của FE — mã được nối vào dạng {@code ?token=...}. */
    @Value("${app.frontend.verify-email-url:http://localhost:3000/xac-thuc-email}")
    private String verifyEmailUrl;

    /** Phát ra trong transaction đăng ký; email chỉ được gửi sau khi transaction đó commit. */
    public record VerificationEmailRequested(String email, String fullName, String companyName, String token) {}

    /**
     * Tạo mã xác thực cho Giám đốc vừa đăng ký. Chạy TRONG transaction đăng ký: đăng ký lỗi thì
     * mã cũng không được lưu và không có email nào được gửi.
     */
    @Transactional
    public void issue(User director, String companyName) {
        String token = UUID.randomUUID().toString();
        tokenRepository.save(EmailVerificationToken.builder()
            .userId(director.getId())
            .token(token)
            .build());

        eventPublisher.publishEvent(new VerificationEmailRequested(
            director.getEmail(), director.getFullName(), companyName, token));
    }

    /**
     * Xác thực: đổi Giám đốc sang ACTIVE và xóa mã. Mã không tồn tại (sai, hoặc đã dùng) thì
     * báo lỗi 400 qua {@code GlobalExceptionHandler}.
     */
    @Transactional
    public void verify(String token) {
        EmailVerificationToken record = tokenRepository.findByToken(token.trim())
            .orElseThrow(() -> new BusinessException(
                "Link xác thực không hợp lệ hoặc tài khoản đã được xác thực."));

        User user = userRepository.findById(record.getUserId())
            .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản cần xác thực."));

        // Chỉ kích hoạt đúng trường hợp Giám đốc đang chờ xác thực; không đụng trạng thái khác.
        if (user.getRole() == Role.DIRECTOR && user.getStatus() == UserStatus.INACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }
        tokenRepository.delete(record);

        log.info("Xác thực email thành công cho {}", user.getEmail());
    }

    /**
     * Gửi lại email xác thực. KHÔNG báo lỗi trong mọi trường hợp (email không tồn tại, đã xác
     * thực, vừa gửi xong…) để người lạ không dùng chức năng này dò email nào đã đăng ký.
     *
     * <p>Sinh mã MỚI ghi đè mã cũ: link trong email trước mất hiệu lực, chỉ link mới nhất dùng
     * được. Chặn gửi dồn dập bằng mốc {@code updated_at} của dòng mã (JPA Auditing tự cập nhật).
     */
    @Transactional
    public void resend(String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null || user.getRole() != Role.DIRECTOR || user.getStatus() != UserStatus.INACTIVE) {
            return;
        }
        EmailVerificationToken record = tokenRepository.findByUserId(user.getId()).orElse(null);
        if (record == null) {
            return;
        }

        LocalDateTime lastSent = record.getUpdatedAt() != null ? record.getUpdatedAt() : record.getCreatedAt();
        if (lastSent != null && lastSent.isAfter(LocalDateTime.now().minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            log.info("Bỏ qua gửi lại email xác thực cho {}: vừa gửi chưa đủ {} giây",
                user.getEmail(), RESEND_COOLDOWN_SECONDS);
            return;
        }

        String token = UUID.randomUUID().toString();
        record.setToken(token);
        tokenRepository.save(record);

        String companyName = tenantRepository.findById(user.getTenantId())
            .map(tenant -> tenant.getName())
            .orElse("");
        eventPublisher.publishEvent(new VerificationEmailRequested(
            user.getEmail(), user.getFullName(), companyName, token));
        log.info("Gửi lại email xác thực cho {}", user.getEmail());
    }

    /**
     * Gửi email SAU KHI đăng ký đã commit, chạy nền để API đăng ký không phải chờ SMTP. Gửi lỗi
     * chỉ ghi log: tài khoản và mã vẫn còn trong DB.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationEmailRequested(VerificationEmailRequested event) {
        String link = verifyEmailUrl + "?token=" + event.token();

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!mailEnabled || mailSender == null) {
            log.info("Gửi email đang tắt (app.mail.enabled=false). Link xác thực cho {}: {}",
                event.email(), link);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, FROM_NAME);
            helper.setTo(event.email());
            helper.setSubject("Xác thực tài khoản Giám đốc");
            helper.setText(buildPlainBody(event, link), buildHtmlBody(event, link));

            mailSender.send(message);
            log.info("Đã gửi email xác thực tới {}", event.email());
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            log.warn("Gửi email xác thực tới {} thất bại: {}. Link: {}", event.email(), e.getMessage(), link);
        }
    }

    private static String buildPlainBody(VerificationEmailRequested event, String link) {
        return """
            Xin chào %s,

            Bạn vừa đăng ký doanh nghiệp "%s". Mở đường dẫn sau để xác thực tài khoản Giám đốc:
            %s

            Nếu bạn không đăng ký, hãy bỏ qua email này.
            """.formatted(event.fullName(), event.companyName(), link);
    }

    /** CSS viết inline và bố cục bằng bảng vì Gmail bỏ thẻ {@code <style>}. */
    private static String buildHtmlBody(VerificationEmailRequested event, String link) {
        String name    = HtmlUtils.htmlEscape(event.fullName());
        String company = HtmlUtils.htmlEscape(event.companyName());
        String href    = HtmlUtils.htmlEscape(link);
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="font-family:Arial,sans-serif;background:#f4f6f8;padding:24px">
              <tr><td align="center">
                <table width="480" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;padding:32px">
                  <tr><td>
                    <h2 style="margin:0 0 16px;color:#1f2937">Xác thực tài khoản của bạn</h2>
                    <p style="color:#374151">Xin chào <b>%s</b>,</p>
                    <p style="color:#374151">Bạn vừa đăng ký doanh nghiệp <b>%s</b>. Bấm nút bên dưới để xác thực tài khoản Giám đốc.</p>
                    <p style="text-align:center;margin:32px 0">
                      <a href="%s" style="background:#2563eb;color:#ffffff;padding:12px 28px;border-radius:6px;text-decoration:none;font-weight:bold;display:inline-block">Xác thực tài khoản</a>
                    </p>
                    <p style="color:#6b7280;font-size:13px">Nếu bạn không đăng ký, hãy bỏ qua email này.</p>
                  </td></tr>
                </table>
              </td></tr>
            </table>
            """.formatted(name, company, href);
    }
}
