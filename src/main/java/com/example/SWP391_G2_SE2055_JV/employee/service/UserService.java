package com.example.SWP391_G2_SE2055_JV.employee.service;

import com.example.SWP391_G2_SE2055_JV.config.UserStatus;
import com.example.SWP391_G2_SE2055_JV.employee.dto.CreateUserRequest;
import com.example.SWP391_G2_SE2055_JV.employee.dto.UpdateUserRequest;
import com.example.SWP391_G2_SE2055_JV.employee.dto.UserResponse;
import com.example.SWP391_G2_SE2055_JV.employee.entity.User;
import com.example.SWP391_G2_SE2055_JV.employee.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return UserResponse.fromEntity(getActiveUserOrThrow(id));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists: " + request.getEmail());
        }

        String tempPassword = RandomStringUtils.randomAlphanumeric(10);

        User user = User.builder()
            .fullName(request.getFullName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .passwordHash(passwordEncoder.encode(tempPassword))
            .role(request.getRole())
            .tenantId(request.getTenantId())
            .locationId(request.getLocationId())
            .status(toStatus(request.getEnabled()))
            .createdBy(currentUserIdOrNull())
            .build();

        User saved = userRepository.save(user);
        sendTempPasswordEmail(saved.getEmail(), saved.getFullName(), tempPassword);
        log.info("Created user: {} role: {}", saved.getEmail(), saved.getRole());
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = getActiveUserOrThrow(id);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        if (request.getLocationId() != null) {
            user.setLocationId(request.getLocationId());
        }

        if (request.getEnabled() != null) {
            user.setStatus(toStatus(request.getEnabled()));
        }

        log.info("Updated user: {}", user.getEmail());
        return UserResponse.fromEntity(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = getActiveUserOrThrow(id);
        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        user.setDeletedBy(currentUserIdOrNull());
        userRepository.save(user);
        log.info("Deleted user: {}", user.getEmail());
    }

    @Transactional
    public void resetUserPassword(Long id) {
        User user = getActiveUserOrThrow(id);

        String tempPassword = RandomStringUtils.randomAlphanumeric(10);
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(user);

        sendTempPasswordEmail(user.getEmail(), user.getFullName(), tempPassword);
        log.info("Reset password for user: {}", user.getEmail());
    }

    private User getActiveUserOrThrow(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        if (user.isDeleted()) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        return user;
    }

    private UserStatus toStatus(Boolean enabled) {
        return (enabled == null || enabled) ? UserStatus.ACTIVE : UserStatus.INACTIVE;
    }

    private Long currentUserIdOrNull() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private void sendTempPasswordEmail(String to, String fullName, String tempPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[Hotel Workforce] Your Account Details");
        message.setText(String.format(
            "Hello %s,%n%nYour account has been created.%n%n"
            + "Email: %s%n"
            + "Temporary Password: %s%n%n"
            + "Please log in and change your password immediately.%n%n"
            + "Hotel Workforce Management System",
            fullName, to, tempPassword
        ));
        mailSender.send(message);
    }
}
