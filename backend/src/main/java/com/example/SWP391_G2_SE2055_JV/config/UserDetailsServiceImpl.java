package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.repository.PositionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository     userRepository;
    private final PositionRepository positionRepository;
    private final TenantRepository   tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public CustomUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản với email: " + email));

        return toPrincipal(user);
    }

    /** Dùng chung cho cả formLogin và Google OAuth2 để hai luồng không lệch nhau. */
    @Transactional(readOnly = true)
    public CustomUserDetails toPrincipal(User user) {
        PositionType positionType = user.getPositionId() == null
            ? null
            : positionRepository.findById(user.getPositionId())
                .map(Position::getPositionType)
                .orElse(null);

        return CustomUserDetails.builder()
            .id(user.getId())
            .username(user.getEmail())
            .password(user.getPasswordHash())
            .role(user.getRole())
            .tenantId(user.getTenantId())
            .locationId(user.getLocationId())
            .positionId(user.getPositionId())
            .positionType(positionType)
            .mustChangePassword(user.isMustChangePassword())
            .enabled(user.isActive() && !isTenantLoginBlocked(user))
            .build();
    }

    /**
     * BR-SAAS-11: Tenant SUSPENDED thì chặn đăng nhập HOÀN TOÀN mọi user bên trong,
     * không có chế độ read-only. PLATFORM_ADMIN không thuộc Tenant nào nên không bị ảnh hưởng.
     */
    private boolean isTenantLoginBlocked(User user) {
        if (user.getTenantId() == null) {
            return false;
        }
        return tenantRepository.findById(user.getTenantId())
            .map(Tenant::isLoginBlocked)
            .orElse(true);
    }
}
