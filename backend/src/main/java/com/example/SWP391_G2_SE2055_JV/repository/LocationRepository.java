package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

    /**
     * Chốt chặn cách ly Tenant cho mọi {@code locationId} nhận từ client: khóa ngoại chỉ
     * đảm bảo Location TỒN TẠI, không đảm bảo nó thuộc cùng Tenant với người thao tác.
     */
    Optional<Location> findByIdAndTenantId(UUID id, UUID tenantId);
}
