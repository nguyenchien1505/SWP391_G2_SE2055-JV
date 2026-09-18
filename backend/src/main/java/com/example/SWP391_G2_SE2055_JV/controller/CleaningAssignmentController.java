package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.CleaningAssignmentResponse;
import com.example.SWP391_G2_SE2055_JV.dto.CreateCleaningAssignmentRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateCleaningAssignmentStatusRequest;
import com.example.SWP391_G2_SE2055_JV.service.CleaningAssignmentService;
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
 * Cleaning Schedule Management — assigning rooms to a housekeeping shift and tracking
 * completion. Mounted under {@code /scheduling/**} (not its own {@code /housekeeping/**}
 * prefix) because SecurityConfig already groups "shifts + housekeeping" under one path;
 * see SecurityConfig for the narrow HOUSEKEEPING write rule on the status endpoint.
 *
 * Authorization:
 * - GET: ADMIN_PLATFORM, DIRECTOR, MANAGER, RECEPTIONIST, HOUSEKEEPING (own or all,
 *   see CleaningAssignmentService#getAssignments)
 * - POST (assign): ADMIN_PLATFORM, MANAGER only
 * - PATCH .../status: ADMIN_PLATFORM, MANAGER, or HOUSEKEEPING for their own assignment
 */
@RestController
@RequestMapping("/scheduling/cleaning-assignments")
@RequiredArgsConstructor
public class CleaningAssignmentController {

    private final CleaningAssignmentService assignmentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'DIRECTOR', 'MANAGER', 'RECEPTIONIST', 'HOUSEKEEPING')")
    public ResponseEntity<Page<CleaningAssignmentResponse>> getAssignments(
            @PageableDefault(size = 20, sort = "assignedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(assignmentService.getAssignments(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'DIRECTOR', 'MANAGER', 'RECEPTIONIST', 'HOUSEKEEPING')")
    public ResponseEntity<CleaningAssignmentResponse> getAssignmentById(@PathVariable Long id) {
        return ResponseEntity.ok(assignmentService.getAssignmentById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER')")
    public ResponseEntity<CleaningAssignmentResponse> createAssignment(
            @Valid @RequestBody CreateCleaningAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.createAssignment(request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER', 'HOUSEKEEPING')")
    public ResponseEntity<CleaningAssignmentResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCleaningAssignmentStatusRequest request) {
        return ResponseEntity.ok(assignmentService.updateStatus(id, request));
    }
}
