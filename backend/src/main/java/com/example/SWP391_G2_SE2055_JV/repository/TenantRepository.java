package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByContactEmail(String contactEmail);

    boolean existsByContactEmail(String contactEmail);

    /** Tập ứng viên cho các job BR-SAAS-09 (hết trial) và BR-SAAS-10 (hết ân hạn). */
    List<Tenant> findByStatus(TenantStatus status);
}
