package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.ChangePasswordRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tự đổi mật khẩu — BR-USER-07 — và nhân viên báo quên mật khẩu.
 *
 * <p>KHÔNG có luồng "quên mật khẩu" tự phục vụ: việc cấp lại mật khẩu vẫn đi qua Manager
 * ({@code POST /users/{id}/reset-password}) và mật khẩu tạm hiển thị trên màn hình cho Manager
 * thông báo (BR-USER-03). Nhân viên quên mật khẩu chỉ GỬI YÊU CẦU: hệ thống email cho Quản lý khách
 * sạn của họ ({@link #requestPasswordReset}), không đổi gì trên tài khoản.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Một nhân viên gửi yêu cầu quên mật khẩu tối đa một lần trong khoảng này — form công khai, không để ai
     * dùng nó gửi thư hàng loạt cho Quản lý.
     */
    static final Duration RESET_REQUEST_COOLDOWN = Duration.ofMinutes(15);

    private final UserRepository            userRepository;
    private final LocationRepository        locationRepository;
    private final PasswordEncoder           passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    /** Email đang tắt thì yêu cầu không tới được Quản lý — báo ngay cho người gửi thay vì để họ chờ. */
    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    /** Lần gần nhất mỗi nhân viên gửi yêu cầu — trong bộ nhớ, đủ cho một máy chủ; khởi động lại thì xóa. */
    private final Map<UUID, Instant> lastResetRequest = new ConcurrentHashMap<>();

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

    /**
     * Nhân viên quên mật khẩu: email cho Quản lý khách sạn của họ để Quản lý cấp mật khẩu tạm ở màn «Nhân viên
     * chi nhánh». Không đổi gì trên tài khoản — không ai dùng form công khai này ép đổi mật khẩu người khác được.
     *
     * <p>Chỉ nhân viên (STAFF) đang làm việc, ở khách sạn đang có Quản lý, mới được chuyển yêu cầu; mỗi người
     * tối đa một lần trong {@link #RESET_REQUEST_COOLDOWN}. Mọi trường hợp khác chỉ ghi log: nơi gọi luôn trả
     * cùng một kết quả để không ai dò được email nào có tài khoản.
     *
     * @throws BusinessException khi hệ thống tắt email ({@code MAIL_ENABLED}) — kiểm trước khi tìm tài khoản nên
     *                           không lộ gì về email đã nhập
     */
    @Transactional(readOnly = true)
    public void requestPasswordReset(String email) {
        if (!mailEnabled) {
            throw new BusinessException("Hệ thống chưa bật gửi email nên không chuyển được yêu cầu. "
                + "Hãy báo trực tiếp Quản lý khách sạn để được cấp lại mật khẩu.");
        }
        User staff = userRepository.findByEmail(email.trim()).orElse(null);
        if (staff == null || !staff.isStaff() || !staff.isActive() || staff.getLocationId() == null) {
            log.info("Yêu cầu quên mật khẩu không chuyển cho ai: không phải nhân viên đang làm việc ({})", email);
            return;
        }
        User manager = userRepository
            .findFirstByLocationIdAndRoleAndStatus(staff.getLocationId(), Role.MANAGER, UserStatus.ACTIVE)
            .orElse(null);
        if (manager == null) {
            log.warn("Khách sạn của {} chưa có Quản lý đang làm việc — yêu cầu quên mật khẩu không tới ai",
                staff.getEmail());
            return;
        }
        if (!claimResetRequest(staff.getId())) {
            log.info("Bỏ qua yêu cầu quên mật khẩu lặp lại của {} — chưa hết {} phút", staff.getEmail(),
                RESET_REQUEST_COOLDOWN.toMinutes());
            return;
        }
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(manager.getEmail(), manager.getFullName(),
            staff.getFullName(), staff.getEmail(), findLocationName(staff.getLocationId(), staff.getTenantId())));
        log.info("Chuyển yêu cầu quên mật khẩu của {} tới Quản lý {}", staff.getEmail(), manager.getEmail());
    }

    /** Giữ chỗ cho một yêu cầu mới; {@code false} nếu người này vừa gửi trong {@link #RESET_REQUEST_COOLDOWN}. */
    private boolean claimResetRequest(UUID staffId) {
        Instant now = Instant.now();
        AtomicBoolean claimed = new AtomicBoolean(false);
        lastResetRequest.compute(staffId, (id, last) -> {
            if (last != null && last.plus(RESET_REQUEST_COOLDOWN).isAfter(now)) {
                return last;
            }
            claimed.set(true);
            return now;
        });
        return claimed.get();
    }

    /**
     * Tên khách sạn người dùng đang làm việc — hiển thị trên thanh tiêu đề. Staff không gọi được
     * {@code GET /locations} nên tên phải đi kèm {@code /auth/me}. Lọc theo Tenant để không lộ
     * tên khách sạn của Tenant khác; chưa gán khách sạn thì trả {@code null}.
     */
    @Transactional(readOnly = true)
    public String findLocationName(UUID locationId, UUID tenantId) {
        if (locationId == null || tenantId == null) {
            return null;
        }
        return locationRepository.findByIdAndTenantId(locationId, tenantId)
            .map(Location::getName)
            .orElse(null);
    }

    /**
     * Email Quản lý đang phụ trách khách sạn — hiển thị trên thanh tiêu đề để nhân viên biết liên
     * hệ ai. Staff không gọi được {@code GET /users} nên email phải đi kèm {@code /auth/me}. Khách
     * sạn chưa có Quản lý đang hoạt động thì trả {@code null}.
     */
    @Transactional(readOnly = true)
    public String findLocationManagerEmail(UUID locationId) {
        if (locationId == null) {
            return null;
        }
        return userRepository.findFirstByLocationIdAndRoleAndStatus(locationId, Role.MANAGER, UserStatus.ACTIVE)
            .map(User::getEmail)
            .orElse(null);
    }
}
