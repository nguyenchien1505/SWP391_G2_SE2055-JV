package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.ChangePasswordRequest;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender  mailSender;

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("No account found for email: " + email));

        String tempPassword = RandomStringUtils.randomAlphanumeric(10);
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(user);

        sendEmailSafe(user.getEmail(), user.getFullName(), tempPassword);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("No account found for email: " + email));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public void sendEmailSafe(String to, String fullName, String tempPassword) {
        log.info("=== TEMP PASSWORD for {} : {} ===", to, tempPassword);
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject("[Hotel Workforce] Temporary Password");
            msg.setText(String.format(
                "Hello %s,%n%nYour temporary password is: %s%n%n"
                + "Please log in and change it immediately.%n%nHotel Workforce System",
                fullName, tempPassword
            ));
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Email not sent to {} — {}", to, e.getMessage());
        }
    }
}
