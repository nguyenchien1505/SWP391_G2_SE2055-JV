package com.example.SWP391_G2_SE2055_JV.scheduling.service;

import com.example.SWP391_G2_SE2055_JV.config.PolicyStatus;
import com.example.SWP391_G2_SE2055_JV.config.Role;
import com.example.SWP391_G2_SE2055_JV.config.ShiftStatus;
import com.example.SWP391_G2_SE2055_JV.employee.entity.User;
import com.example.SWP391_G2_SE2055_JV.employee.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.scheduling.dto.CreateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.scheduling.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.scheduling.dto.UpdateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.scheduling.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.scheduling.entity.WorkSchedulePolicy;
import com.example.SWP391_G2_SE2055_JV.scheduling.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.scheduling.repository.WorkSchedulePolicyRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Shift is the pivot both Work Schedule Management (this class) and Cleaning Schedule
 * Management ({@code CleaningAssignmentService}) build on: a cleaning assignment is a
 * housekeeping-specific task hung off a shift via {@code shift_id}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final WorkSchedulePolicyRepository policyRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<ShiftResponse> getShifts(Pageable pageable) {
        Page<Shift> page = isManagerOrAdmin()
            ? shiftRepository.findByDeletedFalse(pageable)
            : shiftRepository.findByUserIdAndDeletedFalse(SecurityUtils.getCurrentUserId(), pageable);
        return page.map(ShiftResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ShiftResponse getShiftById(Long id) {
        return ShiftResponse.fromEntity(getActiveOrThrow(id));
    }

    @Transactional
    public ShiftResponse createShift(CreateShiftRequest request) {
        User targetUser = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        enforcePolicy(targetUser, request.getShiftDate());

        Shift shift = Shift.builder()
            .userId(request.getUserId())
            .shiftType(request.getShiftType())
            .shiftDate(request.getShiftDate())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .status(ShiftStatus.SCHEDULED)
            .createdAt(LocalDateTime.now())
            .createdBy(currentUserIdOrNull())
            .build();

        Shift saved = shiftRepository.save(shift);
        log.info("Created shift {} for user {} on {}", saved.getId(), saved.getUserId(), saved.getShiftDate());
        return ShiftResponse.fromEntity(saved);
    }

    @Transactional
    public ShiftResponse updateShift(Long id, UpdateShiftRequest request) {
        Shift shift = getActiveOrThrow(id);

        if (request.getShiftType() != null) shift.setShiftType(request.getShiftType());
        if (request.getShiftDate() != null) shift.setShiftDate(request.getShiftDate());
        if (request.getStartTime() != null) shift.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) shift.setEndTime(request.getEndTime());
        if (request.getStatus() != null) shift.setStatus(request.getStatus());

        log.info("Updated shift {}", id);
        return ShiftResponse.fromEntity(shiftRepository.save(shift));
    }

    @Transactional
    public void deleteShift(Long id) {
        Shift shift = getActiveOrThrow(id);
        shift.setDeleted(true);
        shift.setDeletedAt(LocalDateTime.now());
        shift.setDeletedBy(currentUserIdOrNull());
        shiftRepository.save(shift);
        log.info("Deleted shift {}", id);
    }

    /**
     * Validates a new shift against the location's active work-schedule policy
     * (max_shifts_per_week). There is no DB-level FK between shifts and
     * work_schedule_policies — the relationship is enforced here, in application logic.
     */
    private void enforcePolicy(User targetUser, LocalDate shiftDate) {
        if (targetUser.getLocationId() == null) {
            return;
        }
        policyRepository
            .findFirstByLocationIdAndStatusAndDeletedFalseOrderByEffectiveFromDesc(
                targetUser.getLocationId(), PolicyStatus.ACTIVE)
            .ifPresent(policy -> {
                if (policy.getMaxShiftsPerWeek() == null) {
                    return;
                }
                LocalDate weekStart = shiftDate.with(DayOfWeek.MONDAY);
                LocalDate weekEnd = shiftDate.with(DayOfWeek.SUNDAY);
                int existing = shiftRepository.countByUserIdAndShiftDateBetweenAndDeletedFalse(
                    targetUser.getId(), weekStart, weekEnd);
                if (existing >= policy.getMaxShiftsPerWeek()) {
                    throw new BusinessException(String.format(
                        "User %d already has %d shift(s) in the week of %s, at the policy limit of %d",
                        targetUser.getId(), existing, weekStart, policy.getMaxShiftsPerWeek()));
                }
            });
    }

    private Shift getActiveOrThrow(Long id) {
        Shift shift = shiftRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", id));
        if (shift.isDeleted()) {
            throw new ResourceNotFoundException("Shift", "id", id);
        }
        return shift;
    }

    private boolean isManagerOrAdmin() {
        return SecurityUtils.hasAnyRole(Role.ADMIN_PLATFORM, Role.MANAGER, Role.DIRECTOR);
    }

    private Long currentUserIdOrNull() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
