package com.example.SWP391_G2_SE2055_JV.support;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Đặt người đang đăng nhập cho test SERVICE.
 *
 * <p>{@code SecurityUtils} đọc tĩnh từ {@link SecurityContextHolder} và ép kiểu principal về
 * {@link CustomUserDetails}, nên {@code @WithMockUser} (principal là User của Spring) không
 * dùng được ở tầng service. Helper này dựng đúng principal thật của hệ thống.
 *
 * <p>Nhớ gọi {@link #logout()} trong {@code @AfterEach} để test sau không dính người dùng cũ.
 */
public final class TestAuth {

    private TestAuth() {}

    /** Giám đốc — thuộc Tenant, không gắn Location (DM-01). */
    public static CustomUserDetails loginAsDirector(UUID tenantId) {
        return loginAs(Role.DIRECTOR, tenantId, null, null);
    }

    /** Manager — gắn đúng 1 Location (BR-USER-05). */
    public static CustomUserDetails loginAsManager(UUID tenantId, UUID locationId) {
        return loginAs(Role.MANAGER, tenantId, locationId, null);
    }

    /**
     * Staff với quyền mặc định của một Loại Position: Lễ tân, Dọn dẹp hay Khác (không quyền nào).
     * Cần tổ hợp quyền khác thì dựng {@link CustomUserDetails} trực tiếp.
     */
    public static CustomUserDetails loginAsStaff(UUID tenantId, UUID locationId, PositionType positionType) {
        return loginAs(Role.STAFF, tenantId, locationId, positionType);
    }

    public static CustomUserDetails loginAs(Role role, UUID tenantId, UUID locationId,
                                            PositionType positionType) {
        return loginAs(UUID.randomUUID(), role, tenantId, locationId, positionType);
    }

    /**
     * Đăng nhập bằng id của một user CÓ THẬT trong DB — dùng cho test chạy trên MySQL, nơi khóa
     * ngoại ({@code changed_by}, {@code updated_by}…) đòi người thực hiện phải tồn tại.
     */
    public static CustomUserDetails loginAs(UUID userId, Role role, UUID tenantId, UUID locationId,
                                            PositionType positionType) {
        CustomUserDetails user = CustomUserDetails.builder()
            .id(userId)
            .username(role.name().toLowerCase() + "@test.local")
            .role(role)
            .tenantId(tenantId)
            .locationId(locationId)
            .permissions(role == Role.STAFF ? StaffPermission.defaultFor(positionType) : java.util.Set.of())
            .enabled(true)
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        return user;
    }

    public static void logout() {
        SecurityContextHolder.clearContext();
    }
}
