package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.Gender;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tài khoản — DM-01: một bảng duy nhất cho mọi vai trò, trường đặc thù nullable.
 *
 * <pre>
 *   PLATFORM_ADMIN : tenantId = locationId = positionId = null
 *   DIRECTOR       : có tenantId; locationId/positionId null; hồ sơ tối thiểu (BR-USER-05)
 *   MANAGER        : có tenantId + locationId; positionId null (BR-USER-05)
 *   STAFF          : có đủ tenantId + locationId + positionId (BR-USER-01)
 * </pre>
 *
 * <p>Lễ tân / Dọn dẹp KHÔNG phải role — là quyền nghiệp vụ Manager tick cho từng nhân viên
 * ({@link #permissions}); một người có thể có nhiều quyền. Department suy ra từ Position, không
 * lưu ở đây (BR-ORG-07).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    /** NULL với PLATFORM_ADMIN — vai trò này đứng ngoài mọi Tenant (BR-PERM-01). */
    @Column(name = "tenant_id", length = 36)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private Role role;

    /** Username đăng nhập, unique TOÀN HỆ THỐNG kể cả người đã nghỉ việc — BR-USER-06. */
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** BR-USER-07: tài khoản được cấp mật khẩu tạm phải đổi ở lần đăng nhập đầu tiên. */
    @Column(name = "must_change_password", nullable = false)
    @lombok.Builder.Default
    private boolean mustChangePassword = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone", length = 30, nullable = false)
    private String phone;

    @Column(name = "location_id", length = 36)
    private UUID locationId;

    /** Chỉ STAFF mới có Position — BR-USER-05, DM-01. Mỗi người đúng MỘT; Department suy ra từ nó. */
    @Column(name = "position_id", length = 36)
    private UUID positionId;

    /**
     * Quyền nghiệp vụ Manager tick cho nhân viên này (Lễ tân, Dọn dẹp…) — chỉ STAFF. Rỗng = chỉ có
     * quyền chung (BR-PERM-06).
     *
     * <p>LAZY + BatchSize: danh sách nhân sự nạp theo lô thay vì mỗi người một câu. Principal
     * trong session KHÔNG đọc qua đây — entity khi đó có thể đã tách khỏi session (open-in-view
     * tắt) — mà truy vấn riêng qua {@code UserRepository.findPermissionsOfUser}.
     */
    @ElementCollection
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 30, nullable = false)
    @org.hibernate.annotations.BatchSize(size = 100)
    @lombok.Builder.Default
    private Set<StaffPermission> permissions = EnumSet.noneOf(StaffPermission.class);

    @Column(name = "start_work_date")
    private LocalDate startWorkDate;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** BR-USER-04: nghỉ việc là xóa mềm — dữ liệu lịch sử giữ nguyên. */
    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Column(name = "terminated_by", length = 36)
    private UUID terminatedBy;

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /** BR-USER-04: đã nghỉ việc thì không đăng nhập được. */
    public boolean isTerminated() {
        return status == UserStatus.TERMINATED;
    }

    public boolean isStaff() {
        return role == Role.STAFF;
    }
}
