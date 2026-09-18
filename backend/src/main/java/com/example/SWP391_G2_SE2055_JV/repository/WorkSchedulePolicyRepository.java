package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.WorkSchedulePolicy;
import com.example.SWP391_G2_SE2055_JV.enums.PolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkSchedulePolicyRepository extends JpaRepository<WorkSchedulePolicy, Long> {
    Page<WorkSchedulePolicy> findByDeletedFalse(Pageable pageable);
    Page<WorkSchedulePolicy> findByLocationIdAndDeletedFalse(Long locationId, Pageable pageable);
    Optional<WorkSchedulePolicy> findFirstByLocationIdAndStatusAndDeletedFalseOrderByEffectiveFromDesc(
        Long locationId, PolicyStatus status);
}
