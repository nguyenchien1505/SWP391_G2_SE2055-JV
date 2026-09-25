package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mọi truy vấn danh sách đều BẮT BUỘC có tenantId để cách ly dữ liệu giữa các Tenant.
 * Không thêm method trả danh sách mà thiếu tham số này.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Email là username, unique toàn hệ thống nên không cần tenantId — BR-USER-06. */
    Optional<User> findByEmail(String email);

    /** BR-USER-06: email của người đã nghỉ việc VẪN chiếm chỗ, không dùng lại được. */
    boolean existsByEmail(String email);

    Page<User> findByTenantId(UUID tenantId, Pageable pageable);

    Page<User> findByTenantIdAndLocationId(UUID tenantId, UUID locationId, Pageable pageable);

    Page<User> findByTenantIdAndRole(UUID tenantId, Role role, Pageable pageable);

    Page<User> findByTenantIdAndLocationIdAndRole(UUID tenantId, UUID locationId, Role role, Pageable pageable);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    /** BR-SAAS-03: quota "User" CHỈ đếm Staff — Giám đốc và Manager không tính. */
    long countByTenantIdAndRoleAndStatusNot(UUID tenantId, Role role, UserStatus status);

    /** BR-ORG-05, BR-ORG-10: chặn xóa khi còn tham chiếu, TÍNH CẢ người đã nghỉ việc. */
    boolean existsByLocationId(UUID locationId);

    /**
     * Có ai giữ Position này không, tính cả người đã nghỉ việc (BR-ORG-10). Dùng để chặn xóa
     * chức danh.
     */
    @Query("select case when count(u) > 0 then true else false end from User u where u.positionId = :positionId")
    boolean isPositionHeld(@Param("positionId") UUID positionId);

    /**
     * Quyền nghiệp vụ của một người — để dựng principal trong session. Truy vấn riêng thay vì đọc
     * {@code User.permissions}: entity truyền vào lúc đó có thể đã tách khỏi session (open-in-view
     * tắt) nên collection lazy không nạp được.
     */
    @Query("select p from User u join u.permissions p where u.id = :userId")
    List<StaffPermission> findPermissionsOfUser(@Param("userId") UUID userId);

    /** BR-ORG-02: Location phải có Manager mới được vận hành chính thức. */
    Optional<User> findFirstByLocationIdAndRoleAndStatus(UUID locationId, Role role, UserStatus status);

    /**
     * 1 Location chỉ có 1 Manager (BR-SCH-08). Gọi với {@code status = TERMINATED} để đếm
     * cả Manager đang tạm khóa (INACTIVE) — họ vẫn giữ Location.
     */
    boolean existsByLocationIdAndRoleAndStatusNot(UUID locationId, Role role, UserStatus status);
}
