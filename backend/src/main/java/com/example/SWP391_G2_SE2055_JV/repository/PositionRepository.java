package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PositionRepository extends JpaRepository<Position, UUID> {

    /**
     * Tên thuộc tính là {@code active} (field), KHÔNG phải {@code isActive} (tên getter
     * Lombok sinh ra) — viết {@code IsActiveTrue} thì Spring Data không tạo được query
     * và app dừng ngay lúc khởi động.
     */
    List<Position> findByTenantIdAndActiveTrue(UUID tenantId);

    Optional<Position> findByIdAndTenantId(UUID id, UUID tenantId);

    /** BR-ORG-13: tên Position unique trong phạm vi Tenant. */
    boolean existsByTenantIdAndName(UUID tenantId, String name);

    List<Position> findByTenantIdAndPositionType(UUID tenantId, PositionType positionType);

    /** BR-ORG-10: chặn xóa Department khi còn Position trỏ vào. */
    boolean existsByDepartmentId(UUID departmentId);
}
