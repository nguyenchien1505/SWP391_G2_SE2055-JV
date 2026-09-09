package com.example.SWP391_G2_SE2055_JV.employee.service;

import com.example.SWP391_G2_SE2055_JV.employee.dto.CreateUserRequest;
import com.example.SWP391_G2_SE2055_JV.employee.dto.UpdateUserRequest;
import com.example.SWP391_G2_SE2055_JV.employee.dto.UserResponse;
import com.example.SWP391_G2_SE2055_JV.employee.entity.User;
import com.example.SWP391_G2_SE2055_JV.employee.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
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

/**
 * Service for managing user accounts (CRUD operations).
 * Admin/Manager can create, read, update, delete users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    /**
     * Get all users with pagination.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(UserResponse::fromEntity);
    }

    /**
     * Get a single user by ID.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return UserResponse.fromEntity(user);
    }

    /**
     * Create a new user account.
     * Generates a temporary password and sends it via email.
     * User must change password on first login.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // Validate uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists: " + request.getEmail());
        }

        // Generate temporary password
        String tempPassword = RandomStringUtils.randomAlphanumeric(10);

        // Create user
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(tempPassword))
                .role(request.getRole())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .mustChangePassword(true)
                .build();

        User savedUser = userRepository.save(user);

        // Send temporary password email
        sendTempPasswordEmail(savedUser.getEmail(), savedUser.getUsername(), tempPassword);

        log.info("Created new user: {} with role: {}", savedUser.getUsername(), savedUser.getRole());
        return UserResponse.fromEntity(savedUser);
    }

    /**
     * Update an existing user.
     * Supports partial updates (only provided fields are updated).
     */
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Update email if provided
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        // Update role if provided
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        // Update enabled status if provided
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        User updatedUser = userRepository.save(user);
        log.info("Updated user: {}", updatedUser.getUsername());
        return UserResponse.fromEntity(updatedUser);
    }

    /**
     * Delete a user by ID.
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        userRepository.delete(user);
        log.info("Deleted user: {}", user.getUsername());
    }

    /**
     * Reset user password (admin action).
     * Generates a new temporary password and sends it via email.
     */
    @Transactional
    public void resetUserPassword(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        String tempPassword = RandomStringUtils.randomAlphanumeric(10);
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);

        sendTempPasswordEmail(user.getEmail(), user.getUsername(), tempPassword);
        log.info("Reset password for user: {}", user.getUsername());
    }

    private void sendTempPasswordEmail(String to, String username, String tempPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[Hotel Workforce] Your Account Details");
        message.setText(String.format(
                "Hello %s,%n%nYour account has been created.%n%n"
                        + "Username: %s%n"
                        + "Temporary Password: %s%n%n"
                        + "Please log in and change your password immediately.%n%n"
                        + "Hotel Workforce Management System",
                username, username, tempPassword
        ));
        mailSender.send(message);
    }
}
