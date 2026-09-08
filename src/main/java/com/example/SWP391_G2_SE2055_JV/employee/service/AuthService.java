package com.example.SWP391_G2_SE2055_JV.employee.service;

import com.example.SWP391_G2_SE2055_JV.employee.dto.ChangePasswordRequest;
import com.example.SWP391_G2_SE2055_JV.employee.entity.User;
import com.example.SWP391_G2_SE2055_JV.employee.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    /**
     * Sends a temporary password to the user's email.
     * The user is flagged to change it on next login.
     */
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("No account found for email: " + email));

        String tempPassword = RandomStringUtils.randomAlphanumeric(10);
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);

        sendTempPasswordEmail(user.getEmail(), user.getUsername(), tempPassword);
        log.info("Temporary password sent to {}", email);
    }

    /**
     * Allows an authenticated user to change their own password.
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    private void sendTempPasswordEmail(String to, String username, String tempPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[Hotel Workforce] Temporary Password");
        message.setText(String.format(
            "Hello %s,%n%nYour temporary password is: %s%n%n"
            + "Please log in and change your password immediately.%n%n"
            + "Hotel Workforce Management System",
            username, tempPassword
        ));
        mailSender.send(message);
    }
}
