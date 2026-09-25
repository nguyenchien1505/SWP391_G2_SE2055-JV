package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.repository.PositionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository      userRepository;
    private final PositionRepository  positionRepository;
    private final TenantAccessPolicy  tenantAccessPolicy;

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
        // Chỉ STAFF có Position, nên người không có vị trí chính thì cũng không có kiêm nhiệm.
        Set<PositionType> extraPositionTypes = user.getPositionId() == null
            ? Set.of()
            : Set.copyOf(positionRepository.findExtraPositionTypesOfUser(user.getId()));

        // Trạng thái Tenant quyết định người này vào được tới đâu — xem TenantAccessPolicy.
        // BR-SAAS-11 (chặn hoàn toàn khi SUSPENDED) được điều chỉnh: Giám đốc của Tenant hết hạn
        // dùng thử / thanh toán thất bại vẫn vào được ở chế độ chỉ đọc để thanh toán.
        TenantAccessPolicy.Mode access = tenantAccessPolicy.evaluate(user).mode();

        return CustomUserDetails.builder()
            .id(user.getId())
            .username(user.getEmail())
            .password(user.getPasswordHash())
            .role(user.getRole())
            .tenantId(user.getTenantId())
            .locationId(user.getLocationId())
            .positionId(user.getPositionId())
            .positionType(positionType)
            .extraPositionTypes(extraPositionTypes)
            .mustChangePassword(user.isMustChangePassword())
            .enabled(user.isActive() && access != TenantAccessPolicy.Mode.BLOCKED)
            .readOnly(access == TenantAccessPolicy.Mode.READ_ONLY)
            .build();
    }
}
