package com.example.SWP391_G2_SE2055_JV.scheduling.service;

import com.example.SWP391_G2_SE2055_JV.config.PolicyStatus;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.scheduling.dto.CreateWorkSchedulePolicyRequest;
import com.example.SWP391_G2_SE2055_JV.scheduling.dto.UpdateWorkSchedulePolicyRequest;
import com.example.SWP391_G2_SE2055_JV.scheduling.dto.WorkSchedulePolicyResponse;
import com.example.SWP391_G2_SE2055_JV.scheduling.entity.WorkSchedulePolicy;
import com.example.SWP391_G2_SE2055_JV.scheduling.repository.WorkSchedulePolicyRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkSchedulePolicyService {

    private final WorkSchedulePolicyRepository policyRepository;

    @Transactional(readOnly = true)
    public Page<WorkSchedulePolicyResponse> getPolicies(Long locationId, Pageable pageable) {
        Page<WorkSchedulePolicy> page = (locationId != null)
            ? policyRepository.findByLocationIdAndDeletedFalse(locationId, pageable)
            : policyRepository.findByDeletedFalse(pageable);
        return page.map(WorkSchedulePolicyResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public WorkSchedulePolicyResponse getPolicyById(Long id) {
        return WorkSchedulePolicyResponse.fromEntity(getActiveOrThrow(id));
    }

    @Transactional
    public WorkSchedulePolicyResponse createPolicy(CreateWorkSchedulePolicyRequest request) {
        WorkSchedulePolicy policy = WorkSchedulePolicy.builder()
            .locationId(request.getLocationId())
            .name(request.getName())
            .maxShiftsPerWeek(request.getMaxShiftsPerWeek())
            .maxHoursPerWeek(request.getMaxHoursPerWeek())
            .minRestHours(request.getMinRestHours())
            .effectiveFrom(request.getEffectiveFrom())
            .effectiveTo(request.getEffectiveTo())
            .status(PolicyStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .createdBy(currentUserIdOrNull())
            .build();

        WorkSchedulePolicy saved = policyRepository.save(policy);
        log.info("Created work-schedule policy '{}' for location {}", saved.getName(), saved.getLocationId());
        return WorkSchedulePolicyResponse.fromEntity(saved);
    }

    @Transactional
    public WorkSchedulePolicyResponse updatePolicy(Long id, UpdateWorkSchedulePolicyRequest request) {
        WorkSchedulePolicy policy = getActiveOrThrow(id);

        if (request.getName() != null) policy.setName(request.getName());
        if (request.getMaxShiftsPerWeek() != null) policy.setMaxShiftsPerWeek(request.getMaxShiftsPerWeek());
        if (request.getMaxHoursPerWeek() != null) policy.setMaxHoursPerWeek(request.getMaxHoursPerWeek());
        if (request.getMinRestHours() != null) policy.setMinRestHours(request.getMinRestHours());
        if (request.getEffectiveFrom() != null) policy.setEffectiveFrom(request.getEffectiveFrom());
        if (request.getEffectiveTo() != null) policy.setEffectiveTo(request.getEffectiveTo());
        if (request.getStatus() != null) policy.setStatus(request.getStatus());

        log.info("Updated work-schedule policy {}", id);
        return WorkSchedulePolicyResponse.fromEntity(policyRepository.save(policy));
    }

    @Transactional
    public void deletePolicy(Long id) {
        WorkSchedulePolicy policy = getActiveOrThrow(id);
        policy.setDeleted(true);
        policy.setDeletedAt(LocalDateTime.now());
        policy.setDeletedBy(currentUserIdOrNull());
        policyRepository.save(policy);
        log.info("Deleted work-schedule policy {}", id);
    }

    private WorkSchedulePolicy getActiveOrThrow(Long id) {
        WorkSchedulePolicy policy = policyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("WorkSchedulePolicy", "id", id));
        if (policy.isDeleted()) {
            throw new ResourceNotFoundException("WorkSchedulePolicy", "id", id);
        }
        return policy;
    }

    private Long currentUserIdOrNull() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
