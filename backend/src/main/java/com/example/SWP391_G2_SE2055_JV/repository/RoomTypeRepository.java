package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Loại phòng — danh mục cấp TENANT dùng chung mọi Location (BR-ORG-11).
 */
@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {

    List<RoomType> findByTenantIdOrderByNameAsc(UUID tenantId);

    /** Tên thuộc tính là {@code active} (field), không phải {@code isActive} (getter Lombok). */
    List<RoomType> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);

    Optional<RoomType> findByIdAndTenantId(UUID id, UUID tenantId);

    /** BR-ORG-13: tên Loại phòng unique trong phạm vi Tenant. */
    boolean existsByTenantIdAndName(UUID tenantId, String name);

    boolean existsByTenantIdAndNameAndIdNot(UUID tenantId, String name, UUID id);
}
