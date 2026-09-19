package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.SchedulePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * DM-18: mỗi Tenant đúng 1 bản ghi, sửa đè trực tiếp, không lưu lịch sử phiên bản
 * — nên chỉ có tra cứu theo tenantId, không có danh sách và không có CRUD nhiều bản.
 */
@Repository
public interface SchedulePolicyRepository extends JpaRepository<SchedulePolicy, UUID> {

    Optional<SchedulePolicy> findByTenantId(UUID tenantId);
}
