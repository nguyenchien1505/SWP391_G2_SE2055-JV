package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.LocationAttendanceNetwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Mạng chấm công của từng khách sạn. {@code tenantId} là chốt cách ly Tenant như mọi truy vấn. */
@Repository
public interface LocationAttendanceNetworkRepository extends JpaRepository<LocationAttendanceNetwork, UUID> {

    List<LocationAttendanceNetwork> findByTenantIdAndLocationIdOrderByCreatedAtAsc(UUID tenantId, UUID locationId);

    Optional<LocationAttendanceNetwork> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByLocationIdAndCidr(UUID locationId, String cidr);

    long countByLocationId(UUID locationId);
}
