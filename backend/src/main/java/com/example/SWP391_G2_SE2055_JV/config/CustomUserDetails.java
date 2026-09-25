package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
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
 *   <li>{@code POSITION_*} theo {@link PositionType} — chỉ có với STAFF. Đây mới là
 *       thứ quyết định quyền nghiệp vụ đặc thù của Lễ tân / Dọn dẹp, vì hai vai trò
 *       này KHÔNG phải role mà là Loại Position.</li>
 * </ul>
 * Position loại OTHER không nhận thêm quyền đặc thù nào — BR-ORG-09, BR-PERM-06. Nhân viên đa
 * nhiệm nhận một {@code POSITION_*} cho MỖI Loại mình giữ (vị trí chính và kiêm nhiệm).
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
    private final UUID         positionId;    // vị trí chính — chỉ STAFF mới có
    private final PositionType positionType;  // Loại của vị trí chính — chỉ STAFF mới có

    /** Loại của các vị trí KIÊM NHIỆM (nhân viên đa nhiệm); rỗng nếu không kiêm nhiệm. */
    @Builder.Default
    private final Set<PositionType> extraPositionTypes = Set.of();

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
            && positionType == other.positionType
            && getPositionTypes().equals(other.getPositionTypes())
            && mustChangePassword == other.mustChangePassword
            && enabled == other.enabled
            && readOnly == other.readOnly
            && Objects.equals(tenantId, other.tenantId)
            && Objects.equals(locationId, other.locationId)
            && Objects.equals(positionId, other.positionId);
    }

    /**
     * Mọi Loại Position người này giữ — vị trí chính và kiêm nhiệm. Quyền nghiệp vụ là HỢP của
     * chúng: Lễ tân kiêm Dọn dẹp có cả quyền Lễ tân lẫn quyền Dọn dẹp.
     */
    public Set<PositionType> getPositionTypes() {
        Set<PositionType> all = EnumSet.noneOf(PositionType.class);
        if (positionType != null) {
            all.add(positionType);
        }
        if (extraPositionTypes != null) {
            all.addAll(extraPositionTypes);
        }
        return all;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        for (PositionType type : getPositionTypes()) {
            authorities.add(new SimpleGrantedAuthority("POSITION_" + type.name()));
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
