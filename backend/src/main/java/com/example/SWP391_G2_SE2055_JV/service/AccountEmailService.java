package com.example.SWP391_G2_SE2055_JV.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gửi email mật khẩu tạm cho tài khoản mới / tài khoản được cấp lại mật khẩu, và báo Quản lý khi nhân viên
 * của họ quên mật khẩu.
 *
 * <p><b>Mặc định TẮT</b> — chỉ bật khi {@code .env} có {@code MAIL_ENABLED=true}
 * ({@code app.mail.enabled}). Khi tắt, bean này không được tạo và sự kiện bị bỏ qua: hệ
 * thống đúng BR-USER-03 / BR-OUT-01 (mật khẩu tạm chỉ hiển thị trên màn hình). Khi bật,
 * email chỉ là kênh BỔ SUNG — API vẫn trả mật khẩu tạm cho Manager, nên gửi lỗi (sai cấu
 * hình Gmail, địa chỉ không tồn tại) không làm hỏng việc tạo tài khoản.
 *
 * <p>Gửi mật khẩu dạng chữ thường chấp nhận được vì mật khẩu chỉ dùng một lần: hệ thống
 * bắt đổi ngay ở lần đăng nhập đầu (BR-USER-07, xem {@code CurrentUserRefreshFilter}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class AccountEmailService {

    private final JavaMailSender mailSender;

    /** Gmail chỉ cho gửi với địa chỉ người gửi là chính tài khoản SMTP. */
    @Value("${spring.mail.username}")
    private String from;

    @Value("${app.frontend.login-url}")
    private String loginUrl;

    /**
     * Chạy SAU KHI transaction tạo tài khoản đã commit: nếu tạo tài khoản thất bại (ví dụ
     * trùng email lúc commit) thì không có email chứa mật khẩu của tài khoản không tồn tại.
     * Chạy nền ({@code @Async}) để API không phải chờ SMTP (1–3 giây).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCredentialsIssued(AccountCredentialsIssuedEvent event) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(event.email());
            helper.setSubject(event.passwordReset()
                ? "Mật khẩu tạm mới cho tài khoản của bạn"
                : "Tài khoản của bạn đã được tạo");
            helper.setText(buildBody(event));

            mailSender.send(message);
            // KHÔNG log mật khẩu.
            log.info("Đã gửi email mật khẩu tạm tới {}", event.email());
        } catch (MessagingException | MailException e) {
            // Không ném lỗi: tài khoản đã tạo xong, Manager vẫn có mật khẩu tạm trên màn hình
            // để thông báo thủ công (BR-USER-03).
            log.warn("Gửi email mật khẩu tạm tới {} thất bại: {}", event.email(), e.getMessage());
        }
    }

    /**
     * Nhân viên quên mật khẩu → báo Quản lý khách sạn của họ. Email chỉ là lời nhắn: Quản lý tự quyết cấp mật
     * khẩu tạm, và mật khẩu tạm đó đến tay nhân viên qua {@link #onCredentialsIssued}. Chạy sau commit, chạy nền,
     * gửi lỗi chỉ ghi log — giống email mật khẩu tạm.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(event.managerEmail());
            helper.setSubject("Nhân viên " + event.staffName() + " xin cấp lại mật khẩu");
            helper.setText(buildResetRequestBody(event));

            mailSender.send(message);
            log.info("Đã báo Quản lý {} yêu cầu quên mật khẩu của {}", event.managerEmail(), event.staffEmail());
        } catch (MessagingException | MailException e) {
            log.warn("Gửi yêu cầu quên mật khẩu tới {} thất bại: {}", event.managerEmail(), e.getMessage());
        }
    }

    private String buildResetRequestBody(PasswordResetRequestedEvent event) {
        String location = event.locationName() == null ? "khách sạn của bạn" : event.locationName();
        return """
            Xin chào %s,

            Nhân viên %s (%s) ở %s vừa báo quên mật khẩu trên hệ thống quản lý khách sạn.

            Để cấp mật khẩu tạm mới: đăng nhập, vào «Nhân viên chi nhánh», tìm %s và bấm «Cấp lại mật khẩu».
            Mật khẩu tạm hiện trên màn hình của bạn và được gửi tới email của nhân viên.
            Nhân viên phải đổi mật khẩu ở lần đăng nhập tới.

            Đăng nhập tại: %s

            Nếu nhân viên không gửi yêu cầu này, bạn có thể bỏ qua email — mật khẩu hiện tại vẫn giữ nguyên.
            """.formatted(event.managerName(), event.staffName(), event.staffEmail(), location, event.staffName(),
            loginUrl);
    }

    private String buildBody(AccountCredentialsIssuedEvent event) {
        String intro = event.passwordReset()
            ? "Mật khẩu của bạn trên hệ thống quản lý khách sạn vừa được cấp lại."
            : "Tài khoản của bạn trên hệ thống quản lý khách sạn đã được tạo.";
        return """
            Xin chào %s,

            %s

            Email đăng nhập: %s
            Mật khẩu tạm:    %s

            Bạn sẽ phải đổi mật khẩu ở lần đăng nhập đầu tiên.
            Đăng nhập tại: %s

            Nếu bạn không mong đợi email này, hãy báo cho quản lý của bạn.
            """.formatted(event.fullName(), intro, event.email(), event.tempPassword(), loginUrl);
    }
}
