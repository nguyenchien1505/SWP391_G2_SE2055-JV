package com.example.SWP391_G2_SE2055_JV.scheduling.controller;

import com.example.SWP391_G2_SE2055_JV.scheduling.dto.CreateShiftChangeRequest;
import com.example.SWP391_G2_SE2055_JV.scheduling.dto.ReviewShiftChangeRequest;
import com.example.SWP391_G2_SE2055_JV.scheduling.dto.ShiftChangeRequestResponse;
import com.example.SWP391_G2_SE2055_JV.scheduling.service.ShiftChangeRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Work Schedule Management — lets staff ask for a change to their own shift and lets a
 * Manager approve (updates the Shift) or reject it.
 *
 * Authorization:
 * - GET: ADMIN_PLATFORM, DIRECTOR, MANAGER, RECEPTIONIST, HOUSEKEEPING (own or all,
 *   see ShiftChangeRequestService#getRequests)
 * - POST (submit): ADMIN_PLATFORM, MANAGER, RECEPTIONIST, HOUSEKEEPING — for their own shift
 * - approve/reject: ADMIN_PLATFORM, MANAGER only
 */
@RestController
@RequestMapping("/scheduling/shift-change-requests")
@RequiredArgsConstructor
public class ShiftChangeRequestController {

    private final ShiftChangeRequestService requestService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'DIRECTOR', 'MANAGER', 'RECEPTIONIST', 'HOUSEKEEPING')")
    public ResponseEntity<Page<ShiftChangeRequestResponse>> getRequests(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(requestService.getRequests(pageable));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER')")
    public ResponseEntity<Page<ShiftChangeRequestResponse>> getPendingRequests(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(requestService.getPendingRequests(pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER', 'RECEPTIONIST', 'HOUSEKEEPING')")
    public ResponseEntity<ShiftChangeRequestResponse> submitRequest(
            @Valid @RequestBody CreateShiftChangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requestService.submitRequest(request));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER')")
    public ResponseEntity<ShiftChangeRequestResponse> approveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewShiftChangeRequest review) {
        return ResponseEntity.ok(requestService.approveRequest(id, review));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER')")
    public ResponseEntity<ShiftChangeRequestResponse> rejectRequest(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewShiftChangeRequest review) {
        return ResponseEntity.ok(requestService.rejectRequest(id, review));
    }
}
