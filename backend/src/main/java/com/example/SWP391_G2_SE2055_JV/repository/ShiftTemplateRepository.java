package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, UUID> {

    /** Template là danh mục cấp Tenant — không được tham chiếu template của Tenant khác. */
    Optional<ShiftTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
}
