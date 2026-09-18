package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftChangeRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ReviewShiftChangeRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftChangeRequestResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftChangeRequest;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.ShiftChangeRequestStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftChangeRequestRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Work Schedule Management: lets a staff member ask for a change to their own shift and
 * lets a Manager approve (which updates the underlying Shift) or reject it. Backs the
 * "Request shift change" / "Review shift-change request" steps added to the process
 * diagram; the {@code shift_change_requests} table was previously only a name in the
 * ERD's domain index with no columns, migration, or code behind it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftChangeRequestService {

    private final ShiftChangeRequestRepository requestRepository;
    private final ShiftRepository shiftRepository;

    @Transactional(readOnly = true)
    public Page<ShiftChangeRequestResponse> getRequests(Pageable pageable) {
        Page<ShiftChangeRequest> page = isManagerOrAdmin()
            ? requestRepository.findByDeletedFalse(pageable)
            : requestRepository.findByRequestedByAndDeletedFalse(SecurityUtils.getCurrentUserId(), pageable);
        return page.map(ShiftChangeRequestResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<ShiftChangeRequestResponse> getPendingRequests(Pageable pageable) {
        return requestRepository.findByStatusAndDeletedFalse(ShiftChangeRequestStatus.PENDING, pageable)
            .map(ShiftChangeRequestResponse::fromEntity);
    }

    @Transactional
    public ShiftChangeRequestResponse submitRequest(CreateShiftChangeRequest request) {
        Shift shift = shiftRepository.findById(request.getShiftId())
            .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", request.getShiftId()));

        Long callerId = SecurityUtils.getCurrentUserId();
        if (!isManagerOrAdmin() && !shift.getUserId().equals(callerId)) {
            throw new UnauthorizedException("You may only request a change to your own shift");
        }

        ShiftChangeRequest entity = ShiftChangeRequest.builder()
            .shiftId(shift.getId())
            .requestedBy(callerId)
            .requestedDate(request.getRequestedDate())
            .requestedStartTime(request.getRequestedStartTime())
            .requestedEndTime(request.getRequestedEndTime())
            .reason(request.getReason())
            .status(ShiftChangeRequestStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .createdBy(callerId)
            .build();

        ShiftChangeRequest saved = requestRepository.save(entity);
        log.info("Shift-change request {} submitted for shift {} by user {}", saved.getId(), shift.getId(), callerId);
        return ShiftChangeRequestResponse.fromEntity(saved);
    }

    @Transactional
    public ShiftChangeRequestResponse approveRequest(Long id, ReviewShiftChangeRequest review) {
        ShiftChangeRequest request = getPendingOrThrow(id);

        Shift shift = shiftRepository.findById(request.getShiftId())
            .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", request.getShiftId()));
        if (request.getRequestedDate() != null) shift.setShiftDate(request.getRequestedDate());
        if (request.getRequestedStartTime() != null) shift.setStartTime(request.getRequestedStartTime());
        if (request.getRequestedEndTime() != null) shift.setEndTime(request.getRequestedEndTime());
        shiftRepository.save(shift);

        request.setStatus(ShiftChangeRequestStatus.APPROVED);
        applyReview(request, review);

        log.info("Approved shift-change request {}, updated shift {}", id, shift.getId());
        return ShiftChangeRequestResponse.fromEntity(requestRepository.save(request));
    }

    @Transactional
    public ShiftChangeRequestResponse rejectRequest(Long id, ReviewShiftChangeRequest review) {
        ShiftChangeRequest request = getPendingOrThrow(id);
        request.setStatus(ShiftChangeRequestStatus.REJECTED);
        applyReview(request, review);

        log.info("Rejected shift-change request {}", id);
        return ShiftChangeRequestResponse.fromEntity(requestRepository.save(request));
    }

    private void applyReview(ShiftChangeRequest request, ReviewShiftChangeRequest review) {
        request.setReviewedBy(SecurityUtils.getCurrentUserId());
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(review == null ? null : review.getReviewNote());
    }

    private ShiftChangeRequest getPendingOrThrow(Long id) {
        ShiftChangeRequest request = requestRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ShiftChangeRequest", "id", id));
        if (request.isDeleted()) {
            throw new ResourceNotFoundException("ShiftChangeRequest", "id", id);
        }
        if (request.getStatus() != ShiftChangeRequestStatus.PENDING) {
            throw new BusinessException("Shift-change request " + id + " has already been reviewed");
        }
        return request;
    }

    private boolean isManagerOrAdmin() {
        return SecurityUtils.hasAnyRole(Role.ADMIN_PLATFORM, Role.MANAGER, Role.DIRECTOR);
    }
}
