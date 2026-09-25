package com.example.SWP391_G2_SE2055_JV.utils;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Truy cập ngữ cảnh người dùng hiện tại.
 *
 * <p>{@link #getCurrentTenantId()} là chốt chặn cách ly dữ liệu giữa các Tenant:
 * MỌI truy vấn nghiệp vụ phải lọc theo giá trị này, không được dùng
 * {@code findAll()} trần.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static CustomUserDetails getCurrentUser() {
        CustomUserDetails user = getCurrentUserOrNull();
        if (user == null) {
            throw new UnauthorizedException("Không có người dùng đăng nhập trong session hiện tại");
        }
        return user;
    }

    /** Trả về null thay vì ném lỗi — dùng cho scheduled job và luồng đăng ký Tenant. */
    public static CustomUserDetails getCurrentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            return null;
        }
        return details;
    }

    public static UUID   getCurrentUserId() { return getCurrentUser().getId(); }
    public static String getCurrentEmail()  { return getCurrentUser().getUsername(); }
    public static Role   getCurrentRole()   { return getCurrentUser().getRole(); }

    /** Dùng cho JPA auditing: ngoài ngữ cảnh đăng nhập thì created_by để NULL. */
    public static UUID getCurrentUserIdOrNull() {
        CustomUserDetails user = getCurrentUserOrNull();
        return user == null ? null : user.getId();
    }

    /**
     * Tenant của người dùng hiện tại. Ném lỗi với PLATFORM_ADMIN vì vai trò này đứng
     * ngoài mọi Tenant (BR-PERM-01) — các API cấp platform không được gọi hàm này.
     */
    public static UUID getCurrentTenantId() {
        UUID tenantId = getCurrentUser().getTenantId();
        if (tenantId == null) {
            throw new UnauthorizedException("Admin Platform không thuộc Tenant nào");
        }
        return tenantId;
    }

    /** Location của người dùng hiện tại. Ném lỗi với PLATFORM_ADMIN và DIRECTOR. */
    public static UUID getCurrentLocationId() {
        UUID locationId = getCurrentUser().getLocationId();
        if (locationId == null) {
            throw new UnauthorizedException("Người dùng hiện tại không gắn với Location nào");
        }
        return locationId;
    }

    public static UUID getCurrentTenantIdOrNull() {
        CustomUserDetails user = getCurrentUserOrNull();
        return user == null ? null : user.getTenantId();
    }

    public static UUID getCurrentLocationIdOrNull() {
        CustomUserDetails user = getCurrentUserOrNull();
        return user == null ? null : user.getLocationId();
    }

    public static boolean isPlatformAdmin() {
        return getCurrentRole() == Role.PLATFORM_ADMIN;
    }

    public static boolean hasRole(Role role) {
        return getCurrentRole() == role;
    }

    public static boolean hasAnyRole(Role... roles) {
        Role current = getCurrentRole();
        for (Role r : roles) {
            if (current == r) return true;
        }
        return false;
    }

    /**
     * Quyền nghiệp vụ đặc thù đi theo Loại Position, không theo role — BR-ORG-08. Tính cả vị trí
     * kiêm nhiệm của nhân viên đa nhiệm.
     */
    public static boolean hasPositionType(PositionType type) {
        CustomUserDetails user = getCurrentUserOrNull();
        return user != null && user.getPositionTypes().contains(type);
    }
}
