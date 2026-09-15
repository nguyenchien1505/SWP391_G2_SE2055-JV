package com.example.SWP391_G2_SE2055_JV.housekeeping.service;

import com.example.SWP391_G2_SE2055_JV.config.CleaningAssignmentStatus;
import com.example.SWP391_G2_SE2055_JV.config.Role;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import com.example.SWP391_G2_SE2055_JV.housekeeping.dto.CleaningAssignmentResponse;
import com.example.SWP391_G2_SE2055_JV.housekeeping.dto.CreateCleaningAssignmentRequest;
import com.example.SWP391_G2_SE2055_JV.housekeeping.dto.UpdateCleaningAssignmentStatusRequest;
import com.example.SWP391_G2_SE2055_JV.housekeeping.entity.CleaningAssignment;
import com.example.SWP391_G2_SE2055_JV.housekeeping.repository.CleaningAssignmentRepository;
import com.example.SWP391_G2_SE2055_JV.scheduling.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.scheduling.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cleaning Schedule Management. A CleaningAssignment is always tied to a Shift
 * (created by Work Schedule Management via {@code ShiftService}) plus a room — this
 * service is the only place in the codebase that reads across into the {@code scheduling}
 * package's {@code ShiftRepository}, to resolve "my cleaning schedule" from "my shifts".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningAssignmentService {

    private final CleaningAssignmentRepository assignmentRepository;
    private final ShiftRepository shiftRepository;

    @Transactional(readOnly = true)
    public Page<CleaningAssignmentResponse> getAssignments(Pageable pageable) {
        Page<CleaningAssignment> page;
        if (isManagerOrAdmin()) {
            page = assignmentRepository.findByDeletedFalse(pageable);
        } else {
            List<Long> myShiftIds = shiftRepository
                .findByUserIdAndDeletedFalse(SecurityUtils.getCurrentUserId(), Pageable.unpaged())
                .map(Shift::getId)
                .getContent();
            page = assignmentRepository.findByShiftIdInAndDeletedFalse(myShiftIds, pageable);
        }
        return page.map(CleaningAssignmentResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public CleaningAssignmentResponse getAssignmentById(Long id) {
        return CleaningAssignmentResponse.fromEntity(getActiveOrThrow(id));
    }

    @Transactional
    public CleaningAssignmentResponse createAssignment(CreateCleaningAssignmentRequest request) {
        Shift shift = shiftRepository.findById(request.getShiftId())
            .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", request.getShiftId()));

        CleaningAssignment assignment = CleaningAssignment.builder()
            .shiftId(shift.getId())
            .roomId(request.getRoomId())
            .status(CleaningAssignmentStatus.ASSIGNED)
            .assignedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .createdBy(currentUserIdOrNull())
            .build();

        CleaningAssignment saved = assignmentRepository.save(assignment);
        log.info("Assigned room {} to shift {} for cleaning", saved.getRoomId(), saved.getShiftId());
        return CleaningAssignmentResponse.fromEntity(saved);
    }

    /**
     * Self-scoped write: HOUSEKEEPING may only update the completion status of an
     * assignment tied to their own shift (enforced against Shift.userId, since
     * CleaningAssignment has no user_id of its own).
     */
    @Transactional
    public CleaningAssignmentResponse updateStatus(Long id, UpdateCleaningAssignmentStatusRequest request) {
        CleaningAssignment assignment = getActiveOrThrow(id);

        if (!isManagerOrAdmin()) {
            Shift shift = shiftRepository.findById(assignment.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", assignment.getShiftId()));
            if (!shift.getUserId().equals(SecurityUtils.getCurrentUserId())) {
                throw new UnauthorizedException("You may only update your own cleaning assignments");
            }
        }

        assignment.setStatus(request.getStatus());
        if (request.getStatus() == CleaningAssignmentStatus.COMPLETED) {
            assignment.setCompletedAt(LocalDateTime.now());
        }

        log.info("Updated cleaning assignment {} to status {}", id, request.getStatus());
        return CleaningAssignmentResponse.fromEntity(assignmentRepository.save(assignment));
    }

    private CleaningAssignment getActiveOrThrow(Long id) {
        CleaningAssignment assignment = assignmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CleaningAssignment", "id", id));
        if (assignment.isDeleted()) {
            throw new ResourceNotFoundException("CleaningAssignment", "id", id);
        }
        return assignment;
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
