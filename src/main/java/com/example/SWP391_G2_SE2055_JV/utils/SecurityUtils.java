package com.example.SWP391_G2_SE2055_JV.utils;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.config.Role;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static CustomUserDetails getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            throw new UnauthorizedException("No authenticated user in current session");
        }
        return (CustomUserDetails) auth.getPrincipal();
    }

    public static Long    getCurrentUserId()  { return getCurrentUser().getId(); }
    public static String  getCurrentEmail()   { return getCurrentUser().getUsername(); }
    public static Role    getCurrentRole()    { return getCurrentUser().getRole(); }

    /**
     * Returns the tenant this user belongs to.
     * Throws if called for ADMIN_PLATFORM (tenantId is null).
     */
    public static Long getCurrentTenantId() {
        Long tenantId = getCurrentUser().getTenantId();
        if (tenantId == null) {
            throw new UnauthorizedException("Platform admin has no tenant context");
        }
        return tenantId;
    }

    /**
     * Returns the location (hotel property) this user belongs to.
     * Throws if called for ADMIN_PLATFORM (locationId is null).
     */
    public static Long getCurrentLocationId() {
        Long locationId = getCurrentUser().getLocationId();
        if (locationId == null) {
            throw new UnauthorizedException("Platform admin has no location context");
        }
        return locationId;
    }

    public static boolean isAdminPlatform() {
        return getCurrentRole() == Role.ADMIN_PLATFORM;
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
}
