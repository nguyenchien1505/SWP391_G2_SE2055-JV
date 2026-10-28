package com.example.SWP391_G2_SE2055_JV.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Email báo Quản lý khi nhân viên quên mật khẩu — người nhận, tiêu đề, nội dung, và gửi lỗi không làm vỡ gì. */
@ExtendWith(MockitoExtension.class)
class AccountEmailServiceTest {

    @Mock JavaMailSender mailSender;

    @InjectMocks AccountEmailService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "from", "noreply@hotel.test");
        ReflectionTestUtils.setField(service, "loginUrl", "http://localhost:3000/dang-nhap");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    @Test
    void shouldEmailManagerWhoTheStaffIsAndWhatToDo() throws Exception {
        service.onPasswordResetRequested(event());

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("quanly@test.local");
        assertThat(message.getSubject()).isEqualTo("Nhân viên Nguyễn Thị Hoa xin cấp lại mật khẩu");
        assertThat((String) message.getContent()).contains(
            "Xin chào Trần Quản Lý",
            "Nguyễn Thị Hoa (hoa@test.local) ở Sao Mai Hà Nội",
            "«Nhân viên chi nhánh»",
            "«Cấp lại mật khẩu»",
            "Nhân viên phải đổi mật khẩu ở lần đăng nhập tới.",
            "http://localhost:3000/dang-nhap");
    }

    /** Gửi lỗi (SMTP hỏng, địa chỉ sai) chỉ ghi log — yêu cầu của nhân viên vẫn được nhận như bình thường. */
    @Test
    void shouldSwallowMailFailure() {
        doThrow(new MailSendException("SMTP không phản hồi")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service.onPasswordResetRequested(event())).doesNotThrowAnyException();
    }

    private static PasswordResetRequestedEvent event() {
        return new PasswordResetRequestedEvent(
            "quanly@test.local", "Trần Quản Lý", "Nguyễn Thị Hoa", "hoa@test.local", "Sao Mai Hà Nội");
    }
}
