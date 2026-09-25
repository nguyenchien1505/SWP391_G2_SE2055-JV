package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Principal trong session.
 *
 * <p>Cấp HAI loại authority vì phân quyền Milestone 1 đi theo hai trục (BR-ORG-08):
 * <ul>
 *   <li>{@code ROLE_*} theo {@link Role} — 4 giá trị của DM-01.</li>
 *   <li>{@code POSITION_*} theo {@link StaffPermission} — chỉ có với STAFF: mỗi quyền nghiệp vụ
 *       Manager đã tick cho người này là một authority (Lễ tân kiêm Dọn dẹp có cả hai). Đây mới là
 *       thứ quyết định quyền đặc thù của Lễ tân / Dọn dẹp, vì hai vai trò này KHÔNG phải role.</li>
 * </ul>
 * Nhân viên không được tick quyền nào chỉ có quyền chung — BR-ORG-09, BR-PERM-06.
 *
 * <p>{@code equals}/{@code hashCode} CHỈ theo {@code id}: SessionRegistry của
 * {@code maximumSessions(1)} dùng chúng để nhận ra "cùng một người" giữa các lần đăng
 * nhập. Để so toàn bộ dữ liệu phân quyền, dùng {@link #hasSameStateAs}.
 */
@Getter
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CustomUserDetails implements UserDetails {

    @EqualsAndHashCode.Include
    private final UUID         id;
    private final String       username;      // email
    private final String       password;
    private final Role         role;
    private final UUID         tenantId;      // null với PLATFORM_ADMIN
    private final UUID         locationId;    // null với PLATFORM_ADMIN và DIRECTOR
    private final UUID         positionId;    // chỉ STAFF mới có

    /** Quyền nghiệp vụ Manager đã tick — chỉ STAFF; rỗng = chỉ có quyền chung. */
    @Builder.Default
    private final Set<StaffPermission> permissions = Set.of();

    private final boolean      mustChangePassword;
    private final boolean      enabled;

    /**
     * Chỉ được xem, không được ghi. Bật với Giám đốc của Tenant bị khóa do hết hạn dùng thử /
     * thanh toán thất bại (xem {@link TenantAccessPolicy}); {@link CurrentUserRefreshFilter}
     * chặn mọi thao tác ghi khi cờ này bật.
     */
    private final boolean      readOnly;

    /**
     * So mọi trường ảnh hưởng tới phân quyền — dùng để biết principal trong session có
     * cần thay bằng bản mới đọc từ DB hay không (xem {@link CurrentUserRefreshFilter}).
     */
    public boolean hasSameStateAs(CustomUserDetails other) {
        return other != null
            && role == other.role
            && Objects.equals(permissions, other.permissions)
            && mustChangePassword == other.mustChangePassword
            && enabled == other.enabled
            && readOnly == other.readOnly
            && Objects.equals(tenantId, other.tenantId)
            && Objects.equals(locationId, other.locationId)
            && Objects.equals(positionId, other.positionId);
    }

    public boolean hasPermission(StaffPermission permission) {
        return permissions != null && permissions.contains(permission);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        for (StaffPermission permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission.authority()));
        }
        return authorities;
    }

    @Override public String  getPassword()             { return password; }
    @Override public String  getUsername()             { return username; }
    @Override public boolean isEnabled()               { return enabled; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
