package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.ChangePasswordRequest;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tự đổi mật khẩu — BR-USER-07.
 *
 * <p>KHÔNG có luồng "quên mật khẩu" tự phục vụ: Milestone 1 không tích hợp gửi
 * email/SMS (BR-OUT-01), nên việc cấp lại mật khẩu đi qua Manager
 * ({@code POST /users/{id}/reset-password}) và mật khẩu tạm hiển thị trên màn hình
 * cho Manager thông báo thủ công (BR-USER-03).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với email: " + email));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // BR-USER-07: đổi xong thì gỡ cờ bắt buộc đổi mật khẩu.
        user.setMustChangePassword(false);
        userRepository.save(user);

        log.info("Đổi mật khẩu thành công cho {}", email);
    }
}
